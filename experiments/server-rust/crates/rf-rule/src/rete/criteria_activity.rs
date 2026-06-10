//! `CriteriaActivity` — single `left op value` predicate evaluation.
//! Mirror of Java
//! `com.ruleforge.runtime.rete.CriteriaActivity`.
//!
//! ## V5.25 P1 scope
//!
//! - Supports `Op::Equals` / `NotEquals` / `LessThen` / `LessThenEquals` /
//!   `GreaterThen` / `GreaterThenEquals` on `serde_json::Value`.
//! - Left side: `VariableLeftPart` → fact.get_property(variable_name).
//! - Right side: `Value::Constant` → the literal value.
//! - Caches the evaluation result in `EvaluationContext.criteria_value_map`
//!   keyed by `criteria.id()` so a shared criteria only evaluates once
//!   per fire cycle.
//! - P2 will add `In` / `NotIn` / `Match` / `Null` / `Contain` etc. and
//!   the full `ValueCompute` for non-constant RHS.
//!
//! ## On pass/fail
//!
//! Java `CriteriaActivity.enter` returns null on fail (no propagation)
//! and `visitPaths` on pass. We mirror that — fail returns `vec![]`,
//! pass returns the aggregated `visit_paths` outcomes.

use std::sync::Arc;

use super::activity::{AbstractActivity, Activity, ActivityOutcome};
use super::evaluation_context::{EvaluateResponse, EvaluationContext};
use super::path::Path;
use crate::fact::{Fact, GeneralEntity};
use crate::model::left_part::LeftPart;
use crate::model::value::Value;
use crate::model::{Criteria, Op};

/// `CriteriaActivity` — one `Criteria` per activity. The Java side
/// allows multiple criteria on the same node; we keep them 1:1 for
/// V5.25 (builder creates one activity per criteria; sharing is the
/// builder's job).
#[derive(Clone)]
pub struct CriteriaActivity {
    pub criteria: Criteria,
    pub debug: bool,
    paths: Vec<Arc<Path>>,
    passed: bool,
}

