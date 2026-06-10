//! `TerminalActivity` — leaf node. Emits an `Activation` when
//! reached. Mirror of Java
//! `com.ruleforge.runtime.rete.TerminalActivity`.
//!
//! Java also fires an `ActivationCreatedEvent` via the
//! `KnowledgeSession`; V5.25 P1 doesn't ship the event bus (that's
//! P4 alongside the Agenda), so the `enter` method just returns
//! the `Activation` outcome. The caller (the `ReteInstance` in
//! `rete_engine.rs`) collects activations and runs them in salience
//! order.

use super::activity::{AbstractActivity, Activation, Activity, ActivityOutcome};
use super::evaluation_context::EvaluationContext;
use super::path::Path;
use crate::fact::GeneralEntity;

use std::sync::Arc;

/// `TerminalActivity` — leaf, holds the rule reference and emits an
/// `Activation` on `enter`.
pub struct TerminalActivity {
    rule_id: String,
    rule_name: String,
    salience: i32,
    /// `ActivationGroupRuleBox` / `AgendaGroupRuleBox` metadata.
    /// P1 stores these but doesn't act on them (P4 wires the
    /// agenda).
    #[allow(dead_code)]
    activation_group: Option<String>,
    #[allow(dead_code)]
    agenda_group: Option<String>,
}

impl TerminalActivity {
    pub fn new(
        rule_id: impl Into<String>,
        rule_name: impl Into<String>,
        salience: i32,
        activation_group: Option<String>,
        agenda_group: Option<String>,
    ) -> Self {
        Self {
            rule_id: rule_id.into(),
            rule_name: rule_name.into(),
            salience,
            activation_group,
            agenda_group,
        }
    }

    /// Test helper — minimal constructor for unit tests. Real
    /// `rete_engine.rs` uses `new(...)` with the full Rule.
    pub fn for_test(
        rule_id: impl Into<String>,
        rule_name: impl Into<String>,
        salience: i32,
    ) -> Self {
        Self::new(rule_id, rule_name, salience, None, None)
    }
}

impl Activity for TerminalActivity {
    fn enter(
        &self,
        _fact: &GeneralEntity,
        _ctx: &mut EvaluationContext,
    ) -> Vec<ActivityOutcome> {
        // Java `TerminalActivity.enter` creates `ActivationImpl` +
        // fires `ActivationCreatedEvent`. V5.25 P1 just emits the
        // `ActivityOutcome::Activation` for the engine to collect.
        // P4 also attaches activation_group / agenda_group
        // metadata so the engine can do mutual-exclusion and
        // focus routing.
        vec![ActivityOutcome::Activation(
            Activation::new(
                self.rule_id.clone(),
                self.rule_name.clone(),
                self.salience,
            )
            .with_groups(
                self.activation_group.clone(),
                self.agenda_group.clone(),
            ),
        )]
    }

    fn reset(&mut self) {
        // Terminal has no per-cycle state of its own.
    }

    fn join_node_is_passed(&self) -> bool {
        false
    }

    fn pass_and_node(&mut self) {
        // No-op.
    }

    fn as_any_mut(&mut self) -> &mut dyn std::any::Any {
        self
    }
}

impl AbstractActivity for TerminalActivity {
    fn paths(&self) -> &[Arc<Path>] {
        &[] // leaf — no children.
    }
    fn push_path(&mut self, _path: Arc<Path>) {
        // Leaf — no outbound paths. The wire phase skips Terminal
        // sources, so this is unreachable in practice.
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use rf_executor::vars::Vars;
    use rf_executor::working_memory::WorkingMemory;
    use std::sync::Arc;

    fn ctx() -> EvaluationContext {
        let wm: std::rc::Rc<std::cell::RefCell<dyn WorkingMemory>> =
            std::rc::Rc::new(std::cell::RefCell::new(Vars::new()));
        EvaluationContext::new(wm)
    }

    #[test]
    fn enter_emits_one_activation() {
        let t = TerminalActivity::for_test("r1", "approve", 10);
        let mut ctx = ctx();
        let fact = GeneralEntity::new("Applicant");
        let out = t.enter(&fact, &mut ctx);
        assert_eq!(out.len(), 1);
        match &out[0] {
            ActivityOutcome::Activation(a) => {
                assert_eq!(a.rule_id, "r1");
                assert_eq!(a.rule_name, "approve");
                assert_eq!(a.salience, 10);
            }
            _ => panic!("expected Activation"),
        }
    }
}
