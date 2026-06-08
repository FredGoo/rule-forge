//! Placeholder. Real NodeExecutor trait lands in Phase 4.

use crate::node_result::NodeResult;

#[async_trait::async_trait]
pub trait NodeExecutor: Send + Sync {
    async fn execute(&self) -> NodeResult;
}
