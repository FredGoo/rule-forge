//! `Left` + `LeftPart` — LHS expression tree.
//!
//! Java `Left` wraps a `LeftPart` (the kind-specific payload) plus optional
//! `arithmetic` (post-compute chain like `+ 5` or `* 2`). The `LeftPart`
//! kinds in V5.25 P0 are limited to `VariableLeftPart` and `EvalLeftPart` —
//! `MethodLeftPart` / `FunctionLeftPart` / `ExistLeftPart` / `AllLeftPart` /
//! `CollectLeftPart` / `CommonFunctionLeftPart` come in P2.
//!
//! `id` is a derived cache that Java computes lazily; we compute it
//! on-demand with the same formula.

use serde::{Deserialize, Serialize};
use serde_json::Value as JsonValue;

use super::left::LeftType;

/// `LeftPart` — the LHS "what to read" piece. Only the V5.25 P0 subset.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(tag = "kind", rename_all = "PascalCase")]
pub enum LeftPart {
    /// `VariableLeftPart` — read a field from a fact.
    Variable {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        variable_category: Option<String>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        variable_label: Option<String>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        variable_name: Option<String>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        datatype: Option<String>,
    },
    /// `EvalLeftPart` — embedded expression evaluated via Spring EL
    /// (Java) or `simpleeval` (Rust). P2 will wire this.
    Eval {
        expression: String,
    },
}

impl LeftPart {
    /// Compute the canonical id, matching Java `LeftPart.getId()`. Used to
    /// key the EvaluationContext `part_value_map` cache.
    pub fn id(&self) -> String {
        match self {
            Self::Variable {
                variable_category,
                variable_label,
                ..
            } => format!(
                "[变量]{}.{}",
                variable_category.as_deref().unwrap_or(""),
                variable_label.as_deref().unwrap_or(""),
            ),
            Self::Eval { expression } => format!("[eval]{expression}"),
        }
    }
}

/// `Left` — typed LHS expression: `kind` + `leftPart` payload.
///
/// Java's `Left` also carries a `ComplexArithmetic` for post-compute
/// (`leftValue + 5`); we add that in P2 when the chain evaluator lands.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Left {
    #[serde(rename = "type")]
    pub left_type: LeftType,
    pub left_part: LeftPart,
    /// Java carries ComplexArithmetic here; v0 always `None`.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub arithmetic: Option<JsonValue>,
}

impl Left {
    pub fn id(&self) -> String {
        let mut id = self.left_part.id();
        if let Some(arith) = &self.arithmetic {
            id.push_str(&arith.to_string());
        }
        id
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn variable_left_part_id() {
        let p = LeftPart::Variable {
            variable_category: Some("Applicant".to_string()),
            variable_label: Some("age".to_string()),
            variable_name: Some("age".to_string()),
            datatype: Some("int".to_string()),
        };
        assert_eq!(p.id(), "[变量]Applicant.age");
    }

    #[test]
    fn eval_left_part_id() {
        let p = LeftPart::Eval {
            expression: "applicant.age + 5".to_string(),
        };
        assert_eq!(p.id(), "[eval]applicant.age + 5");
    }

    #[test]
    fn left_serde_roundtrip() {
        let l = Left {
            left_type: LeftType::Variable,
            left_part: LeftPart::Variable {
                variable_category: Some("A".into()),
                variable_label: Some("b".into()),
                variable_name: Some("b".into()),
                datatype: Some("String".into()),
            },
            arithmetic: None,
        };
        let s = serde_json::to_string(&l).unwrap();
        let back: Left = serde_json::from_str(&s).unwrap();
        assert_eq!(back, l);
    }
}
