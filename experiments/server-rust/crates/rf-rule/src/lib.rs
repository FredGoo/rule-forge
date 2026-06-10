//! Rule engine implementations.
//!
//! `RuleEngine` trait + `RuleResults` live in `rf-executor` (the trait needs
//! `FlowContext` and is invoked by `RuleNodeExecutor` — both in `rf-executor`).
//! This crate holds concrete impls: `MockRuleEngine` (Phase 4), and a future
//! `RemoteRuleEngine` that talks to the Java executor via HTTP (Phase 7+).

#![allow(dead_code)]

pub mod agenda;
pub mod assertor;
pub mod deserialize;
pub mod fact;
pub mod loader;
pub mod mock;
pub mod model;
pub mod rete;
pub mod rete_builder;
pub mod rete_engine;
pub mod rule_type;
pub mod value_compute;
