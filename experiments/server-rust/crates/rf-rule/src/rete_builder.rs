//! `ReteBuilder` — compile a `KnowledgePackageWrapper` (the static
//! `Rete` graph) into a runtime `ReteInstance` (the `Activity` graph
//! that processes facts).
//!
//! V5.25 P1 scope: **single-condition rules only**.
//! - One `ObjectTypeActivity` per OTN.
//! - One `CriteriaActivity` per `CriteriaNode` in `all_nodes`.
//! - One `TerminalActivity` per `TerminalNode` in `all_nodes`.
//! - Wires `Path`s from `Line.from` / `to` (after
//!   `KnowledgePackageWrapper::build_deserialize` resolves them to
//!   indices).
//!
//! P3 will add `AndActivity` / `OrActivity` builders; for P1 a rule
//! with `lhs.criterions.len() > 1` is unsupported (we panic with a
//! clear message).

use std::sync::Arc;

use crate::deserialize::KnowledgePackageWrapper;
use crate::fact::Fact;
use crate::model::{Line, ReteNode};
use crate::rete::{
    activity::Activity, AbstractActivity, CriteriaActivity, EvaluationContext,
    ObjectTypeActivity, Path, TerminalActivity,
};

/// `ReteInstance` — the runtime activity graph. V5.25 P1 holds:
/// - `otn_activities: Vec<Arc<ObjectTypeActivity>>` — the entry points
///   keyed by index into the wrapper's `rete.object_type_nodes`.
/// - `criteria_activities: Vec<Option<Arc<CriteriaActivity>>>` —
///   the criteria activities, addressed by `all_nodes` index.
/// - `terminal_activities: Vec<Option<Arc<TerminalActivity>>>` —
///   the terminal slots, parallel to `all_nodes`. We keep them typed
///   rather than `Arc<dyn Activity>` so the `ReteRuleEngine` can read
///   the rule id / salience directly.
///
/// `Send + Sync`: required by `RuleEngine: Send + Sync`. All fields
/// use `Arc` (Send) + `Path { AtomicBool }` (Sync) so the whole
/// struct is `Send + Sync` without `Arc<Mutex<…>>`.
pub struct ReteInstance {
    pub otn_activities: Vec<Arc<ObjectTypeActivity>>,
    pub criteria_activities: Vec<Option<Arc<CriteriaActivity>>>,
    pub terminal_activities: Vec<Option<Arc<TerminalActivity>>>,
}

impl ReteInstance {
    /// Build a ReteInstance from a `KnowledgePackageWrapper`. The
    /// caller is responsible for calling `build_deserialize` first
    /// (so `Line.from` / `to` indices are populated).
    pub fn from_wrapper(wrapper: &KnowledgePackageWrapper) -> Self {
        let mut otn_activities = Vec::with_capacity(
            wrapper.knowledge_package.rete.object_type_nodes.len(),
        );
        for otn_node in &wrapper.knowledge_package.rete.object_type_nodes {
            let ReteNode::ObjectType {
                object_type_class, ..
            } = otn_node
            else {
                panic!("rete.object_type_nodes must all be ObjectType");
            };
            let class = object_type_class.clone().unwrap_or_default();
            otn_activities.push(Arc::new(ObjectTypeActivity::new(class)));
        }

        let mut criteria_activities = Vec::with_capacity(wrapper.all_nodes.len());
        let mut terminal_activities = Vec::with_capacity(wrapper.all_nodes.len());
        for node in &wrapper.all_nodes {
            match node {
                ReteNode::Criteria { criteria, debug, .. } => {
                    criteria_activities.push(Some(Arc::new(
                        CriteriaActivity::new(criteria.clone(), *debug),
                    )));
                    terminal_activities.push(None);
                }
                ReteNode::Terminal { rule, .. } => {
                    criteria_activities.push(None);
                    terminal_activities.push(Some(Arc::new(TerminalActivity::new(
                        rule.id.clone(),
                        rule.name.clone(),
                        rule.salience,
                        rule.activation_group.clone(),
                        rule.agenda_group.clone(),
                    ))));
                }
                ReteNode::And { .. } | ReteNode::Or { .. } => {
                    panic!("And/Or not supported in P1 — see P3 plan")
                }
                ReteNode::ObjectType { .. } => {
                    criteria_activities.push(None);
                    terminal_activities.push(None);
                }
            }
        }

        // Wire paths. Order matters: wire the criteria→terminal
        // edges FIRST while each `Arc<CriteriaActivity>` has refcount
        // 1 (only the Vec holds a ref), so `Arc::get_mut` is cheap
        // (no clone). THEN wire the OTN→criteria edges, which bump
        // the crit refcount to 2 (Vec + OTN's Path) but we don't
        // need to mutate the crit after that.
        Self::wire_criteria_paths(wrapper, &mut criteria_activities, &terminal_activities);
        Self::wire_otn_paths(
            wrapper,
            &mut otn_activities,
            &criteria_activities,
        );

        ReteInstance {
            otn_activities,
            criteria_activities,
            terminal_activities,
        }
    }

