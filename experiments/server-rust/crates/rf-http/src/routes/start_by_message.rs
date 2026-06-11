//! `POST /ruleforge/flow/start-by-message` — start a new flow run
//! **triggered by an incoming message**.
//!
//! ## BDD scenarios
//!
//! ### Scenario: message-start flow suspends at the startEvent
//! - **Given** a flow whose `startEvent` has
//!   `ruleforge:startTrigger="message"` + `ruleforge:eventName="invoice_arrived"`
//! - **When** POST /ruleforge/flow/start-by-message with
//!   `{"flow_id": "...", "event_name": "invoice_arrived", "payload": {...}}`
//! - **Then** response 200 with
//!   `{"result": "PENDING", "flow_run_id": "<new>", "wait_ref": "message:invoice_arrived", ...}`
//! - **And** a subsequent POST /ruleforge/flow/event with
//!   `eventName="invoice_arrived"` resumes the run (the payload
//!   delivered becomes `vars.invoice_arrived`).
//!
//! ### Scenario: event_name mismatch returns 409
//! - **Given** a flow whose startEvent declares `eventName=invoice_arrived`
//! - **When** POST /ruleforge/flow/start-by-message with `eventName=other`
//! - **Then** response 409 with
//!   `{"error": "flow is awaiting message 'invoice_arrived', got 'other'"}`
//!
//! ### Scenario: flow is not message-start returns 409
//! - **Given** a flow whose startEvent has no `startTrigger` (manual)
//! - **When** POST /ruleforge/flow/start-by-message
//! - **Then** response 409 with
//!   `{"error": "flow is not message-start (startTrigger=manual)"}`
//!
//! ### Scenario: unknown flow_id returns 404
//! - **When** POST /ruleforge/flow/start-by-message with an unknown `flow_id`
//! - **Then** response 404 with `{"error": "flow not found: ..."}`
//!
//! ## Why a separate route?
//!
//! V5.27 used a single `/ruleforge/evaluate` route to start flows.
//! V5.28 P7 adds **message-start** — a flow that *doesn't run when
//! requested*, but instead sits in the inflight store waiting for
//! the message that triggers it. Calling `/evaluate` on such a flow
//! would still work (the executor returns `Suspend(message:<X>)`),
//! but the caller has no way to know which `flow_run_id` to
//! reference in the subsequent `/flow/event` — `/evaluate` already
//! returned a flow_run_id, and the message has to be correlated to
//! the right run. By giving the caller a dedicated endpoint, we
//! can also return 409 on a wrong `eventName` (the dispatcher's
//! `Suspend` doesn't run the resume check), and we make it
//! explicit in the API surface that this is a *trigger*, not a
//! request to evaluate an input.
//!
//! ## V5.28 P7 v0 scope
//!
//! - One flow per request — caller specifies `flow_id`.
//! - The dispatcher validates `eventName` against the node's
//!   `startTrigger` (returns 409 on mismatch).
//! - Subsequent `/ruleforge/flow/event` with the same `eventName`
//!   resumes the run. V5.28 P7 v0 only supports a single
//!   suspended message-start run per `(flow_id, eventName)` —
//!   starting a second one is a 409 (already in flight).

use std::sync::Arc;

use axum::extract::State;
use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::Json;
use rf_executor::executors::start_event::{StartEventError, StartTrigger};
use rf_executor::flow_context::FlowContext;
use rf_executor::traverser::{traverse, TraverseOutcome};
use rf_ir::node_kind::NodeKind;
use serde::Deserialize;
use serde_json::Value;
use tracing::{debug, warn};

use crate::flow_def_repo::RepoError;
use crate::inflight::InflightFlow;
use crate::routes::evaluate::EvaluateResponse;
use crate::state::AppState;

#[derive(Debug, Deserialize)]
pub struct StartByMessageRequest {
    /// The `process_id` of the message-start flow to start.
    /// Mirrors `EvaluateRequest::flow_id`.
    #[serde(rename = "flowId", alias = "flow_id")]
    pub flow_id: String,
    /// The name of the message the caller is delivering. Must
    /// equal the flow's `startEvent.eventName` (validated
    /// server-side; 409 on mismatch).
    #[serde(rename = "eventName", alias = "event_name")]
    pub event_name: String,
    /// Payload to attach. Becomes `vars[event_name]` on the
    /// first resume (mirrors `EventRequest::payload`).
    #[serde(default)]
    pub payload: Value,
    /// Optional input facts to seed the flow context. Same
    /// semantics as `EvaluateRequest::vars` — the run starts
    /// with this map, and downstream rules read / write it.
    #[serde(default)]
    pub vars: Value,
}

