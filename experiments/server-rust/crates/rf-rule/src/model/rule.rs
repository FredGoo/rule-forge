//! `Rule` — a single rule, port of `com.ruleforge.model.rule.Rule`.
//!
//! LHS / RHS trees are simplified for V5.25 P0:
//! - `lhs` is the flat list of criteria to AND-join (the And-builder in
//!   Java flattens nested `Lhs` trees; we accept a `Vec<Criteria>` here
//!   and let the builder wire the AndNode).
//! - `rhs` is the list of action tags; concrete `Action` enum comes in
//!   P4 / P5 (terminal node execution).
//!
//! Fields like `effectiveDate` / `expiresDate` are kept as `Option<i64>`
//! (epoch millis) for v0; chrono can replace later if needed.

use serde::{Deserialize, Serialize};

use super::criteria::Criteria;

/// Rule type — controls the builder strategy. Mirrors Java's `RuleType`
/// (script / decision-table / decision-tree / scorecard / rl).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum RuleType {
    Script,
    DecisionTable,
    DecisionTree,
    Scorecard,
    Rl,
    Ul,
}

/// The `Lhs` shape we accept on the wire. Java's `Lhs` is a tree of
/// `Criteria` / `And` / `Or` / `Junction`; for v0 we accept the simplest
/// form (flat AND of criteria) and rely on the builder to wire it up.
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
pub struct Lhs {
    #[serde(default)]
    pub criterions: Vec<Criteria>,
}

/// `Rhs` — list of action tags. Concrete actions come in P4.
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
pub struct Rhs {
    #[serde(default)]
    pub actions: Vec<String>,
}

/// Top-level `Rule` model.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Rule {
    pub id: String,
    pub name: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub rule_type: Option<RuleType>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub file: Option<String>,
    /// Salience: higher fires first. Java uses `Integer`; default 0.
    #[serde(default)]
    pub salience: i32,
    /// Epoch millis; `None` means always effective.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub effective_date: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub expires_date: Option<i64>,
    #[serde(default)]
    pub enabled: bool,
    #[serde(default)]
    pub debug: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub activation_group: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub agenda_group: Option<String>,
    #[serde(default)]
    pub auto_focus: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ruleflow_group: Option<String>,
    #[serde(default)]
    pub lhs: Lhs,
    #[serde(default)]
    pub rhs: Rhs,
    /// `loop: true` means the same rule may re-activate within one fire
    /// cycle (e.g. accumulator-style scoring).
    #[serde(default)]
    pub r#loop: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub remark: Option<String>,
    /// `withElse: true` means there's a paired else-rule that fires when
    /// no criteria in the lhs match. The paired rule is set after
    /// deserialization (Java: `KnowledgePackageImpl.buildWithElseRules`).
    #[serde(default)]
    pub with_else: bool,
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::left::LeftType;
    use crate::model::left_part::LeftPart;
    use crate::model::op::Op;
    use crate::model::value::Value;

    #[test]
    fn rule_minimal_serde() {
        let r = Rule {
            id: "r1".into(),
            name: "approve if age >= 18".into(),
            rule_type: Some(RuleType::Rl),
            file: None,
            salience: 10,
            effective_date: None,
            expires_date: None,
            enabled: true,
            debug: true,
            activation_group: None,
            agenda_group: None,
            auto_focus: false,
            ruleflow_group: None,
            lhs: Lhs {
                criterions: vec![Criteria {
                    op: Op::GreaterThenEquals,
                    left: crate::model::left_part::Left {
                        left_type: LeftType::Variable,
                        left_part: LeftPart::Variable {
                            variable_category: Some("Applicant".into()),
                            variable_label: Some("age".into()),
                            variable_name: Some("age".into()),
                            datatype: Some("int".into()),
                        },
                        arithmetic: None,
                    },
                    value: Some(Value::Constant {
                        constant_name: Some("MIN_AGE".into()),
                        constant_label: None,
                        constant_category: None,
                        constant_value: Some(serde_json::json!(18)),
                    }),
                }],
            },
            rhs: Rhs {
                actions: vec!["approve".into()],
            },
            r#loop: false,
            remark: None,
            with_else: false,
        };
        let s = serde_json::to_string(&r).unwrap();
        let back: Rule = serde_json::from_str(&s).unwrap();
        assert_eq!(back, r);
    }
}
