//! `NodeExecutor` trait — the seam between the dispatcher and a concrete
//! implementation. Phase 4 adds the 5 concrete impls; the dispatcher in
//! `dispatch.rs` holds a `ExecutorRegistry` of `Arc<dyn NodeExecutor>`s.
//!
//! ## `execute_with`
//!
//! Most executors only need the node and the context. The
//! [`SubProcessExecutor`](crate::executors::sub_process::SubProcessExecutor)
//! is the one exception: it has to recursively call
//! `traverse()` on a sub-flow, which needs the parent
//! `ExecutorRegistry`. The default `execute_with` falls through
//! to `execute`, so existing impls don't need to change.

use crate::dispatch::ExecutorRegistry;
use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_result::NodeResult;
use rf_ir::flow_node::FlowNode;

#[async_trait::async_trait]
pub trait NodeExecutor: Send + Sync {
    async fn execute(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError>;

    /// Run the executor with access to the parent
    /// `ExecutorRegistry`. Default delegates to `execute`;
    /// SubProcessExecutor overrides this to recursively
    /// traverse a sub-flow.
    async fn execute_with(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
        _reg: &ExecutorRegistry,
    ) -> Result<NodeResult, FlowError> {
        self.execute(node, ctx).await
    }
}
