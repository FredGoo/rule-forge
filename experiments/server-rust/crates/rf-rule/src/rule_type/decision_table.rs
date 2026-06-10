//! `decision_table` — table-format rule adapter.
//!
//! A decision table is a 2D grid: rows are alternative
//! `if-then` rules, columns are conditions or actions. The
//! adapter flattens each row into one `Rule` whose `Lhs` is the
//! AND of the row's conditions.
//!
//! Java `DecisionTableRulesBuilder` does the same thing — one
//! `Rule` per row, with `Lhs.setCriterion(new And(...row's
//! conditions...))` and `Rhs` populated from the row's action
//! cells.
//!
//! ## Hit policy
//!
//! - `First` (default) — only one rule fires (the first row that
//!   matches). Each row's `salience` is `base_salience - row_num`
//!   so earlier rows beat later rows on ties.
//! - `Any` — multiple rows can fire in one cycle (no priority
//!   reshuffling). All rows get the table's `salience`.
//!
//! `First` is what 90% of the editor uses; `Any` is for
//! accumulator-style tables where every matching row should
//! contribute.

use serde::{Deserialize, Serialize};

use crate::model::criteria::Criteria;
use crate::model::rule::{Lhs, Rhs, Rule, RuleType};

/// `HitPolicy` — controls which rows can fire in one cycle.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum HitPolicy {
    /// Only the first matching row fires (rows ordered by
    /// ascending `row_num`, descending salience).
    First,
    /// All matching rows can fire in the same cycle.
    Any,
}

impl Default for HitPolicy {
    fn default() -> Self {
        HitPolicy::First
    }
}

/// `DecisionTableRow` — one row of the table.
///
/// `conditions` is a list of `Criteria` (AND within the row). If
/// any cell in the row is empty / null, the corresponding
/// `criteria` is `None` and the row matches unconditionally on
/// that column (matches the Java `null-cell = no condition`
/// semantics).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct DecisionTableRow {
    pub row_num: i32,
    #[serde(default)]
    pub conditions: Vec<Option<Criteria>>,
    #[serde(default)]
    pub actions: Vec<String>,
}

/// `DecisionTableSpec` — the table as authored in the editor.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct DecisionTableSpec {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub salience: i32,
    #[serde(default)]
    pub hit_policy: HitPolicy,
    pub rows: Vec<DecisionTableRow>,
}

