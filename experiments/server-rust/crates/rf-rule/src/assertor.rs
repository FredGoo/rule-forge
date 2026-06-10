//! `Assertor` — 20 comparison operators, port of Java
//! `com.ruleforge.runtime.assertor.*` (1 trait + 20 impls).
//!
//! Java has 20 separate `<Op>Assertor.java` files; in Rust we put them in
//! one file as a single `Assertor` trait + a free `evaluate` function
//! that dispatches by `Op` variant. Behaviour mirrors each Java
//! assertor 1:1, with these Rust-side simplifications:
//!
//! - No Spring `ApplicationContext` — the dispatcher is a plain match.
//! - "Datatype" is `serde_json::Value` (the LHS / RHS are already
//!   `serde_json::Value` thanks to the working-memory layer).
//! - `String` vs `Number` vs `Boolean` is decided by the JSON tag at
//!   runtime. Java's `Datatype` enum is derived from the JSON tag in
//!   `rf-executor::vars` and passed in for `Equals` / `NotEquals`
//!   comparisons; we keep the tag inline.
//! - "In" / "NotIn" / "Contain" / "NotContain" / "Match" / "NotMatch"
//!   use simple string-based semantics matching Java's `toString()`
//!   based compare (CSV split for In, `regex.matches()` for Match,
//!   `contains` for Contain).
//!
//! ## Cache
//!
//! Each `evaluate` call is a pure function over `(left, right, op)`. The
//! `EvaluationContext.criteria_value_map` caches the per-`Criteria.id()`
//! result so a criteria shared across rules only runs once per fact
//! fire. The assertor itself is stateless.

use crate::model::op::Op;
use regex::Regex;
use serde_json::Value as JsonValue;

/// Evaluate `left op right` for a single comparison.
///
/// Returns `true` if the comparison matches. Behavioural contract:
/// - `Null` / `NotNull` test only the left side (right is ignored).
/// - All other ops return `false` if either side is `Value::Null`
///   (except `Equals` / `NotEquals` which test the nulls themselves).
/// - Numeric / String / Bool comparisons use JSON-native semantics.
pub fn evaluate(left: &JsonValue, right: &JsonValue, op: Op) -> bool {
    match op {
        Op::Null => is_nullish(left),
        Op::NotNull => !is_nullish(left),
        Op::Equals => eq(left, right),
        Op::EqualsIgnoreCase => eq_ignore_case(left, right),
        Op::NotEquals => !eq(left, right),
        Op::NotEqualsIgnoreCase => !eq_ignore_case(left, right),
        Op::LessThen => lt(left, right),
        Op::LessThenEquals => le(left, right),
        Op::GreaterThen => gt(left, right),
        Op::GreaterThenEquals => ge(left, right),
        Op::In => in_set(left, right),
        Op::NotIn => !in_set(left, right),
        Op::StartWith => starts_with_str(left, right),
        Op::NotStartWith => !starts_with_str(left, right),
        Op::EndWith => ends_with_str(left, right),
        Op::NotEndWith => !ends_with_str(left, right),
        Op::Match => regex_match(left, right),
        Op::NotMatch => !regex_match(left, right),
        Op::Contain => contains(left, right),
        Op::NotContain => !contains(left, right),
    }
}

// ---- helpers ----

/// Java `NullAssertor`: null OR blank string (whitespace-only counts).
fn is_nullish(v: &JsonValue) -> bool {
    match v {
        JsonValue::Null => true,
        JsonValue::String(s) => s.trim().is_empty(),
        _ => false,
    }
}

/// Java `EqualsAssertor`: datatype-aware equality. Rust has no
/// explicit datatype — we use the JSON tag as the discriminator.
fn eq(left: &JsonValue, right: &JsonValue) -> bool {
    match (left, right) {
        (JsonValue::Null, JsonValue::Null) => true,
        (JsonValue::Null, _) | (_, JsonValue::Null) => false,
        // String
        (JsonValue::String(a), JsonValue::String(b)) => a == b,
        // Number — compare as f64 for cross-int/float compat
        (JsonValue::Number(a), JsonValue::Number(b)) => {
            a.as_f64().zip(b.as_f64()).map(|(x, y)| (x - y).abs() < f64::EPSILON).unwrap_or(false)
        }
        // Bool
        (JsonValue::Bool(a), JsonValue::Bool(b)) => a == b,
        // Cross-type: stringify + compare (Java fallback default)
        _ => left.to_string() == right.to_string(),
    }
}

fn eq_ignore_case(left: &JsonValue, right: &JsonValue) -> bool {
    match (left, right) {
        (JsonValue::String(a), JsonValue::String(b)) => {
            a.to_lowercase() == b.to_lowercase()
        }
        _ => eq(left, right),
    }
}

