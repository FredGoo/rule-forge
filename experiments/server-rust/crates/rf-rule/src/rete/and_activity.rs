//! `AndActivity` — RETE join node, port of Java
//! `com.ruleforge.runtime.rete.AndNode`.
//!
//! Fires when **all** inbound paths are in the `passed` state for the
//! current fire cycle. Mirrors Java's `JoinActivity.isAllPassed` —
//! on pass, walks outbound paths via `AbstractActivity::visit_paths`.
//!
//! ## Inbound vs outbound
//!
//! `AndNode` carries two separate path lists:
//! - `inbound_paths` — the upstream edges whose `passed` flag this
//!   node watches. Java wires these from the upstream
//!   `CriteriaActivity` / `OrActivity` outbound edges.
//! - `outbound_paths` — the downstream edges this node fires when
//!   all inbound conditions are met.
//!
//! P3's test API exposes both via `add_inbound_path` /
//! `push_path` (the latter being the `AbstractActivity` trait's
//! "add outbound path" hook used by the wire phase).
//!
//! ## Cycle-correctness
//!
//! `enter` is called every time a fact drives a criteria pass on an
//! upstream edge. We don't track which fact arrived — that's
//! `FactTracker`'s job in the Java side. P3 of the V5.25 port
//! uses the simpler "any passed → propagate" model. A fact tracker
//! that bounds re-fires per fact is the next milestone (P3+ / P4
//! when the agenda + activation groups land).
//!
//! ## Clone + interior mutability
//!
//! `#[derive(Clone)]` mirrors `CriteriaActivity` so the engine's
//! `reset()` can clone-and-discard to get `&mut self` access. All
//! state is interior-mutability through `Path::AtomicBool`.

use std::sync::Arc;

use super::activity::{AbstractActivity, Activity, ActivityOutcome};
use super::evaluation_context::EvaluationContext;
use super::path::Path;
use crate::fact::GeneralEntity;

/// `AndActivity` — joins multiple inbound paths. Fires when all
/// inbound paths are marked passed.
#[derive(Clone)]
pub struct AndActivity {
    /// Java `to_line_count` — number of inbound edges. Used for
    /// cycle detection in the builder; P3 reads it for a sanity
    /// check on `enter`.
    pub to_line_count: i32,
    /// Inbound paths — edges from upstream criteria/Or that this
    /// And waits on. `Path::is_passed` is read in `enter`.
    inbound_paths: Vec<Arc<Path>>,
    /// Outbound paths — edges to downstream activities fired when
    /// the And passes.
    outbound_paths: Vec<Arc<Path>>,
    passed: bool,
}

impl AndActivity {
    pub fn new(to_line_count: i32) -> Self {
        Self {
            to_line_count,
            inbound_paths: Vec::new(),
            outbound_paths: Vec::new(),
            passed: false,
        }
    }

    /// Add an inbound path (an upstream edge the And waits on).
    /// Wire-phase helper for the builder; tests also use this to
    /// wire inbound edges directly.
    pub fn add_inbound_path(&mut self, path: Arc<Path>) {
        self.inbound_paths.push(path);
    }

    pub fn add_path(&mut self, path: Arc<Path>) {
        // Inherent method kept for backward-compat with P1-era
        // test scaffolding. Routes to outbound (default).
        self.outbound_paths.push(path);
    }

    pub fn paths_len(&self) -> usize {
        self.outbound_paths.len()
    }

    pub fn inbound_len(&self) -> usize {
        self.inbound_paths.len()
    }
}

impl Activity for AndActivity {
    fn enter(
        &self,
        fact: &GeneralEntity,
        ctx: &mut EvaluationContext,
    ) -> Vec<ActivityOutcome> {
        // Java: `if (joinNode.isAllPassed()) { return doPassAndNode(...); }`
        // P3 model: just check `path.passed` for each inbound edge.
        let all_passed = self.inbound_paths.iter().all(|p| p.is_passed());
        if !all_passed {
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
        // An AndNode is itself "passed" if all its inbound paths
        // are passed (i.e. it would fire on the current cycle).
        self.inbound_paths.iter().all(|p| p.is_passed())
    }

    fn pass_and_node(&mut self) {
        // No-op: AndNode doesn't propagate "blocked" downstream —
        // it only checks the upstream state on `enter`.
    }

    fn as_any_mut(&mut self) -> &mut dyn std::any::Any {
        self
    }
}

impl AbstractActivity for AndActivity {
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
    fn fires_only_when_all_inbound_paths_passed() {
        // AndNode with 2 inbound edges (both watched) and 2
        // outbound edges (each fires its own terminal).
        let term_a: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("r1", "approve", 10));
        let term_b: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("r2", "review", 5));
        let up_a: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("up_a", "u", 0));
        let up_b: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("up_b", "u", 0));
        let mut and = AndActivity::new(2);
        // Outbound — fires on pass.
        and.add_path(Arc::new(Path::new(&term_a)));
        and.add_path(Arc::new(Path::new(&term_b)));
        // Inbound — checked on `enter`.
        let path_a = Arc::new(Path::new(&up_a));
        let path_b = Arc::new(Path::new(&up_b));
        and.add_inbound_path(path_a.clone());
        and.add_inbound_path(path_b.clone());

        let fact = GeneralEntity::new("X");
        let mut c = ctx();

        // Neither inbound passed → no fire.
        assert!(and.enter(&fact, &mut c).is_empty());

        // Only path_a passed → no fire.
        path_a.mark_passed();
        assert!(and.enter(&fact, &mut c).is_empty());

        // Both inbound passed → both outbound terminals fire.
        path_b.mark_passed();
        let out = and.enter(&fact, &mut c);
        assert_eq!(out.len(), 2);
        let ids: Vec<&str> = out
            .iter()
            .filter_map(|o| match o {
                ActivityOutcome::Activation(a) => Some(a.rule_id.as_str()),
                _ => None,
            })
            .collect();
        assert!(ids.contains(&"r1"));
        assert!(ids.contains(&"r2"));
    }

    #[test]
    fn reset_clears_passed_flags() {
        // After reset, the outbound paths' `passed` flag is
        // cleared. `visit_paths` re-marks them on the next
        // `enter`, so the And still fires if all inbound
        // conditions are still met — but the cleared flag is
        // observable directly via the path.
        let term: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("r1", "approve", 0));
        let up: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("up", "u", 0));
        let mut and = AndActivity::new(1);
        let out_path = Arc::new(Path::new(&term));
        and.add_path(out_path.clone());
        let p = Arc::new(Path::new(&up));
        and.add_inbound_path(p.clone());
        p.mark_passed();
        let fact = GeneralEntity::new("X");
        let mut c = ctx();
        // First enter: marks out_path as passed, fires terminal.
        assert_eq!(and.enter(&fact, &mut c).len(), 1);
        assert!(out_path.is_passed());
        // Reset clears outbound `passed`. The And will still fire
        // on the next enter (visit_paths re-marks) — but the flag
        // is briefly false after reset.
        and.reset();
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
        let mut and = AndActivity::new(2);
        and.add_path(Arc::new(Path::new(&term)));
        and.add_path(Arc::new(Path::new(&term)));
        let p1 = Arc::new(Path::new(&a));
        let p2 = Arc::new(Path::new(&b));
        and.add_inbound_path(p1.clone());
        and.add_inbound_path(p2);
        assert!(!and.join_node_is_passed());
        p1.mark_passed();
        assert!(!and.join_node_is_passed()); // still one missing
    }
}
