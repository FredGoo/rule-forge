//! `ObjectTypeActivity` — typed fact entry point. Mirror of Java
//! `com.ruleforge.runtime.rete.ObjectTypeActivity`.
//!
//! ## Behavior
//!
//! Java's `ObjectTypeActivity.support(Object)` does one of:
//! - `__*__` wildcard → matches everything
//! - exact class name → matches if the fact's class equals the OTN's
//!   `object_type_class`
//!
//! In V5.25 P1 the OTN's `object_type_class` is a `String` (we don't
//! carry `Class<?>` because Rust has no runtime class system). The
//! `GeneralEntity::class_name()` is also a `String`, so matching is
//! straight `==`.
//!
//! `enter` for an OTN is just `visit_paths` — no own evaluation; the
//! OTN's job is to admit the fact to downstream criteria, gated on
//! the type match.

use std::sync::Arc;

use rf_executor::vars::WILDCARD_CLASS;

use super::activity::{AbstractActivity, Activity, ActivityOutcome};
use super::evaluation_context::EvaluationContext;
use super::path::Path;
use crate::fact::{Fact, GeneralEntity};

/// `ObjectTypeActivity` — filters by class name.
#[derive(Clone)]
pub struct ObjectTypeActivity {
    object_type_class: String,
    paths: Vec<Arc<Path>>,
    /// Per-cycle: have we already admitted a fact this fire? Java
    /// does this via the upstream `Path.passed` flag.
    passed: bool,
}

impl ObjectTypeActivity {
    pub fn new(object_type_class: impl Into<String>) -> Self {
        Self {
            object_type_class: object_type_class.into(),
            paths: Vec::new(),
            passed: false,
        }
    }

    pub fn add_path(&mut self, path: Arc<Path>) {
        self.paths.push(path);
    }

    /// Test/debug helper: how many outbound paths are wired.
    pub fn paths_len(&self) -> usize {
        self.paths.len()
    }

    pub fn object_type_class(&self) -> &str {
        &self.object_type_class
    }

    /// Java `support(String)`. `WILDCARD_CLASS` matches all; otherwise
    /// exact match.
    pub fn supports(&self, class_name: &str) -> bool {
        self.object_type_class == WILDCARD_CLASS || self.object_type_class == class_name
    }
}

impl Activity for ObjectTypeActivity {
    fn enter(
        &self,
        fact: &GeneralEntity,
        ctx: &mut EvaluationContext,
    ) -> Vec<ActivityOutcome> {
        if !self.supports(fact.class_name()) {
            return vec![];
        }
        // Java `ObjectTypeActivity.enter` does `visitPaths` only when
        // `joinNodeIsPassed() == false`; the OTN is a leaf from the
        // join's POV so it's always false.
        <Self as AbstractActivity>::visit_paths(self, fact, ctx)
    }

    fn reset(&mut self) {
        self.passed = false;
        for p in &self.paths {
            p.reset();
        }
    }

    fn join_node_is_passed(&self) -> bool {
        false
    }

    fn pass_and_node(&mut self) {
        // No-op: OTN is not a join node.
    }
}

impl AbstractActivity for ObjectTypeActivity {
    fn paths(&self) -> &[Arc<Path>] {
        &self.paths
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rete::activity::Activity;
    use crate::rete::evaluation_context::EvaluationContext;
    use rf_executor::vars::Vars;
    use rf_executor::working_memory::WorkingMemory;
    use serde_json::json;

    fn ctx() -> EvaluationContext {
        let wm: std::rc::Rc<std::cell::RefCell<dyn WorkingMemory>> =
            std::rc::Rc::new(std::cell::RefCell::new(Vars::new()));
        EvaluationContext::new(wm)
    }

    #[test]
    fn supports_exact_match() {
        let otn = ObjectTypeActivity::new("com.example.Applicant");
        assert!(otn.supports("com.example.Applicant"));
        assert!(!otn.supports("com.example.Order"));
    }

    #[test]
    fn supports_wildcard() {
        let otn = ObjectTypeActivity::new(WILDCARD_CLASS);
        assert!(otn.supports("Anything"));
        assert!(otn.supports(""));
    }

    #[test]
    fn enter_admits_matching_fact_and_drops_others() {
        let otn = ObjectTypeActivity::new("Applicant");
        // No downstream paths — outcomes should be empty.
        let mut ctx = ctx();
        let fact = GeneralEntity::new("Applicant");
        let out = otn.enter(&fact, &mut ctx);
        assert!(out.is_empty());

        let wrong = GeneralEntity::new("Order");
        let out = otn.enter(&wrong, &mut ctx);
        assert!(out.is_empty());
    }

    #[test]
    fn enter_propagates_to_child() {
        // OTN → Path → TerminalActivity. Confirm that enter on the
        // OTN triggers enter on the Terminal and we get the
        // activation back.
        use crate::rete::terminal_activity::TerminalActivity;
        let mut otn = ObjectTypeActivity::new("Applicant");
        let terminal: Arc<dyn Activity + Send + Sync> = Arc::new(TerminalActivity::for_test(
            "r1",
            "approve",
            10,
        ));
        otn.add_path(Arc::new(Path::new(terminal)));
        let mut ctx = ctx();
        let fact = GeneralEntity::new("Applicant");
        let out = otn.enter(&fact, &mut ctx);
        assert_eq!(out.len(), 1);
        match &out[0] {
            ActivityOutcome::Activation(a) => {
                assert_eq!(a.rule_id, "r1");
            }
            _ => panic!("expected Activation, got {out:?}"),
        }
    }
}