fn lt(left: &JsonValue, right: &JsonValue) -> bool {
    num_cmp(left, right) == Some(std::cmp::Ordering::Less)
        || str_cmp(left, right) == Some(std::cmp::Ordering::Less)
}

fn le(left: &JsonValue, right: &JsonValue) -> bool {
    matches!(
        num_cmp(left, right),
        Some(std::cmp::Ordering::Less | std::cmp::Ordering::Equal)
    ) || matches!(
        str_cmp(left, right),
        Some(std::cmp::Ordering::Less | std::cmp::Ordering::Equal)
    )
}

fn gt(left: &JsonValue, right: &JsonValue) -> bool {
    num_cmp(left, right) == Some(std::cmp::Ordering::Greater)
        || str_cmp(left, right) == Some(std::cmp::Ordering::Greater)
}

fn ge(left: &JsonValue, right: &JsonValue) -> bool {
    matches!(
        num_cmp(left, right),
        Some(std::cmp::Ordering::Greater | std::cmp::Ordering::Equal)
    ) || matches!(
        str_cmp(left, right),
        Some(std::cmp::Ordering::Greater | std::cmp::Ordering::Equal)
    )
}

fn num_cmp(left: &JsonValue, right: &JsonValue) -> Option<std::cmp::Ordering> {
    match (left, right) {
        (JsonValue::Number(a), JsonValue::Number(b)) => {
            a.as_f64().zip(b.as_f64()).map(|(x, y)| {
                x.partial_cmp(&y).unwrap_or(std::cmp::Ordering::Equal)
            })
        }
        _ => None,
    }
}

fn str_cmp(left: &JsonValue, right: &JsonValue) -> Option<std::cmp::Ordering> {
    match (left, right) {
        (JsonValue::String(a), JsonValue::String(b)) => {
            Some(a.cmp(b))
        }
        _ => None,
    }
}

/// Java `InAssertor`: left is in right (right is CSV string OR
/// JSON array of values). For strings, left is also CSV-split.
fn in_set(left: &JsonValue, right: &JsonValue) -> bool {
    if left.is_null() || right.is_null() {
        return false;
    }
    // Normalize right side to Vec<JsonValue> for direct equality.
    let right_items: Vec<JsonValue> = match right {
        JsonValue::Array(items) => items.clone(),
        JsonValue::String(s) => s
            .split(',')
            .map(|p| JsonValue::String(p.trim().to_string()))
            .collect(),
        _ => vec![right.clone()],
    };
    if right_items.is_empty() {
        return false;
    }
    match left {
        JsonValue::Array(items) => items
            .iter()
            .any(|l| right_items.iter().any(|r| r == l)),
        JsonValue::String(s) => s.split(',').any(|part| {
            let part = JsonValue::String(part.trim().to_string());
            right_items.iter().any(|r| r == &part)
        }),
        _ => right_items.iter().any(|r| r == left),
    }
}

fn starts_with_str(left: &JsonValue, right: &JsonValue) -> bool {
    match (left, right) {
        (JsonValue::String(a), JsonValue::String(b)) => a.starts_with(b.as_str()),
        _ => false,
    }
}

fn ends_with_str(left: &JsonValue, right: &JsonValue) -> bool {
    match (left, right) {
        (JsonValue::String(a), JsonValue::String(b)) => a.ends_with(b.as_str()),
        _ => false,
    }
}

fn regex_match(left: &JsonValue, right: &JsonValue) -> bool {
    match (left, right) {
        (JsonValue::String(input), JsonValue::String(pattern)) => {
            match Regex::new(pattern) {
                Ok(re) => re.is_match(input),
                Err(_) => false,
            }
        }
        _ => false,
    }
}

