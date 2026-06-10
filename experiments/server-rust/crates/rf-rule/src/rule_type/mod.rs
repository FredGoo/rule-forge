//! High-level rule-type adapters.
//!
//! V5.25 P5 ports the Java `builder/*` adapters that turn a
//! domain-specific rule format (decision table / decision tree /
//! scorecard / script) into a list of `Rule` instances the
//! `ReteBuilder` can compile.
//!
//! Java splits these into per-type packages
//! (`com.ruleforge.builder.table.DecisionTableRulesBuilder` etc.),
//! each producing `List<Rule>`. We mirror that one-to-one: each
//! sub-module exposes a `Spec` input type and a `build() -> Vec<Rule>`
//! function.
//!
//! All adapters produce the same output shape — `Vec<Rule>` with a
//! flat `Lhs` (a `Vec<Criteria>` that the And-builder flattens into
//! an AndNode chain). This is the same shape the JSON deserializer
//! produces; once we have `Vec<Rule>`, the existing
//! `KnowledgePackageWrapper` + `ReteInstance::from_wrapper` pipeline
//! is unchanged.

pub mod decision_table;
pub mod decision_tree;
pub mod scorecard;
pub mod simple_rule;
pub mod ul_rule;

pub use decision_table::{DecisionTableRow, DecisionTableSpec, HitPolicy};
pub use decision_tree::{DecisionTreeNode, DecisionTreeSpec};
pub use scorecard::{ScorecardCondition, ScorecardSpec};
