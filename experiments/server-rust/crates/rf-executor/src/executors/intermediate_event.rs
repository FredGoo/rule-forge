//! IntermediateEventNodeExecutor — message / signal / timer catch & throw.
//!
//! BPMN 2.0 splits intermediate events into two flavors:
//! - **IntermediateCatchEvent** — pauses the flow until something happens
//!   (a message arrives, a signal is received, a timer fires, a
//!   condition becomes true).
//! - **IntermediateThrowEvent** — emits a message/signal and continues.
//!
//! Java `NodeType.INTERMEDIATE_EVENT` is currently a stub (comment:
//! "暂支持 message/signal 等待" — only message/signal waiting is
//! supported in P0). V5.26 P0 ships the Rust equivalent: message
//! and signal catch suspend the flow with `WaitType::AsyncData`;
//! timer catch suspends with `WaitType::AsyncTask` + a
//! `next_retry_at`; throw events are no-ops (Continue).
//!
//! ## V5.28 P2 — wait_ref namespacing
//!
//! V5.26 P0 used the raw `event_name` as the `wait_ref` and
//! `current_awaiting_field`. That collides if two different
//! kinds share the same name (e.g. a flow has a
//! `message:foo` catch AND a `signal:foo` catch — both
//! would have `wait_ref = "foo"`, breaking the
//! `/flow/event` handler's resume-by-wait_ref lookup and
//! the gateway routing). V5.28 P2 namespaces every
//! wait_ref by kind:
//!
//! - message catch  → `message:<event_name>`
//! - signal catch   → `signal:<event_name>`
//! - timer catch    → `timer:<node_id>`
//!
//! Same shape as `BoundaryEventExecutor`'s
//! `error:<error_ref>` / `boundaryTimer:<node_id>`. The
//! `current_awaiting_field` follows the same namespacing
//! so the resume path (HTTP `/flow/event` handler
//! writing `current_awaiting_field` to the event name) can
//! disambiguate.
//
//! ## Discriminator
//!
//! The event kind is read from the `ruleforge:eventType` attribute
//! on the node, matching the `ruleforge:taskType` /
//! `ruleforge:decisionField` pattern used elsewhere. The
//! `IntermediateEventKind::from_attrs` helper centralizes the
//! parsing so the dispatch site is clean.
//!
//! ## Resume
//!
//! The resume path mirrors `UserTaskNodeExecutor`: if the caller
//! (HTTP `/flow/event` handler — Phase 4+) already wrote
//! `current_awaiting_field` matching the namespaced event ref
//! (`message:<name>` etc.) and a `current_awaiting_value`,
//! this is a continuation — return `Continue` so the next
//! gateway can route on the event payload.

use async_trait::async_trait;
use chrono::{DateTime, Duration, Utc};
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;
use serde_json::json;
use std::str::FromStr;

use crate::dispatch::ExecutorRegistry;
use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::{NodeResult, SuspendInfo, WaitType};

/// `IntermediateEventError` — local error type for parsing
/// intermediate-event attrs. Wraps into `FlowError::Action` so the
/// dispatch site doesn't need a separate variant.
#[derive(Debug, thiserror::Error)]
pub enum IntermediateEventError {
    #[error("intermediate event missing required field: {field}")]
    MissingField { field: String },
    #[error("intermediate event unknown kind: {kind}")]
    UnknownKind { kind: String },
    #[error("intermediate event bad ISO 8601 duration: {raw}")]
    BadDuration { raw: String },
}

/// `IntermediateEventKind` — discriminator parsed from
/// `ruleforge:eventType` on the node attrs. Mirrors the BPMN 2.0
/// intermediate-catch/throw flavors we support in v0.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum IntermediateEventKind {
    /// IntermediateCatchEvent waiting for a named message.
    Message { name: String },
    /// IntermediateCatchEvent waiting for a broadcast signal.
    Signal { name: String },
    /// IntermediateCatchEvent waiting for a timer.
    Timer { duration: Duration },
    /// V5.32 — IntermediateCatchEvent waiting for a
    /// condition. V5.32 v0 stores the condition text only and
    /// suspends with `wait_ref = "conditional:<node_id>"`.
    /// An external agent (or a future polling worker —
    /// V5.32+ scope) writes
    /// `current_awaiting_field = "conditional:<node_id>"` +
    /// a value to resume. The condition text in `payload` is
    /// for observability only; V5.32 v0 does not evaluate it.
    Conditional { condition: String },
    /// V5.32 — IntermediateThrowEvent with
    /// `<linkEventDefinition name="L"/>`. Jumps to the
    /// `LinkCatch` with the matching `link_name` via
    /// `NodeResult::Branch(catch_id)`. The throw's outgoing
    /// edges are bypassed by the traverser's `Branch` arm.
    LinkThrow { link_name: String },
    /// V5.32 — IntermediateCatchEvent with
    /// `<linkEventDefinition name="L"/>`. Pass-through; the
    /// throw already did the routing, so the catch just
    /// returns `Continue`.
    LinkCatch { link_name: String },
    /// IntermediateThrowEvent (or no eventType set): the node
    /// passes through without suspending. Throw events don't
    /// need a separate kind because they always Continue.
    None,
}

