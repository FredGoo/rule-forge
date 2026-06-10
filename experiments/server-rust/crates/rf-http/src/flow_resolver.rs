//! `FlowResolver` impl backed by `FlowDefinitionRepo`.
//!
//! The `SubProcessExecutor` (in rf-executor) needs a way to look
//! up sub-flow `FlowDefinition`s by id. The trait lives in
//! rf-executor (so rf-executor can stay decoupled from
//! rf-http); the production impl lives here and wraps the
//! existing `FlowDefinitionRepo` — so sub-flow lookups go
//! through the same cache + HTTP-fetch path that the main
//! `/evaluate` handler uses.
//!
//! ## `RepoError` → `FlowError`
//!
//! `FlowDefinitionRepo` returns its own `RepoError` enum. The
//! `FlowResolver` trait wants `FlowError`. The mapping is:
//!
//! - `RepoError::Loader(NotFound)` → `FlowError::NodeNotFound`
//! - everything else → `FlowError::Action(err.to_string())`
//!
//! This is a deliberate one-way street: a missing sub-flow is
//! a "not found" error that the dispatch site recognizes;
//! anything else (HTTP failure, parse error) is a generic
//! action error. The HTTP `/health` endpoint or the
//! persistence layer can later classify these.

use std::sync::Arc;

use async_trait::async_trait;
use rf_executor::error::FlowError;
use rf_executor::flow_resolver::FlowResolver;
use rf_ir::flow_definition::FlowDefinition;

use crate::flow_def_repo::{FlowDefinitionRepo, FlowLoaderError, RepoError};

/// `HttpFlowResolver` — wraps a `FlowDefinitionRepo` so the
/// `SubProcessExecutor` can resolve sub-flows through the same
/// cache + HTTP-fetch path as the main `/evaluate` handler.
pub struct HttpFlowResolver {
    repo: Arc<FlowDefinitionRepo>,
}

impl HttpFlowResolver {
    pub fn new(repo: Arc<FlowDefinitionRepo>) -> Self {
        Self { repo }
    }
}

#[async_trait]
impl FlowResolver for HttpFlowResolver {
    async fn resolve(
        &self,
        flow_id: &str,
    ) -> Result<Arc<FlowDefinition>, FlowError> {
        match self.repo.get_or_load(flow_id).await {
            Ok(def) => Ok(def),
            Err(RepoError::Loader(FlowLoaderError::NotFound(_))) => {
                Err(FlowError::NodeNotFound(format!(
                    "sub-flow not found: {flow_id}"
                )))
            }
            Err(e) => Err(FlowError::Action(e.to_string())),
        }
    }
}
