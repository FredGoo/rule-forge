//! `StartEventNodeExecutor` — V5.28 P7.
//!
//! BPMN 2.0 `<bpmn:startEvent>` is the entry point of every process.
//! V5.27 treated it as a parameterless no-op (the dispatcher
//! returned `NodeResult::Continue`). V5.28 P7 adds a
//! **start-trigger discriminator** so the same node can model:
//!
//! - **Manual start** (the default) — the flow starts when a
//!   caller POSTs `/ruleforge/evaluate`. The executor is a
//!   no-op `Continue`.
//! - **Message start** — the flow sits in the inflight store
//!   waiting for a message of the configured name. A caller
//!   hits `POST /flow/start-by-message` with the same event
//!   name to start a new run. The executor returns
//!   `NodeResult::Suspend` with `AsyncData` + wait_ref
//!   `"message:<eventName>"` (mirrors the
//!   `IntermediateEventExecutor`'s message-catch shape; the
//!   existing `/flow/event` handler can resume it).
//! - **Timer start** — the executor returns an `Unsupported`
//!   error in v0. The timer scheduler in `rf-http`/`main.rs`
//!   is responsible for starting timer-triggered flows
//!   directly (it does NOT go through the dispatcher — the
//!   scheduler calls `traverse()` itself with a fresh
//!   `flow_run_id`). Recognising the trigger on the parser
//!   side is enough for v0.
//!
//! ## Why a new executor?
//!
//! The StartEvent is the **first node the traverser steps on**
//! — there is no upstream context (no "previous node") to set
//! the trigger. The dispatcher can't infer the trigger from
//! `ctx`; it has to read the node's `attrs.startTrigger`.
//! This is structurally identical to the
//! `IntermediateEventExecutor` (which reads `eventType` from
//! attrs) — same shape, same suspend contract.

use async_trait::async_trait;
use rf_ir::attrs::Attrs;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;
use serde_json::json;

use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::{NodeResult, SuspendInfo, WaitType};

/// `StartEventError` — local error type for parsing
/// start-event attrs. Wraps into `FlowError::Action` at the
/// dispatch site.
#[derive(Debug, thiserror::Error)]
pub enum StartEventError {
    #[error("start event missing required field: {field}")]
    MissingField { field: String },
    #[error("start event unknown trigger: {kind}")]
    UnknownTrigger { kind: String },
}

/// `StartTrigger` — discriminator parsed from
/// `ruleforge:startTrigger` on the node attrs. Mirrors
/// `IntermediateEventKind` / `BoundaryEventKind`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum StartTrigger {
    /// No `startTrigger` (or `startTrigger="manual"`) — the
    /// default. The flow starts on a `/ruleforge/evaluate`
    /// POST. Executor returns `Continue`.
    Manual,
    /// `startTrigger="message"` + `eventName`. The flow
    /// starts when a `/flow/start-by-message` POST
    /// delivers the named event. Executor returns
    /// `Suspend(AsyncData, "message:<eventName>")`.
    Message { name: String },
    /// `startTrigger="timer"` + `timerDuration` — v0
    /// surfaces an `Unsupported` error from the executor
    /// (the scheduler in `main.rs` runs timer flows
    /// directly; the dispatcher isn't called for them).
    /// Parsing the trigger is still useful so a future
    /// scheduler can register timer-start flows.
    Timer { duration_iso: String },
}

impl StartTrigger {
    pub fn from_attrs(attrs: &Attrs) -> Result<Self, StartEventError> {
        let trigger = attrs.ruleforge("startTrigger");
        match trigger {
            None => Ok(StartTrigger::Manual),
            Some("manual") | Some("") => Ok(StartTrigger::Manual),
            Some("message") => {
                let name = attrs
                    .ruleforge("eventName")
                    .ok_or_else(|| StartEventError::MissingField {
                        field: "eventName".to_string(),
                    })?
                    .to_string();
                Ok(StartTrigger::Message { name })
            }
            Some("timer") => {
                let duration = attrs
                    .ruleforge("timerDuration")
                    .ok_or_else(|| StartEventError::MissingField {
                        field: "timerDuration".to_string(),
                    })?
                    .to_string();
                Ok(StartTrigger::Timer {
                    duration_iso: duration,
                })
            }
            Some(other) => Err(StartEventError::UnknownTrigger {
                kind: other.to_string(),
            }),
        }
    }
}

pub struct StartEventExecutor;