impl IntermediateEventKind {
    /// Parse from `attrs`. The discriminator is
    /// `ruleforge:eventType`; the event name lives in
    /// `ruleforge:eventName` (message/signal) or
    /// `ruleforge:timerDuration` (timer, ISO 8601 or "PT5S").
    pub fn from_attrs(
        attrs: &rf_ir::attrs::Attrs,
    ) -> Result<Self, IntermediateEventError> {
        let event_type = attrs.ruleforge("eventType");
        let event_name = attrs.ruleforge("eventName");
        match event_type {
            None => Ok(IntermediateEventKind::None),
            Some("message") => {
                let name = event_name
                    .ok_or_else(|| IntermediateEventError::MissingField {
                        field: "eventName".to_string(),
                    })?
                    .to_string();
                Ok(IntermediateEventKind::Message { name })
            }
            Some("signal") => {
                let name = event_name
                    .ok_or_else(|| IntermediateEventError::MissingField {
                        field: "eventName".to_string(),
                    })?
                    .to_string();
                Ok(IntermediateEventKind::Signal { name })
            }
            Some("timer") => {
                let raw = attrs
                    .ruleforge("timerDuration")
                    .ok_or_else(|| IntermediateEventError::MissingField {
                        field: "timerDuration".to_string(),
                    })?;
                let duration = parse_iso_duration(raw)?;
                Ok(IntermediateEventKind::Timer { duration })
            }
            // ── V5.32 ─────────────────────────────────────────
            // Conditional intermediate catch. The condition
            // text is whatever the parser extracted from
            // `<bpmn:condition>...</bpmn:condition>`. An
            // empty string is **not** a parse error here —
            // the executor's runtime check
            // (see `execute`) is the place where
            // "missing condition" fails. This mirrors the
            // message/signal "missing eventName" pattern:
            // `from_attrs` succeeds, `execute` returns
            // `FlowError::Action` for runtime validation
            // failures.
            Some("conditional") => {
                let condition = attrs
                    .ruleforge("condition")
                    .unwrap_or("")
                    .to_string();
                Ok(IntermediateEventKind::Conditional { condition })
            }
            Some("linkThrow") => {
                let link_name = attrs
                    .ruleforge("linkName")
                    .ok_or_else(|| IntermediateEventError::MissingField {
                        field: "linkName".to_string(),
                    })?
                    .to_string();
                Ok(IntermediateEventKind::LinkThrow { link_name })
            }
            Some("linkCatch") => {
                let link_name = attrs
                    .ruleforge("linkName")
                    .ok_or_else(|| IntermediateEventError::MissingField {
                        field: "linkName".to_string(),
                    })?
                    .to_string();
                Ok(IntermediateEventKind::LinkCatch { link_name })
            }
            Some(other) => Err(IntermediateEventError::UnknownKind {
                kind: other.to_string(),
            }),
        }
    }
}

