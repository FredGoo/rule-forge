//! `Activity` — RETE node runtime trait, mirror of Java `Activity` +
//! `AbstractActivity` + `Instance`.
//!
//! Every node in the RETE graph (`ObjectTypeActivity`,
//! `CriteriaActivity`, `TerminalActivity`, `AndActivity`, `OrActivity`)
//! implements this trait. The `enter` method takes a fact and an
//! `EvaluationContext`, and returns a list of `ActivityOutcome`s
//! (Activations created, debug events, or empty for pass-through
//! nodes).
//!
//! ## How enter() chains
//!
//! `AbstractActivity::visitPaths` (Java) / our `AbstractActivity` trait's
//! `visit_paths` helper loops over each child `Path`, marks it
//! `passed = true`, and recursively calls `child.enter(fact, ctx)`. The
//! returned outcomes aggregate into the parent's outcome list.
//!
//! ## P1 scope
//!
//! - `join_node_is_passed` / `pass_and_node` are And/Or join glue
//!   used in P3. For P1 they return false / no-op.
//! - `reset` clears per-cycle state (the `passed` flag on this
//!   activity + its paths).

use std::sync::Arc;

use crate::fact::GeneralEntity;

use super::evaluation_context::EvaluationContext;
use super::path::Path;

/// What an `enter` call returns. V5.25 P1 only needs `Activation`;
/// `Debug` (for trace logging) and `PassThrough` (intermediate
/// nodes) come in P3.
#[derive(Debug, Clone, PartialEq)]
pub enum ActivityOutcome {
    /// A rule fired — collect into the agenda.
    Activation(Activation),
    /// A pass-through marker (no agenda effect). Used by And/Or
    /// join nodes that just propagate.
    PassThrough,
}

/// `ActionTemplate` — minimal "what to do when this rule fires"
/// payload attached to each `Activation`. P1 only supports
/// `VariableAssignAction` (write a value to a var). P5 expands to
/// scoring / method calls.
#[derive(Debug, Clone, PartialEq)]
pub struct ActionTemplate {
    pub target: String,
    pub value: serde_json::Value,
}

/// `Activation` — the unit the Agenda schedules. V5.25 P1 carries
/// just the `Rule` id + salience; `object_criteria_map` comes in P4.
/// The `action_template` is set by the engine (after the agenda
/// picks the activation) to drive `Rhs.actions` execution.
#[derive(Debug, Clone, PartialEq)]
pub struct Activation {
    pub rule_id: String,
    pub rule_name: String,
    pub salience: i32,
    /// Optional P1 action payload (variable assign). `None` for
    /// activations whose rule has no RHS / where the engine
    /// hasn't attached an action yet. Set by
    /// [`crate::rete_engine::ReteRuleEngine`] before firing.
    pub action_template: Option<ActionTemplate>,
}

impl Activation {
    pub fn new(rule_id: impl Into<String>, rule_name: impl Into<String>, salience: i32) -> Self {
        Self {
            rule_id: rule_id.into(),
            rule_name: rule_name.into(),
            salience,
            action_template: None,
        }
    }

    /// Attach a P1 `VariableAssignAction` (target, value) to this
    /// activation. The `ReteRuleEngine::fire_rules` consumes it to
    /// write back into `ctx.vars`.
    pub fn with_action(
        mut self,
        target: impl Into<String>,
        value: serde_json::Value,
    ) -> Self {
        self.action_template = Some(ActionTemplate {
            target: target.into(),
            value,
        });
        self
    }
}

/// `Activity` — the RETE runtime trait.
pub trait Activity {
    /// Process one fact. Returns the outcomes produced by this
    /// activity + all downstream activities it propagated to.
    fn enter(
        &self,
        fact: &GeneralEntity,
        ctx: &mut EvaluationContext,
    ) -> Vec<ActivityOutcome>;

    /// Reset per-fire-cycle state (e.g. `passed` flag on this
    /// activity + its outbound paths).
    fn reset(&mut self);

    /// For And/Or join nodes: have all required inbound paths
    /// already fired this cycle? (Used to short-circuit a no-op
    /// re-fire when the same fact matches the same criteria twice
    /// in one fire cycle.)
    fn join_node_is_passed(&self) -> bool;

    /// Pass-and-node glue: when a criteria fails, propagate
    /// "this branch is blocked" downstream so an And/Or knows to
    /// re-evaluate. P3.
    fn pass_and_node(&mut self);
}

/// `AbstractActivity` — common behavior for nodes that have
/// outbound `Path`s. Mirrors Java's `AbstractActivity.visitPaths` +
/// `doPassAndNode`. Concrete nodes (ObjectType / Criteria / Terminal)
/// delegate to this for the "fire my children" part.
pub trait AbstractActivity: Activity {
    /// Outbound paths. `&[Arc<Path>]` for shared ownership.
    fn paths(&self) -> &[Arc<Path>];

    /// Walk each child path, mark it passed, recurse. Aggregates
    /// downstream outcomes.
    fn visit_paths(
        &self,
        fact: &GeneralEntity,
        ctx: &mut EvaluationContext,
    ) -> Vec<ActivityOutcome> {
        let mut outcomes = Vec::new();
        for path in self.paths() {
            path.mark_passed();
            // `path.to()` returns `&Arc<dyn Activity + Send + Sync>`;
            // double-deref to call `enter` through `&dyn Activity`.
            let activity_rc: &Arc<dyn Activity + Send + Sync> = path.to();
            let activity: &dyn Activity = &**activity_rc;
            outcomes.extend(activity.enter(fact, ctx));
        }
        outcomes
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn activation_construct() {
        let a = Activation::new("r1", "approve", 10);
        assert_eq!(a.rule_id, "r1");
        assert_eq!(a.salience, 10);
    }

    #[test]
    fn outcome_distinguishes_activation_and_passthrough() {
        let act = ActivityOutcome::Activation(Activation::new("r", "n", 0));
        let pt = ActivityOutcome::PassThrough;
        assert!(matches!(act, ActivityOutcome::Activation(_)));
        assert!(matches!(pt, ActivityOutcome::PassThrough));
    }
}
