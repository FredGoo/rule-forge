//! `Criteria` — single `left op value` condition. The atomic unit a
//! `CriteriaNode` evaluates.
//!
//! Java `Criteria.getId()` builds the canonical id from
//! `left.id + "【" + op + "】" + value.id` — we mirror that. The `id` is
//! used as a key in `EvaluationContext.criteria_value_map` to cache the
//! (left, right, result) tuple per fire cycle.

use serde::{Deserialize, Serialize};

use super::left_part::Left;
use super::op::Op;
use super::value::Value;
use super::value_type::ValueType;

/// `Criteria` — `left op value` predicate. The base class for an
/// "atomic" condition in a rule's LHS. And/Or/Junction are tree nodes
/// over multiple `Criteria`s.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Criteria {
    pub op: Op,
    pub left: Left,
    /// The RHS — `None` for `Null` / `NotNull` operators that don't need
    /// a value. (Java keeps the field but nulls it out.)
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub value: Option<Value>,
}

impl Criteria {
    /// Canonical id, matching Java `Criteria.getId()`.
    pub fn id(&self) -> String {
        let left_id = self.left.id();
        let op_label = self.op_label();
        match &self.value {
            Some(v) => format!("{left_id}【{op_label}】{}", v.id()),
            None => format!("{left_id}【{op_label}】"),
        }
    }

    fn op_label(&self) -> &'static str {
        match self.op {
            Op::Equals => "等于",
            Op::EqualsIgnoreCase => "等于(不分大小写)",
            Op::NotEquals => "不等于",
            Op::NotEqualsIgnoreCase => "不等于(不分大小写)",
            Op::LessThen => "小于",
            Op::LessThenEquals => "小于等于",
            Op::GreaterThen => "大于",
            Op::GreaterThenEquals => "大于等于",
            Op::In => "在集合中",
            Op::NotIn => "不在集合中",
            Op::StartWith => "开始于",
            Op::NotStartWith => "不开始于",
            Op::EndWith => "结束于",
            Op::NotEndWith => "不结束于",
            Op::Null => "为空",
            Op::NotNull => "不为空",
            Op::Match => "匹配",
            Op::NotMatch => "不匹配",
            Op::Contain => "包含",
            Op::NotContain => "不包含",
        }
    }
}

impl ValueType {
    /// Compact label used in the criteria id — matches Java's `[变量]` etc.
    pub fn as_label(&self) -> &'static str {
        match self {
            Self::Input => "[输入]",
            Self::Variable => "[变量]",
            Self::Constant => "[常量]",
            Self::VariableCategory => "[变量对象]",
            Self::Method => "[BEAN]",
            Self::Parameter => "[参数]",
            Self::Paren => "[PAREN]",
            Self::CommonFunction => "[CFUNC]",
            Self::NamedReference => "[REF]",
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::left::LeftType;
    use crate::model::left_part::LeftPart;
    use crate::model::value::Value;

    fn simple_left() -> Left {
        Left {
            left_type: LeftType::Variable,
            left_part: LeftPart::Variable {
                variable_category: Some("Applicant".into()),
                variable_label: Some("age".into()),
                variable_name: Some("age".into()),
                datatype: Some("int".into()),
            },
            arithmetic: None,
        }
    }

    #[test]
    fn id_format_with_value() {
        let c = Criteria {
            op: Op::GreaterThenEquals,
            left: simple_left(),
            value: Some(Value::VariableCategory {
                variable_category: "Applicant".into(),
            }),
        };
        // Java: left.id + "【op】" + value.getId() — no space, no trailing category in label.
        // We have to include the category explicitly because the Value enum
        // flattens it; that's intentional for v0.
        assert_eq!(
            c.id(),
            "[变量]Applicant.age【大于等于】[变量对象]Applicant"
        );
    }

    #[test]
    fn id_format_without_value_for_null_op() {
        let c = Criteria {
            op: Op::NotNull,
            left: simple_left(),
            value: None,
        };
        assert_eq!(c.id(), "[变量]Applicant.age【不为空】");
    }
}
