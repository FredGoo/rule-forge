//! Decision flow executor.
//!
//! Phase 3 — type-state traverser, routing, condition evaluator, dispatch
//! stub. Phase 4 fills in the 5 NodeExecutors; Phase 5 wires HTTP;
//! Phase 6 adds pg persistence.

#![allow(dead_code)]

pub mod condition;
pub mod dispatch;
pub mod error;
pub mod flow_context;
pub mod next_node;
pub mod node_executor;
pub mod node_result;
pub mod rule_engine;
pub mod traverser;
pub mod vars;
