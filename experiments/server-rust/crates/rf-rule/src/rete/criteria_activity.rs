//! `CriteriaActivity` — single `left op value` predicate evaluation.
//! Mirror of Java
//! `com.ruleforge.runtime.rete.CriteriaActivity`.
//!
//! ## V5.25 P2 scope
//!
//! - All 20 `Op` values supported via [`crate::assertor::evaluate`].
//! - Left side: `VariableLeftPart` → fact.get_property(variable_name).
//!   `EvalLeftPart` is treated as a property name in P2 (real
//!   expression eval is P5 work).
//! - Right side: any `Value` variant via
//!   [`crate::value_compute::fetch_value`] — `Constant` returns its
//!   inline value, `Variable` walks working memory, `Method` and
//!   `CommonFunction` dispatch through the registries, etc.
//! - Caches the evaluation result in `EvaluationContext.criteria_value_map`
//!   keyed by `criteria.id()` so a shared criteria only evaluates once
//!   per fire cycle.
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
use crate::assertor;
use crate::fact::{Fact, GeneralEntity};
use crate::model::left_part::LeftPart;
use crate::model::value::Value;
use crate::model::{Criteria, Op};
use crate::value_compute;

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
            // to downstream join nodes. P3 wires the real glue; P2
            // just returns empty.
            return vec![];
        }

        // Java: `tracker.addObjectCriteria(obj, this.criteria)`.
        // P2 doesn't thread FactId through GeneralEntity yet, so we
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
        // No-op in P2; P3 walks outbound paths and calls passAndNode
        // on join nodes.
    }

    fn as_any_mut(&mut self) -> &mut dyn std::any::Any {
        self
    }
}

