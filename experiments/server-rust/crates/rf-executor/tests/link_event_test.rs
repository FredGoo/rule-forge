//! V5.32 — `IntermediateThrowEvent` with `linkEventDefinition`
//! (BPMN 2.0 §9.3) tests.
//!
//! Link throw/catch is BPMN's "goto" — a LinkThrowEvent with
//! `linkEventDefinition name="L"` jumps to the LinkCatchEvent with
//! the same name. The throw and catch don't have to be in the
//! same subgraph (BPMN allows cross-subgraph linking); V5.32 v0
//! supports the same-process case via
//! `def.link_targets: BTreeMap<link_name, catch_node_id>`.
//!
//! The semantic: the LinkThrow executor returns
//! `NodeResult::Branch(catch_node_id)`, and the traverser's
//! `Branch(target) => self.next = Some(target)` arm routes directly
//! to the catch — bypassing the throw's outgoing (which is a
//! dead-letter path in BPMN). The LinkCatch executor is a no-op
//! `Continue`: routing was already done by the throw.
//!
//! ## Test layout
//!
//! 2 tests:
//! - `given_link_throw_when_jump_to_catch_then_skip_intermediate_nodes`
//!   — happy path: throw with `name="L"` skips over any nodes that
//!   would have run between the throw and the catch (in the
//!   normal "follow the sequence flow" topology).
//! - `given_link_throw_with_unmatched_name_then_fail`
//!   — throw with a name that has no matching catch is a hard
//!   `Failed` (mirrors `linkEventDefinition` requiring a matching
//!   pair per BPMN spec §9.3.2).

use std::sync::Arc;

use rf_executor::dispatch::ExecutorRegistry;
use rf_executor::executors::action::{ActionExecutor, MockActionRegistry};
use rf_executor::flow_context::FlowContext;
use rf_executor::traverser::{traverse, TraverseOutcome};
use rf_ir::flow_definition::FlowDefinition;
use rf_parse::bpmn_parser::BpmnXmlParser;
use serde_json::json;

/// Build a registry whose actions append to `vars.main` so we can
/// assert visited order.
fn registry() -> Arc<ExecutorRegistry> {
    let actions = Arc::new(MockActionRegistry::new().register("mark", |vars| {
        // Read `action` from vars (set by the XML's `ruleforge:method`
        // doesn't exist here — we use a single shared "mark" that
        // pulls the label from the node name via... actually no, the
        // serviceTask doesn't know its own name. We'll use a different
        // scheme: each serviceTask has its own method, and the mark
        // method appends the method name.)
        //
        // Simpler: each serviceTask is registered separately.
        let _ = vars;
        Ok(())
    }));
    let mut reg = ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    ));
    reg.action = Arc::new(ActionExecutor::new(actions));
    Arc::new(reg)
}

/// Helper — build a registry where each `mark_<label>` action
/// appends `<label>` to `vars.main` (an array).
fn registry_with_marks(labels: &[&str]) -> Arc<ExecutorRegistry> {
    let mut actions = MockActionRegistry::new();
    for label in labels {
        let l = label.to_string();
        actions = actions.register(&format!("mark_{label}"), move |vars| {
            let cur = vars
                .get("main")
                .and_then(|v| v.as_array())
                .cloned()
                .unwrap_or_default();
            let mut next = cur;
            next.push(json!(l.clone()));
            vars.assign("main".to_string(), json!(next));
            Ok(())
        });
    }
    let actions = Arc::new(actions);
    let mut reg = ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    ));
    reg.action = Arc::new(ActionExecutor::new(actions));
    Arc::new(reg)
}

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

