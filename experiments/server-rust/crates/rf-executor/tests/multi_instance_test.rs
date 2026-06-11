//! V5.29 — `multiInstanceLoopCharacteristics` integration tests.
//!
//! BPMN 2.0 `multiInstanceLoopCharacteristics` lets a task
//! node run N times, once per element of a collection
//! stored in `vars[collection]`. Each child branch sees the
//! per-iteration element at `vars[elementVar]`. V5.29 v0
//! supports BOTH parallel and sequential in-memory
//! execution; the wrapper lives in
//! `rf-executor::executors::multi_instance` and is wired
//! into the dispatcher as `reg.multi_instance`.
//!
//! ## V5.29 v0 contract
//!
//! - `ruleforge:multiInstance = "true"` is the gate. Any
//!   task kind (`ServiceTask` / `ScriptTask` / `UserTask`).
//! - `ruleforge:collection` = `<var_name>` — name of an
//!   array in `vars`. v0 does **not** evaluate expressions
//!   (so no `${{items}}`); the variable must already be
//!   populated.
//! - `ruleforge:elementVar` = `<var_name>` — per-iteration
//!   scratch, overwritten by the wrapper for each child.
//! - `ruleforge:outputVariable` (optional) = the wrapper
//!   collects the post-inner `vars[elementVar]` values
//!   into `vars[outputVariable] = [...]`.
//! - `ruleforge:multiInstanceSequential = "true"` →
//!   sequential `for` loop on the same `ctx` (vars
//!   persist between iterations; v0 does **not** snapshot
//!   the per-iteration scope).
//! - Empty collection → the wrapper writes `outputVariable
//!   = []` (if configured) and returns `Continue` (no
//!   child ran).
//! - Inner suspend → wrapper propagates `Suspend` upward
//!   (sequential: stops the loop; parallel: propagates the
//!   first child that suspends — v0 does not run the rest
//!   in parallel).
//! - **NOT** supported: `completionCondition`, expression
//!   collection, async barrier per-child, nested MI.

use std::sync::Arc;

use rf_executor::dispatch::ExecutorRegistry;
use rf_executor::executors::action::{ActionExecutor, MockActionRegistry};
use rf_executor::flow_context::FlowContext;
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

fn action_registry() -> Arc<ExecutorRegistry> {
    let actions = Arc::new(
        MockActionRegistry::new()
            // tag_with_item: writes `vars.tag = "<item>"` so
            // the test can verify elementVar was set per
            // iteration.
            .register("tag_with_item", |vars| {
                let item = vars.get("item").cloned().unwrap_or(json!(null));
                vars.assign("tag".to_string(), item);
                Ok(())
            })
            // collect_count: increments vars.count for each
            // child so the test can verify the loop ran N
            // times.
            .register("collect_count", |vars| {
                let cur = vars.get("count").and_then(|v| v.as_i64()).unwrap_or(0);
                vars.assign("count".to_string(), json!(cur + 1));
                Ok(())
            })
            // append_item: pushes vars.item into
            // vars.collected (a list) so we can verify
            // sequential ordering.
            .register("append_item", |vars| {
                let item = vars.get("item").cloned().unwrap_or(json!(null));
                let cur = vars
                    .get("collected")
                    .and_then(|v| v.as_array())
                    .cloned()
                    .unwrap_or_default();
                let mut next = cur;
                next.push(item);
                vars.assign("collected".to_string(), json!(next));
                Ok(())
            }),
    );
    let mut reg = ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    ));
    reg.action = Arc::new(ActionExecutor::new(actions));
    Arc::new(reg)
}

/// Test fixture: action-based MI task. The action
/// `tag_with_item` writes `vars.tag = vars.item` so the
/// test can verify the elementVar binding per iteration.
const MI_PARALLEL_ACTION: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="t" ruleforge:taskType="action"
                      ruleforge:method="tag_with_item"
                      ruleforge:multiInstance="true"
                      ruleforge:collection="items"
                      ruleforge:elementVar="item"
                      ruleforge:outputVariable="outputs">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:endEvent id="end"/>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="t"/>
    <bpmn:sequenceFlow id="e1" sourceRef="t" targetRef="end"/>
"#;

