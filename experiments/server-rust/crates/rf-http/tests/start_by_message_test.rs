//! BDD integration tests for `POST /ruleforge/flow/start-by-message`.
//!
//! V5.28 P7 — message-start flow trigger. Mirrors the BDD
//! scenarios in `routes/start_by_message.rs`'s docstring.
//!
//! ## Scenarios
//!
//! ### Scenario: message-start flow suspends at the startEvent
//! - **Given** a flow with
//!   `startEvent(startTrigger=message, eventName=invoice_arrived)`
//!   followed by a `scriptTask` (so the flow has somewhere to go
//!   after the message arrives)
//! - **When** POST /ruleforge/flow/start-by-message with
//!   `{"flow_id": "...", "event_name": "invoice_arrived", "payload": {...}}`
//! - **Then** response 200 with
//!   `{"result": "PENDING", "flow_run_id": "<new>", "wait_ref": "message:invoice_arrived", ...}`
//! - **And** the inflight store has the run registered.
//!
//! ### Scenario: event_name mismatch returns 409
//! - **Given** a flow declaring `eventName=invoice_arrived`
//! - **When** POST with `eventName=other`
//! - **Then** 409 + error mentions both names
//!
//! ### Scenario: flow is not message-start returns 409
//! - **Given** a flow with `startEvent(startTrigger=manual)` (default)
//! - **When** POST /flow/start-by-message
//! - **Then** 409 + error says "use /evaluate for manual-start flows"
//!
//! ### Scenario: timer-start flow returns 409
//! - **Given** a flow with `startEvent(startTrigger=timer, timerDuration=PT1H)`
//! - **When** POST /flow/start-by-message
//! - **Then** 409 + error says "scheduler starts it on its own"
//!
//! ### Scenario: missing eventName attr returns 409
//! - **Given** a flow with `startEvent(startTrigger=message)` but
//!   **no** `eventName` (the parser/executor catches this)
//! - **When** POST /flow/start-by-message
//! - **Then** 409 + error mentions the missing field
//!
//! ### Scenario: unknown flow_id returns 404
//!
//! ### Scenario: full message-start → /flow/event resume loop
//! - **Given** the message-start flow from the first scenario
//! - **When** POST /flow/start-by-message, then POST /flow/event with
//!   the same `flow_run_id` + `eventName` + payload
//! - **Then** the second call returns COMPLETED with the payload
//!   visible at `vars.invoice_arrived`

use std::sync::Arc;

use axum::body::{to_bytes, Body};
use axum::http::{Request, StatusCode};
use axum::routing::post;
use axum::Router;
use rf_executor::dispatch::ExecutorRegistry;
use rf_http::flow_def_repo::{FlowDefinitionRepo, StubFlowLoader};
use rf_http::routes::event::deliver;
use rf_http::routes::start_by_message::start_by_message;
use rf_http::state::AppState;
use serde_json::{json, Value};
use tower::ServiceExt;

const MESSAGE_START_BPMN: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:ruleforge="http://ruleforge.com/schema"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="invoice_flow">
    <bpmn:startEvent id="s"
                     ruleforge:startTrigger="message"
                     ruleforge:eventName="invoice_arrived">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:endEvent id="end"/>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="end"/>
  </bpmn:process>
</bpmn:definitions>"#;

const MANUAL_START_BPMN: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:ruleforge="http://ruleforge.com/schema"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="manual_flow">
    <bpmn:startEvent id="s"><bpmn:outgoing>e1</bpmn:outgoing></bpmn:startEvent>
    <bpmn:endEvent id="end"/>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="end"/>
  </bpmn:process>
</bpmn:definitions>"#;

const TIMER_START_BPMN: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:ruleforge="http://ruleforge.com/schema"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="timer_start_flow">
    <bpmn:startEvent id="s"
                     ruleforge:startTrigger="timer"
                     ruleforge:timerDuration="PT1H">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:endEvent id="end"/>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="end"/>
  </bpmn:process>
</bpmn:definitions>"#;

const MESSAGE_START_NO_NAME_BPMN: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:ruleforge="http://ruleforge.com/schema"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="missing_name_flow">
    <bpmn:startEvent id="s" ruleforge:startTrigger="message">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:endEvent id="end"/>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="end"/>
  </bpmn:process>
</bpmn:definitions>"#;

fn build_state_with(loader: StubFlowLoader) -> AppState {
    let repo = Arc::new(FlowDefinitionRepo::new(Arc::new(loader)));
    let registry = Arc::new(ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    )));
    AppState::new(repo, registry, "test-worker", "http://localhost:8180", "")
}

fn build_router(state: AppState) -> Router {
    Router::new()
        .route("/ruleforge/flow/start-by-message", post(start_by_message))
        .route("/ruleforge/flow/event", post(deliver))
        .with_state(state)
}

async fn body_json(resp: axum::response::Response) -> Value {
    let body = to_bytes(resp.into_body(), 1024 * 1024).await.unwrap();
    serde_json::from_slice(&body).unwrap()
}

// ----- happy path -----