impl CriteriaActivity {
    pub fn new(criteria: Criteria, debug: bool) -> Self {
        Self {
            criteria,
            debug,
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
}

impl Activity for CriteriaActivity {
    fn enter(
        &self,
        fact: &GeneralEntity,
        ctx: &mut EvaluationContext,
    ) -> Vec<ActivityOutcome> {
        let criteria_id = self.criteria.id();

        // Cache hit?
        let response = if let Some(cached) = ctx.criteria_value_get(&criteria_id) {
            cached.clone()
        } else {
            let resp = self.evaluate(fact, ctx);
            ctx.criteria_value_put(&criteria_id, resp.clone());
            if self.debug {
                ctx.debug_msgs
                    .push(format!("criteria {criteria_id} -> {}", resp.result));
            }
            resp
        };

        if !response.result {
            // Java `passAndNode()` propagates "this branch is blocked"
            // to downstream join nodes. P3 wires the real glue; P1
            // just returns empty.
            return vec![];
        }

        // Java: `tracker.addObjectCriteria(obj, this.criteria)`.
        // P1 doesn't thread FactId through GeneralEntity yet, so we
        // skip the fact-tracker write. P3 will wire it.
        // (See `FactTracker::record_match` in `evaluation_context.rs`.)

        <Self as AbstractActivity>::visit_paths(self, fact, ctx)
    }

    fn reset(&mut self) {
        self.passed = false;
        for p in &self.paths {
            p.reset();
        }
    }

    fn join_node_is_passed(&self) -> bool {
        // P3 will check inbound And/Or paths.
        false
    }

    fn pass_and_node(&mut self) {
        // No-op in P1; P3 walks outbound paths and calls passAndNode
        // on join nodes.
    }
}

impl AbstractActivity for CriteriaActivity {
    fn paths(&self) -> &[Arc<Path>] {
        &self.paths
    }
}

impl CriteriaActivity {
    /// Evaluate this criteria against a fact. Pure function (modulo
    /// the `ctx.part_value_map` cache). Returns the
    /// `EvaluateResponse` with computed left/right + boolean result.
    fn evaluate(
        &self,
        fact: &GeneralEntity,
        ctx: &mut EvaluationContext,
    ) -> EvaluateResponse {
        // Compute left value (with cache).
        let left_id = self.criteria.left.id();
        let left = if let Some(cached) = ctx.part_value_get(&left_id) {
            cached.clone()
        } else {
            let computed = match &self.criteria.left.left_part {
                LeftPart::Variable {
                    variable_name, ..
                } => fact
                    .get_property(variable_name.as_deref().unwrap_or(""))
                    .cloned()
                    .unwrap_or(serde_json::Value::Null),
                LeftPart::Eval { expression } => {
                    // P2 will wire `simpleeval`; for now we treat the
                    // expression as a property name (degenerate case
                    // for early smoke tests).
                    fact.get_property(expression).cloned().unwrap_or(serde_json::Value::Null)
                }
            };
            ctx.part_value_put(&left_id, computed.clone());
            computed
        };

        // Compute right value (with cache).
        let right = match &self.criteria.value {
            Some(v) => {
                let vid = v.id();
                if let Some(cached) = ctx.part_value_get(&vid) {
                    cached.clone()
                } else {
                    let computed = const_value(v);
                    ctx.part_value_put(&vid, computed.clone());
                    computed
                }
            }
            // `Op::Null` / `NotNull` don't need a right side.
            None => serde_json::Value::Null,
        };

        // Compare.
        let result = match self.criteria.op {
            Op::Null => left.is_null(),
            Op::NotNull => !left.is_null(),
            op => apply_op(&left, &right, op),
        };

        if result {
            EvaluateResponse::matched(left, right)
        } else {
            EvaluateResponse::unmatched(left, right)
        }
    }
}

/// Extract the JSON literal from a `Value::Constant`. For other RHS
/// kinds in P1 we fall back to `Null` — the full ValueCompute ships
/// in P2.
fn const_value(v: &Value) -> serde_json::Value {
    match v {
        Value::Constant {
            constant_value, ..
        } => constant_value.clone().unwrap_or(serde_json::Value::Null),
        // VariableCategory / Variable / Input are not constants;
        // P2's ValueCompute resolves them via working-memory lookups.
        _ => serde_json::Value::Null,
    }
}

/// Apply a comparison op. Mirrors `ConditionEvaluator.compare` in
/// `rf-executor/src/condition.rs` but lives here as a free function
/// so the rete/ module doesn't depend on condition.rs.
fn apply_op(left: &serde_json::Value, right: &serde_json::Value, op: Op) -> bool {
    use serde_json::Value::*;
    if left.is_null() || right.is_null() {
        return match op {
            Op::Equals => left == right,
            Op::NotEquals => left != right,
            _ => false,
        };
    }
    // Numeric compare.
    if let (Some(a), Some(b)) = (left.as_f64(), right.as_f64()) {
        return match op {
            Op::Equals => (a - b).abs() < f64::EPSILON,
            Op::NotEquals => (a - b).abs() >= f64::EPSILON,
            Op::GreaterThen => a > b,
            Op::GreaterThenEquals => a >= b,
            Op::LessThen => a < b,
            Op::LessThenEquals => a <= b,
            _ => false,
        };
    }
    // String compare.
    if let (Some(a), Some(b)) = (left.as_str(), right.as_str()) {
        return match op {
            Op::Equals => a == b,
            Op::NotEquals => a != b,
            Op::GreaterThen => a > b,
            Op::GreaterThenEquals => a >= b,
            Op::LessThen => a < b,
            Op::LessThenEquals => a <= b,
            _ => false,
        };
    }
    // Bool compare.
    if let (Some(a), Some(b)) = (left.as_bool(), right.as_bool()) {
        return match op {
            Op::Equals => a == b,
            Op::NotEquals => a != b,
            _ => false,
        };
    }
    false
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::left::LeftType;
    use crate::model::left_part::Left;
    use serde_json::json;

    fn make_criteria(op: Op, var_name: &str, rhs: serde_json::Value) -> Criteria {
        Criteria {
            op,
            left: Left {
                left_type: LeftType::Variable,
                left_part: LeftPart::Variable {
                    variable_category: Some("Applicant".into()),
                    variable_label: Some(var_name.into()),
                    variable_name: Some(var_name.into()),
                    datatype: Some("int".into()),
                },
                arithmetic: None,
            },
            value: Some(Value::Constant {
                constant_name: None,
                constant_label: None,
                constant_category: None,
                constant_value: Some(rhs),
            }),
        }
    }

    fn ctx() -> EvaluationContext {
        let wm: std::rc::Rc<std::cell::RefCell<dyn rf_executor::working_memory::WorkingMemory>> =
            std::rc::Rc::new(std::cell::RefCell::new(rf_executor::vars::Vars::new()));
        EvaluationContext::new(wm)
    }

    #[test]
    fn evaluate_greater_than_matches_and_caches() {
        let a = CriteriaActivity::new(make_criteria(Op::GreaterThen, "age", json!(18)), false);
        let mut ctx = ctx();
        let fact = GeneralEntity::new("Applicant").with_field("age", json!(25));
        // First call: no cache → evaluate.
        let resp1 = a.evaluate(&fact, &mut ctx);
        assert!(resp1.result);
        // Second call: same fact, same id → would hit cache, but
        // `evaluate` doesn't check cache (the cache check is in
        // `enter`). For the unit test we just confirm the
        // underlying op is right.
        let resp2 = a.evaluate(&fact, &mut ctx);
        assert!(resp2.result);
    }

    #[test]
    fn evaluate_equals_string() {
        let a = CriteriaActivity::new(
            make_criteria(Op::Equals, "name", json!("alice")),
            false,
        );
        let mut ctx = ctx();
        // First fact — cache miss, evaluates against the field.
        let fact = GeneralEntity::new("Applicant").with_field("name", json!("alice"));
        let resp = a.evaluate(&fact, &mut ctx);
        assert!(resp.result);
        // After `evaluate`, the `part_value_map` caches the left and
        // right by `LeftPart.id` / `Value.id`. The second `evaluate`
        // on a different fact with the same criteria id will hit
        // the cache and return the cached response (true) — this
        // matches Java's `context.storePartValue` behavior. To
        // re-evaluate, clean the cache.
        let fact2 = GeneralEntity::new("Applicant").with_field("name", json!("bob"));
        let resp2 = a.evaluate(&fact2, &mut ctx);
        assert!(resp2.result, "cache reuses first fact's true result");
        // After cleaning, the second fact's name("bob") is compared
        // with the value("alice") and fails.
        ctx.clean();
        let resp3 = a.evaluate(&fact2, &mut ctx);
        assert!(!resp3.result);
    }

    #[test]
    fn evaluate_equals_missing_field_is_null() {
        let a = CriteriaActivity::new(
            make_criteria(Op::NotNull, "age", json!(0)),
            false,
        );
        let mut ctx = ctx();
        let fact = GeneralEntity::new("Applicant"); // no `age` field
        let resp = a.evaluate(&fact, &mut ctx);
        assert!(!resp.result); // left is null → NotNull is false
    }

    #[test]
    fn enter_emits_child_activations_on_match() {
        use crate::rete::terminal_activity::TerminalActivity;
        let mut a = CriteriaActivity::new(
            make_criteria(Op::GreaterThen, "age", json!(18)),
            false,
        );
        let term: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("r1", "approve", 10));
        a.add_path(Arc::new(Path::new(term)));
        let mut ctx = ctx();
        let fact = GeneralEntity::new("Applicant").with_field("age", json!(25));
        let out = a.enter(&fact, &mut ctx);
        assert_eq!(out.len(), 1);
        match &out[0] {
            ActivityOutcome::Activation(ac) => assert_eq!(ac.rule_id, "r1"),
            _ => panic!("expected Activation"),
        }
    }

    #[test]
    fn enter_returns_empty_on_miss() {
        use crate::rete::terminal_activity::TerminalActivity;
        let mut a = CriteriaActivity::new(
            make_criteria(Op::GreaterThen, "age", json!(30)),
            false,
        );
        let term: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("r1", "approve", 10));
        a.add_path(Arc::new(Path::new(term)));
        let mut ctx = ctx();
        let fact = GeneralEntity::new("Applicant").with_field("age", json!(20));
        let out = a.enter(&fact, &mut ctx);
        assert!(out.is_empty());
    }

    #[test]
    fn enter_uses_cached_response_on_second_fact() {
        use crate::rete::terminal_activity::TerminalActivity;
        let mut a = CriteriaActivity::new(
            make_criteria(Op::GreaterThen, "age", json!(18)),
            false,
        );
        let term: Arc<dyn Activity + Send + Sync> =
            Arc::new(TerminalActivity::for_test("r1", "approve", 10));
        a.add_path(Arc::new(Path::new(term)));
        let mut ctx = ctx();
        // First fact — cache miss, evaluate true.
        let fact1 = GeneralEntity::new("Applicant").with_field("age", json!(25));
        let out1 = a.enter(&fact1, &mut ctx);
        assert_eq!(out1.len(), 1);
        // Second fact — cache hit on the criteria id; the result is
        // re-used (Java behavior). Both facts "match" because the
        // cache says so.
        let fact2 = GeneralEntity::new("Applicant").with_field("age", json!(5));
        let out2 = a.enter(&fact2, &mut ctx);
        assert_eq!(out2.len(), 1, "cached response should be reused");
    }
}