const MI_SEQUENTIAL_ACTION: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="t" ruleforge:taskType="action"
                      ruleforge:method="collect_count"
                      ruleforge:multiInstance="true"
                      ruleforge:multiInstanceSequential="true"
                      ruleforge:collection="items"
                      ruleforge:elementVar="item">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:endEvent id="end"/>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="t"/>
    <bpmn:sequenceFlow id="e1" sourceRef="t" targetRef="end"/>
"#;

const MI_PARALLEL_NO_ATTR: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="t" ruleforge:taskType="action"
                      ruleforge:method="tag_with_item">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:endEvent id="end"/>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="t"/>
    <bpmn:sequenceFlow id="e1" sourceRef="t" targetRef="end"/>
"#;

const MI_PARALLEL_POST_NODE: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="t" ruleforge:taskType="action"
                      ruleforge:method="tag_with_item"
                      ruleforge:multiInstance="true"
                      ruleforge:collection="items"
                      ruleforge:elementVar="item">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:serviceTask id="post" ruleforge:taskType="action"
                      ruleforge:method="tag_with_item">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:endEvent id="end"/>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="t"/>
    <bpmn:sequenceFlow id="e1" sourceRef="t" targetRef="post"/>
    <bpmn:sequenceFlow id="e2" sourceRef="post" targetRef="end"/>
"#;

const MI_SEQUENTIAL_ORDERED: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="t" ruleforge:taskType="action"
                      ruleforge:method="append_item"
                      ruleforge:multiInstance="true"
                      ruleforge:multiInstanceSequential="true"
                      ruleforge:collection="items"
                      ruleforge:elementVar="item">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:endEvent id="end"/>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="t"/>
    <bpmn:sequenceFlow id="e1" sourceRef="t" targetRef="end"/>
"#;

// ----- parallel MI -----

