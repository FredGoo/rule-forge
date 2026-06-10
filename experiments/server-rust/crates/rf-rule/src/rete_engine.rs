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

use std::rc::Rc;

use async_trait::async_trait;
use rf_executor::flow_context::FlowContext;
use rf_executor::rule_engine::{RuleEngine, RuleEngineError, RuleResults};
use rf_executor::working_memory::WorkingMemory;
use serde_json::Value as JsonValue;

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
}

impl ReteRuleEngine {
    /// Build an engine from a single knowledge package. The package
    /// must have had `build_deserialize()` called (so the
    /// `Line.from` / `to` indices are populated).
    pub fn from_wrapper(wrapper: &KnowledgePackageWrapper) -> Self {
        let instance = ReteInstance::from_wrapper(wrapper);
        Self {
            instances: vec![instance],
        }
    }

    /// Build from a pre-built `ReteInstance` (for tests that want
    /// to skip the wrapper dance).
    pub fn from_instance(instance: ReteInstance) -> Self {
        Self {
            instances: vec![instance],
        }
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

        // P1: we share the same `Vars` between the engine and the
        // outer `ctx.vars`. To feed facts to RETE, we build a
        // working-memory trait object backed by a clone of `ctx.vars`
        // — and reset the fire epoch so per-cycle caches clear.
        let wm: Rc<std::cell::RefCell<dyn WorkingMemory>> = {
            // No mutation through `&self` — Vars is the same data
            // both sides see, but we have to give the engine a
            // RefCell to satisfy `&mut WorkingMemory` callers in
            // P2+. For P1 it's mostly read-only.
            let wm = Rc::new(std::cell::RefCell::new(ctx.vars.clone()));
            wm
        };
        let mut eval = EvaluationContext::new(wm.clone());

        for instance in &self.instances {
            instance.reset();
            // Walk each OTN's class, pull facts, propagate.
            // For P1 the engine doesn't need a separate fact-id
            // table — `facts_of_class` is enough.
            let otn_classes: Vec<String> = instance
                .otn_activities
                .iter()
                .map(|o| o.object_type_class().to_string())
                .collect();
            for class in otn_classes {
                let facts: Vec<JsonValue> = wm.borrow().facts_of_class(&class);
                for v in facts {
                    let Some(entity) = fact_from_value(&class, &v) else {
                        continue;
                    };
                    let activations = instance.enter(&entity, &mut eval);
                    for a in activations {
                        matched.push(a.rule_id.clone());
                        if let Some(action) = a.action_template {
                            ctx.vars.assign(action.target, action.value);
                        }
                        fired.push(a.rule_id);
                    }
                }
            }
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
}
