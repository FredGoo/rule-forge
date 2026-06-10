//! `Value` — RHS expression tree, port of Java `Value` interface + 8 impls.
//!
//! Java's `Value` is an interface with 8 concrete subtypes
//! (`ConstantValue`, `VariableValue`, `VariableCategoryValue`, `MethodValue`,
//! `ParameterValue`, `ParenValue`, `CommonFunctionValue`, `NamedReferenceValue`).
//! Jackson deserializes by reading the `"valueType"` discriminator then
//! mapping to the right concrete class via a `@JsonTypeInfo` /
//! `@JsonSubTypes` config in the parent model.
//!
//! Rust port: a single **tagged enum** mirrors the discriminator.
//! V5.25 P2 ships the 8 Java variants.
//! - `Constant` / `Variable` / `Input` / `VariableCategory` — simple
//!   data-only cases.
//! - `Method` — call a Rust trait method by id; P2 wires a tiny
//!   `MethodRegistry` (no Spring beans).
//! - `Parameter` — lookup a fact in the "参数" category.
//! - `Paren` — nested ValueCompute (arithmetic grouping).
//! - `CommonFunction` — call a built-in function (`max`, `min`, `len`...).
//! - `NamedReference` — named reference (alias of a library constant);
//!   P2 treats as a Constant with name lookup.

use serde::{Deserialize, Serialize};
use serde_json::Value as JsonValue;

use super::value_type::ValueType;

/// RHS expression — tagged by [`ValueType`].
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(tag = "valueType", rename_all = "PascalCase")]
pub enum Value {
    /// `[常量]` — `constantCategory.constantLabel` reference into a
    /// resource library; for v0 we carry the value inline.
    Constant {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        constant_name: Option<String>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        constant_label: Option<String>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        constant_category: Option<String>,
        /// The actual constant value, when materialised.
        #[serde(default, skip_serializing_if = "Option::is_none")]
        constant_value: Option<JsonValue>,
    },
    /// `[变量]` — `vars.variableCategory.variableLabel` path.
    Variable {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        variable_name: Option<String>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        variable_label: Option<String>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        variable_category: Option<String>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        datatype: Option<String>,
    },
    /// `[变量对象]` — binding to a fact (a working-memory object). The
    /// `variableCategory` is the OTN class name to look up.
    VariableCategory {
        variable_category: String,
    },
    /// `Input` — input parameter (a variable bound by the caller).
    Input {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        variable_name: Option<String>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        variable_label: Option<String>,
    },
    /// Java `MethodValue` — call a method on a registered bean. P2
    /// wires a tiny `MethodRegistry` (no Spring). The `parameters`
    /// are themselves `Value`s, recursively evaluated.
    Method {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        bean_id: Option<String>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        bean_label: Option<String>,
        method_name: String,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        method_label: Option<String>,
        #[serde(default, skip_serializing_if = "Vec::is_empty")]
        parameters: Vec<Value>,
    },
    /// Java `ParameterValue` — read a property from the `参数`
    /// (parameter) category's fact.
    Parameter {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        variable_name: Option<String>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        variable_label: Option<String>,
    },
    /// Java `ParenValue` — wraps a nested `Value` for grouping
    /// (arithmetic precedence). Evaluated by recursing through
    /// `ValueCompute::compute`.
    Paren {
        value: Box<Value>,
    },
    /// Java `CommonFunctionValue` — call a built-in (`max`, `min`,
    /// `len`, `contains`, …). P2 ships a small built-in set; P5
    /// extends.
    CommonFunction {
        name: String,
        /// The "object" passed to the function.
        object_parameter: Box<Value>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        property: Option<String>,
    },
    /// Java `NamedReferenceValue` — a named reference. Treated as a
    /// constant lookup by `name` in P2.
    NamedReference {
        name: String,
    },
}