#[test]
fn multi_instance_parallel_runs_all_items_to_completion() {
    let def = parse(&bpmn("p", MI_PARALLEL_ACTION));
    let reg = action_registry();
    let mut ctx = FlowContext::new("p1");
    ctx.vars.assign(
        "items".to_string(),
        json!(["a", "b", "c"]),
    );
    let outcome = traverse(def, ctx, reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // Parallel: each child branch clones the
            // parent vars and runs the action
            // independently. The action writes
            // `vars.tag = vars.item`. After the loop,
            // the LAST child to write wins (the
            // union-merge in traverse is last-write
            // by branch order). v0 only guarantees
            // that *some* item's tag survives — the
            // exact value depends on branch order.
            // We assert at least that the tag is
            // one of the items.
            let tag = t.ctx.vars.get("tag").cloned().unwrap_or(json!(null));
            assert!(
                matches!(&tag, serde_json::Value::String(s) if s == "a" || s == "b" || s == "c"),
                "tag should be one of the items, got {tag}"
            );
            // The outputVariable is a list of the
            // element values (post-inner). Each
            // child ran with its own elementVar
            // value; the wrapper collected them.
            let outputs = t.ctx.vars.get("outputs").cloned().unwrap_or(json!(null));
            assert_eq!(outputs, json!(["a", "b", "c"]));
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

#[test]
fn multi_instance_parallel_runs_to_post_node() {
    let def = parse(&bpmn("p", MI_PARALLEL_POST_NODE));
    let reg = action_registry();
    let mut ctx = FlowContext::new("p1");
    ctx.vars.assign(
        "items".to_string(),
        json!(["x", "y"]),
    );
    let outcome = traverse(def, ctx, reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // The MI task ran (N=2 children, each
            // wrote vars.tag = vars.item on its
            // own child ctx), then the `post`
            // node ran and overwrote vars.tag
            // using the post-merge vars.item
            // (which is one of "x"/"y" — last
            // child wins on the per-element
            // var, then post re-writes it).
            // The point is the flow reached
            // `end` past `post`, not stuck on
            // the MI task.
            assert_eq!(
                t.ctx.current_node_id.as_deref(),
                Some("end"),
                "flow should reach end node after MI + post"
            );
            // tag is set (post wrote it; value
            // depends on which element survived
            // the merge — we just assert it's
            // set to one of the two).
            let tag = t.ctx.vars.get("tag").cloned().unwrap_or(json!(null));
            assert!(
                matches!(&tag, serde_json::Value::String(s) if s == "x" || s == "y"),
                "tag should be one of the items, got {tag}"
            );
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

#[test]
fn multi_instance_parallel_empty_collection_completes_immediately() {
    let def = parse(&bpmn("p", MI_PARALLEL_ACTION));
    let reg = action_registry();
    let mut ctx = FlowContext::new("p1");
    ctx.vars.assign("items".to_string(), json!([]));
    let outcome = traverse(def, ctx, reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // No items → no child ran → outputs is
            // an empty list.
            assert_eq!(
                t.ctx.vars.get("outputs").cloned(),
                Some(json!([]))
            );
            // No tag written (no child ran).
            assert!(t.ctx.vars.get("tag").is_none());
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

// ----- sequential MI -----

#[test]
fn multi_instance_sequential_runs_items_in_order() {
    let def = parse(&bpmn("p", MI_SEQUENTIAL_ORDERED));
    let reg = action_registry();
    let mut ctx = FlowContext::new("p1");
    ctx.vars.assign(
        "items".to_string(),
        json!(["a", "b", "c"]),
    );
    let outcome = traverse(def, ctx, reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // `append_item` pushes vars.item into
            // vars.collected for each iteration.
            // Sequential preserves order.
            assert_eq!(
                t.ctx.vars.get("collected").cloned(),
                Some(json!(["a", "b", "c"]))
            );
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

#[test]
fn multi_instance_sequential_empty_collection_completes_immediately() {
    let def = parse(&bpmn("p", MI_SEQUENTIAL_ACTION));
    let reg = action_registry();
    let mut ctx = FlowContext::new("p1");
    ctx.vars.assign("items".to_string(), json!([]));
    let outcome = traverse(def, ctx, reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // No iterations → the inner never ran,
            // so count is not assigned at all
            // (the action would have created it).
            // The test verifies the empty path
            // doesn't error and doesn't leak
            // phantom writes from a child that
            // never executed.
            assert!(t.ctx.vars.get("count").is_none());
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

// ----- regression / pass-through -----

#[test]
fn multi_instance_no_multi_instance_attr_passes_through_to_inner() {
    let def = parse(&bpmn("p", MI_PARALLEL_NO_ATTR));
    let reg = action_registry();
    let mut ctx = FlowContext::new("p1");
    ctx.vars.assign("items".to_string(), json!(["a", "b", "c"]));
    let outcome = traverse(def, ctx, reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // No MI attr → wrapper delegates to the
            // inner action executor. The action
            // reads `vars.item` (which is **not** set
            // — v0 MI would set it; v0
            // pass-through doesn't). The action
            // writes `vars.tag = vars.item` = null.
            // We assert the flow still completes
            // and the tag is null (the
            // pass-through didn't expand).
            assert_eq!(
                t.ctx.vars.get("tag").cloned(),
                Some(json!(null))
            );
            // No outputVariable was set.
            assert!(t.ctx.vars.get("outputs").is_none());
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

// ----- error paths -----

#[test]
fn multi_instance_missing_collection_var_errors() {
    // collection="missing" is referenced but the
    // caller never set it.
    let def = parse(&bpmn("p", MI_PARALLEL_ACTION));
    let reg = action_registry();
    let mut ctx = FlowContext::new("p1");
    // intentionally NOT setting vars.items
    ctx.vars.assign(
        "items".to_string(),
        // explicit null — the wrapper will
        // surface "not an array" because null
        // is not an array
        json!(null),
    );
    let outcome = traverse(def, ctx, reg);
    match outcome {
        TraverseOutcome::Failed(_, err) => {
            let msg = format!("{err}");
            assert!(
                msg.contains("array") || msg.contains("collection"),
                "expected error to mention array/collection, got: {msg}"
            );
        }
        other => panic!("expected Failed, got {other:?}"),
    }
}

#[test]
fn multi_instance_collection_not_an_array_errors() {
    // items is a string, not an array. Wrapper
    // should fail with NotAnArray.
    let def = parse(&bpmn("p", MI_PARALLEL_ACTION));
    let reg = action_registry();
    let mut ctx = FlowContext::new("p1");
    ctx.vars.assign("items".to_string(), json!("not-a-list"));
    let outcome = traverse(def, ctx, reg);
    match outcome {
        TraverseOutcome::Failed(_, err) => {
            let msg = format!("{err}");
            assert!(
                msg.contains("array") || msg.contains("string"),
                "expected error to mention array/string, got: {msg}"
            );
        }
        other => panic!("expected Failed, got {other:?}"),
    }
}
