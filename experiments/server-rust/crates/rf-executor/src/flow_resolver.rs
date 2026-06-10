//! `FlowResolver` trait — the seam between
//! [`SubProcessExecutor`](crate::executors::sub_process::SubProcessExecutor)
//! and a concrete source of `FlowDefinition`s.
//!
//! The rf-executor crate can't depend on rf-http (the dependency
//! chain is `rf-executor ← rf-http`), so the trait lives here and
//! rf-http's `FlowDefinitionRepo` implements it. The trait is
//! intentionally minimal: a single async `resolve` that takes a
//! `flow_id` and returns the parsed `Arc<FlowDefinition>`.
//!
//! ## Why async
//!
//! The HTTP implementation hits the Java console over the wire
//! and may need to wait for the response. The trait's `async`
//! surface lets the production binary use `HttpFlowResolver`
//! (which calls `FlowDefinitionRepo::get_or_load`) and tests use
//! an in-memory stub without changing the SubProcess executor.
//!
//! ## Error
//!
//! The trait uses `FlowError` (not a sub-error) so the dispatch
//! site can return the resolver's failure as-is. The most common
//! case is "flow not found" (the called element points at a
//! flow_id that's not been published yet) — `FlowError::NodeNotFound`
//! is the right surface.

use std::sync::Arc;

use async_trait::async_trait;
use rf_ir::flow_definition::FlowDefinition;

use crate::error::FlowError;

#[async_trait]
pub trait FlowResolver: Send + Sync {
    /// Resolve a `flow_id` to a parsed `FlowDefinition`. The
    /// returned `Arc` is cheap to clone (just a refcount bump)
    /// so callers can hand it to the sub-process traverser
    /// without re-parsing.
    async fn resolve(
        &self,
        flow_id: &str,
    ) -> Result<Arc<FlowDefinition>, FlowError>;
}
