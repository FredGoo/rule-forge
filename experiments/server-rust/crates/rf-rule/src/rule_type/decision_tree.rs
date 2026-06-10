//! `decision_tree` — tree-format rule adapter.
//!
//! A decision tree is a binary `if / else if` cascade: each
//! internal node has a `Criteria` plus `true` and `false`
//! sub-trees; leaves carry actions.
//!
//! Java `DecisionTreeRulesBuilder` walks the tree in-order, and
//! for each `Leaf` produces a `Rule` whose `Lhs` is the AND of
//! every `Criteria` on the path from root to that leaf
//! (negated for `false` branches using `Op::NotEquals` or
//! `Op::NotMatch` etc.).
//!
//! V5.25 P5 mirrors this: each leaf becomes one `Rule`, and the
//! `Lhs` is the chained AND of path conditions (with `false`
//! branches negated).
//!
//! ## Negation
//!
//! We negate by flipping the operator: `==` → `!=`, `>` → `<=`,
//! `In` → `NotIn`, `Match` → `NotMatch`, etc. The full
//! `not(op)` mapping is in `invert_op` (private to the module).

use serde::{Deserialize, Serialize};

use crate::model::criteria::Criteria;
use crate::model::op::Op;
use crate::model::rule::{Lhs, Rhs, Rule, RuleType};

/// `DecisionTreeNode` — one node in the decision tree.
///
/// - `Leaf { actions }` is a terminal node (rule fires).
/// - `Branch { condition, true_branch, false_branch }` is an
///   internal decision.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum DecisionTreeNode {
    Leaf {
        actions: Vec<String>,
    },
    Branch {
        condition: Box<Criteria>,
        true_branch: Box<DecisionTreeNode>,
        false_branch: Box<DecisionTreeNode>,
    },
}

/// `DecisionTreeSpec` — the tree as authored in the editor.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct DecisionTreeSpec {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub salience: i32,
    pub root: DecisionTreeNode,
}

impl DecisionTreeSpec {
    /// Build the rule list. One `Rule` per leaf; the leaf's path
    /// from root is the AND of every condition on that path
    /// (with `false`-branch conditions negated).
    pub fn build(&self) -> Vec<Rule> {
        let mut rules = Vec::new();
        let mut path: Vec<Criteria> = Vec::new();
        let mut leaf_counter: i32 = 0;
        Self::walk(&self.root, &mut path, &mut leaf_counter, &self.id, &self.name, self.salience, &mut rules);
        rules
    }

    fn walk(
        node: &DecisionTreeNode,
        path: &mut Vec<Criteria>,
        leaf_counter: &mut i32,
        rule_id_prefix: &str,
        rule_name_prefix: &str,
        salience: i32,
        out: &mut Vec<Rule>,
    ) {
        match node {
            DecisionTreeNode::Leaf { actions } => {
                *leaf_counter += 1;
                let rule = Rule {
                    id: format!("{}-leaf{}", rule_id_prefix, leaf_counter),
                    name: format!("{}-leaf{}", rule_name_prefix, leaf_counter),
                    rule_type: Some(RuleType::DecisionTree),
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
                    lhs: Lhs {
                        criterions: path.clone(),
                    },
                    rhs: Rhs {
                        actions: actions.clone(),
                    },
                    r#loop: false,
                    remark: None,
                    with_else: false,
                };
                out.push(rule);
            }
            DecisionTreeNode::Branch {
                condition,
                true_branch,
                false_branch,
            } => {
                // Walk the true branch with the condition in the path.
                path.push((**condition).clone());
                Self::walk(true_branch, path, leaf_counter, rule_id_prefix, rule_name_prefix, salience, out);
                path.pop();

                // Walk the false branch with the condition negated.
                let mut negated = (**condition).clone();
                negated.op = invert_op(negated.op);
                path.push(negated);
                Self::walk(false_branch, path, leaf_counter, rule_id_prefix, rule_name_prefix, salience, out);
                path.pop();
            }
        }
    }
}

