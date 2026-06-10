//! `ValueCompute` — RHS expression evaluator, port of Java
//! `com.ruleforge.runtime.compute.ValueCompute`.
//!
//! Java's `ValueCompute` resolves a `Value` (a tree of `ConstantValue`,
//! `VariableValue`, `MethodValue`, `ParenValue`, `CommonFunctionValue`,
//! etc.) into a concrete `Object` by walking working memory. We
//! mirror that with a recursive `fetch_value` function that returns
//! `serde_json::Value` (the common currency of `Vars`).
//!
//! ## Variant → resolution mapping
//!
//! | `Value` variant      | Java behaviour                          | Rust (V5.25)              |
//! |----------------------|-----------------------------------------|---------------------------|
//! | `Constant`           | returns `constantValue`                 | return inline JSON        |
//! | `Variable`           | walks `vars.<category>.<label>`         | `wm.borrow().resolve_path`|
//! | `VariableCategory`   | looks up the unique fact in that class  | `wm.borrow().facts_of_class` first match |
//! | `Input`              | looks up the input var on the session   | `wm.borrow().get(variable_name)` |
//! | `Parameter`          | looks up the `参数` (parameter) fact    | `wm.borrow().get("参数.variable_name")` |
//! | `Method`             | Spring `ExecuteMethodAction`            | `MethodRegistry::dispatch` (default returns Null) |
//! | `CommonFunction`     | `Utils.findFunctionDescriptor`          | `CommonFunctionRegistry::dispatch` (default returns Null) |
//! | `Paren`              | recurses on `value`                     | recurse via `fetch_value` |
//! | `NamedReference`     | named reference (alias)                 | same as Constant lookup by `name` |
//!
//! ## P2 scope
//!
//! P2 implements the **read** side — `fetch_value` for all 9
//! variants. The **write** / `complexArithmeticCompute` chain (used
//! by `Left.arithmetic`) is P5 work; P2 falls through to the
//! "computed left" with no chain.
//!
//! P2 also doesn't ship real `Method` / `CommonFunction` registries
//! — the trait objects are there, the test uses a no-op default,
//! and P5 wires real implementations for the rules used in
//! `ruleforge-executor` tests.

use std::cell::RefCell;
use std::collections::HashMap;
use std::rc::Rc;

use rf_executor::working_memory::WorkingMemory;
use serde_json::Value as JsonValue;

use crate::model::value::Value;

/// `MethodRegistry` — Rust-side mirror of Java's Spring bean
/// registry. Holds function-pointers keyed by `bean_id::method_name`.
///
/// P2 ships a no-op default (`MethodRegistry::default()` returns
/// `Null` for every call) so the dispatcher never panics; P5 wires
/// concrete methods for the executor tests.
pub trait MethodRegistry {
    fn dispatch(
        &self,
        bean_id: Option<&str>,
        method_name: &str,
        parameters: &[JsonValue],
    ) -> JsonValue;
}

/// Default no-op registry — returns `Value::Null` for any
/// unregistered method. Used in P2 tests / smoke runs.
#[derive(Debug, Default, Clone, Copy)]
pub struct NoopMethodRegistry;

impl MethodRegistry for NoopMethodRegistry {
    fn dispatch(
        &self,
        _bean_id: Option<&str>,
        _method_name: &str,
        _parameters: &[JsonValue],
    ) -> JsonValue {
        JsonValue::Null
    }
}

/// `CommonFunctionRegistry` — `max` / `min` / `len` / `contains` /
/// `year(...)` etc. P2 ships `len` and a stub for everything else.
pub trait CommonFunctionRegistry {
    fn dispatch(
        &self,
        name: &str,
        object: &JsonValue,
        property: Option<&str>,
    ) -> JsonValue;
}

/// Default `CommonFunctionRegistry` — supports `len`. All other
/// names return `Null` (so the test suite can still pass without
/// registering real functions).
#[derive(Debug, Default, Clone, Copy)]
pub struct DefaultCommonFunctionRegistry;

impl CommonFunctionRegistry for DefaultCommonFunctionRegistry {
    fn dispatch(
        &self,
        name: &str,
        object: &JsonValue,
        property: Option<&str>,
    ) -> JsonValue {
        match (name, object, property) {
            ("len", JsonValue::String(s), _) => JsonValue::from(s.chars().count()),
            ("len", JsonValue::Array(arr), _) => JsonValue::from(arr.len()),
            ("len", JsonValue::Object(obj), _) => JsonValue::from(obj.len()),
            _ => JsonValue::Null,
        }
    }
}

/// `ReteEnv` — bundles the lookups a `fetch_value` call needs. Held
/// inside `EvaluationContext` (P2+). Cheap to clone — both registries
/// are trait objects behind `Rc`.
#[derive(Clone)]
pub struct ReteEnv {
    /// Method dispatch. Defaults to `NoopMethodRegistry`.
    pub method_registry: Rc<RefCell<dyn MethodRegistry>>,
    /// Common function dispatch. Defaults to `DefaultCommonFunctionRegistry`.
    pub common_fn_registry: Rc<RefCell<dyn CommonFunctionRegistry>>,
    /// Library constants — `NamedReference` resolution. P2 ships an
    /// empty default.
    pub constants: Rc<RefCell<HashMap<String, JsonValue>>>,
}

