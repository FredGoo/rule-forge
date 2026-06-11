//! SubProcessNodeExecutor — call a sub-flow and rejoin.
//!
//! BPMN 2.0 sub-processes are nodes that, when entered, spawn a
//! nested flow (a separate `FlowDefinition` loaded by id).
//! When the sub-flow completes, the parent flow continues from
//! the SubProcess node's outgoing edge.
//!
//! ## V5.27 model
//!
//! - The sub-process is identified by the
//!   `ruleforge:calledElement` attribute on the node (BPMN 2.0
//!   spec uses the same name).
//! - The sub-flow's `FlowDefinition` is resolved via a
//!   [`FlowResolver`] that the registry holds. The HTTP main
//!   wires `FlowDefinitionRepo` (which hits the Java console's
//!   `/ruleforge/flow/load`) as the resolver.
//! - The sub-flow runs in a fresh `FlowContext` that inherits
//!   the parent's vars (via `WorkingMemory::clone_for_subflow`
//!   on the executor side — the parent vars map is copied
//!   shallowly so the sub-flow's writes don't leak back to the
//!   parent on the "before sub-flow completes" path).
//! - When the sub-flow `Completes`, the parent continues with
//!   the same `ctx` (the parent vars stay; sub-flow writes that
//!   we want to surface to the parent are passed back via the
//!   "output mapping" attrs on the SubProcess node — V5.27
//!   only supports a flat copy of the sub-flow's output vars).
//! - When the sub-flow `Suspends`, the parent also suspends
//!   with the sub-flow's `SuspendInfo`. The `/flow/event`
//!   handler at Phase 4+ needs to know which flow it's
//!   resuming; V5.27's resume key is `parent_flow_run_id` and
//!   we record the sub-flow's wait_ref in the payload so the
//!   resume handler can disambiguate.
//!
//! ## Why a separate executor
//!
//! The SubProcess is the only node that needs recursive
//! `traverse()`. Routing it through the dispatcher's regular
//! `execute()` would require the executor to access the parent
//! `ExecutorRegistry`, which the trait didn't expose. The new
//! `NodeExecutor::execute_with` method (V5.27) gives the
//! SubProcess executor a hook to call back into the registry.

use std::sync::Arc;

use async_trait::async_trait;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;
use serde_json::json;

use crate::dispatch::ExecutorRegistry;
use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::NodeResult;
use crate::traverser::{traverse, TraverseOutcome};

/// `SubProcessError` — local error type for parsing
/// sub-process attrs. Wraps into `FlowError::Action` at the
/// dispatch site.
#[derive(Debug, thiserror::Error)]
pub enum SubProcessError {
    #[error("sub process missing required field: {field}")]
    MissingField { field: String },
    #[error("sub process resolver not configured")]
    NoResolver,
    #[error("sub process {field} is empty")]
    EmptyField { field: String },
}

pub struct SubProcessExecutor;

#[async_trait]
impl NodeExecutor for SubProcessExecutor {
    async fn execute(
        &self,
        _node: &FlowNode,
        _ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        // Without the registry we can't resolve a sub-flow. The
        // dispatch site should call `execute_with` (which has
        // the registry), but a default-constructed registry
        // (no resolver wired) routes here and we surface a
        // clear error.
        Err(FlowError::Unsupported(
            "SubProcessExecutor.execute called without registry — \
             dispatch must use execute_with"
                .to_string(),
        ))
    }