    fn wire_otn_paths(
        wrapper: &KnowledgePackageWrapper,
        otns: &mut [Arc<ObjectTypeActivity>],
        crits: &[Option<Arc<CriteriaActivity>>],
    ) {
        for (i, otn_node) in wrapper
            .knowledge_package
            .rete
            .object_type_nodes
            .iter()
            .enumerate()
        {
            let ReteNode::ObjectType { lines, .. } = otn_node else {
                continue;
            };
            for line in lines {
                let Some(to_idx) = line.to else { continue };
                if let Some(Some(crit)) = crits.get(to_idx) {
                    let target: Arc<dyn Activity + Send + Sync> = crit.clone();
                    let path = Arc::new(Path::new(target));
                    // OTN is freshly wrapped; refcount is 1 → no clone.
                    if let Some(otn_arc) = otns.get_mut(i) {
                        if let Some(otn_mut) = Arc::get_mut(otn_arc) {
                            otn_mut.add_path(path);
                        }
                    }
                }
            }
        }
    }

    fn wire_criteria_paths(
        wrapper: &KnowledgePackageWrapper,
        crits: &mut [Option<Arc<CriteriaActivity>>],
        terms: &[Option<Arc<TerminalActivity>>],
    ) {
        for (i, node) in wrapper.all_nodes.iter().enumerate() {
            let lines: &[Line] = match node {
                ReteNode::Criteria { lines, .. } => lines,
                _ => continue,
            };
            let Some(crit_slot) = crits.get_mut(i) else { continue };
            let Some(crit_arc) = crit_slot.as_mut() else { continue };
            for line in lines {
                let Some(to_idx) = line.to else { continue };
                if let Some(Some(term)) = terms.get(to_idx) {
                    let target: Arc<dyn Activity + Send + Sync> = term.clone();
                    let path = Arc::new(Path::new(target));
                    // Crits are freshly wrapped; refcount is 1 → no clone.
                    if let Some(crit_mut) = Arc::get_mut(crit_arc) {
                        crit_mut.add_path(path);
                    }
                }
            }
        }
    }

    /// Run a fact through the network. Returns the list of
    /// `Activation`s produced.
    pub fn enter(
        &self,
        fact: &crate::fact::GeneralEntity,
        ctx: &mut EvaluationContext,
    ) -> Vec<crate::rete::Activation> {
        let mut out = Vec::new();
        for otn_arc in &self.otn_activities {
            let otn: &ObjectTypeActivity = &**otn_arc;
            if !otn.supports(fact.class_name()) {
                continue;
            }
            for o in <ObjectTypeActivity as Activity>::enter(otn, fact, ctx) {
                if let crate::rete::ActivityOutcome::Activation(a) = o {
                    out.push(a);
                }
            }
        }
        out
    }

    /// Reset all activities' per-cycle state. Called by
    /// `ReteRuleEngine` at the start of every fire cycle.
    ///
    /// `Activity::reset` takes `&mut self` to flip the activity's
    /// `passed` flag, but we only have `&self` on the instance (the
    /// engine is shared via `Arc<dyn RuleEngine>`). Activities are
    /// `#[derive(Clone)]` so we clone each into a local owned
    /// mutable, reset, and discard.
    pub fn reset(&self) {
        for otn_arc in &self.otn_activities {
            let mut owned: ObjectTypeActivity = (**otn_arc).clone();
            <ObjectTypeActivity as Activity>::reset(&mut owned);
        }
        for c in &self.criteria_activities {
            if let Some(c_arc) = c {
                let mut owned: CriteriaActivity = (**c_arc).clone();
                <CriteriaActivity as Activity>::reset(&mut owned);
            }
        }
        // Terminal has no per-cycle state; skip.
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
    use crate::model::{Criteria, Left, Rete};
    use crate::deserialize::KnowledgePackage;

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
            lhs: crate::model::Lhs::default(),
            rhs: crate::model::Rhs::default(),
            r#loop: false,
            remark: None,
            with_else: false,
        }
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
                constant_value: Some(serde_json::json!(18)),
            }),
        }
    }

    #[test]
    fn build_minimal_single_criteria_rule() {
        let otn = ReteNode::ObjectType {
            id: 1,
            object_type_class: Some("Applicant".into()),
            lines: vec![Line {
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
            lines: vec![Line {
                from_node_id: 2,
                to_node_id: 3,
                from: None,
                to: None,
            }],
        };
        let term = ReteNode::Terminal {
            id: 3,
            rule: simple_rule("r1"),
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
        let instance = ReteInstance::from_wrapper(&wrap);
        assert_eq!(instance.otn_activities.len(), 1);
        assert_eq!(instance.criteria_activities.len(), 2);
        assert!(instance.criteria_activities[0].is_some());
        assert!(instance.terminal_activities[1].is_some());
    }
}