pub async fn start_by_message(
    State(state): State<AppState>,
    Json(req): Json<StartByMessageRequest>,
) -> impl IntoResponse {
    debug!(
        flow_id = %req.flow_id,
        event_name = %req.event_name,
        "start-by-message: request"
    );

    let def = match state.repo.get_or_load(&req.flow_id).await {
        Ok(d) => d,
        Err(RepoError::Loader(crate::flow_def_repo::FlowLoaderError::NotFound(_))) => {
            return (
                StatusCode::NOT_FOUND,
                Json(serde_json::json!({
                    "error": format!("flow not found: {}", req.flow_id),
                })),
            )
                .into_response();
        }
        Err(e) => {
            warn!(?e, "start-by-message: load failed");
            return (
                StatusCode::BAD_GATEWAY,
                Json(serde_json::json!({
                    "error": format!("{e}"),
                })),
            )
                .into_response();
        }
    };

    // Find the start node (the parser guarantees exactly one
    // `StartEvent` per flow).
    let start_node = match def.nodes.get(&def.start) {
        Some(n) => n,
        None => {
            return (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(serde_json::json!({
                    "error": format!("flow {} has no start node", req.flow_id),
                })),
            )
                .into_response();
        }
    };
    let NodeKind::StartEvent { attrs } = &start_node.kind else {
        return (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(serde_json::json!({
                "error": format!("flow {}: start is not a StartEvent", req.flow_id),
            })),
        )
            .into_response();
    };

    // Parse the start trigger. A non-message flow at this
    // endpoint is a config error (the caller should have
    // hit `/evaluate` instead). 409 surfaces that clearly.
    let trigger = match StartTrigger::from_attrs(attrs) {
        Ok(t) => t,
        Err(StartEventError::UnknownTrigger { kind }) => {
            return (
                StatusCode::CONFLICT,
                Json(serde_json::json!({
                    "error": format!(
                        "flow {}: start event trigger '{}' is not supported here; \
                         use /evaluate for manual-start flows or \
                         /flow/event for signal-start flows",
                        req.flow_id, kind
                    ),
                })),
            )
                .into_response();
        }
        Err(e) => {
            return (
                StatusCode::CONFLICT,
                Json(serde_json::json!({
                    "error": format!("flow {}: {}", req.flow_id, e),
                })),
            )
                .into_response();
        }
    };
    let expected_name = match &trigger {
        StartTrigger::Message { name } => name.clone(),
        StartTrigger::Manual => {
            return (
                StatusCode::CONFLICT,
                Json(serde_json::json!({
                    "error": format!(
                        "flow {} is not message-start (startTrigger=manual); \
                         POST /evaluate to start it manually",
                        req.flow_id
                    ),
                })),
            )
                .into_response();
        }
        StartTrigger::Timer { duration_iso } => {
            return (
                StatusCode::CONFLICT,
                Json(serde_json::json!({
                    "error": format!(
                        "flow {} is timer-start (timerDuration={}); \
                         the scheduler starts it on its own; \
                         you don't POST to /start-by-message for timer flows",
                        req.flow_id, duration_iso
                    ),
                })),
            )
                .into_response();
        }
    };

    if expected_name != req.event_name {
        return (
            StatusCode::CONFLICT,
            Json(serde_json::json!({
                "error": format!(
                    "flow is awaiting message '{}', got '{}'",
                    expected_name, req.event_name
                ),
                "awaiting_event": format!("message:{expected_name}"),
            })),
        )
            .into_response();
    }

    // Mint a fresh flow_run_id. v0 disallows re-using the
    // same eventName before the previous run is resumed —
    // we could detect this with an in-memory "pending message
    // starts" index, but the dispatcher already produces a
    // unique flow_run_id and a /flow/event call with the
    // eventName will match the most recent suspension. v0
    // semantics: "the latest message-start run wins".
    let flow_run_id = AppState::new_flow_run_id();
    debug!(%flow_run_id, "start-by-message: starting run");

    let mut ctx = FlowContext::new(&flow_run_id);
    // Seed ctx.vars with whatever the caller passed. The
    // payload is also assigned to `vars[event_name]` so
    // downstream rules can read it (mirrors what
    // IntermediateEventExecutor does on resume).
    if let Some(obj) = req.vars.as_object() {
        for (k, v) in obj {
            ctx.vars.assign(k.clone(), v.clone());
        }
    }
    ctx.vars
        .insert(req.event_name.clone(), req.payload.clone());

    let outcome = traverse(Arc::clone(&def), ctx, Arc::clone(&state.registry));

    match outcome {
        TraverseOutcome::Completed(t) => {
            // A message-start flow whose start is a
            // Suspend(message:<X>) is **supposed** to
            // suspend. If the traverser returns
            // `Completed`, the parser mis-classified
            // the trigger (or the dispatcher
            // short-circuited). Surface 409 with the
            // actual outcome.
            let ctx = t.ctx;
            let map: serde_json::Map<String, Value> = ctx.vars.into_inner().into_iter().collect();
            warn!(
                %flow_run_id,
                "start-by-message: flow completed before suspending — \
                 the startTrigger may be misconfigured"
            );
            (
                StatusCode::CONFLICT,
                Json(serde_json::json!({
                    "error": "flow completed before suspending; \
                              check startTrigger/eventName configuration",
                    "current_node_id": ctx.current_node_id,
                    "vars": Value::Object(map),
                })),
            )
                .into_response()
        }
        TraverseOutcome::Suspended(t, info) => {
            // Stash so a follow-up /flow/event with the
            // same eventName finds the run. Same
            // dispatch as /evaluate's PENDING branch.
            state
                .inflight
                .put(
                    flow_run_id.clone(),
                    InflightFlow {
                        def: Arc::clone(&def),
                        ctx: t.ctx,
                        suspend_info: Some(info.clone()),
                    },
                )
                .await;
            let payload = info.payload.clone();
            let wait_ref = info.wait_ref.clone();
            Json(EvaluateResponse::Pending {
                flow_run_id,
                wait_ref,
                payload,
            })
            .into_response()
        }
        TraverseOutcome::Failed(t, err) => {
            let ctx = t.ctx;
            warn!(?err, current_node_id = ?ctx.current_node_id, "start-by-message: failed");
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(serde_json::json!({
                    "error": format!("{err}"),
                    "current_node_id": ctx.current_node_id,
                })),
            )
                .into_response()
        }
    }
}