/// `invert_op` — map an operator to its negation.
fn invert_op(op: Op) -> Op {
    match op {
        Op::Equals => Op::NotEquals,
        Op::EqualsIgnoreCase => Op::NotEqualsIgnoreCase,
        Op::NotEquals => Op::Equals,
        Op::NotEqualsIgnoreCase => Op::EqualsIgnoreCase,
        Op::LessThen => Op::GreaterThenEquals,
        Op::LessThenEquals => Op::GreaterThen,
        Op::GreaterThen => Op::LessThenEquals,
        Op::GreaterThenEquals => Op::LessThen,
        Op::In => Op::NotIn,
        Op::NotIn => Op::In,
        Op::StartWith => Op::NotStartWith,
        Op::NotStartWith => Op::StartWith,
        Op::EndWith => Op::NotEndWith,
        Op::NotEndWith => Op::EndWith,
        Op::Null => Op::NotNull,
        Op::NotNull => Op::Null,
        Op::Match => Op::NotMatch,
        Op::NotMatch => Op::Match,
        Op::Contain => Op::NotContain,
        Op::NotContain => Op::Contain,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::left::LeftType;
    use crate::model::left_part::{Left, LeftPart};
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
    fn binary_tree_produces_two_rules_with_negated_false_branch() {
        // if age >= 18: leaf("adult")
        // else:       leaf("minor")
        let spec = DecisionTreeSpec {
            id: "dt1".into(),
            name: "age_check".into(),
            salience: 0,
            root: DecisionTreeNode::Branch {
                condition: Box::new(age_criteria()),
                true_branch: Box::new(DecisionTreeNode::Leaf {
                    actions: vec!["adult".into()],
                }),
                false_branch: Box::new(DecisionTreeNode::Leaf {
                    actions: vec!["minor".into()],
                }),
            },
        };
        let rules = spec.build();
        assert_eq!(rules.len(), 2);
        // First leaf: age >= 18 (no negation).
        assert_eq!(rules[0].lhs.criterions.len(), 1);
        assert_eq!(rules[0].lhs.criterions[0].op, Op::GreaterThenEquals);
        assert_eq!(rules[0].rhs.actions, vec!["adult"]);
        // Second leaf: NOT(age >= 18) → age < 18.
        assert_eq!(rules[1].lhs.criterions.len(), 1);
        assert_eq!(rules[1].lhs.criterions[0].op, Op::LessThen);
        assert_eq!(rules[1].rhs.actions, vec!["minor"]);
    }

    #[test]
    fn three_level_nested_tree_chains_conditions() {
        // if age >= 18:
        //   if income >= 5000: leaf("approve")
        //   else:              leaf("review")
        // else:                  leaf("reject")
        let spec = DecisionTreeSpec {
            id: "dt1".into(),
            name: "approval".into(),
            salience: 5,
            root: DecisionTreeNode::Branch {
                condition: Box::new(age_criteria()),
                true_branch: Box::new(DecisionTreeNode::Branch {
                    condition: Box::new(income_criteria()),
                    true_branch: Box::new(DecisionTreeNode::Leaf {
                        actions: vec!["approve".into()],
                    }),
                    false_branch: Box::new(DecisionTreeNode::Leaf {
                        actions: vec!["review".into()],
                    }),
                }),
                false_branch: Box::new(DecisionTreeNode::Leaf {
                    actions: vec!["reject".into()],
                }),
            },
        };
        let rules = spec.build();
        assert_eq!(rules.len(), 3);
        // approve: age>=18 AND income>=5000
        assert_eq!(rules[0].lhs.criterions.len(), 2);
        assert_eq!(rules[0].lhs.criterions[0].op, Op::GreaterThenEquals);
        assert_eq!(rules[0].lhs.criterions[1].op, Op::GreaterThenEquals);
        assert_eq!(rules[0].salience, 5);
        assert_eq!(rules[0].rhs.actions, vec!["approve"]);
        // review: age>=18 AND income<5000
        assert_eq!(rules[1].lhs.criterions.len(), 2);
        assert_eq!(rules[1].lhs.criterions[0].op, Op::GreaterThenEquals);
        assert_eq!(rules[1].lhs.criterions[1].op, Op::LessThen);
        assert_eq!(rules[1].rhs.actions, vec!["review"]);
        // reject: age<18 (single negated condition)
        assert_eq!(rules[2].lhs.criterions.len(), 1);
        assert_eq!(rules[2].lhs.criterions[0].op, Op::LessThen);
        assert_eq!(rules[2].rhs.actions, vec!["reject"]);
    }
}