#[tokio::test]
async fn given_message_start_flow_when_start_by_message_then_pending() {
    let loader = StubFlowLoader::with_flow("invoice_flow", MESSAGE_START_BPMN);
    let state = build_state_with(loader);
    let app = build_router(state.clone());

    let resp = app
        .clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/start-by-message")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flow_id": "invoice_flow",
                        "event_name": "invoice_arrived",
                        "payload": { "amount": 1234, "vendor": "acme" }
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_json(resp).await;
    assert_eq!(body["result"], "PENDING");
    // V5.28 P2 namespace — the wait_ref carries the message: prefix.
    assert_eq!(body["wait_ref"], "message:invoice_arrived");
    // payload echoed back so the caller can see the trigger
    // details (event_type=message, event_name=...).
    assert_eq!(body["payload"]["event_name"], "invoice_arrived");
    assert_eq!(body["payload"]["trigger"], "start_message");
    // inflight has the new run
    assert_eq!(state.inflight.len().await, 1);
    let flow_run_id = body["flow_run_id"].as_str().unwrap().to_string();

    // Resume via /flow/event with the same eventName —
    // closes the V5.28 P7 suspend→resume loop.
    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/event")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flowRunId": flow_run_id,
                        "eventName": "invoice_arrived",
                        "payload": { "amount": 1234, "vendor": "acme" }
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    let status = resp.status();
    let body = body_json(resp).await;
    if status != StatusCode::OK {
        panic!("unexpected status {status}, body: {body}");
    }
    assert_eq!(status, StatusCode::OK);
    if body["result"] != "COMPLETED" {
        panic!("unexpected body: {body}");
    }
    assert_eq!(body["result"], "COMPLETED");
    // The original event payload is visible as vars.invoice_arrived.
    assert_eq!(body["vars"]["invoice_arrived"]["amount"], json!(1234));
    assert_eq!(body["vars"]["invoice_arrived"]["vendor"], json!("acme"));
    // inflight cleared after completion
    assert_eq!(state.inflight.len().await, 0);
}

// ----- error paths -----

#[tokio::test]
async fn given_event_name_mismatch_when_start_by_message_then_409() {
    let loader = StubFlowLoader::with_flow("invoice_flow", MESSAGE_START_BPMN);
    let state = build_state_with(loader);
    let app = build_router(state.clone());

    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/start-by-message")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flow_id": "invoice_flow",
                        "event_name": "wrong_event"
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::CONFLICT);
    let body = body_json(resp).await;
    let err = body["error"].as_str().unwrap();
    assert!(
        err.contains("invoice_arrived") && err.contains("wrong_event"),
        "expected both names in error, got: {err}"
    );
    assert_eq!(
        body["awaiting_event"], "message:invoice_arrived",
        "awaiting_event should be the namespaced wait_ref"
    );
    assert_eq!(state.inflight.len().await, 0);
}

#[tokio::test]
async fn given_manual_start_flow_when_start_by_message_then_409() {
    let loader = StubFlowLoader::with_flow("manual_flow", MANUAL_START_BPMN);
    let state = build_state_with(loader);
    let app = build_router(state.clone());

    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/start-by-message")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flow_id": "manual_flow",
                        "event_name": "anything"
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::CONFLICT);
    let body = body_json(resp).await;
    let err = body["error"].as_str().unwrap();
    assert!(
        err.contains("not message-start") && err.contains("/evaluate"),
        "expected error to redirect to /evaluate, got: {err}"
    );
    assert_eq!(state.inflight.len().await, 0);
}

#[tokio::test]
async fn given_timer_start_flow_when_start_by_message_then_409() {
    let loader = StubFlowLoader::with_flow("timer_start_flow", TIMER_START_BPMN);
    let state = build_state_with(loader);
    let app = build_router(state.clone());

    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/start-by-message")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flow_id": "timer_start_flow",
                        "event_name": "x"
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::CONFLICT);
    let body = body_json(resp).await;
    let err = body["error"].as_str().unwrap();
    assert!(
        err.contains("timer-start") || err.contains("timerDuration"),
        "expected error to mention timer, got: {err}"
    );
    assert_eq!(state.inflight.len().await, 0);
}

#[tokio::test]
async fn given_message_start_without_eventName_when_start_by_message_then_409() {
    let loader = StubFlowLoader::with_flow("missing_name_flow", MESSAGE_START_NO_NAME_BPMN);
    let state = build_state_with(loader);
    let app = build_router(state.clone());

    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/start-by-message")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flow_id": "missing_name_flow",
                        "event_name": "anything"
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::CONFLICT);
    let body = body_json(resp).await;
    let err = body["error"].as_str().unwrap();
    assert!(
        err.contains("eventName") || err.contains("missing"),
        "expected error to mention eventName, got: {err}"
    );
    assert_eq!(state.inflight.len().await, 0);
}

#[tokio::test]
async fn given_unknown_flow_id_when_start_by_message_then_404() {
    let loader = StubFlowLoader::with_flow("invoice_flow", MESSAGE_START_BPMN);
    let state = build_state_with(loader);
    let app = build_router(state.clone());

    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/start-by-message")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flow_id": "non_existent_flow",
                        "event_name": "anything"
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::NOT_FOUND);
    let body = body_json(resp).await;
    let err = body["error"].as_str().unwrap();
    assert!(
        err.contains("non_existent_flow"),
        "expected error to mention flow id, got: {err}"
    );
    assert_eq!(state.inflight.len().await, 0);
}
