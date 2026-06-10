//! `scorecard` — scoring-rule adapter.
//!
//! A scorecard is a list of "if X then add N to the score" rules
//! plus a threshold trigger. The classic example is credit
//! scoring: each factor (income > X, no late payments, etc.)
//! contributes a weighted score; the rule fires when the
//! accumulated score crosses a threshold.
//!
//! Java `ScorecardResourceBuilder` produces a single `Rule`
//! whose LHS is the AND of every condition, and whose RHS is
//! "compute sum, then conditionally assign action based on
//! threshold". Since we don't yet have a
//! `ScoringAction`/`SumAction` execution path in P5, the
//! adapter emits a flat rule with all conditions AND'd and the
//! "on pass" action as the only RHS action; the actual score
//! accumulation is a TODO for the action layer.
//!
//! V5.25 P5 keeps the scorecard adapter minimal: the
//! `build()` output is one `Rule` whose LHS is the AND of all
//! conditions and whose RHS carries a synthetic
//! `assign(score, <sum>)` action tag. The
//! `ReteRuleEngine` doesn't execute it differently from a
//! regular rule yet — it's a placeholder for the real
//! scoring execution layer.

use serde::{Deserialize, Serialize};

use crate::model::criteria::Criteria;
use crate::model::rule::{Lhs, Rhs, Rule, RuleType};

/// `ScorecardCondition` — one "if X then add N points" entry.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ScorecardCondition {
    pub criteria: Criteria,
    /// Points to add if `criteria` matches.
    pub score: f64,
}

/// `ScorecardSpec` — the scorecard as authored in the editor.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ScorecardSpec {
    pub id: String,
    pub name: String,
    /// Target variable that holds the running score. The RHS
    /// emits an `assign(score, <sum of matched scores>)` style
    /// action tagged with this name.
    pub score_var: String,
    /// Score threshold — if the accumulated score meets or
    /// exceeds this, `on_pass_action` fires.
    pub threshold: f64,
    /// Optional "if total >= threshold" actions. P5 emits
    /// these as a flat list on the rule's RHS.
    #[serde(default)]
    pub on_pass_action: Vec<String>,
    /// The list of "if X then add N points" entries.
    pub conditions: Vec<ScorecardCondition>,
    #[serde(default)]
    pub salience: i32,
}

impl ScorecardSpec {
    /// Build the rule. P5 emits a single rule with the AND of
    /// all conditions and the threshold actions on the RHS.
    /// The score accumulation itself is the action layer's
    /// job (P5+).
    pub fn build(&self) -> Vec<Rule> {
        let criterions: Vec<Criteria> = self
            .conditions
            .iter()
            .map(|c| c.criteria.clone())
            .collect();
        let mut actions = self.on_pass_action.clone();
        // Marker action so the engine / trace layer can see this
        // rule is a scorecard. Format: `scorecard:<score_var>:<threshold>`.
        actions.push(format!(
            "scorecard:{}:{}",
            self.score_var, self.threshold
        ));
        vec![Rule {
            id: format!("{}-sc", self.id),
            name: format!("{}-sc", self.name),
            rule_type: Some(RuleType::Scorecard),
            file: None,
            salience: self.salience,
            effective_date: None,
            expires_date: None,
            enabled: true,
            debug: false,
            activation_group: None,
            agenda_group: None,
            auto_focus: false,
            ruleflow_group: None,
            lhs: Lhs { criterions },
            rhs: Rhs { actions },
            r#loop: false,
            remark: None,
            with_else: false,
        }]
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
    fn multi_condition_scorecard_ands_all_criteria() {
        let spec = ScorecardSpec {
            id: "sc1".into(),
            name: "credit".into(),
            score_var: "score".into(),
            threshold: 60.0,
            on_pass_action: vec!["approve".into()],
            conditions: vec![
                ScorecardCondition {
                    criteria: age_criteria(),
                    score: 30.0,
                },
                ScorecardCondition {
                    criteria: income_criteria(),
                    score: 40.0,
                },
            ],
            salience: 0,
        };
        let rules = spec.build();
        assert_eq!(rules.len(), 1);
        let r = &rules[0];
        assert_eq!(r.lhs.criterions.len(), 2);
        assert_eq!(r.rule_type, Some(RuleType::Scorecard));
        // RHS has on_pass_action + the scorecard marker.
        assert_eq!(r.rhs.actions.len(), 2);
        assert_eq!(r.rhs.actions[0], "approve");
        assert!(r.rhs.actions[1].starts_with("scorecard:"));
        assert!(r.rhs.actions[1].contains("60"));
    }

    #[test]
    fn empty_scorecard_emits_rule_with_empty_lhs() {
        // No conditions — scorecard is "always match if 0 >= threshold",
        // which is true only if threshold <= 0. The build() should still
        // emit a rule (caller decides whether to enable it).
        let spec = ScorecardSpec {
            id: "sc0".into(),
            name: "t".into(),
            score_var: "score".into(),
            threshold: 0.0,
            on_pass_action: vec![],
            conditions: vec![],
            salience: 0,
        };
        let rules = spec.build();
        assert_eq!(rules.len(), 1);
        assert!(rules[0].lhs.criterions.is_empty());
    }
}
