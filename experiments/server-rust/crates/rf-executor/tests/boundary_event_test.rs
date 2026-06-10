//! V5.27 BoundaryEvent suspend + resume tests.
//!
//! The boundary event is structurally a sibling of an activity in
//! the v0 model: it's a node the traversal visits, and its
//! outgoing edges form the "handler" path. The discriminator
//! (`ruleforge:eventType`) determines whether it suspends on
//! an external error (`error:<ref>`) or on a timer.
//!
//! These tests assert the in-memory contract — Phase 4+ will add
//! the HTTP `/flow/event` handler that writes
//! `current_awaiting_field` and `current_awaiting_value` for the
//! resume path.

use std::sync::Arc;

use chrono::Utc;
use rf_executor::dispatch::ExecutorRegistry;
use rf_executor::flow_context::FlowContext;
use rf_executor::node_result::WaitType;
use rf_executor::traverser::{traverse, TraverseOutcome};
use rf_ir::flow_definition::FlowDefinition;
use rf_parse::bpmn_parser::BpmnXmlParser;
use serde_json::json;

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

const NO_TYPE_FLOW: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:boundaryEvent id="be" attachedToRef="s">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:boundaryEvent>
    <bpmn:endEvent id="end"/>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="be"/>
    <bpmn:sequenceFlow id="e2" sourceRef="be" targetRef="end"/>
"#;

#[test]
fn error_boundary_on_main_path_suspends_with_error_wait_ref() {
    // A boundary is on the main path when the main flow's
    // outgoing chain traverses through it. This is a degenerate
    // shape (boundary isn't really a "boundary" anymore), but
    // it's a useful way to test the executor's suspend payload
    // without bringing in flow-attached semantics.
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:boundaryEvent id="be" ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="be"/>
        <bpmn:sequenceFlow id="e2" sourceRef="be" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    match outcome {
        TraverseOutcome::Suspended(_, info) => {
            assert_eq!(info.wait_type, WaitType::AsyncData);
            assert_eq!(info.wait_ref, "error:boom");
            assert_eq!(info.payload["event_type"], json!("boundaryError"));
            assert_eq!(info.payload["error_ref"], json!("boom"));
        }
        _ => panic!("expected Suspended"),
    }
}

#[test]
fn error_boundary_uses_default_error_ref_when_attr_missing() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:boundaryEvent id="be" ruleforge:eventType="error">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="be"/>
        <bpmn:sequenceFlow id="e2" sourceRef="be" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    match outcome {
        TraverseOutcome::Suspended(_, info) => {
            // default is "error" so wait_ref is "error:error"
            assert_eq!(info.wait_ref, "error:error");
        }
        _ => panic!("expected Suspended"),
    }
}

#[test]
fn error_boundary_resume_with_matching_awaiting_field_continues() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:boundaryEvent id="be" ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="be"/>
        <bpmn:sequenceFlow id="e2" sourceRef="be" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let outcome = traverse(def.clone(), FlowContext::new("r"), test_registry());
    let (suspended, _) = match outcome {
        TraverseOutcome::Suspended(s, i) => (s, i),
        _ => panic!("expected Suspended"),
    };
    let mut ctx = suspended.ctx;
    ctx.current_awaiting_field = Some("error:boom".to_string());
    ctx.current_awaiting_value = Some(json!({"detail": "timeout"}));

    let outcome = traverse(def, ctx, test_registry());
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(t.ctx().current_node_id.as_deref(), Some("end"));
        }
        _ => panic!("expected Completed after resume"),
    }
}

#[test]
fn timer_boundary_suspends_with_async_task_and_retry_at() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:boundaryEvent id="be" ruleforge:eventType="timer"
                            ruleforge:timerDuration="PT5S">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="be"/>
        <bpmn:sequenceFlow id="e2" sourceRef="be" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let before = Utc::now();
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    match outcome {
        TraverseOutcome::Suspended(_, info) => {
            assert_eq!(info.wait_type, WaitType::AsyncTask);
            assert!(info.next_retry_at.is_some(), "timer must set next_retry_at");
            let retry = info.next_retry_at.unwrap();
            let diff = (retry - before).num_seconds();
            assert!(diff >= 4 && diff <= 6, "expected ~5s, got {diff}s");
            assert_eq!(info.payload["event_type"], json!("boundaryTimer"));
            assert_eq!(info.payload["duration_seconds"], json!(5));
        }
        _ => panic!("expected Suspended"),
    }
}

#[test]
fn timer_boundary_requires_duration() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:boundaryEvent id="be" ruleforge:eventType="timer">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="be"/>
        <bpmn:sequenceFlow id="e2" sourceRef="be" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    assert!(matches!(outcome, TraverseOutcome::Failed(_, _)));
}

#[test]
fn no_event_type_passes_through_as_continue() {
    let def = parse(&bpmn("p", NO_TYPE_FLOW));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(t.ctx().current_node_id.as_deref(), Some("end"));
        }
        _ => panic!("expected Completed (no eventType = pass-through)"),
    }
}

#[test]
fn unknown_event_type_errors() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:boundaryEvent id="be" ruleforge:eventType="signal">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="be"/>
        <bpmn:sequenceFlow id="e2" sourceRef="be" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    assert!(matches!(outcome, TraverseOutcome::Failed(_, _)));
}
