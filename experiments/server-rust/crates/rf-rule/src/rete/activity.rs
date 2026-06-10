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
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ActionTemplate {
    pub target: String,
    pub value: serde_json::Value,
}

/// `Activation` — the unit the Agenda schedules. V5.25 P1 carries
/// just the `Rule` id + salience; `object_criteria_map` comes in P4.
/// The `action_template` is set by the engine (after the agenda
/// picks the activation) to drive `Rhs.actions` execution.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Activation {
    pub rule_id: String,
    pub rule_name: String,
    pub salience: i32,
    /// P4: Drools `activation-group` — mutually exclusive. At most
    /// one rule per group can fire per fire cycle. `None` means no
    /// group (always eligible).
    pub activation_group: Option<String>,
    /// P4: Drools `agenda-group` — must be focused to fire. The
    /// engine tracks which group is "in focus" (default: `"MAIN"`).
    /// `None` means it always fires regardless of focus.
    pub agenda_group: Option<String>,
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
            activation_group: None,
            agenda_group: None,
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

    /// Attach activation_group / agenda_group metadata.
    pub fn with_groups(
        mut self,
        activation_group: Option<String>,
        agenda_group: Option<String>,
    ) -> Self {
        self.activation_group = activation_group;
        self.agenda_group = agenda_group;
        self
    }
}

// `Ord` / `PartialOrd` — `BinaryHeap` is a max-heap, so the
// activation with the highest salience pops first. Ties broken
// by `rule_id` for deterministic ordering (Java's
// `PriorityQueue` uses insertion order on ties).
impl Ord for Activation {
    fn cmp(&self, other: &Self) -> std::cmp::Ordering {
        self.salience
            .cmp(&other.salience)
            .then(self.rule_id.cmp(&other.rule_id))
    }
}
impl PartialOrd for Activation {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        Some(self.cmp(other))
    }
}

/// `Activity` — the RETE runtime trait.
pub trait Activity: 'static {
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

    /// `Any` downcast — used by the builder to call
    /// `AbstractActivity::push_path` on a `&mut dyn Activity`.
    /// All concrete activities are `'static`, so this works
    /// through a trait object.
    fn as_any_mut(&mut self) -> &mut dyn std::any::Any;
}

/// `AbstractActivity` — common behavior for nodes that have
/// outbound `Path`s. Mirrors Java's `AbstractActivity.visitPaths` +
/// `doPassAndNode`. Concrete nodes (ObjectType / Criteria / And / Or /
/// Terminal) delegate to this for the "fire my children" part.
pub trait AbstractActivity: Activity {
    /// Outbound paths. `&[Arc<Path>]` for shared ownership.
    fn paths(&self) -> &[Arc<Path>];

    /// Add an outbound `Path`. The builder calls this once per
    /// `Line` during the wire phase. Implemented as `self.paths.push(path)`
    /// for each concrete activity. Named `push_path` to avoid
    /// shadowing the inherent `add_path` methods that some
    /// activities ship.
    fn push_path(&mut self, path: Arc<Path>);

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
            // SAFETY: the path's raw pointer is valid as long as
            // the `ReteInstance` that owns the target slot is
            // alive. The engine holds the only `&mut` to the
            // slot; `Activity::enter` is `&self` (read-only on
            // the activity's own state). The `Path::passed` flag
            // is updated via the path's own `AtomicBool`, not
            // through the activity.
            let activity_ptr: *const (dyn Activity + Send + Sync) = path.to();
            let activity: &dyn Activity = unsafe { &*activity_ptr };
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
