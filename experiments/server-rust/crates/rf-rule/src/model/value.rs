//! `Value` — RHS expression tree, port of Java `Value` interface + 8 impls.
//!
//! Java's `Value` is an interface with 8 concrete subtypes
//! (`ConstantValue`, `VariableValue`, `VariableCategoryValue`, `MethodValue`,
//! `ParameterValue`, `ParenValue`, `CommonFunctionValue`, `NamedReferenceValue`).
//! Jackson deserializes by reading the `"valueType"` discriminator then
//! mapping to the right concrete class via a `@JsonTypeInfo` /
//! `@JsonSubTypes` config in the parent model.
//!
//! Rust port: a single **tagged enum** mirrors the discriminator. Only the
//! 4 variants we actually need for V5.25 P0–P2 are populated:
//! - `Variable` — `vars.path.to.field` reference
//! - `Constant` — literal value (carried as `serde_json::Value`)
//! - `Input` — input parameter (variable name + label)
//! - `VariableCategory` — binding to a fact object (e.g. "applicant")
//!
//! The other 4 (`Method` / `Parameter` / `Paren` / `CommonFunction` /
//! `NamedReference`) are added in P2 alongside `ValueCompute`.

use serde::{Deserialize, Serialize};
use serde_json::Value as JsonValue;

use super::value_type::ValueType;

/// RHS expression — tagged by [`ValueType`].
///
/// In V5.25 P0 we keep the four variants that the JSON deserializer can
/// roundtrip without extra wiring. The discriminant in JSON is the
/// `valueType` field (lowercase / PascalCase per the Java convention).
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
        }
    }

    /// Canonical id, matching Java's per-`Value` `getId()`.
    /// Used by `Criteria.id()` to build the full criteria cache key.
    pub fn id(&self) -> String {
        let label = self.value_type().as_label();
        match self {
            Self::Constant {
                constant_category,
                constant_label,
                ..
            } => {
                format!(
                    "{label}{}.{}",
                    constant_category.as_deref().unwrap_or(""),
                    constant_label.as_deref().unwrap_or("")
                )
            }
            Self::Variable {
                variable_category,
                variable_label,
                ..
            } => {
                format!(
                    "{label}{}.{}",
                    variable_category.as_deref().unwrap_or(""),
                    variable_label.as_deref().unwrap_or("")
                )
            }
            Self::VariableCategory {
                variable_category,
            } => format!("{label}{variable_category}"),
            Self::Input {
                variable_name,
                variable_label,
            } => {
                format!(
                    "{label}{}.{}",
                    variable_name.as_deref().unwrap_or(""),
                    variable_label.as_deref().unwrap_or("")
                )
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
}