/// **Test 1 — happy path: throw jumps to catch, skipping intermediate nodes.**
///
/// Topology:
/// ```text
///   s → a → throw(name=L1) → c → d → end
///              ↘ catch(name=L1) → c
/// ```
///
/// In the "normal" sequence flow, after `a` the flow would go to
/// `throw`, which BPMN-wise is a one-way ticket: the throw's
/// outgoing (`throw → c`) is a dead-letter. The throw instead
/// branches to the catch (`L1`), and from the catch the flow
/// continues to `c` (the catch's outgoing). `b` and `f` are
/// inserted between `a → throw` and `catch` in the document
/// order, but in a "natural" reading they're not on the throw's
/// execution path — the test confirms the throw jumps over them.
///
/// `Behavior: A LinkThrowEvent with
/// `<bpmn:linkEventDefinition name="L1"/>` returns
/// `NodeResult::Branch(catch_node_id)` where `catch_node_id` is
/// the LinkCatch with the same name. The traverser's
/// `Branch(target) => self.next = Some(target)` arm routes
/// directly. The LinkCatch returns `Continue` so the flow
/// proceeds from the catch's outgoing. Intermediate nodes
/// between the throw (in document order) and the catch are
/// NOT executed.`
#[test]
fn given_link_throw_when_jump_to_catch_then_skip_intermediate_nodes() {
    // Build a registry that marks every serviceTask with a label
    // we can later inspect in `vars.main`.
    let reg = registry_with_marks(&["a", "b", "c", "d", "f"]);

    // Topology (with b and f on the "side" — they appear in
    // document order but are not on the throw's path):
    //   s → a → throw(L1) —jumps to—> catch(L1) → c → d → end
    //   b: orphan serviceTask (not connected by sequenceFlow)
    //   f: orphan serviceTask (not connected by sequenceFlow)
    //
    // To assert "throw skips", we put b and f INSIDE the path
    // that the throw would have taken (throw.outgoing = b, b →
    // c) — but the throw branches to catch, so b never runs.
    // The test confirms vars.main = ["a", "c", "d"] and NOT
    // ["a", "b", "c", "d"] or ["a", "b", "f", "c", "d"].
    let xml = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="a" ruleforge:taskType="action"
                          ruleforge:method="mark_a">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:intermediateThrowEvent id="throw">
          <bpmn:incoming>e1</bpmn:incoming>
          <bpmn:outgoing>e_throw</bpmn:outgoing>
          <bpmn:linkEventDefinition id="ldef" name="L1"/>
        </bpmn:intermediateThrowEvent>
        <!-- This is the "would-have-run" path the throw bypasses. -->
        <bpmn:serviceTask id="b" ruleforge:taskType="action"
                          ruleforge:method="mark_b"/>
        <bpmn:intermediateCatchEvent id="catch">
          <bpmn:incoming>e_catch</bpmn:incoming>
          <bpmn:outgoing>e_catch_out</bpmn:outgoing>
          <bpmn:linkEventDefinition id="cdef" name="L1"/>
        </bpmn:intermediateCatchEvent>
        <bpmn:serviceTask id="c" ruleforge:taskType="action"
                          ruleforge:method="mark_c">
          <bpmn:outgoing>e_c</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:serviceTask id="d" ruleforge:taskType="action"
                          ruleforge:method="mark_d">
          <bpmn:outgoing>e_d</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="a"/>
        <bpmn:sequenceFlow id="e1" sourceRef="a" targetRef="throw"/>
        <bpmn:sequenceFlow id="e_throw" sourceRef="throw" targetRef="b"/>
        <bpmn:sequenceFlow id="eb" sourceRef="b" targetRef="end"/>
        <bpmn:sequenceFlow id="e_catch" sourceRef="catch" targetRef="c"/>
        <bpmn:sequenceFlow id="e_catch_out" sourceRef="catch" targetRef="c"/>
        <bpmn:sequenceFlow id="e_c" sourceRef="c" targetRef="d"/>
        <bpmn:sequenceFlow id="e_d" sourceRef="d" targetRef="end"/>
    "#;

    let def = parse(&bpmn("p", xml));
    let outcome = traverse(def, FlowContext::new("r1"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            let main = t.ctx.vars.get("main").cloned().unwrap_or(json!(null));
            // The throw should have skipped b. a runs first, then
            // throw branches to catch, then c → d → end. So
            // vars.main should be ["a", "c", "d"] — NO b.
            assert_eq!(
                main,
                json!(["a", "c", "d"]),
                "link throw should skip b; main = {main}"
            );
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

/// **Test 2 — throw with no matching catch: `Failed`.**
///
/// `Behavior: A LinkThrowEvent with a `linkEventDefinition name`
/// that has no matching LinkCatchEvent is a hard `Failed` at
/// execute time. This matches BPMN 2.0 §9.3.2 — a link throw
/// without a matching catch is undefined behaviour, and the
/// conservative Rust v0 makes it a clear action error. The
/// `def.link_targets` reverse-lookup is the source of truth;
/// the throw looks up the catch node by name in this map.`
#[test]
fn given_link_throw_with_unmatched_name_then_fail() {
    // `b` is the only catch candidate, with a *different* name
    // ("L99") than the throw's ("L_missing").
    let xml = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:intermediateThrowEvent id="throw">
          <bpmn:incoming>e0</bpmn:incoming>
          <bpmn:outgoing>e1</bpmn:outgoing>
          <bpmn:linkEventDefinition id="ldef" name="L_missing"/>
        </bpmn:intermediateThrowEvent>
        <bpmn:intermediateCatchEvent id="catch">
          <bpmn:outgoing>e2</bpmn:outgoing>
          <bpmn:linkEventDefinition id="cdef" name="L99"/>
        </bpmn:intermediateCatchEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="throw"/>
        <bpmn:sequenceFlow id="e1" sourceRef="throw" targetRef="catch"/>
        <bpmn:sequenceFlow id="e2" sourceRef="catch" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", xml));
    let outcome = traverse(def, FlowContext::new("r2"), registry());
    match outcome {
        TraverseOutcome::Failed(_, err) => {
            let msg = format!("{err}");
            assert!(
                msg.contains("L_missing") || msg.contains("linkThrow") || msg.contains("link"),
                "expected error mentioning the missing link name, got: {msg}"
            );
        }
        other => panic!("expected Failed for unmatched link name, got {other:?}"),
    }
}
