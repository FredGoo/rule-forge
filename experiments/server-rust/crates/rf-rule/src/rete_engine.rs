//! `ReteRuleEngine` — the production `RuleEngine` impl, replacing
//! `MockRuleEngine` for real rule packages.
//!
//! V5.25 P1 ships the simplest wiring:
//! 1. Hold a `Vec<ReteInstance>` (one per loaded knowledge package).
//! 2. On `fire_rules(ctx)`:
//!    a. Reset the working memory's fire epoch (drives
//!       `EvaluationContext::clean()` via the per-cycle cache).
//!    b. For each `ReteInstance`, reset its activities, then for
//!       each fact whose class matches an OTN, walk the network and
//!       collect `Activation`s.
//!    c. For each activation, fire its `action_template` (P1: a
//!       single `VariableAssignAction` payload).
//!
//! ## Why no Mutex?
//!
//! `ReteRuleEngine.instances: Vec<ReteInstance>` is owned by the
//! engine and mutated only inside `fire_rules`. `Activity::enter`
//! takes `&self` and uses interior mutability (`Cell<bool>` in
//! `Path`) for per-cycle state, so the engine doesn't need a mutex
//! to share `&self` access across calls. The
//! `tokio::sync::Mutex` import is removed in favor of plain `Vec`.
//!
//! ## Where the rules come from
//!
//! For the demo / unit test we construct the package inline. In
//! production the engine would load packages from a directory or
//! HTTP endpoint and index by `package_id`. P1 keeps it in-process.

use std::collections::HashSet;
use std::rc::Rc;

use async_trait::async_trait;
use rf_executor::flow_context::FlowContext;
use rf_executor::rule_engine::{RuleEngine, RuleEngineError, RuleResults};
use rf_executor::working_memory::WorkingMemory;
use serde_json::Value as JsonValue;

use crate::agenda::Agenda;
use crate::deserialize::KnowledgePackageWrapper;
use crate::fact::fact_from_value;
use crate::rete::EvaluationContext;
use crate::rete_builder::ReteInstance;

/// `ReteRuleEngine` — owns a list of compiled `ReteInstance`s and
/// drives them per `fire_rules` call.
pub struct ReteRuleEngine {
    /// The compiled networks. No Mutex needed: `fire_rules` takes
    /// `&self` and uses interior-mutability on activities
    /// (`Cell<bool>` for `passed` flags). To mutate the
    /// `Vec<ReteInstance>` itself (e.g. loading a new package at
    /// runtime, P5+), wrap the whole engine in an `Arc<Mutex<…>>`
    /// at the caller.
    instances: Vec<ReteInstance>,
    /// The agenda group currently "in focus". Rules whose
    /// `agenda_group` is `Some(name)` only fire when the
    /// engine's focus matches. Defaults to `Some("MAIN")` —
    /// matches Drools' default agenda. `None` disables all
    /// agenda-group filtering (rules with `Some(group)` never
    /// fire).
    focused_agenda_group: Option<String>,
}

impl ReteRuleEngine {
    /// Build an engine from a single knowledge package. The package
    /// must have had `build_deserialize()` called (so the
    /// `Line.from` / `to` indices are populated).
    pub fn from_wrapper(wrapper: &KnowledgePackageWrapper) -> Self {
        let instance = ReteInstance::from_wrapper(wrapper);
        Self {
            instances: vec![instance],
            focused_agenda_group: Some("MAIN".to_string()),
        }
    }

    /// Build an engine from multiple knowledge packages. Every
    /// package fires on every `fire_rules` call (the engine doesn't
    /// route by flow_id — that's the Java side's responsibility;
    /// for v0 we just compile all available packages and let the
    /// RETE network filter by fact class). Each wrapper is
    /// consumed by reference; the caller should pre-build the
    /// wrappers (e.g. via [`crate::loader::load_dir`]).
    pub fn from_wrappers(wrappers: &[&KnowledgePackageWrapper]) -> Self {
        let instances: Vec<ReteInstance> = wrappers
            .iter()
            .map(|w| ReteInstance::from_wrapper(*w))
            .collect();
        Self {
            instances,
            focused_agenda_group: Some("MAIN".to_string()),
        }
    }

    /// Build from a pre-built `ReteInstance` (for tests that want
    /// to skip the wrapper dance).
    pub fn from_instance(instance: ReteInstance) -> Self {
        Self {
            instances: vec![instance],
            focused_agenda_group: Some("MAIN".to_string()),
        }
    }