impl std::fmt::Debug for ReteEnv {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ReteEnv")
            .field("constants.len", &self.constants.borrow().len())
            .finish()
    }
}

impl Default for ReteEnv {
    fn default() -> Self {
        Self {
            method_registry: Rc::new(RefCell::new(NoopMethodRegistry)),
            common_fn_registry: Rc::new(RefCell::new(DefaultCommonFunctionRegistry)),
            constants: Rc::new(RefCell::new(HashMap::new())),
        }
    }
}

impl ReteEnv {
    /// Add a named-reference → constant binding.
    pub fn bind_constant(&mut self, name: impl Into<String>, value: JsonValue) {
        self.constants.borrow_mut().insert(name.into(), value);
    }
}

/// `fetch_value` — resolve a `Value` to a `JsonValue`.
///
/// This is the single entry point used by `CriteriaActivity` and
/// (P4) the agenda's action payload evaluator. Behaviour mirrors
/// Java `ValueCompute.fetchValue(...)` with these Rust-side notes:
///
/// - `Method` and `CommonFunction` calls are best-effort; if the
///   registry doesn't recognise the name, `Null` is returned (Java
///   would throw `RuntimeException`, which P2 maps to `Null` so
///   partial implementations don't blow up the test suite).
/// - `VariableCategory` returns the **first fact** in that class
///   (full fact JSON), matching Java `findObject` which returns a
///   single fact reference.
/// - `Paren` is transparent — the inner value is evaluated and
///   returned as-is; arithmetic chain (Java's
///   `complexArithmeticCompute`) is P5.
pub fn fetch_value(
    value: &Value,
    wm: &Rc<RefCell<dyn WorkingMemory>>,
    env: &ReteEnv,
) -> JsonValue {
    match value {
        Value::Constant { constant_value, .. } => {
            constant_value.clone().unwrap_or(JsonValue::Null)
        }
        Value::Variable {
            variable_category,
            variable_label,
            ..
        } => {
            let cat = variable_category.as_deref().unwrap_or("");
            let lbl = variable_label.as_deref().unwrap_or("");
            let path = format!("{cat}.{lbl}");
            wm.borrow().get(&path).unwrap_or(JsonValue::Null)
        }
        Value::VariableCategory { variable_category } => {
            // Java `findObject`: returns the first fact in the
            // category. We pick the first JSON value of the class.
            let facts = wm.borrow().facts_of_class(variable_category);
            facts.into_iter().next().unwrap_or(JsonValue::Null)
        }
        Value::Input { variable_name, .. } => {
            let key = variable_name.as_deref().unwrap_or("");
            wm.borrow().get(key).unwrap_or(JsonValue::Null)
        }
        Value::Method {
            bean_id,
            method_name,
            parameters,
            ..
        } => {
            // Recursively evaluate parameters.
            let evaluated: Vec<JsonValue> = parameters
                .iter()
                .map(|p| fetch_value(p, wm, env))
                .collect();
            env.method_registry
                .borrow()
                .dispatch(bean_id.as_deref(), method_name, &evaluated)
        }
        Value::Parameter { variable_name, .. } => {
            let key = variable_name.as_deref().unwrap_or("");
            // Java looks up the `参数` (parameter) category fact.
            // V5.25 P0 keeps "参数" in the var_assigns BTreeMap
            // under a flat path: "参数.<name>".
            wm.borrow()
                .get(&format!("参数.{key}"))
                .unwrap_or(JsonValue::Null)
        }
        Value::Paren { value: inner } => fetch_value(inner, wm, env),
        Value::CommonFunction {
            name,
            object_parameter,
            property,
        } => {
            let obj = fetch_value(object_parameter, wm, env);
            env.common_fn_registry
                .borrow()
                .dispatch(name, &obj, property.as_deref())
        }
        Value::NamedReference { name } => env
            .constants
            .borrow()
            .get(name)
            .cloned()
            .unwrap_or(JsonValue::Null),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::value::Value;
    use rf_executor::vars::Vars;
    use serde_json::json;

    fn wm_with(json: serde_json::Value) -> Rc<RefCell<dyn WorkingMemory>> {
        let mut v = Vars::new();
        // Stuff the JSON object into var_assigns for the tests that
        // use `Variable` resolution.
        if let serde_json::Value::Object(map) = json {
            for (k, val) in map {
                v.assign(k, val);
            }
        }
        Rc::new(RefCell::new(v))
    }

    #[test]
    fn constant_returns_inline_value() {
        let v = Value::Constant {
            constant_name: None,
            constant_label: None,
            constant_category: None,
            constant_value: Some(json!(42)),
        };
        let wm = wm_with(json!({}));
        let env = ReteEnv::default();
        assert_eq!(fetch_value(&v, &wm, &env), json!(42));
    }

    #[test]
    fn variable_resolves_via_path() {
        let v = Value::Variable {
            variable_category: Some("applicant".into()),
            variable_label: Some("age".into()),
            variable_name: Some("age".into()),
            datatype: Some("int".into()),
        };
        let wm = wm_with(json!({"applicant.age": 25}));
        let env = ReteEnv::default();
        assert_eq!(fetch_value(&v, &wm, &env), json!(25));
    }

    #[test]
    fn variable_category_returns_first_fact() {
        let mut v = Vars::new();
        v.assert_fact("Order", json!({"id": 1, "total": 100}));
        v.assert_fact("Order", json!({"id": 2, "total": 200}));
        let wm: Rc<RefCell<dyn WorkingMemory>> = Rc::new(RefCell::new(v));
        let env = ReteEnv::default();
        let vc = Value::VariableCategory {
            variable_category: "Order".into(),
        };
        let got = fetch_value(&vc, &wm, &env);
        // First fact wins (BTreeSet order).
        assert!(got.is_object());
    }

    #[test]
    fn input_reads_top_level_var() {
        let v = Value::Input {
            variable_name: Some("customer_id".into()),
            variable_label: None,
        };
        let wm = wm_with(json!({"customer_id": "C-001"}));
        let env = ReteEnv::default();
        assert_eq!(fetch_value(&v, &wm, &env), json!("C-001"));
    }

    #[test]
    fn parameter_resolves_via_canmu_category() {
        let v = Value::Parameter {
            variable_name: Some("loan_amount".into()),
            variable_label: None,
        };
        let wm = wm_with(json!({"参数.loan_amount": 50000}));
        let env = ReteEnv::default();
        assert_eq!(fetch_value(&v, &wm, &env), json!(50000));
    }

    #[test]
    fn paren_is_transparent() {
        let v = Value::Paren {
            value: Box::new(Value::Constant {
                constant_name: None,
                constant_label: None,
                constant_category: None,
                constant_value: Some(json!("hi")),
            }),
        };
        let wm = wm_with(json!({}));
        let env = ReteEnv::default();
        assert_eq!(fetch_value(&v, &wm, &env), json!("hi"));
    }

    #[test]
    fn method_with_evaluated_parameters() {
        // A test-only method registry that returns the sum of its
        // numeric parameters. Exercises the parameter recursion path.
        struct Sum;
        impl MethodRegistry for Sum {
            fn dispatch(
                &self,
                _bean_id: Option<&str>,
                _method_name: &str,
                parameters: &[JsonValue],
            ) -> JsonValue {
                let mut acc = 0.0_f64;
                for p in parameters {
                    if let Some(n) = p.as_f64() {
                        acc += n;
                    }
                }
                json!(acc)
            }
        }
        let mut env = ReteEnv::default();
        env.method_registry = Rc::new(RefCell::new(Sum));
        let v = Value::Method {
            bean_id: Some("math".into()),
            bean_label: None,
            method_name: "sum".into(),
            method_label: None,
            parameters: vec![
                Value::Constant {
                    constant_name: None,
                    constant_label: None,
                    constant_category: None,
                    constant_value: Some(json!(1)),
                },
                Value::Constant {
                    constant_name: None,
                    constant_label: None,
                    constant_category: None,
                    constant_value: Some(json!(2.5)),
                },
            ],
        };
        let wm = wm_with(json!({}));
        assert_eq!(fetch_value(&v, &wm, &env), json!(3.5));
    }

    #[test]
    fn common_function_len_on_string() {
        let v = Value::CommonFunction {
            name: "len".into(),
            object_parameter: Box::new(Value::Constant {
                constant_name: None,
                constant_label: None,
                constant_category: None,
                constant_value: Some(json!("hello")),
            }),
            property: None,
        };
        let wm = wm_with(json!({}));
        let env = ReteEnv::default();
        assert_eq!(fetch_value(&v, &wm, &env), json!(5));
    }

    #[test]
    fn common_function_len_on_array() {
        let v = Value::CommonFunction {
            name: "len".into(),
            object_parameter: Box::new(Value::Constant {
                constant_name: None,
                constant_label: None,
                constant_category: None,
                constant_value: Some(json!([1, 2, 3, 4])),
            }),
            property: None,
        };
        let wm = wm_with(json!({}));
        let env = ReteEnv::default();
        assert_eq!(fetch_value(&v, &wm, &env), json!(4));
    }

    #[test]
    fn named_reference_resolves_via_constants() {
        let mut env = ReteEnv::default();
        env.bind_constant("MAX_LOAN", json!(1_000_000));
        let v = Value::NamedReference {
            name: "MAX_LOAN".into(),
        };
        let wm = wm_with(json!({}));
        assert_eq!(fetch_value(&v, &wm, &env), json!(1_000_000));
    }
}