impl AbstractActivity for CriteriaActivity {
    fn paths(&self) -> &[Arc<Path>] {
        &self.paths
    }
    fn push_path(&mut self, path: Arc<Path>) {
        self.paths.push(path);
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
                    // P5 will wire a real expression engine; P2
                    // treats the expression as a property name
                    // (degenerate case for early smoke tests).
                    fact.get_property(expression)
                        .cloned()
                        .unwrap_or(serde_json::Value::Null)
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
                    let computed = value_compute::fetch_value(v, &ctx.working_memory, &ctx.env);
                    ctx.part_value_put(&vid, computed.clone());
                    computed
                }
            }
            // `Op::Null` / `NotNull` don't need a right side.
            None => serde_json::Value::Null,
        };

        // Compare via the assertor dispatcher (P2: all 20 ops).
        let result = assertor::evaluate(&left, &right, self.criteria.op);

        if result {
            EvaluateResponse::matched(left, right)
        } else {
            EvaluateResponse::unmatched(left, right)
        }
    }
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
        let resp1 = a.evaluate(&fact, &mut ctx);
        assert!(resp1.result);
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
        let fact = GeneralEntity::new("Applicant").with_field("name", json!("alice"));
        let resp = a.evaluate(&fact, &mut ctx);
        assert!(resp.result);
        let fact2 = GeneralEntity::new("Applicant").with_field("name", json!("bob"));
        let resp2 = a.evaluate(&fact2, &mut ctx);
        assert!(resp2.result, "cache reuses first fact's true result");
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
    fn evaluate_in_with_array_rhs() {
        // Constant + array on RHS — exercises the assertor dispatcher
        // path for `In`.
        let crit = Criteria {
            op: Op::In,
            left: Left {
                left_type: LeftType::Variable,
                left_part: LeftPart::Variable {
                    variable_category: Some("Applicant".into()),
                    variable_label: Some("city".into()),
                    variable_name: Some("city".into()),
                    datatype: Some("string".into()),
                },
                arithmetic: None,
            },
            value: Some(Value::Constant {
                constant_name: None,
                constant_label: None,
                constant_category: None,
                constant_value: Some(json!(["Beijing", "Shanghai", "Shenzhen"])),
            }),
        };
        let a = CriteriaActivity::new(crit, false);
        let mut ctx = ctx();
        let f1 = GeneralEntity::new("Applicant").with_field("city", json!("Shanghai"));
        assert!(a.evaluate(&f1, &mut ctx).result);
        let f2 = GeneralEntity::new("Applicant").with_field("city", json!("Hangzhou"));
        // Cache says true on first hit; clean to re-evaluate.
        ctx.clean();
        assert!(!a.evaluate(&f2, &mut ctx).result);
    }

    #[test]
    fn evaluate_match_with_regex_rhs() {
        let crit = Criteria {
            op: Op::Match,
            left: Left {
                left_type: LeftType::Variable,
                left_part: LeftPart::Variable {
                    variable_category: Some("Applicant".into()),
                    variable_label: Some("postal".into()),
                    variable_name: Some("postal".into()),
                    datatype: Some("string".into()),
                },
                arithmetic: None,
            },
            value: Some(Value::Constant {
                constant_name: None,
                constant_label: None,
                constant_category: None,
                constant_value: Some(json!("^1[0-9]{5}$")),
            }),
        };
        let a = CriteriaActivity::new(crit, false);
        let mut ctx = ctx();
        let f1 = GeneralEntity::new("Applicant").with_field("postal", json!("100000"));
        assert!(a.evaluate(&f1, &mut ctx).result);
        let f2 = GeneralEntity::new("Applicant").with_field("postal", json!("20000"));
        ctx.clean();
        assert!(!a.evaluate(&f2, &mut ctx).result);
    }

    #[test]
    fn evaluate_contain_substring() {
        let a = CriteriaActivity::new(
            Criteria {
                op: Op::Contain,
                left: Left {
                    left_type: LeftType::Variable,
                    left_part: LeftPart::Variable {
                        variable_category: Some("Applicant".into()),
                        variable_label: Some("name".into()),
                        variable_name: Some("name".into()),
                        datatype: Some("string".into()),
                    },
                    arithmetic: None,
                },
                value: Some(Value::Constant {
                    constant_name: None,
                    constant_label: None,
                    constant_category: None,
                    constant_value: Some(json!("ali")),
                }),
            },
            false,
        );
        let mut ctx = ctx();
        let f = GeneralEntity::new("Applicant").with_field("name", json!("alice"));
        assert!(a.evaluate(&f, &mut ctx).result);
    }

    #[test]
    fn evaluate_rhs_variable_uses_value_compute() {
        // RHS is a `Variable`, not a constant. The evaluate() path
        // must walk working memory (`Vars.get("applicant.age")`).
        use crate::value_compute::ReteEnv;
        let wm: std::rc::Rc<std::cell::RefCell<dyn rf_executor::working_memory::WorkingMemory>> = {
            let mut v = rf_executor::vars::Vars::new();
            v.assign("applicant.age", json!(30));
            std::rc::Rc::new(std::cell::RefCell::new(v))
        };
        let mut ctx = EvaluationContext::new(wm);
        ctx.set_env(ReteEnv::default());
        let crit = Criteria {
            op: Op::GreaterThen,
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
            value: Some(Value::Variable {
                variable_name: Some("age".into()),
                variable_label: Some("age".into()),
                variable_category: Some("applicant".into()),
                datatype: Some("int".into()),
            }),
        };
        let a = CriteriaActivity::new(crit, false);
        let fact = GeneralEntity::new("Applicant").with_field("age", json!(40));
        assert!(a.evaluate(&fact, &mut ctx).result);
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
        a.add_path(Arc::new(Path::new(&term)));
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
        a.add_path(Arc::new(Path::new(&term)));
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
        a.add_path(Arc::new(Path::new(&term)));
        let mut ctx = ctx();
        let fact1 = GeneralEntity::new("Applicant").with_field("age", json!(25));
        let out1 = a.enter(&fact1, &mut ctx);
        assert_eq!(out1.len(), 1);
        let fact2 = GeneralEntity::new("Applicant").with_field("age", json!(5));
        let out2 = a.enter(&fact2, &mut ctx);
        assert_eq!(out2.len(), 1, "cached response should be reused");
    }
}