/// `parse_iso_duration` — accepts a small subset of ISO 8601
/// duration syntax used by BPMN: `PT5S` (5 sec), `PT1M` (1 min),
/// `PT2H` (2 hr). V5.26 P0 doesn't need the full `PnYnMnDTnHnMnS`
/// grammar — date-based durations are not used in decision flows.
/// `pub(crate)` so the BoundaryEventExecutor can reuse it.
pub(crate) fn parse_iso_duration(raw: &str) -> Result<Duration, IntermediateEventError> {
    let s = raw.trim();
    if !s.starts_with("PT") {
        return Err(IntermediateEventError::BadDuration {
            raw: raw.to_string(),
        });
    }
    let body = &s[2..];
    if body.is_empty() {
        return Err(IntermediateEventError::BadDuration {
            raw: raw.to_string(),
        });
    }
    // Walk the string; last char is the unit (S, M, H, D, W), the
    // rest is a number.
    let (num_str, unit) = body.split_at(body.len() - 1);
    let n: i64 = i64::from_str(num_str).map_err(|_| {
        IntermediateEventError::BadDuration {
            raw: raw.to_string(),
        }
    })?;
    if n < 0 {
        return Err(IntermediateEventError::BadDuration {
            raw: raw.to_string(),
        });
    }
    let secs = match unit {
        "S" => n,
        "M" => n * 60,
        "H" => n * 3600,
        "D" => n * 86_400,
        "W" => n * 604_800,
        _ => {
            return Err(IntermediateEventError::BadDuration {
                raw: raw.to_string(),
            })
        }
    };
    Ok(Duration::seconds(secs))
}

pub struct IntermediateEventExecutor;

#[async_trait]
impl NodeExecutor for IntermediateEventExecutor {
    async fn execute(
        &self,
        _node: &FlowNode,
        _ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        // V5.32 — `execute_with` is the real entry point
        // because `LinkThrow` needs the registry's `def` to
        // resolve `def.link_targets[link_name]`. Other
        // intermediate-event kinds (message / signal / timer
        // / conditional / linkCatch / None) don't use `def`,
        // but the dispatcher routes everything through
        // `execute_with` for uniformity. Mirrors
        // `CompensationThrowExecutor` / `SubProcessExecutor` /
        // `ParallelGatewayExecutor` (V5.28 P6).
        Err(FlowError::Unsupported(
            "IntermediateEventExecutor requires execute_with (use dispatcher)"
                .to_string(),
        ))
    }

    async fn execute_with(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
        reg: &ExecutorRegistry,
    ) -> Result<NodeResult, FlowError> {
        let NodeKind::IntermediateEvent { attrs } = &node.kind else {
            return Err(FlowError::Unsupported(
                "IntermediateEventExecutor on non-IntermediateEvent".to_string(),
            ));
        };
        let kind = IntermediateEventKind::from_attrs(attrs)
            .map_err(|e| FlowError::Action(e.to_string()))?;

        match kind {
            IntermediateEventKind::None => Ok(NodeResult::Continue),
            IntermediateEventKind::Message { name } => {
                catch_message(node, ctx, &name)
            }
            IntermediateEventKind::Signal { name } => {
                catch_signal(node, ctx, &name)
            }
            IntermediateEventKind::Timer { duration } => {
                catch_timer(node, ctx, duration)
            }
            // ── V5.32 ─────────────────────────────────────────
            IntermediateEventKind::Conditional { condition } => {
                catch_conditional(node, ctx, &condition)
            }
            IntermediateEventKind::LinkThrow { link_name } => {
                throw_link(node, reg, &link_name)
            }
            IntermediateEventKind::LinkCatch { .. } => {
                // Routing was already done by the throw.
                // The catch is a no-op `Continue` so the
                // traverser follows the catch's outgoing
                // edges normally.
                Ok(NodeResult::Continue)
            }
        }
    }
}

/// V5.32 — Conditional intermediate catch path. `wait_ref`
/// namespace is `conditional:<node_id>`. The condition text is
/// stored in `SuspendInfo.payload` for observability only;
/// v0 doesn't evaluate it. Resume is driven by an external
/// agent that writes
/// `current_awaiting_field = "conditional:<node_id>"` +
/// `current_awaiting_value` to the suspended flow. Polling
/// (V5.32+ scope) is the alternative resume path.
fn catch_conditional(
    node: &FlowNode,
    ctx: &mut FlowContext,
    condition: &str,
) -> Result<NodeResult, FlowError> {
    // Runtime validation: a conditional catch with an
    // empty `<bpmn:condition>` body is a hard failure.
    // This matches the missing-field semantic for
    // message/signal: the executor surfaces a clear
    // `FlowError::Action` for the in-editor user to fix.
    if condition.is_empty() {
        return Err(FlowError::Action(format!(
            "conditional catch '{}': missing condition expression",
            node.node_id
        )));
    }
    let kind_ref = format!("conditional:{}", node.node_id);
    if is_resume(ctx, &kind_ref) {
        // External signal landed — resume. The value is
        // whatever the upstream callback sent (v0 doesn't
        // read it; we just check presence).
        return Ok(NodeResult::Continue);
    }
    ctx.current_awaiting_field = Some(kind_ref.clone());
    let payload = json!({
        "node_id": node.node_id,
        "event_type": "conditional",
        "condition": condition,
    });
    Ok(NodeResult::Suspend(SuspendInfo {
        wait_type: WaitType::AsyncData,
        wait_ref: kind_ref,
        next_retry_at: None,
        payload,
    }))
}

