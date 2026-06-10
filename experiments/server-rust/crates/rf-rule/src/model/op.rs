//! `Op` — comparison operator enum (20 values, 1:1 with Java `Op.java`).
//!
//! Java `Op.parse(symbol)` maps short symbols (`">"`, `"=="`, `"<="`) to the
//! matching variant. The JSON form uses the **variant name** (e.g. `"Equals"`,
//! `"GreaterThen"`), so the `serde(rename_all = "PascalCase")` default is
//! correct — but Java uses camelCase for some (`EqualsIgnoreCase` is already
//! PascalCase, no change). All 20 match.

use serde::{Deserialize, Serialize};

/// 20 comparison operators — port of `com.ruleforge.model.rule.Op`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum Op {
    Equals,
    EqualsIgnoreCase,
    NotEquals,
    NotEqualsIgnoreCase,
    LessThen,
    LessThenEquals,
    GreaterThen,
    GreaterThenEquals,
    In,
    NotIn,
    StartWith,
    NotStartWith,
    EndWith,
    NotEndWith,
    Null,
    NotNull,
    Match,
    NotMatch,
    Contain,
    NotContain,
}

impl Op {
    /// Short UEL-style symbol (`">"`, `"=="`, `"!="`, …) to `Op`. Mirrors
    /// Java `Op.parse(String)`. Used by the legacy condition evaluator in
    /// `rf-executor/src/condition.rs` for backward compatibility.
    pub fn from_symbol(s: &str) -> Option<Self> {
        Some(match s {
            "==" => Self::Equals,
            "!=" => Self::NotEquals,
            ">" => Self::GreaterThen,
            ">=" => Self::GreaterThenEquals,
            "<" => Self::LessThen,
            "<=" => Self::LessThenEquals,
            _ => return None,
        })
    }

    pub fn as_symbol(&self) -> &'static str {
        match self {
            Self::Equals => "==",
            Self::NotEquals => "!=",
            Self::GreaterThen => ">",
            Self::GreaterThenEquals => ">=",
            Self::LessThen => "<",
            Self::LessThenEquals => "<=",
            // No short symbol — Java uses the full name as the string
            // key (`"In"`, `"NotIn"`, etc).
            _ => "",
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn from_symbol_maps_basic() {
        assert_eq!(Op::from_symbol(">="), Some(Op::GreaterThenEquals));
        assert_eq!(Op::from_symbol("=="), Some(Op::Equals));
        assert_eq!(Op::from_symbol("foo"), None);
    }

    #[test]
    fn round_trip_json() {
        let s = serde_json::to_string(&Op::Match).unwrap();
        assert_eq!(s, "\"Match\"");
        let back: Op = serde_json::from_str(&s).unwrap();
        assert_eq!(back, Op::Match);
    }
}
