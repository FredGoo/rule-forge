//! V5.32 — `ConditionalIntermediateCatchEvent` (BPMN 2.0 §9.4.5) tests.
//!
//! Conditional intermediate catch is the BPMN 2.0 pattern for
//! "wait until some condition on the running vars becomes true."
//! Java's Flowable uses a polling worker that periodically evaluates
//! the condition expression. V5.32 v0 deliberately takes a simpler
//! shape: the node suspends with a namespaced `wait_ref`
//! (`conditional:<node_id>`), and an external agent (e.g. a callback
//! from an upstream system, or a worker that observed a state
//! change) signals via `/flow/event` to resume. The condition text
//! itself is stored in the `SuspendInfo.payload` for observability
//! only — V5.32 v0 does not evaluate it. The UEL evaluator and
//! polling worker are V5.32+ scope.
//!
//! ## Test layout
//!
//! 3 tests, one per failure / success path:
//! - `given_conditional_catch_when_external_signal_then_resume`
//!   — happy path: `wait_ref` namespace is `conditional:`, resume
//!   on signal goes `Continue`.
//! - `given_conditional_catch_with_no_condition_then_fail`
//!   — missing condition expression is a hard `Failed` at execute
//!   time (mirrors message/signal's `MissingField` semantic).
//! - `given_conditional_catch_when_no_resume_signal_then_stay_suspended`
//!   — without the resume signal, the flow stays suspended (does
//!   not auto-progress). This is the "stuck" semantic — without
//!   an external signal, the flow waits forever. V5.32+ adds
//!   polling-driven auto-resume; v0 only resumes on signal.

use std::sync::Arc;

use rf_executor::dispatch::ExecutorRegistry;
use rf_executor::flow_context::FlowContext;
use rf_executor::node_result::WaitType;
use rf_executor::traverser::{traverse, TraverseOutcome};
use rf_ir::flow_definition::FlowDefinition;
use rf_parse::bpmn_parser::BpmnXmlParser;

fn bpmn(process_id: &str, body: &str) -> String {
    format!(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:ruleforge="http://ruleforge.com/schema"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="{process_id}">
    {body}
  </bpmn:process>
</bpmn:definitions>"#
    )
}

fn parse(xml: &str) -> Arc<FlowDefinition> {
    Arc::new(BpmnXmlParser::parse(xml).expect("parse ok"))
}

fn test_registry() -> Arc<ExecutorRegistry> {
    Arc::new(ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    )))
}

/// **Test 1 — happy path: condition is set, external signal triggers resume.**
///
/// `Behavior: A conditional intermediate catch suspends the flow with
/// `wait_type = AsyncData` and `wait_ref = "conditional:<node_id>"`.
/// The condition text is preserved in the `SuspendInfo.payload` for
/// observability. On resume (caller sets
/// `current_awaiting_field = "conditional:<node_id>"` and a value),
/// the node returns `Continue` and the flow advances to the next
/// node.`
#[test]
fn given_conditional_catch_when_external_signal_then_resume() {
    let xml = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:intermediateCatchEvent id="cond">
          <bpmn:incoming>e1</bpmn:incoming>
          <bpmn:outgoing>e2</bpmn:outgoing>
          <bpmn:conditionalEventDefinition id="condDef">
            <bpmn:condition>${vars.amount > 1000}</bpmn:condition>
          </bpmn:conditionalEventDefinition>
        </bpmn:intermediateCatchEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="cond"/>
        <bpmn:sequenceFlow id="e2" sourceRef="cond" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", xml));
    let outcome = traverse(def, FlowContext::new("r1"), test_registry());
    match outcome {
        TraverseOutcome::Suspended(_, info) => {
            // Suspend with AsyncData + conditional: namespace
            assert_eq!(info.wait_type, WaitType::AsyncData);
            assert_eq!(info.wait_ref, "conditional:cond");
            // Condition text preserved in payload for observability
            assert_eq!(info.payload["condition"], "${vars.amount > 1000}");
        }
        other => panic!("expected Suspended, got {other:?}"),
    }

    // Resume: caller sets current_awaiting_field matching the
    // namespaced wait_ref. The `current_awaiting_value` is the
    // resume signal payload (in production this is the upstream
    // system's callback body; v0 just checks presence).
    let xml = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:intermediateCatchEvent id="cond">
          <bpmn:incoming>e1</bpmn:incoming>
          <bpmn:outgoing>e2</bpmn:outgoing>
          <bpmn:conditionalEventDefinition id="condDef">
            <bpmn:condition>${vars.amount > 1000}</bpmn:condition>
          </bpmn:conditionalEventDefinition>
        </bpmn:intermediateCatchEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="cond"/>
        <bpmn:sequenceFlow id="e2" sourceRef="cond" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", xml));
    let mut ctx = FlowContext::new("r1");
    ctx.current_awaiting_field = Some("conditional:cond".to_string());
    ctx.current_awaiting_value = Some(serde_json::json!(true));
    let outcome = traverse(def, ctx, test_registry());
    match outcome {
        TraverseOutcome::Completed(_) => {
            // Got to the end. Resume works.
        }
        other => panic!("expected Completed after resume, got {other:?}"),
    }
}