#[async_trait]
impl NodeExecutor for StartEventExecutor {
    async fn execute(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        let NodeKind::StartEvent { attrs } = &node.kind else {
            return Err(FlowError::Unsupported(
                "StartEventExecutor on non-StartEvent".to_string(),
            ));
        };
        let trigger = StartTrigger::from_attrs(attrs)
            .map_err(|e| FlowError::Action(e.to_string()))?;
        match trigger {
            StartTrigger::Manual => {
                // V5.28 P7 observability — log the
                // start so dashboards see the manual
                // trigger fired. (No metrics
                // counter in v0; V5.32 adds
                // Prometheus.)
                tracing::debug!(
                    flow_run_id = %ctx.flow_run_id,
                    node_id = %node.node_id,
                    trigger = "manual",
                    "start event (manual)"
                );
                Ok(NodeResult::Continue)
            }
            StartTrigger::Message { name } => {
                let kind_ref = format!("message:{name}");
                // V5.28 P7 — resume check mirrors
                // `IntermediateEventExecutor`. On
                // resume, the `/flow/event` handler
                // writes `current_awaiting_value` and
                // sets `current_awaiting_field` to
                // the namespaced `message:<name>`. The
                // traverser will dispatch the start
                // event again (it's the first node in
                // the flow); the executor sees
                // `is_resume` true and returns
                // `Continue` so the next step runs.
                if ctx.current_awaiting_field.as_deref() == Some(&kind_ref)
                    && ctx.current_awaiting_value.is_some()
                {
                    return Ok(NodeResult::Continue);
                }
                // V5.28 P2 — set the awaiting field
                // on the ctx so the /flow/event
                // handler's 409 check can match.
                ctx.current_awaiting_field = Some(kind_ref.clone());
                let payload = json!({
                    "node_id": node.node_id,
                    "event_type": "message",
                    "event_name": name,
                    "trigger": "start_message",
                });
                Ok(NodeResult::Suspend(SuspendInfo {
                    wait_type: WaitType::AsyncData,
                    wait_ref: kind_ref,
                    next_retry_at: None,
                    payload,
                }))
            }
            StartTrigger::Timer { duration_iso } => {
                // V5.28 P7 v0 — timer-start flow should
                // not be reached by the dispatcher
                // (the scheduler runs timer flows
                // directly). If the dispatcher DOES
                // see a timer-start node, it's a
                // configuration error (the operator
                // didn't wire the scheduler). Surface
                // a clear `Unsupported` rather than
                // silently running it.
                Err(FlowError::Unsupported(format!(
                    "StartEvent {}: timer-start ({}) is not dispatched — \
                     the scheduler in main.rs runs timer flows directly. \
                     Configure the TimerScheduler to register this flow.",
                    node.node_id, duration_iso
                )))
            }
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
    fn from_attrs_manual_when_no_trigger() {
        let a = Attrs::new();
        assert_eq!(StartTrigger::from_attrs(&a).unwrap(), StartTrigger::Manual);
        let a = attrs_with(&[("startTrigger", "manual")]);
        assert_eq!(StartTrigger::from_attrs(&a).unwrap(), StartTrigger::Manual);
    }

    #[test]
    fn from_attrs_message_requires_name() {
        let a = attrs_with(&[("startTrigger", "message")]);
        assert!(matches!(
            StartTrigger::from_attrs(&a),
            Err(StartEventError::MissingField { .. })
        ));
    }

    #[test]
    fn from_attrs_message_with_name() {
        let a = attrs_with(&[
            ("startTrigger", "message"),
            ("eventName", "external_invoice_arrived"),
        ]);
        assert_eq!(
            StartTrigger::from_attrs(&a).unwrap(),
            StartTrigger::Message {
                name: "external_invoice_arrived".into()
            }
        );
    }

    #[test]
    fn from_attrs_timer_requires_duration() {
        let a = attrs_with(&[("startTrigger", "timer")]);
        assert!(matches!(
            StartTrigger::from_attrs(&a),
            Err(StartEventError::MissingField { .. })
        ));
    }

    #[test]
    fn from_attrs_timer_with_duration() {
        let a = attrs_with(&[("startTrigger", "timer"), ("timerDuration", "PT1H")]);
        assert_eq!(
            StartTrigger::from_attrs(&a).unwrap(),
            StartTrigger::Timer {
                duration_iso: "PT1H".into()
            }
        );
    }

    #[test]
    fn from_attrs_unknown_trigger_rejected() {
        let a = attrs_with(&[("startTrigger", "signal")]);
        assert!(matches!(
            StartTrigger::from_attrs(&a),
            Err(StartEventError::UnknownTrigger { .. })
        ));
    }
}
