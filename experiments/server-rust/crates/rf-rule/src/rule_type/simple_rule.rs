//! `simple_rule` — the default path. A `Rule` already in the flat
//! `Lhs` form is passed through unchanged.
//!
//! Java: `Rule` constructed by the user (RL/UL) IS already a
//! `Rule { Lhs, Rhs }`; the "RL builder" is a no-op pass-through.
//! We do the same: `build()` returns `vec![rule]` for a single
//! `Rule`, or `rules` for a `Vec<Rule>`.
//!
//! This module exists so the rule-type dispatcher
//! (`build_for_rule_type` in the future) can use a uniform
//! `Vec<Rule>` return type from every adapter.

use crate::model::rule::Rule;

/// `build` — pass-through. The "rule" in the source code is
/// already in the canonical flat-Lhs form.
pub fn build(rule: Rule) -> Vec<Rule> {
    vec![rule]
}

/// `build_many` — pass-through for an explicit list of rules. Used
/// by callers that batch-build rules (e.g. test fixtures).
pub fn build_many(rules: Vec<Rule>) -> Vec<Rule> {
    rules
}
