//! `ul_rule` — script-rule adapter.
//!
//! Java `UlRuleBuilder` / `ScriptRule` produces a single `Rule`
//! whose LHS is whatever the user typed in the script (typically a
//! `Criterion`), and whose RHS is the script body's
//! `console.print` / `assign` actions. Since the user is
//! authoring the rule in our DSL, the script form is structurally
//! identical to a simple rule.
//!
//! V5.25 P5 doesn't ship a script interpreter; `build()` is a
//! pass-through wrapper that tags the rule with
//! `rule_type: Some(RuleType::Ul)` so the runtime knows the rule
//! came from a script source. P6+ may add a real script body.

use crate::model::rule::{Rule, RuleType};

/// `build` — pass-through with `rule_type` tag. The script's
/// conditions are already in the flat `Lhs` form; we just stamp
/// the type so consumers can distinguish a UL rule from an RL
/// rule at runtime (e.g. for trace logging).
pub fn build(mut rule: Rule) -> Vec<Rule> {
    rule.rule_type = Some(RuleType::Ul);
    vec![rule]
}
