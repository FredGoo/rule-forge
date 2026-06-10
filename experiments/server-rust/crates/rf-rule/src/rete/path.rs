//! `Path` — a directed edge in the RETE graph, with a `passed` flag
//! that records whether the upstream node has fired for the current
//! fire cycle.
//!
//! Java `Path` carries just `to: Activity` and `passed: boolean`. The
//! `passed` flag is set inside `AbstractActivity.visitPaths` and
//! checked by downstream `JoinActivity` (And / Or) to short-circuit
//! when their in-edges have already fired (de-dup across multiple
//! fact firings within one fire cycle).
//!
//! ## Why a raw pointer and not `Arc` / `Weak`?
//!
//! Both `Arc` and `Weak` block `Arc::get_mut` on the target slot
//! (the former by raising strong count, the latter by raising
//! weak count — `Arc::get_mut` requires `strong == 1 && weak ==
//! 0`). The wire phase needs `Arc::get_mut` to push inbound paths
//! onto join nodes (And/Or) and outbound paths onto source nodes
//! — both per `Line`, in topological order. A raw pointer
//! `*const dyn Activity + Send + Sync` is non-owning and does
//! not count toward either total, so `Arc::get_mut` works.
//!
//! ## Safety
//!
//! The raw pointer's validity is the engine's responsibility:
//! the pointer must not be used after the `ReteInstance` that
//! owns the target slot is dropped. The `Path` carries
//! `PhantomData<Arc<dyn Activity + Send + Sync>>` so the
//! compiler treats it as `Send + Sync` (the raw pointer itself is
//! `!Send + !Sync`).
//!
//! `Activity::enter(&self, ...)` is read-only on the activity —
//! all per-cycle state lives in `Path::passed` (or, in P5+,
//! `Cell<bool>` on the activity itself). The only `&mut self` is
//! on `reset`, called by the engine holding the only `&mut` to
//! the slot, so no aliasing occurs.
//!
//! ## Interior mutability on `passed`
//!
//! `Activity::enter` is `&self`, but `visit_paths` needs to flip the
//! child's `Path.passed` flag. We use `AtomicBool` so the flag is
//! `&self`-mutable AND `Send + Sync` (we need Sync because
//! `RuleEngine: Send + Sync` and the engine holds `Vec<Path>` via
//! `Arc`). `Cell` would also give interior mutability, but `Cell` is
//! `!Sync` — incompatible with the engine's bound.
//!
//! Ordering: `SeqCst` is overkill for the per-fire-cycle flag (no
//! real cross-thread contention — RETE processes a single fire
//! cycle at a time on one task), but it's the safest default and
//! matches the cost of a single `MOV`.

use std::marker::PhantomData;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

use super::activity::Activity;

/// `Path` — `passed` flag + non-owning raw pointer to the target
/// `Activity`. The target is owned by a `ReteInstance` slot.
pub struct Path {
    to: *const (dyn Activity + Send + Sync),
    _marker: PhantomData<Arc<dyn Activity + Send + Sync>>,
    passed: AtomicBool,
}

// SAFETY: the raw pointer is to a `dyn Activity + Send + Sync`.
// The target's lifetime is bounded by the `ReteInstance` that
// owns it; while the `ReteInstance` lives, the pointer is valid
// and the activity is `Send + Sync`. The `Path` itself holds no
// state that requires `!Send + !Sync` access.
unsafe impl Send for Path {}
unsafe impl Sync for Path {}

impl std::fmt::Debug for Path {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Path")
            .field("passed", &self.passed.load(Ordering::SeqCst))
            .finish_non_exhaustive()
    }
}

impl Path {
    /// Build a `Path` that points to `target`. The path does not
    /// own the activity — the caller must keep the `target` Arc
    /// alive (typically in a `ReteInstance` slot) for the path to
    /// be usable.
    pub fn new(target: &Arc<dyn Activity + Send + Sync>) -> Self {
        Self {
            to: Arc::as_ptr(target),
            _marker: PhantomData,
            passed: AtomicBool::new(false),
        }
    }

    /// Return the raw pointer to the target. Caller must
    /// dereference to `&dyn Activity`. The pointer is valid as
    /// long as the `ReteInstance` that owns the target is alive.
    pub fn to(&self) -> *const (dyn Activity + Send + Sync) {
        self.to
    }

    pub fn is_passed(&self) -> bool {
        self.passed.load(Ordering::SeqCst)
    }

    /// Mark this path as having fired. Safe through `&self` because
    /// `passed` is an `AtomicBool`.
    pub fn mark_passed(&self) {
        self.passed.store(true, Ordering::SeqCst);
    }

    pub fn reset(&self) {
        self.passed.store(false, Ordering::SeqCst);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rete::activity::{Activity, ActivityOutcome};
    use crate::rete::evaluation_context::EvaluationContext;
    use crate::fact::GeneralEntity;

    struct NoopActivity;
    impl Activity for NoopActivity {
        fn enter(&self, _fact: &GeneralEntity, _ctx: &mut EvaluationContext) -> Vec<ActivityOutcome> {
            vec![]
        }
        fn reset(&mut self) {}
        fn join_node_is_passed(&self) -> bool { false }
        fn pass_and_node(&mut self) {}
        fn as_any_mut(&mut self) -> &mut dyn std::any::Any { self }
    }

    #[test]
    fn path_starts_unpassed() {
        let a: Arc<dyn Activity + Send + Sync> = Arc::new(NoopActivity);
        let p = Path::new(&a);
        assert!(!p.is_passed());
    }

    #[test]
    fn path_mark_passed_through_shared_ref() {
        let a: Arc<dyn Activity + Send + Sync> = Arc::new(NoopActivity);
        let p = Path::new(&a);
        let p_ref = &p;
        p_ref.mark_passed();
        assert!(p.is_passed());
        p_ref.reset();
        assert!(!p.is_passed());
    }
}
