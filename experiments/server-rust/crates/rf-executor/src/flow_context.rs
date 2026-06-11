//! Per-execution context.
//!
//! Every `evaluate` call gets a fresh `FlowContext`. The traverser mutates
//! `current_node_id` as it steps; the userTask path writes
//! `current_awaiting_field` + `current_awaiting_value` to coordinate with
//! the next gateway's binary-decision routing (mirrors Java's
//! `currentAwaitingField` mechanism).

use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::vars::Vars;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct FlowContext {
    /// UUID for this run, links to `rust_decision_flow_state.flow_run_id`.
    pub flow_run_id: String,
    /// Process variables. Plain JSON values — `Value::String`,
    /// `Value::Number`, `Value::Object`, etc. No `Object`/POJO escape hatch
    /// like Java's `outputModel` — the Rust executor stays inside the JSON
    /// model end-to-end.
    pub vars: Vars,
    /// Updated by the traverser on every step. Useful for the persistence
    /// layer to write `current_node_id` to the state row.
    pub current_node_id: Option<String>,
    /// Field name whose value determines the next gateway's branch — set
    /// by `UserTaskNodeExecutor` (Phase 4) before suspending, read by
    /// [`next_node`](crate::next_node) on the very next step.
    pub current_awaiting_field: Option<String>,
    /// Value of `current_awaiting_field` in the current vars. Cleared once
    /// consumed by the gateway.
    pub current_awaiting_value: Option<Value>,
    /// V5.28 P1 — set by an action / rule that wants to
    /// throw an error. The `traverse` driver reads this
    /// after the activity's `dispatch` and routes the
    /// flow to the attached boundary's outgoing (if a
    /// boundary with a matching `errorRef` exists).
    /// Cleared once consumed. Convention: an action
    /// that wants to throw calls
    /// `ctx.thrown_error = Some("error".to_string())` (or
    /// any other ref it wants the boundary to match).
    #[serde(default)]
    pub thrown_error: Option<String>,
}

impl FlowContext {
    pub fn new(flow_run_id: impl Into<String>) -> Self {
        Self {
            flow_run_id: flow_run_id.into(),
            vars: Vars::new(),
            current_node_id: None,
            current_awaiting_field: None,
            current_awaiting_value: None,
            thrown_error: None,
        }
    }
}
