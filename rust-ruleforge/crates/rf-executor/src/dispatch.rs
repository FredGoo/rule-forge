//! Exhaustive node dispatch — the core architectural win over Java's
//! `NodeExecutorRegistry`.
//!
//! Java: `registry.get(node.getType() + ":" + ext.get("ruleforge:taskType"))`
//! misses at runtime as NPE if a new node kind is added without registering.
//!
//! Rust: this `match` is exhaustive. Adding a `NodeKind` variant without
//! handling it here is a **compile error** at the `match` arm. The
//! `NodeKind` sum type + this dispatcher form a closed system.

use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;

use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_result::NodeResult;

/// Run a single node against the context. Phase 3 stub: every kind returns
/// `Continue`. Phase 4 fills in Rule / Action / Script / UserTask / Gateway.
pub async fn dispatch(_node: &FlowNode, _ctx: &mut FlowContext) -> Result<NodeResult, FlowError> {
    match &_node.kind {
        NodeKind::StartEvent => Ok(NodeResult::Continue),
        NodeKind::EndEvent => Ok(NodeResult::Continue),
        NodeKind::ServiceTask { .. } => Ok(NodeResult::Continue),
        NodeKind::ScriptTask { .. } => Ok(NodeResult::Continue),
        NodeKind::UserTask { .. } => Ok(NodeResult::Continue), // Phase 4: Suspend
        NodeKind::ExclusiveGateway { .. } => Ok(NodeResult::Continue),
        NodeKind::ParallelGateway { .. } => Ok(NodeResult::Continue),
        NodeKind::IntermediateEvent { .. } => Ok(NodeResult::Continue),
        NodeKind::SubProcess { .. } => Err(FlowError::Unsupported("SubProcess")),
    }
}
