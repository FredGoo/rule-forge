//! Gateway executors — `ExclusiveGateway` + `ParallelGateway`.
//!
//! Gateway *routing* (which outgoing edge to follow) for
//! `ExclusiveGateway` lives in [`crate::next_node`], not in
//! the executor. The executor itself just signals
//! "continue" so the traverser can call `next_node()` and
//! pick one edge. This matches Java's
//! `GatewayNodeExecutor.execute()` which also doesn't pick
//! the next edge — `FlowNodeRunner.nextNode` does.
//!
//! ## `ParallelGateway` (V5.28)
//!
//! Parallel gateway is different. Its semantics are
//! "fork N branches", which means the executor itself
//! produces the routing decision (an N-way split, not a
//! 1-way choice). We can't model that with `next_node`'s
//! `Option<String>` return — it returns a single target.
//!
//! The `GatewayExecutor::execute_with` for a
//! `ParallelGateway` therefore:
//!
//! 1. Reads the registry's `def` (auto-wired by
//!    `traverse()`).
//! 2. For each outgoing edge, builds a [`ForkBranch`]
//!    with a cloned `FlowContext` and a per-branch
//!    `visited` set (so each branch's loop detection is
//!    independent).
//! 3. Returns [`NodeResult::Fork`] so the traverser
//!    driver runs the branches.
//!
//! ### V5.28 v0 join semantics
//!
//! We **do not** model join synchronization. Each branch
//! runs to its own end event; the parent's "post-fork"
//! continuation is the end of flow (the traverser sets
//! `next = None` after all branches complete). The
//! diamond pattern (split + join) is supported
//! "naively" — each branch runs to its own end, the join
//! gateway on each branch is a no-op `Continue`.
//! Future versions can add a per-gateway visit-count
//! tracker + `outputMapping` to merge branches back at a
//! true join.
//!
//! [`ForkBranch`]: crate::node_result::ForkBranch

use std::collections::HashSet;

use async_trait::async_trait;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;

use crate::dispatch::ExecutorRegistry;
use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::{ForkBranch, NodeResult};

pub struct GatewayExecutor;

#[async_trait]
impl NodeExecutor for GatewayExecutor {
    /// Exclusive gateway: pass through. The 4-segment routing
    /// in [`crate::next_node`] picks one outgoing edge
    /// (UEL condition / weighted random / default).
    /// The "no outgoing edges" case is also handled by
    /// `next_node` (it returns `None` → `Done`).
    async fn execute(
        &self,
        _node: &FlowNode,
        _ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        Ok(NodeResult::Continue)
    }

    /// Parallel gateway: fork N branches. Override of
    /// `execute_with` because we need the registry's
    /// `def` to resolve outgoing edge targets (the
    /// `FlowNode` only carries edge `id`s, not target
    /// `id`s). If the registry has no `def` wired, we
    /// surface a clear error rather than silently
    /// mis-executing.
    async fn execute_with(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
        reg: &ExecutorRegistry,
    ) -> Result<NodeResult, FlowError> {
        // We accept both ParallelGateway and ExclusiveGateway
        // here defensively — the dispatcher routes
        // ExclusiveGateway to `execute` and ParallelGateway
        // to `execute_with`, but if a caller invokes
        // `execute_with` directly for an ExclusiveGateway
        // (e.g. from a test), we still want the right
        // behavior.
        let NodeKind::ParallelGateway { .. } = &node.kind else {
            return Ok(NodeResult::Continue);
        };

        let def = reg.def.as_ref().ok_or_else(|| {
            FlowError::Action(format!(
                "ParallelGateway {}: registry has no def wired (callers must \
                 invoke via traverse() or set reg.def explicitly)",
                node.node_id
            ))
        })?;

        if node.outgoing_ids.is_empty() {
            return Err(FlowError::Action(format!(
                "ParallelGateway {}: has no outgoing edges (a parallel gateway \
                 must have at least one outgoing edge to be meaningful)",
                node.node_id
            )));
        }

        // 1 outgoing edge → "join only" / pass-through
        // (no fan-out). Return Continue so the traverser
        // routes via `next_node` like an exclusive
        // gateway. This is the case where a parallel
        // gateway is used purely as a sync point — the
        // single branch that entered continues.
        if node.outgoing_ids.len() == 1 {
            return Ok(NodeResult::Continue);
        }

        // 2+ outgoing edges → fork. Build one
        // `ForkBranch` per edge. Each branch's `ctx` is
        // a clone of the parent's `ctx` BEFORE the
        // gateway executes (so each branch's writes are
        // isolated until the post-fork merge). The
        // visited set is shared so that nodes the
        // parent has already visited (i.e. before
        // reaching the parallel gateway) are not
        // re-visited by any branch — branches inherit
        // the parent's loop-detection state.
        //
        // We don't track which nodes the parent has
        // visited here directly; the traverser hands
        // each branch a `visited` snapshot via
        // `ForkBranch.visited` (see
        // `Traverser::step`'s `NodeResult::Fork` arm and
        // the `traverse()` driver's branch loop).
        let mut branches = Vec::with_capacity(node.outgoing_ids.len());
        for edge_id in &node.outgoing_ids {
            let edge = def
                .edges
                .iter()
                .find(|e| &e.id == edge_id)
                .ok_or_else(|| {
                    FlowError::EdgeNotFound(format!(
                        "{} (referenced by parallel gateway {})",
                        edge_id, node.node_id
                    ))
                })?;
            let branch_ctx = ctx.clone();
            // The branch's visited set starts EMPTY —
            // each branch's loop detection is local to
            // the branch. The traverser, when it runs
            // the branch, will not pass the parent's
            // visited into it; instead, it creates a
            // fresh `Traverser<Running>` for the branch
            // (see `Traverser::begin_at`). The parallel
            // gateway's own id is already in the
            // parent's visited (it was just stepped on).
            let branch_visited: HashSet<String> = HashSet::new();
            branches.push(ForkBranch {
                start: edge.target.clone(),
                ctx: branch_ctx,
                visited: branch_visited,
            });
        }

        tracing::debug!(
            node_id = %node.node_id,
            branch_count = branches.len(),
            "parallel gateway fork"
        );
        Ok(NodeResult::Fork(branches))
    }
}