/// **Test 2 — missing condition: `Failed`.**
///
/// `Behavior: A conditional catch with an empty `condition`
/// expression is a hard failure at execute time. This mirrors
/// message/signal's `MissingField` semantic. The flow is marked
/// `Failed` with a clear action error message.`
#[test]
fn given_conditional_catch_with_no_condition_then_fail() {
    let xml = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:intermediateCatchEvent id="cond">
          <bpmn:incoming>e1</bpmn:incoming>
          <bpmn:outgoing>e2</bpmn:outgoing>
          <bpmn:conditionalEventDefinition id="condDef">
            <bpmn:condition></bpmn:condition>
          </bpmn:conditionalEventDefinition>
        </bpmn:intermediateCatchEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="cond"/>
        <bpmn:sequenceFlow id="e2" sourceRef="cond" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", xml));
    let outcome = traverse(def, FlowContext::new("r2"), test_registry());
    match outcome {
        TraverseOutcome::Failed(_, err) => {
            let msg = format!("{err}");
            assert!(
                msg.contains("condition") || msg.contains("conditional"),
                "expected error mentioning 'condition' or 'conditional', got: {msg}"
            );
        }
        other => panic!("expected Failed for empty condition, got {other:?}"),
    }
}

/// **Test 3 — no resume signal: stay suspended.**
///
/// `Behavior: If the flow reaches a conditional catch and the caller
/// never writes a matching `current_awaiting_field` + value, the
/// flow stays suspended. This is the V5.32 v0 contract: v0 has no
/// polling worker, so the flow only resumes on external signal.
/// The resume signal is whatever causes the next call to
/// `traverse()` to come in with `current_awaiting_field` set; v0
/// does not auto-progress.`
#[test]
fn given_conditional_catch_when_no_resume_signal_then_stay_suspended() {
    let xml = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:intermediateCatchEvent id="cond">
          <bpmn:incoming>e1</bpmn:incoming>
          <bpmn:outgoing>e2</bpmn:outgoing>
          <bpmn:conditionalEventDefinition id="condDef">
            <bpmn:condition>${vars.ready == true}</bpmn:condition>
          </bpmn:conditionalEventDefinition>
        </bpmn:intermediateCatchEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="cond"/>
        <bpmn:sequenceFlow id="e2" sourceRef="cond" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", xml));

    // First traverse: suspends on cond.
    let outcome = traverse(def.clone(), FlowContext::new("r3"), test_registry());
    assert!(
        matches!(outcome, TraverseOutcome::Suspended(_, _)),
        "first traverse should Suspend, got {outcome:?}"
    );

    // Second traverse (no signal written): same node, still suspended.
    // This is the "stuck" semantic — the next retry on the same
    // context (no await field set) must NOT auto-progress.
    let outcome2 = traverse(def, FlowContext::new("r3"), test_registry());
    match outcome2 {
        TraverseOutcome::Suspended(_, info) => {
            // Still on the same wait_ref — no auto-progress.
            assert_eq!(info.wait_ref, "conditional:cond");
        }
        other => panic!("expected re-Suspended without signal, got {other:?}"),
    }
}
