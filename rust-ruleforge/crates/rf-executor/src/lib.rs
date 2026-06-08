//! Decision flow executor.
//!
//! Phase 1 placeholder. Real Traverser + NodeExecutors land in Phase 3-4.
//! Holds the `RuleEngine` trait so `rf-rule` impls can satisfy it without a
//! cyclic dep (the trait needs `FlowContext` which lives here).

#![allow(dead_code)]

pub mod error;
pub mod flow_context;
pub mod node_executor;
pub mod node_result;
pub mod rule_engine;
pub mod vars;