    async fn execute_with(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
        reg: &ExecutorRegistry,
    ) -> Result<NodeResult, FlowError> {
        let NodeKind::SubProcess { attrs } = &node.kind else {
            return Err(FlowError::Unsupported(
                "SubProcessExecutor on non-SubProcess".to_string(),
            ));
        };
        let called_element = attrs
            .ruleforge("calledElement")
            .ok_or_else(|| {
                FlowError::Action(
                    SubProcessError::MissingField {
                        field: "calledElement".to_string(),
                    }
                    .to_string(),
                )
            })?
            .to_string();
        if called_element.trim().is_empty() {
            return Err(FlowError::Action(
                SubProcessError::EmptyField {
                    field: "calledElement".to_string(),
                }
                .to_string(),
            ));
        }
        let resolver = reg.flow_resolver.as_ref().ok_or_else(|| {
            FlowError::Action(SubProcessError::NoResolver.to_string())
        })?;
        let sub_def = resolver.resolve(&called_element).await?;

        // Spawn a sub-context. For V5.27 we copy the parent's
        // vars (so the sub-flow sees the inputs) and clear the
        // awaiting field/value (the sub-flow has its own).
        let sub_run_id = format!("{}-sub-{}", ctx.flow_run_id, called_element);
        let mut sub_ctx = FlowContext::new(&sub_run_id);
        sub_ctx.vars = ctx.vars.clone();
        sub_ctx.current_awaiting_field = None;
        sub_ctx.current_awaiting_value = None;
        // The parent flow_run_id is the one we want to keep using
        // on resume (the suspend blob is stored under the parent
        // id, not the sub id).
        sub_ctx.flow_run_id = ctx.flow_run_id.clone();

        // V5.28 P3 — clear `def` so the recursive
        // `traverse()` re-auto-wires to `sub_def`. The
        // parent's `def` (which may be set) would
        // otherwise leak into the sub-flow's parallel
        // gateway lookup (the gateway resolves outgoing
        // edges against `reg.def.edges` — using the
        // parent's `def.edges` here would surface
        // `EdgeNotFound` for sub-flow edges that don't
        // exist in the parent).
        let mut reg_for_sub = reg.clone();
        reg_for_sub.def = None;
        let outcome = traverse(sub_def, sub_ctx, Arc::new(reg_for_sub));

        // V5.28 P3 — parse `ruleforge:outputMapping` to filter
        // which sub-flow vars get copied back to the parent.
        // V5.27 unconditionally copied every var — silent bug:
        // - sub-flow's loop counters / temp accumulators
        //   leaked into parent
        // - parent's pre-existing vars could be silently
        //   overwritten by sub-flow writes
        //
        // V5.28 P3 contract:
        // - `outputMapping` not set  → V5.27 behavior (copy all)
        // - `outputMapping=""`        → copy NOTHING (explicit
        //                                opt-out)
        // - `outputMapping="a,b,c"`   → copy ONLY a, b, c
        let output_mapping: Option<Vec<String>> = attrs
            .ruleforge("outputMapping")
            .map(|raw| raw.split(',').map(|s| s.trim().to_string()).collect());

        match outcome {
            TraverseOutcome::Completed(t) => {
                // Copy back vars per the outputMapping filter.
                // We can't move out of `&Traverser<Completed>`,
                // so iterate by reference.
                match output_mapping {
                    None => {
                        // V5.27 behavior — copy all sub-flow
                        // vars back to parent.
                        for (k, v) in t.ctx().vars.as_object() {
                            ctx.vars.assign(k.clone(), v.clone());
                        }
                    }
                    Some(allowed) => {
                        // Filter: only copy vars in the
                        // allowed list. Empty allowed list
                        // = copy nothing (explicit opt-out).
                        for k in &allowed {
                            if let Some(v) = t.ctx().vars.get(k) {
                                ctx.vars.assign(k.clone(), v.clone());
                            }
                        }
                    }
                }
                Ok(NodeResult::Continue)
            }
            TraverseOutcome::Suspended(t, info) => {
                // The sub-flow suspended. We pass the suspend
                // info up to the parent caller. The
                // SuspendInfo's `wait_ref` already encodes
                // what the sub-flow was waiting for (e.g.
                // `userTask:approve`); the parent
                // handler stores the suspend under the
                // parent's `flow_run_id`, and on resume
                // the sub-context is rehydrated from the
                // in-flight store.
                let sub_ctx = t.ctx();
                ctx.current_awaiting_field = sub_ctx.current_awaiting_field.clone();
                ctx.current_awaiting_value = sub_ctx.current_awaiting_value.clone();
                let mut payload = info.payload.clone();
                // Annotate the payload so observability can
                // see the sub-flow id and called element.
                if let Some(obj) = payload.as_object_mut() {
                    obj.insert(
                        "sub_called_element".to_string(),
                        json!(called_element),
                    );
                    obj.insert(
                        "sub_flow_run_id".to_string(),
                        json!(sub_ctx.flow_run_id),
                    );
                }
                Ok(NodeResult::Suspend(crate::node_result::SuspendInfo {
                    wait_type: info.wait_type,
                    wait_ref: info.wait_ref,
                    next_retry_at: info.next_retry_at,
                    payload,
                }))
            }
            TraverseOutcome::Failed(_, err) => Err(err),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use rf_ir::attrs::Attrs;

    fn attrs_with(pairs: &[(&str, &str)]) -> Attrs {
        let mut a = Attrs::new();
        for (k, v) in pairs {
            a.0.insert(format!("ruleforge:{k}"), v.to_string());
        }
        a
    }

    #[test]
    fn empty_called_element_errors() {
        // We can't easily construct a NodeKind::SubProcess in a
        // unit test, so we just sanity-check the error types.
        let e = SubProcessError::EmptyField {
            field: "calledElement".to_string(),
        };
        assert!(e.to_string().contains("calledElement"));
    }

    #[test]
    fn attrs_with_helper_stores_ruleforge_keys() {
        let a = attrs_with(&[("calledElement", "child_flow")]);
        assert_eq!(a.ruleforge("calledElement"), Some("child_flow"));
    }
}