/// V5.32 — Link throw path. Looks up the matching catch
/// node id in `def.link_targets[link_name]` and returns
/// `NodeResult::Branch(catch_id)`. The traverser's
/// `Branch(target) => self.next = Some(target)` arm routes
/// directly to the catch — bypassing the throw's outgoing
/// edges (which are a dead-letter path in BPMN).
///
/// On missing link name: hard `Failed` (BPMN 2.0 §9.3.2
/// requires every link throw to have a matching catch; v0
/// makes the failure explicit).
fn throw_link(
    node: &FlowNode,
    reg: &ExecutorRegistry,
    link_name: &str,
) -> Result<NodeResult, FlowError> {
    let def = reg.def.as_ref().ok_or_else(|| {
        FlowError::Action(format!(
            "linkThrow '{}': registry missing def",
            node.node_id
        ))
    })?;
    match def.link_targets.get(link_name) {
        Some(catch_id) => Ok(NodeResult::Branch(catch_id.clone())),
        None => Err(FlowError::Action(format!(
            "linkThrow '{}': no linkCatch with name '{}'",
            node.node_id, link_name
        ))),
    }
}

/// V5.28 P2 — shared resume check helper. Returns true if
/// `current_awaiting_field` matches `kind_ref` (the namespaced
/// wait_ref, e.g. `"message:approval_received"`) AND a value
/// is present. Pulled out so message / signal / timer all
/// share the same shape — the only thing that varies is
/// how the namespaced `kind_ref` is built.
fn is_resume(ctx: &FlowContext, kind_ref: &str) -> bool {
    ctx.current_awaiting_field.as_deref() == Some(kind_ref)
        && ctx.current_awaiting_value.is_some()
}

/// Message catch path. `wait_ref` and `current_awaiting_field`
/// are both `message:<event_name>` (V5.28 P2 namespacing).
/// The resume handler (`/flow/event`) writes
/// `current_awaiting_value` with the event payload AND
/// `current_awaiting_field = "message:<event_name>"`.
fn catch_message(
    node: &FlowNode,
    ctx: &mut FlowContext,
    event_name: &str,
) -> Result<NodeResult, FlowError> {
    let kind_ref = format!("message:{event_name}");
    if is_resume(ctx, &kind_ref) {
        return Ok(NodeResult::Continue);
    }
    ctx.current_awaiting_field = Some(kind_ref.clone());
    let payload = json!({
        "node_id": node.node_id,
        "event_type": "message",
        "event_name": event_name,
    });
    Ok(NodeResult::Suspend(SuspendInfo {
        wait_type: WaitType::AsyncData,
        wait_ref: kind_ref,
        next_retry_at: None,
        payload,
    }))
}

/// Signal catch path. `wait_ref` and `current_awaiting_field`
/// are both `signal:<event_name>` (V5.28 P2 namespacing).
fn catch_signal(
    node: &FlowNode,
    ctx: &mut FlowContext,
    event_name: &str,
) -> Result<NodeResult, FlowError> {
    let kind_ref = format!("signal:{event_name}");
    if is_resume(ctx, &kind_ref) {
        return Ok(NodeResult::Continue);
    }
    ctx.current_awaiting_field = Some(kind_ref.clone());
    let payload = json!({
        "node_id": node.node_id,
        "event_type": "signal",
        "event_name": event_name,
    });
    Ok(NodeResult::Suspend(SuspendInfo {
        wait_type: WaitType::AsyncData,
        wait_ref: kind_ref,
        next_retry_at: None,
        payload,
    }))
}

