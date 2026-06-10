//! `EvaluationContext` — per-fire-cycle state for the RETE engine.
//!
//! Mirrors Java `EvaluationContextImpl` (1:1 with `EvaluationContext`):
//! - `criteria_value_map` — caches the per-`Criteria` evaluation
//!   result so a criteria that appears in multiple rules only runs
//!   once per fire cycle (and only re-runs if the fact changes).
//! - `part_value_map` — caches the per-`LeftPart` / per-`Value`
//!   computed value (e.g. `applicant.age` looked up on a fact).
//! - `fact_tracker` — the per-cycle fact→criteria matching record.
//!
//! Java also carries `tip_msg` (for trace logging) and an
//! `ApplicationContext` (Spring). V5.25 P1 doesn't ship those — we
//! have a `debug_msgs: Vec<String>` instead.
//!
//! ## V5.25 simplification
//!
//! Java `WorkingMemory` is a separate object; `EvaluationContext` holds
//! a `getWorkingMemory()` reference to it. In V5.25 P0 we collapsed
//! the two — `Vars` is the working memory. `EvaluationContext` still
//! has its own per-cycle state, but reads/writes the working memory
//! via the `WorkingMemory` trait (for testability + future P2
//! persistence).
//!
//! ## P1 cache invalidation
//!
//! `clean()` clears all three maps and is called by `ReteInstance`
//! at the start of every fire cycle (driven by
//! `WorkingMemory::reset_fire_epoch`).

use std::collections::HashMap;

use rf_executor::working_memory::WorkingMemory;

use super::fact_tracker::FactTracker;
use crate::model::Criteria;
use crate::value_compute::ReteEnv;

/// `EvaluateResponse` — the result of evaluating a single `Criteria`
/// for a given fact. Carries the computed `left_result` and
/// `right_result` (both `serde_json::Value`) plus the boolean
/// comparison `result`.
#[derive(Debug, Clone, PartialEq)]
pub struct EvaluateResponse {
    pub left_result: Option<serde_json::Value>,
    pub right_result: Option<serde_json::Value>,
    pub result: bool,
}

impl EvaluateResponse {
    pub fn matched(left: serde_json::Value, right: serde_json::Value) -> Self {
        Self {
            left_result: Some(left),
            right_result: Some(right),
            result: true,
        }
    }

    pub fn unmatched(left: serde_json::Value, right: serde_json::Value) -> Self {
        Self {
            left_result: Some(left),
            right_result: Some(right),
            result: false,
        }
    }

    pub fn null_matched() -> Self {
        Self {
            left_result: None,
            right_result: None,
            result: true,
        }
    }

    pub fn null_unmatched() -> Self {
        Self {
            left_result: None,
            right_result: None,
            result: false,
        }
    }
}

/// `EvaluationContext` — per-fire-cycle state.
pub struct EvaluationContext {
    /// `Criteria.id()` → `EvaluateResponse`. Java
    /// `criteria_value_map`.
    pub criteria_value_map: HashMap<String, EvaluateResponse>,
    /// `LeftPart.id()` / `Value.id()` → `serde_json::Value`. Java
    /// `part_value_map`. Caches field lookups and computed RHS values
    /// so a criteria referenced twice in a rule only walks the fact
    /// tree once.
    pub part_value_map: HashMap<String, serde_json::Value>,
    /// Per-cycle fact → criteria matching record.
    pub fact_tracker: FactTracker,
    /// Optional debug trace (P1 minimum; real impl mirrors Java's
    /// `MessageItem` / `MsgType` in P4 with proper event listeners).
    pub debug_msgs: Vec<String>,
    /// Reference to the working memory. Held as `Rc<RefCell<…>>` so
    /// the engine can `borrow_mut()` it inside `enter` (which takes
    /// `&self`).
    pub working_memory: std::rc::Rc<std::cell::RefCell<dyn WorkingMemory>>,
    /// `ReteEnv` — method / common-function dispatchers + named
    /// reference table. Created with defaults in `new`; tests can
    /// replace via `set_env`.
    pub env: ReteEnv,
}

impl EvaluationContext {
    pub fn new(wm: std::rc::Rc<std::cell::RefCell<dyn WorkingMemory>>) -> Self {
        Self {
            criteria_value_map: HashMap::new(),
            part_value_map: HashMap::new(),
            fact_tracker: FactTracker::new(),
            debug_msgs: Vec::new(),
            working_memory: wm,
            env: ReteEnv::default(),
        }
    }

    /// P2 helper — replace the dispatch env (used by tests that
    /// register a custom method / function).
    pub fn set_env(&mut self, env: ReteEnv) {
        self.env = env;
    }

    /// Clear all per-cycle caches. Called by `ReteInstance` at the
    /// start of every fire cycle.
    pub fn clean(&mut self) {
        self.criteria_value_map.clear();
        self.part_value_map.clear();
        self.fact_tracker = FactTracker::new();
        self.debug_msgs.clear();
    }

    // ---- criteria cache helpers ----

    pub fn criteria_value_get(&self, id: &str) -> Option<&EvaluateResponse> {
        self.criteria_value_map.get(id)
    }

    pub fn criteria_value_put(&mut self, id: &str, resp: EvaluateResponse) {
        self.criteria_value_map.insert(id.to_string(), resp);
    }

    // ---- part cache helpers ----

    pub fn part_value_get(&self, id: &str) -> Option<&serde_json::Value> {
        self.part_value_map.get(id)
    }

    pub fn part_value_put(&mut self, id: &str, value: serde_json::Value) {
        self.part_value_map.insert(id.to_string(), value);
    }

    pub fn part_value_exists(&self, id: &str) -> bool {
        self.part_value_map.contains_key(id)
    }

    // ---- tracker helpers (P3 will use these) ----

    pub fn record_match(&mut self, fact: rf_executor::vars::FactId, criteria: &Criteria) {
        self.fact_tracker.add(fact, criteria.id());
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use rf_executor::vars::Vars;
    use serde_json::json;

    #[test]
    fn clean_resets_all_caches() {
        let wm: std::rc::Rc<std::cell::RefCell<dyn WorkingMemory>> =
            std::rc::Rc::new(std::cell::RefCell::new(Vars::new()));
        let mut ctx = EvaluationContext::new(wm);
        ctx.criteria_value_put(
            "c1",
            EvaluateResponse::matched(json!(1), json!(1)),
        );
        ctx.part_value_put("p1", json!(42));
        ctx.debug_msgs.push("trace".to_string());
        assert_eq!(ctx.criteria_value_map.len(), 1);
        ctx.clean();
        assert!(ctx.criteria_value_map.is_empty());
        assert!(ctx.part_value_map.is_empty());
        assert!(ctx.debug_msgs.is_empty());
    }

    #[test]
    fn matched_unmatched_helpers() {
        let m = EvaluateResponse::matched(json!(1), json!(1));
        assert!(m.result);
        let u = EvaluateResponse::unmatched(json!(1), json!(2));
        assert!(!u.result);
    }
}