impl DecisionTableSpec {
    /// Build the rule list. One `Rule` per row; the row's
    /// `conditions` become a flat `Lhs { criterions }` (with
    /// `None`-cells skipped).
    ///
    /// For `HitPolicy::First`, each row's salience is
    /// `spec.salience - row_num`, so earlier rows win on ties.
    /// For `HitPolicy::Any`, all rows get `spec.salience`.
    pub fn build(&self) -> Vec<Rule> {
        self.rows
            .iter()
            .map(|row| {
                let criterions: Vec<Criteria> = row
                    .conditions
                    .iter()
                    .filter_map(|c| c.clone())
                    .collect();
                let salience = match self.hit_policy {
                    HitPolicy::First => self.salience.saturating_sub(row.row_num),
                    HitPolicy::Any => self.salience,
                };
                Rule {
                    id: format!("{}-row{}", self.id, row.row_num),
                    name: format!("{}-row{}", self.name, row.row_num),
                    rule_type: Some(RuleType::DecisionTable),
                    file: None,
                    salience,
                    effective_date: None,
                    expires_date: None,
                    enabled: true,
                    debug: false,
                    activation_group: None,
                    agenda_group: None,
                    auto_focus: false,
                    ruleflow_group: None,
                    lhs: Lhs { criterions },
                    rhs: Rhs {
                        actions: row.actions.clone(),
                    },
                    r#loop: false,
                    remark: None,
                    with_else: false,
                }
            })
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::left::LeftType;
    use crate::model::left_part::{Left, LeftPart};
    use crate::model::op::Op;
    use crate::model::value::Value;

    fn age_criteria() -> Criteria {
        Criteria {
            op: Op::GreaterThenEquals,
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
            value: Some(Value::Constant {
                constant_name: None,
                constant_label: None,
                constant_category: None,
                constant_value: Some(serde_json::json!(18)),
            }),
        }
    }

    fn income_criteria() -> Criteria {
        Criteria {
            op: Op::GreaterThenEquals,
            left: Left {
                left_type: LeftType::Variable,
                left_part: LeftPart::Variable {
                    variable_category: Some("Applicant".into()),
                    variable_label: Some("income".into()),
                    variable_name: Some("income".into()),
                    datatype: Some("int".into()),
                },
                arithmetic: None,
            },
            value: Some(Value::Constant {
                constant_name: None,
                constant_label: None,
                constant_category: None,
                constant_value: Some(serde_json::json!(5000)),
            }),
        }
    }

    #[test]
    fn simple_two_by_two_table_first_policy() {
        // Row 1: age>=18 AND income>=5000 → "approve"
        // Row 2: age>=18 → "review"
        let spec = DecisionTableSpec {
            id: "dt1".into(),
            name: "approval".into(),
            salience: 0,
            hit_policy: HitPolicy::First,
            rows: vec![
                DecisionTableRow {
                    row_num: 1,
                    conditions: vec![Some(age_criteria()), Some(income_criteria())],
                    actions: vec!["approve".into()],
                },
                DecisionTableRow {
                    row_num: 2,
                    conditions: vec![Some(age_criteria())],
                    actions: vec!["review".into()],
                },
            ],
        };
        let rules = spec.build();
        assert_eq!(rules.len(), 2);
        // Row 1 — 2 criteria, salience 0 - 1 = -1
        assert_eq!(rules[0].lhs.criterions.len(), 2);
        assert_eq!(rules[0].salience, -1);
        assert_eq!(rules[0].rhs.actions, vec!["approve"]);
        // Row 2 — 1 criteria, salience 0 - 2 = -2
        assert_eq!(rules[1].lhs.criterions.len(), 1);
        assert_eq!(rules[1].salience, -2);
        assert_eq!(rules[1].rhs.actions, vec!["review"]);
    }

    #[test]
    fn first_match_wins_uses_descending_salience_per_row() {
        // Both rows match, but row 1 has higher salience.
        let spec = DecisionTableSpec {
            id: "dt1".into(),
            name: "t".into(),
            salience: 10,
            hit_policy: HitPolicy::First,
            rows: vec![
                DecisionTableRow {
                    row_num: 1,
                    conditions: vec![Some(age_criteria())],
                    actions: vec!["first".into()],
                },
                DecisionTableRow {
                    row_num: 2,
                    conditions: vec![Some(age_criteria())],
                    actions: vec!["second".into()],
                },
            ],
        };
        let rules = spec.build();
        // Row 1 salience 10-1=9; Row 2 salience 10-2=8. Row 1 fires first.
        assert!(rules[0].salience > rules[1].salience);
    }

    #[test]
    fn any_policy_uses_table_salience_for_all_rows() {
        let spec = DecisionTableSpec {
            id: "dt1".into(),
            name: "t".into(),
            salience: 7,
            hit_policy: HitPolicy::Any,
            rows: vec![
                DecisionTableRow {
                    row_num: 1,
                    conditions: vec![Some(age_criteria())],
                    actions: vec!["a".into()],
                },
                DecisionTableRow {
                    row_num: 2,
                    conditions: vec![Some(income_criteria())],
                    actions: vec!["b".into()],
                },
            ],
        };
        let rules = spec.build();
        assert_eq!(rules[0].salience, 7);
        assert_eq!(rules[1].salience, 7);
    }

    #[test]
    fn none_conditions_are_skipped() {
        // A row with all None conditions is "match anything" — emits
        // an empty Lhs (catches every fact).
        let spec = DecisionTableSpec {
            id: "dt".into(),
            name: "t".into(),
            salience: 0,
            hit_policy: HitPolicy::Any,
            rows: vec![DecisionTableRow {
                row_num: 1,
                conditions: vec![None, None],
                actions: vec!["catch_all".into()],
            }],
        };
        let rules = spec.build();
        assert_eq!(rules.len(), 1);
        assert!(rules[0].lhs.criterions.is_empty());
    }
}