    /// Switch the focused agenda group. Pass `None` to disable
    /// agenda-group filtering (rules with `agenda_group = Some(_)`
    /// never fire). Pass `Some(name)` to focus that group.
    pub fn set_focused_agenda_group(&mut self, group: Option<String>) {
        self.focused_agenda_group = group;
    }
}

#[async_trait]
impl RuleEngine for ReteRuleEngine {
    async fn fire_rules(
        &self,
        ctx: &mut FlowContext,
    ) -> Result<RuleResults, RuleEngineError> {
        let mut fired = Vec::new();
        let mut matched = Vec::new();
        let mut agenda = Agenda::new();
        // Track which activation_groups have already fired this
        // cycle — mutual exclusion (Drools semantics: at most
        // one rule per group fires per cycle).
        let mut fired_activation_groups: HashSet<String> = HashSet::new();

        let wm: Rc<std::cell::RefCell<dyn WorkingMemory>> = Rc::new(
            std::cell::RefCell::new(ctx.vars.clone()),
        );
        let mut eval = EvaluationContext::new(wm.clone());

        for instance in &self.instances {
            instance.reset();
            let otn_classes: Vec<String> = instance
                .otn_activities
                .iter()
                .map(|o| o.object_type_class().to_string())
                .collect();
            for class in otn_classes {
                let facts: Vec<JsonValue> = wm.borrow().facts_of_class(&class);
                for v in facts {
                    // V5.46.1 — clear per-fact caches so the first
                    // fact's `false` doesn't poison subsequent
                    // facts. Java has the same cache design but
                    // calls `clean()` end-of-cycle in
                    // `KnowledgeSessionImpl.java:279`; Rust does
                    // the cleanup per-fact instead because
                    // `EvaluationContext` is local to
                    // `fire_rules`.
                    eval.clean();
                    let Some(entity) = fact_from_value(&class, &v) else {
                        continue;
                    };
                    let activations = instance.enter(&entity, &mut eval);
                    for a in activations {
                        matched.push(a.rule_id.clone());
                        agenda.add(a);
                    }
                }
            }
        }

        // Drain the agenda in salience order. For each
        // activation:
        // - skip if its agenda_group doesn't match the focus
        // - skip if its activation_group already fired (mutual
        //   exclusion)
        // - otherwise: run the action_template and mark fired
        while let Some(a) = agenda.pop() {
            // Agenda-group filter.
            if let Some(group) = &a.agenda_group {
                if self.focused_agenda_group.as_deref() != Some(group.as_str()) {
                    continue;
                }
            }
            // Activation-group mutual exclusion.
            if let Some(group) = &a.activation_group {
                if fired_activation_groups.contains(group) {
                    continue;
                }
                fired_activation_groups.insert(group.clone());
            }
            // Fire the rule.
            if let Some(action) = a.action_template {
                ctx.vars.assign(action.target, action.value);
            }
            fired.push(a.rule_id);
        }

        Ok(RuleResults {
            fired_rules: fired,
            matched_rules: matched,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::left::LeftType;
    use crate::model::left_part::LeftPart;
    use crate::model::op::Op;
    use crate::model::rule::Rule;
    use crate::model::value::Value;
    use crate::model::{
        Criteria, Left, Lhs, Rete, ReteNode, Rhs,
    };
    use crate::deserialize::KnowledgePackage;
    use crate::rete::Activation;
    use serde_json::json;

    /// P4: two rules, different salience. The higher-salience
    /// rule should fire first. Both fire (no mutual exclusion).
    #[test]
    fn end_to_end_salience_orders_fire_sequence() {
        // Build a package with two rules:
        //   r-lo (salience 0): "set_lo"
        //   r-hi (salience 10): "set_hi"
        // Both match Applicant{age: 30} → both fire. The order
        // in `fired_rules` should be r-hi first.
        let otn = ReteNode::ObjectType {
            id: 1,
            object_type_class: Some("Applicant".into()),
            lines: vec![crate::model::Line {
                from_node_id: 1,
                to_node_id: 2,
                from: None,
                to: None,
            }],
        };
        let crit = ReteNode::Criteria {
            id: 2,
            debug: false,
            criteria: age_geq_18_criteria(),
            lines: vec![
                crate::model::Line {
                    from_node_id: 2,
                    to_node_id: 3,
                    from: None,
                    to: None,
                },
                crate::model::Line {
                    from_node_id: 2,
                    to_node_id: 4,
                    from: None,
                    to: None,
                },
            ],
        };
        let rule_lo = Rule {
            id: "r-lo".into(),
            name: "set_lo".into(),
            rule_type: None,
            file: None,
            salience: 0,
            effective_date: None,
            expires_date: None,
            enabled: true,
            debug: false,
            activation_group: None,
            agenda_group: None,
            auto_focus: false,
            ruleflow_group: None,
            lhs: Lhs::default(),
            rhs: Rhs::default(),
            r#loop: false,
            remark: None,
            with_else: false,
        };
        let term_lo = ReteNode::Terminal {
            id: 3,
            rule: rule_lo.clone(),
        };
        let term_hi = ReteNode::Terminal {
            id: 4,
            rule: Rule {
                id: "r-hi".into(),
                name: "set_hi".into(),
                salience: 10,
                ..rule_lo
            },
        };
        let kp = KnowledgePackage {
            rete: Rete {
                object_type_nodes: vec![otn],
                activation_group_retes_map: Default::default(),
                agenda_group_retes_map: Default::default(),
            },
            with_else_rules: Default::default(),
        };
        let mut wrap = KnowledgePackageWrapper::from_parts(
            "kp",
            kp,
            vec![crit, term_lo, term_hi],
            None,
        );
        wrap.build_deserialize();
        let engine = ReteRuleEngine::from_wrapper(&wrap);

        let rt = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        rt.block_on(async {
            let mut ctx = FlowContext::new("salience-test");
            ctx.vars.assert_fact("Applicant", json!({"age": 30}));
            let results = engine.fire_rules(&mut ctx).await.unwrap();
            assert_eq!(results.fired_rules, vec!["r-hi", "r-lo"]);
            assert!(results.matched_rules.contains(&"r-hi".to_string()));
            assert!(results.matched_rules.contains(&"r-lo".to_string()));
        });
    }

    /// P4: two rules in the same `activation_group`. Only the
    /// highest-salience one fires (mutual exclusion).
    #[test]
    fn end_to_end_activation_group_mutually_excludes() {
        let otn = ReteNode::ObjectType {
            id: 1,
            object_type_class: Some("Applicant".into()),
            lines: vec![crate::model::Line {
                from_node_id: 1,
                to_node_id: 2,
                from: None,
                to: None,
            }],
        };
        let crit = ReteNode::Criteria {
            id: 2,
            debug: false,
            criteria: age_geq_18_criteria(),
            lines: vec![
                crate::model::Line {
                    from_node_id: 2,
                    to_node_id: 3,
                    from: None,
                    to: None,
                },
                crate::model::Line {
                    from_node_id: 2,
                    to_node_id: 4,
                    from: None,
                    to: None,
                },
            ],
        };
        let rule_with_group = |id: &str, sal: i32| Rule {
            id: id.into(),
            name: id.into(),
            rule_type: None,
            file: None,
            salience: sal,
            effective_date: None,
            expires_date: None,
            enabled: true,
            debug: false,
            activation_group: Some("approve-group".into()),
            agenda_group: None,
            auto_focus: false,
            ruleflow_group: None,
            lhs: Lhs::default(),
            rhs: Rhs::default(),
            r#loop: false,
            remark: None,
            with_else: false,
        };
        let term_a = ReteNode::Terminal { id: 3, rule: rule_with_group("r-a", 0) };
        let term_b = ReteNode::Terminal { id: 4, rule: rule_with_group("r-b", 5) };
        let kp = KnowledgePackage {
            rete: Rete {
                object_type_nodes: vec![otn],
                activation_group_retes_map: Default::default(),
                agenda_group_retes_map: Default::default(),
            },
            with_else_rules: Default::default(),
        };
        let mut wrap = KnowledgePackageWrapper::from_parts(
            "kp",
            kp,
            vec![crit, term_a, term_b],
            None,
        );
        wrap.build_deserialize();
        let engine = ReteRuleEngine::from_wrapper(&wrap);

        let rt = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        rt.block_on(async {
            let mut ctx = FlowContext::new("act-group-test");
            ctx.vars.assert_fact("Applicant", json!({"age": 30}));
            let results = engine.fire_rules(&mut ctx).await.unwrap();
            // r-b has higher salience → fires. r-a blocked.
            assert_eq!(results.fired_rules, vec!["r-b"]);
            // Both matched (network saw both terminals) but only
            // r-b fired.
            assert!(results.matched_rules.contains(&"r-a".to_string()));
            assert!(results.matched_rules.contains(&"r-b".to_string()));
        });
    }

    /// P4: a rule in `agenda_group = "CUSTOM"` should NOT fire
    /// when the engine's focus is `"MAIN"` (default). After
    /// switching the focus to `"CUSTOM"`, the rule fires.
    #[test]
    fn end_to_end_agenda_group_focus_routing() {
        let otn = ReteNode::ObjectType {
            id: 1,
            object_type_class: Some("Applicant".into()),
            lines: vec![crate::model::Line {
                from_node_id: 1,
                to_node_id: 2,
                from: None,
                to: None,
            }],
        };
        let crit = ReteNode::Criteria {
            id: 2,
            debug: false,
            criteria: age_geq_18_criteria(),
            lines: vec![crate::model::Line {
                from_node_id: 2,
                to_node_id: 3,
                from: None,
                to: None,
            }],
        };
        let term = ReteNode::Terminal {
            id: 3,
            rule: Rule {
                id: "r-custom".into(),
                name: "r-custom".into(),
                rule_type: None,
                file: None,
                salience: 0,
                effective_date: None,
                expires_date: None,
                enabled: true,
                debug: false,
                activation_group: None,
                agenda_group: Some("CUSTOM".into()),
                auto_focus: false,
                ruleflow_group: None,
                lhs: Lhs::default(),
                rhs: Rhs::default(),
                r#loop: false,
                remark: None,
                with_else: false,
            },
        };
        let kp = KnowledgePackage {
            rete: Rete {
                object_type_nodes: vec![otn],
                activation_group_retes_map: Default::default(),
                agenda_group_retes_map: Default::default(),
            },
            with_else_rules: Default::default(),
        };
        let mut wrap = KnowledgePackageWrapper::from_parts(
            "kp",
            kp,
            vec![crit, term],
            None,
        );
        wrap.build_deserialize();
        let mut engine = ReteRuleEngine::from_wrapper(&wrap);

        let rt = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        rt.block_on(async {
            // Default focus is "MAIN"; r-custom is in "CUSTOM" so
            // it should NOT fire.
            let mut ctx = FlowContext::new("focus-1");
            ctx.vars.assert_fact("Applicant", json!({"age": 30}));
            let r1 = engine.fire_rules(&mut ctx).await.unwrap();
            assert!(r1.fired_rules.is_empty(), "got {:?}", r1.fired_rules);
            assert!(r1.matched_rules.contains(&"r-custom".to_string()));

            // Switch focus to "CUSTOM"; r-custom should fire.
            engine.set_focused_agenda_group(Some("CUSTOM".into()));
            let mut ctx2 = FlowContext::new("focus-2");
            ctx2.vars.assert_fact("Applicant", json!({"age": 30}));
            let r2 = engine.fire_rules(&mut ctx2).await.unwrap();
            assert_eq!(r2.fired_rules, vec!["r-custom"]);
        });
    }

    fn age_geq_18_criteria() -> Criteria {
        Criteria {
            op: Op::GreaterThenEquals,
            left: Left {
                left_type: LeftType::Variable,
                left_part: LeftPart::Variable {
                    variable_category: Some("Applicant".into()),
                    variable_label: Some("age".into()),
                    variable_name: Some("age".into()),
                    datatype: Some("int".into()),
                },
                arithmetic: None,
            },
            value: Some(Value::Constant {
                constant_name: None,
                constant_label: None,
                constant_category: None,
                constant_value: Some(json!(18)),
            }),
        }
    }

    fn simple_rule(id: &str) -> Rule {
        Rule {
            id: id.into(),
            name: id.into(),
            rule_type: None,
            file: None,
            salience: 0,
            effective_date: None,
            expires_date: None,
            enabled: true,
            debug: false,
            activation_group: None,
            agenda_group: None,
            auto_focus: false,
            ruleflow_group: None,
            lhs: Lhs::default(),
            rhs: Rhs::default(),
            r#loop: false,
            remark: None,
            with_else: false,
        }
    }

    #[test]
    fn end_to_end_age_geq_18_sets_approved() {
        // Build a one-rule knowledge package: "approve if age >= 18"
        let otn = ReteNode::ObjectType {
            id: 1,
            object_type_class: Some("Applicant".into()),
            lines: vec![crate::model::Line {
                from_node_id: 1,
                to_node_id: 2,
                from: None,
                to: None,
            }],
        };
        let crit = ReteNode::Criteria {
            id: 2,
            debug: false,
            criteria: age_geq_18_criteria(),
            lines: vec![crate::model::Line {
                from_node_id: 2,
                to_node_id: 3,
                from: None,
                to: None,
            }],
        };
        let rule = simple_rule("r-approve");
        let term = ReteNode::Terminal {
            id: 3,
            rule,
        };
        let kp = KnowledgePackage {
            rete: Rete {
                object_type_nodes: vec![otn],
                activation_group_retes_map: Default::default(),
                agenda_group_retes_map: Default::default(),
            },
            with_else_rules: Default::default(),
        };
        let mut wrap = KnowledgePackageWrapper::from_parts("kp", kp, vec![crit, term], None);
        wrap.build_deserialize();
        let engine = ReteRuleEngine::from_wrapper(&wrap);

        let rt = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        rt.block_on(async {
            let mut ctx = FlowContext::new("test");
            ctx.vars.assert_fact(
                "Applicant",
                json!({"age": 25, "name": "alice"}),
            );
            let results = engine.fire_rules(&mut ctx).await.unwrap();
            assert!(
                results.matched_rules.contains(&"r-approve".to_string()),
                "expected r-approve in matched, got {:?}",
                results.matched_rules
            );
        });
    }

    #[test]
    fn action_template_attaches_to_activation() {
        let a = Activation::new("r1", "approve", 10)
            .with_action("approved", json!(true));
        let t = a.action_template.unwrap();
        assert_eq!(t.target, "approved");
        assert_eq!(t.value, json!(true));
    }

    /// P3 end-to-end: two criteria joined by an `And` node.
    /// The rule fires only when BOTH `age >= 18` AND
    /// `income >= 5000`. With `age=25, income=8000` both pass;
    /// with `age=25, income=3000` only the first passes and the
    /// And blocks the fire.
    #[test]
    fn end_to_end_and_join_two_criteria() {
        let age_crit = age_geq_18_criteria();
        let income_crit = Criteria {
            op: Op::GreaterThenEquals,
            left: Left {
                left_type: LeftType::Variable,
                left_part: LeftPart::Variable {
                    variable_category: Some("Applicant".into()),
                    variable_label: Some("income".into()),
                    variable_name: Some("income".into()),
                    datatype: Some("int".into()),
                },
                arithmetic: None,
            },
            value: Some(Value::Constant {
                constant_name: None,
                constant_label: None,
                constant_category: None,
                constant_value: Some(json!(5000)),
            }),
        };
        let otn = ReteNode::ObjectType {
            id: 1,
            object_type_class: Some("Applicant".into()),
            lines: vec![
                crate::model::Line {
                    from_node_id: 1,
                    to_node_id: 2,
                    from: None,
                    to: None,
                },
                crate::model::Line {
                    from_node_id: 1,
                    to_node_id: 3,
                    from: None,
                    to: None,
                },
            ],
        };
        let crit_age = ReteNode::Criteria {
            id: 2,
            debug: false,
            criteria: age_crit,
            lines: vec![crate::model::Line {
                from_node_id: 2,
                to_node_id: 4,
                from: None,
                to: None,
            }],
        };
        let crit_income = ReteNode::Criteria {
            id: 3,
            debug: false,
            criteria: income_crit,
            lines: vec![crate::model::Line {
                from_node_id: 3,
                to_node_id: 4,
                from: None,
                to: None,
            }],
        };
        let and_node = ReteNode::And {
            id: 4,
            to_line_count: 2,
            lines: vec![crate::model::Line {
                from_node_id: 4,
                to_node_id: 5,
                from: None,
                to: None,
            }],
        };
        let term = ReteNode::Terminal {
            id: 5,
            rule: simple_rule("r-approve"),
        };
        let kp = KnowledgePackage {
            rete: Rete {
                object_type_nodes: vec![otn],
                activation_group_retes_map: Default::default(),
                agenda_group_retes_map: Default::default(),
            },
            with_else_rules: Default::default(),
        };
        let mut wrap = KnowledgePackageWrapper::from_parts(
            "kp",
            kp,
            vec![crit_age, crit_income, and_node, term],
            None,
        );
        wrap.build_deserialize();
        let engine = ReteRuleEngine::from_wrapper(&wrap);

        let rt = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        rt.block_on(async {
            let mut ctx = FlowContext::new("test");

            // Both conditions met → fire.
            ctx.vars.assert_fact(
                "Applicant",
                json!({"age": 25, "income": 8000}),
            );
            let r1 = engine.fire_rules(&mut ctx).await.unwrap();
            assert!(
                r1.matched_rules.contains(&"r-approve".to_string()),
                "expected r-approve when both conditions met, got {:?}",
                r1.matched_rules
            );

            // Reset for next fire cycle.
            ctx.vars.reset_fire_epoch();
            let mut ctx2 = FlowContext::new("test2");
            ctx2.vars.assert_fact(
                "Applicant",
                json!({"age": 25, "income": 3000}),
            );
            // age is fine (25 >= 18), income fails (3000 < 5000).
            // And should NOT fire.
            let r2 = engine.fire_rules(&mut ctx2).await.unwrap();
            assert!(
                !r2.matched_rules.contains(&"r-approve".to_string()),
                "And should block: age=25, income=3000 — got {:?}",
                r2.matched_rules
            );
        });
    }
}