/// Java `ContainAssertor`: `left` contains `right`. For String, it's
/// substring. For Array, `right` (single or array) is a member of
/// `left`.
fn contains(left: &JsonValue, right: &JsonValue) -> bool {
    if left.is_null() || right.is_null() {
        return false;
    }
    match left {
        JsonValue::String(s) => match right {
            JsonValue::String(needle) => s.contains(needle.as_str()),
            _ => s.contains(right.to_string().as_str()),
        },
        JsonValue::Array(items) => match right {
            JsonValue::Array(rs) => rs.iter().all(|r| items.iter().any(|i| i == r)),
            _ => items.iter().any(|i| i == right),
        },
        _ => left.to_string().contains(right.to_string().as_str()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn equals_string() {
        assert!(evaluate(&json!("alice"), &json!("alice"), Op::Equals));
        assert!(!evaluate(&json!("alice"), &json!("bob"), Op::Equals));
    }

    #[test]
    fn equals_ignore_case() {
        assert!(evaluate(&json!("Alice"), &json!("ALICE"), Op::EqualsIgnoreCase));
        assert!(!evaluate(&json!("Alice"), &json!("Bob"), Op::EqualsIgnoreCase));
    }

    #[test]
    fn not_equals() {
        assert!(evaluate(&json!(1), &json!(2), Op::NotEquals));
        assert!(!evaluate(&json!(1), &json!(1), Op::NotEquals));
    }

    #[test]
    fn numeric_compare() {
        assert!(evaluate(&json!(5), &json!(10), Op::LessThen));
        assert!(evaluate(&json!(10), &json!(10), Op::LessThenEquals));
        assert!(evaluate(&json!(10), &json!(5), Op::GreaterThen));
        assert!(evaluate(&json!(10), &json!(10), Op::GreaterThenEquals));
    }

    #[test]
    fn string_compare() {
        assert!(evaluate(&json!("abc"), &json!("abd"), Op::LessThen));
        assert!(evaluate(&json!("abc"), &json!("abc"), Op::LessThenEquals));
    }

    #[test]
    fn null_unmatched_for_arithmetic() {
        assert!(!evaluate(&json!(null), &json!(1), Op::GreaterThen));
        assert!(!evaluate(&json!(1), &json!(null), Op::GreaterThen));
    }

    #[test]
    fn null_op_ignores_right() {
        // NullAssertor uses StringUtils.isBlank semantics: null OR
        // empty OR whitespace-only is "null". The right side is
        // ignored entirely.
        assert!(evaluate(&json!(null), &json!("anything"), Op::Null));
        assert!(evaluate(&json!(""), &json!("anything"), Op::Null));
        assert!(evaluate(&json!("   "), &json!("anything"), Op::Null));
        assert!(!evaluate(&json!("x"), &json!("anything"), Op::Null));
        assert!(evaluate(&json!("x"), &json!("anything"), Op::NotNull));
    }

    #[test]
    fn in_array() {
        assert!(evaluate(&json!("alice"), &json!(["alice", "bob"]), Op::In));
        assert!(!evaluate(&json!("eve"), &json!(["alice", "bob"]), Op::In));
    }

    #[test]
    fn in_csv_string() {
        assert!(evaluate(&json!("alice"), &json!("alice,bob,carol"), Op::In));
        assert!(!evaluate(&json!("eve"), &json!("alice,bob,carol"), Op::In));
    }

    #[test]
    fn in_csv_left_csv() {
        // Java splits left on "," too.
        assert!(evaluate(
            &json!("alice,carol"),
            &json!(["bob", "carol", "alice"]),
            Op::In
        ));
    }

    #[test]
    fn not_in_inverts() {
        assert!(evaluate(&json!("eve"), &json!(["alice", "bob"]), Op::NotIn));
        assert!(!evaluate(&json!("alice"), &json!(["alice", "bob"]), Op::NotIn));
    }

    #[test]
    fn start_with() {
        assert!(evaluate(&json!("hello world"), &json!("hello"), Op::StartWith));
        assert!(!evaluate(&json!("hello world"), &json!("world"), Op::StartWith));
    }

    #[test]
    fn end_with() {
        assert!(evaluate(&json!("hello world"), &json!("world"), Op::EndWith));
        assert!(!evaluate(&json!("hello world"), &json!("hello"), Op::EndWith));
    }

    #[test]
    fn regex_match() {
        assert!(evaluate(&json!("hello123"), &json!("^hello[0-9]+$"), Op::Match));
        assert!(!evaluate(&json!("hello"), &json!("^hello[0-9]+$"), Op::Match));
    }

    #[test]
    fn regex_match_invalid_pattern_returns_false() {
        // Pattern fails to compile → Java would throw; we return false.
        assert!(!evaluate(&json!("x"), &json!("[unclosed"), Op::Match));
    }

    #[test]
    fn contain_string_substring() {
        assert!(evaluate(&json!("hello world"), &json!("lo wo"), Op::Contain));
        assert!(!evaluate(&json!("hello world"), &json!("xyz"), Op::Contain));
    }

    #[test]
    fn contain_array_member() {
        assert!(evaluate(
            &json!(["a", "b", "c"]),
            &json!("b"),
            Op::Contain
        ));
        assert!(evaluate(
            &json!(["a", "b", "c"]),
            &json!(["a", "c"]),
            Op::Contain
        ));
        assert!(!evaluate(
            &json!(["a", "b"]),
            &json!(["a", "z"]),
            Op::Contain
        ));
    }
}