/// Timer catch path. `wait_ref` and `current_awaiting_field`
/// are both `timer:<node_id>` (V5.28 P2 namespacing). Timer
/// uses node_id (not event_name) because timer events don't
/// have a name — they're defined by their position in the
/// flow + duration. `next_retry_at` is set to now + duration.
fn catch_timer(
    node: &FlowNode,
    ctx: &mut FlowContext,
    duration: Duration,
) -> Result<NodeResult, FlowError> {
    let kind_ref = format!("timer:{}", node.node_id);
    if is_resume(ctx, &kind_ref) {
        return Ok(NodeResult::Continue);
    }
    ctx.current_awaiting_field = Some(kind_ref.clone());
    let next_retry_at: DateTime<Utc> = Utc::now() + duration;
    let payload = json!({
        "node_id": node.node_id,
        "event_type": "timer",
        "duration_seconds": duration.num_seconds(),
    });
    Ok(NodeResult::Suspend(SuspendInfo {
        wait_type: WaitType::AsyncTask,
        wait_ref: kind_ref,
        next_retry_at: Some(next_retry_at),
        payload,
    }))
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
    fn from_attrs_none_when_no_event_type() {
        let a = Attrs::new();
        assert_eq!(IntermediateEventKind::from_attrs(&a).unwrap(), IntermediateEventKind::None);
    }

    #[test]
    fn from_attrs_message_requires_name() {
        let a = attrs_with(&[("eventType", "message")]);
        assert!(matches!(
            IntermediateEventKind::from_attrs(&a),
            Err(IntermediateEventError::MissingField { .. })
        ));
    }

    #[test]
    fn from_attrs_message_with_name() {
        let a = attrs_with(&[("eventType", "message"), ("eventName", "approval_received")]);
        assert_eq!(
            IntermediateEventKind::from_attrs(&a).unwrap(),
            IntermediateEventKind::Message {
                name: "approval_received".into()
            }
        );
    }

    #[test]
    fn from_attrs_signal_with_name() {
        let a = attrs_with(&[("eventType", "signal"), ("eventName", "broadcast_xyz")]);
        assert_eq!(
            IntermediateEventKind::from_attrs(&a).unwrap(),
            IntermediateEventKind::Signal {
                name: "broadcast_xyz".into()
            }
        );
    }

    #[test]
    fn from_attrs_timer_parses_iso_durations() {
        let a = attrs_with(&[("eventType", "timer"), ("timerDuration", "PT5S")]);
        match IntermediateEventKind::from_attrs(&a).unwrap() {
            IntermediateEventKind::Timer { duration } => {
                assert_eq!(duration.num_seconds(), 5);
            }
            other => panic!("expected Timer, got {:?}", other),
        }
        let a = attrs_with(&[("eventType", "timer"), ("timerDuration", "PT1M")]);
        match IntermediateEventKind::from_attrs(&a).unwrap() {
            IntermediateEventKind::Timer { duration } => {
                assert_eq!(duration.num_seconds(), 60);
            }
            other => panic!("expected Timer, got {:?}", other),
        }
        let a = attrs_with(&[("eventType", "timer"), ("timerDuration", "PT2H")]);
        match IntermediateEventKind::from_attrs(&a).unwrap() {
            IntermediateEventKind::Timer { duration } => {
                assert_eq!(duration.num_seconds(), 7200);
            }
            other => panic!("expected Timer, got {:?}", other),
        }
    }

    #[test]
    fn from_attrs_timer_rejects_bad_duration() {
        let a = attrs_with(&[("eventType", "timer"), ("timerDuration", "5S")]);
        assert!(matches!(
            IntermediateEventKind::from_attrs(&a),
            Err(IntermediateEventError::BadDuration { .. })
        ));
        let a = attrs_with(&[("eventType", "timer"), ("timerDuration", "PTxyz")]);
        assert!(matches!(
            IntermediateEventKind::from_attrs(&a),
            Err(IntermediateEventError::BadDuration { .. })
        ));
    }

    #[test]
    fn from_attrs_unknown_kind_rejected() {
        let a = attrs_with(&[("eventType", "link")]);
        assert!(matches!(
            IntermediateEventKind::from_attrs(&a),
            Err(IntermediateEventError::UnknownKind { .. })
        ));
    }

    #[test]
    fn parse_iso_duration_units() {
        assert_eq!(parse_iso_duration("PT30S").unwrap().num_seconds(), 30);
        assert_eq!(parse_iso_duration("PT2M").unwrap().num_seconds(), 120);
        assert_eq!(parse_iso_duration("PT1H").unwrap().num_seconds(), 3600);
        assert_eq!(parse_iso_duration("PT1D").unwrap().num_seconds(), 86_400);
        assert_eq!(parse_iso_duration("PT1W").unwrap().num_seconds(), 604_800);
    }
}