impl Value {
    /// Discriminator — useful when iterating without pattern-matching on
    /// every variant. Maps 1:1 to the Java enum.
    pub fn value_type(&self) -> ValueType {
        match self {
            Self::Constant { .. } => ValueType::Constant,
            Self::Variable { .. } => ValueType::Variable,
            Self::VariableCategory { .. } => ValueType::VariableCategory,
            Self::Input { .. } => ValueType::Input,
            Self::Method { .. } => ValueType::Method,
            Self::Parameter { .. } => ValueType::Parameter,
            Self::Paren { .. } => ValueType::Paren,
            Self::CommonFunction { .. } => ValueType::CommonFunction,
            Self::NamedReference { .. } => ValueType::NamedReference,
        }
    }

    /// Canonical id, matching Java's per-`Value` `getId()`.
    /// Used by `Criteria.id()` to build the full criteria cache key.
    /// The id must uniquely identify the value within a fire
    /// cycle's `part_value_map` cache — so a `Constant` with two
    /// different `constant_value`s MUST produce two different ids.
    /// Including the JSON form of `constant_value` (alongside
    /// category/label for human-readability) guarantees that.
    pub fn id(&self) -> String {
        let label = self.value_type().as_label();
        match self {
            Self::Constant {
                constant_name,
                constant_category,
                constant_label,
                constant_value,
            } => format!(
                "{label}{}.{}.{}={}",
                constant_name.as_deref().unwrap_or(""),
                constant_category.as_deref().unwrap_or(""),
                constant_label.as_deref().unwrap_or(""),
                constant_value
                    .as_ref()
                    .map(|v| v.to_string())
                    .unwrap_or_else(|| "null".to_string()),
            ),
            Self::Variable {
                variable_category,
                variable_label,
                ..
            } => format!(
                "{label}{}.{}",
                variable_category.as_deref().unwrap_or(""),
                variable_label.as_deref().unwrap_or("")
            ),
            Self::VariableCategory { variable_category } => {
                format!("{label}{variable_category}")
            }
            Self::Input {
                variable_name,
                variable_label,
            } => format!(
                "{label}{}.{}",
                variable_name.as_deref().unwrap_or(""),
                variable_label.as_deref().unwrap_or("")
            ),
            Self::Method { method_name, .. } => {
                format!("{label}{method_name}")
            }
            Self::Parameter {
                variable_name,
                variable_label,
            } => format!(
                "{label}{}.{}",
                variable_name.as_deref().unwrap_or(""),
                variable_label.as_deref().unwrap_or("")
            ),
            Self::Paren { value } => {
                format!("{label}({})", value.id())
            }
            Self::CommonFunction { name, .. } => {
                format!("{label}{name}")
            }
            Self::NamedReference { name } => {
                format!("{label}{name}")
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn constant_roundtrip() {
        let v = Value::Constant {
            constant_name: Some("MAX_AGE".to_string()),
            constant_label: Some("max age".to_string()),
            constant_category: Some("LoanConst".to_string()),
            constant_value: Some(json!(70)),
        };
        let s = serde_json::to_string(&v).unwrap();
        let back: Value = serde_json::from_str(&s).unwrap();
        assert_eq!(back, v);
    }

    #[test]
    fn variable_category_roundtrip() {
        let v = Value::VariableCategory {
            variable_category: "Applicant".to_string(),
        };
        let s = serde_json::to_string(&v).unwrap();
        let back: Value = serde_json::from_str(&s).unwrap();
        assert_eq!(back, v);
    }

    #[test]
    fn value_type_discriminator() {
        let v = Value::VariableCategory {
            variable_category: "X".into(),
        };
        assert_eq!(v.value_type(), ValueType::VariableCategory);
    }

    #[test]
    fn paren_roundtrip() {
        let v = Value::Paren {
            value: Box::new(Value::Constant {
                constant_name: None,
                constant_label: None,
                constant_category: None,
                constant_value: Some(json!(1)),
            }),
        };
        let s = serde_json::to_string(&v).unwrap();
        let back: Value = serde_json::from_str(&s).unwrap();
        assert_eq!(back, v);
    }

    #[test]
    fn method_roundtrip() {
        let v = Value::Method {
            bean_id: Some("util".into()),
            bean_label: None,
            method_name: "now".into(),
            method_label: None,
            parameters: vec![],
        };
        let s = serde_json::to_string(&v).unwrap();
        let back: Value = serde_json::from_str(&s).unwrap();
        assert_eq!(back, v);
    }
}
