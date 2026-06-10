//! `OrActivity` — RETE join node, port of Java
//! `com.ruleforge.runtime.rete.OrNode`.
//!
//! Fires when **any** inbound path is in the `passed` state for the
//! current fire cycle. Mirrors Java's `JoinActivity.isAnyPassed` —
//! on pass, walks outbound paths via `AbstractActivity::visit_paths`.
//!
//! ## Inbound vs outbound
//!
//! `OrNode` carries two separate path lists:
//! - `inbound_paths` — upstream edges that the Or watches.
//! - `outbound_paths` — downstream edges fired when the Or passes.
//!
//! P3's test API exposes both via `add_inbound_path` / `add_path`.
//! The wire phase uses `push_path` (from the `AbstractActivity`
//! trait) to add outbound edges.
//!
//! ## Why not just `<AndActivity as AbstractActivity>` with `any`?
//!
//! Behaviourally close, but Java keeps the two as separate types
//! (`JoinNode` is the abstract parent) so the builder can produce
//! `OrActivity` instances on the `OrNode` JSON wire format. P3 of
//! the V5.25 port mirrors that — small type, clear semantics, no
//! runtime cost.
//!
//! ## Clone + interior mutability
//!
//! Same pattern as `AndActivity` / `CriteriaActivity`.

use std::sync::Arc;

use super::activity::{AbstractActivity, Activity, ActivityOutcome};
use super::evaluation_context::EvaluationContext;
use super::path::Path;
use crate::fact::GeneralEntity;

/// `OrActivity` — joins multiple inbound paths. Fires when at least
/// one inbound path is marked passed.
#[derive(Clone)]
pub struct OrActivity {
    inbound_paths: Vec<Arc<Path>>,
    outbound_paths: Vec<Arc<Path>>,
    passed: bool,
}

impl OrActivity {
    pub fn new() -> Self {
        Self {
            inbound_paths: Vec::new(),
            outbound_paths: Vec::new(),
            passed: false,
        }
    }

    /// Add an inbound path (an upstream edge the Or watches).
    pub fn add_inbound_path(&mut self, path: Arc<Path>) {
        self.inbound_paths.push(path);
    }

    /// Add an outbound path (a downstream edge fired on pass).
    pub fn add_path(&mut self, path: Arc<Path>) {
        self.outbound_paths.push(path);
    }

    pub fn paths_len(&self) -> usize {
        self.outbound_paths.len()
    }

    pub fn inbound_len(&self) -> usize {
        self.inbound_paths.len()
    }
}

impl Default for OrActivity {
    fn default() -> Self {
        Self::new()
    }
}

impl Activity for OrActivity {
    fn enter(
        &self,
        fact: &GeneralEntity,
        ctx: &mut EvaluationContext,
    ) -> Vec<ActivityOutcome> {
        // Java `JoinActivity.isAnyPassed` — fire on the first pass.
        let any_passed = self.inbound_paths.iter().any(|p| p.is_passed());
        if !any_passed {
            return vec![];
        }
        <Self as AbstractActivity>::visit_paths(self, fact, ctx)
    }

    fn reset(&mut self) {
        self.passed = false;
        for p in &self.outbound_paths {
            p.reset();
        }
    }

    fn join_node_is_passed(&self) -> bool {
        // OrNode is "passed" if any inbound path passed.
        self.inbound_paths.iter().any(|p| p.is_passed())
    }

    fn pass_and_node(&mut self) {
        // No-op: OrNode doesn't propagate "blocked" downstream.
    }

    fn as_any_mut(&mut self) -> &mut dyn std::any::Any {
        self
    }
}

impl AbstractActivity for OrActivity {
    fn paths(&self) -> &[Arc<Path>] {
        // The wire-phase `push_path` adds outbound paths.
        &self.outbound_paths
    }
    fn push_path(&mut self, path: Arc<Path>) {
        self.outbound_paths.push(path);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rete::activity::ActivityOutcome;
    use crate::rete::terminal_activity::TerminalActivity;
    use crate::rete::evaluation_context::EvaluationContext;
    use rf_executor::vars::Vars;
    use std::cell::RefCell;
    use std::rc::Rc;

    fn ctx() -> EvaluationContext {
        let wm: Rc<RefCell<dyn rf_executor::working_memory::WorkingMemory>> =
            Rc::new(RefCell::new(Vars::new()));
        EvaluationContext::new(wm)
    }

    #[test]
    fn fires_when_any_inbound_path_passed() {
        let term: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("r1", "approve", 10));
        let up_a: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("up_a", "u", 0));
        let up_b: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("up_b", "u", 0));
        let mut or = OrActivity::new();
        // Outbound — fires on pass.
        or.add_path(Arc::new(Path::new(&term)));
        // Inbound — checked on `enter`.
        let path_a = Arc::new(Path::new(&up_a));
        let path_b = Arc::new(Path::new(&up_b));
        or.add_inbound_path(path_a.clone());
        or.add_inbound_path(path_b);

        let fact = GeneralEntity::new("X");
        let mut c = ctx();

        // Nothing passed → no fire.
        assert!(or.enter(&fact, &mut c).is_empty());

        // path_a passed → fire (one is enough). 1 outcome: the
        // outbound terminal's activation.
        path_a.mark_passed();
        let out = or.enter(&fact, &mut c);
        assert_eq!(out.len(), 1);
        match &out[0] {
            ActivityOutcome::Activation(a) => assert_eq!(a.rule_id, "r1"),
            _ => panic!("expected Activation"),
        }
    }

    #[test]
    fn reset_clears_passed_flags() {
        let term: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("r1", "n", 0));
        let up: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("up", "u", 0));
        let mut or = OrActivity::new();
        let out_path = Arc::new(Path::new(&term));
        or.add_path(out_path.clone());
        let p = Arc::new(Path::new(&up));
        or.add_inbound_path(p.clone());
        p.mark_passed();
        let fact = GeneralEntity::new("X");
        let mut c = ctx();
        // First enter: marks out_path as passed, fires terminal.
        assert_eq!(or.enter(&fact, &mut c).len(), 1);
        assert!(out_path.is_passed());
        // Reset clears the outbound `passed` flag.
        or.reset();
        assert!(!out_path.is_passed());
    }

    #[test]
    fn join_node_is_passed_reflects_inbound_state() {
        let term: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("r1", "n", 0));
        let a: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("a", "u", 0));
        let b: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("b", "u", 0));
        let mut or = OrActivity::new();
        or.add_path(Arc::new(Path::new(&term)));
        or.add_path(Arc::new(Path::new(&term)));
        let p1 = Arc::new(Path::new(&a));
        let p2 = Arc::new(Path::new(&b));
        or.add_inbound_path(p1.clone());
        or.add_inbound_path(p2);
        assert!(!or.join_node_is_passed());
        or.inbound_paths[0].mark_passed();
        assert!(or.join_node_is_passed());
    }
}
