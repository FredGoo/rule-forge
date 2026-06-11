//! V5.28 P1 — `attachedToRef` 路由 集成测试.
//!
//! V5.27 把 boundary 当 sibling node 走 dispatcher(visited via
//! sequenceFlow)。V5.28 P1 引入"真的 attached"语义:
//! - boundary 的 `bpmn:attachedToRef="<activity_id>"`
//! - 当 activity 抛 error 时(写 `ctx.thrown_error = Some(ref)`)
//! - traverser 跳过 activity 的 normal outgoing,直接走 boundary 的
//!   first outgoing(handler path)
//! - boundary 本身不被 visit
//!
//! 这些 test 走 `traverse()` 端到端,验证 attached routing 正确,
//! sibling-style 行为不受影响(向后兼容 V5.27),以及边界情况:
//! - thrown ref 不匹配任何 boundary → fall through 正常 outgoing
//! - activity 没抛 → 走 normal outgoing,boundary 被 bypass
//! - 一个 activity 上挂多个 boundary → first match wins

use std::sync::Arc;

use async_trait::async_trait;
use rf_executor::dispatch::ExecutorRegistry;
use rf_executor::error::FlowError;
use rf_executor::flow_context::FlowContext;
use rf_executor::node_executor::NodeExecutor;
use rf_executor::node_result::NodeResult;
use rf_executor::traverser::{traverse, TraverseOutcome};
use rf_ir::flow_definition::FlowDefinition;
use rf_ir::flow_node::FlowNode;
use rf_parse::bpmn_parser::BpmnXmlParser;
use serde_json::json;

/// 自定义 action executor — 读 `ruleforge:throwRef` attr 并写
/// `ctx.thrown_error`。模拟"action 执行后 throw"。
///
/// 我们的 `MockActionRegistry::ActionFn` 签名是 `fn(&mut Vars)`,
/// 没法写 `ctx.thrown_error`(那是 FlowContext 字段,不是 Vars)。
/// 这个 executor 是 test-only 的逃生口 — production 应该用
/// 一个真的 throw action(走 `MockActionRegistry`,签名扩展)而不是
/// 替换整个 action slot。
pub struct ThrowActionExecutor {
    /// 哪个 serviceTask 节点 throw。匹配节点 id 才会 throw。
    pub throws_for: String,
    /// 抛的 ref。给 boundary 的 `errorRef` 用。
    pub thrown_ref: String,
}

#[async_trait]
impl NodeExecutor for ThrowActionExecutor {
    async fn execute(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        if node.node_id == self.throws_for {
            ctx.thrown_error = Some(self.thrown_ref.clone());
            // 模拟 action 副作用:也写一个 marker 到 vars,这样
            // 测试可以验证 action 确实跑了
            ctx.vars.insert("__throw_ran__".to_string(), json!(true));
        }
        Ok(NodeResult::Continue)
    }
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

fn base_registry() -> Arc<ExecutorRegistry> {
    Arc::new(ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    )))
}

fn registry_with_throw(
    throws_for: &str,
    thrown_ref: &str,
) -> Arc<ExecutorRegistry> {
    let mut r = (*base_registry()).clone();
    r.action = Arc::new(ThrowActionExecutor {
        throws_for: throws_for.to_string(),
        thrown_ref: thrown_ref.to_string(),
    });
    Arc::new(r)
}

/// **Test 1: 基础 attached 路由** — activity 抛 matching error,
/// 跳过 activity 的 normal outgoing,直接走 boundary 的 outgoing。
///
/// Flow shape:
/// ```text
///   s ──► task (attached boundary "b") ──► normal_end
///   b ──► error_end
/// ```
///
/// 当 task throws,traverse 应该:
/// 1. visit s → Continue → next = task
/// 2. visit task → ThrowActionExecutor 写 `thrown_error="boom"`
/// 3. step() post-dispatch 检查 thrown_error,匹配 b(errorRef="boom"),
///    路由到 b.outgoing_ids[0] = e_error
/// 4. visit error_end → Continue → Done
///
/// Final node: `error_end` (NOT `normal_end`).
/// `__throw_ran__` marker 应该被设置(action 跑了)。
#[test]
fn attached_error_boundary_routes_thrown_error() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_ok</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:boundaryEvent id="b" attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e_err</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="normal_end"/>
        <bpmn:endEvent id="error_end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="task"/>
        <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="normal_end"/>
        <bpmn:sequenceFlow id="e_err" sourceRef="b" targetRef="error_end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let reg = registry_with_throw("task", "boom");
    let outcome = traverse(def, FlowContext::new("r"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(
                t.ctx().current_node_id.as_deref(),
                Some("error_end"),
                "thrown error should route to boundary's outgoing → error_end, not normal_end"
            );
            // action ran (ThrowActionExecutor 写了 marker)
            assert_eq!(
                t.ctx().vars.get("__throw_ran__"),
                Some(&json!(true)),
                "ThrowActionExecutor should have run before the boundary routing kicked in"
            );
        }
        other => panic!("expected Completed at error_end, got {other:?}"),
    }
}

/// **Test 2: 不抛 → 走 normal outgoing,boundary 被 bypass**
///
/// 同样的 flow shape, 但 task 不 throw。Traverse 应该:
/// 1. visit s → next = task
/// 2. visit task → no throw, returns Continue
/// 3. step() 看到 thrown_error = None → 走 next_node = e_ok → normal_end
///
/// Boundary 永远不被 visit。
#[test]
fn attached_boundary_bypassed_when_activity_does_not_throw() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_ok</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:boundaryEvent id="b" attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e_err</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="normal_end"/>
        <bpmn:endEvent id="error_end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="task"/>
        <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="normal_end"/>
        <bpmn:sequenceFlow id="e_err" sourceRef="b" targetRef="error_end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    // throw_for = "__none__" 这样 ThrowActionExecutor 不会真 throw
    let reg = registry_with_throw("__none__", "boom");
    let outcome = traverse(def, FlowContext::new("r"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(
                t.ctx().current_node_id.as_deref(),
                Some("normal_end"),
                "without a thrown error, flow should follow the activity's normal outgoing"
            );
        }
        other => panic!("expected Completed at normal_end, got {other:?}"),
    }
}

/// **Test 3: thrown ref 不匹配任何 attached boundary → fall through**
///
/// 抛 `"wrong_ref"`,但 boundary 的 errorRef 是 `"boom"`。没有匹配,
/// step() 记录 warn 但继续走 normal outgoing → normal_end。
///
/// V5.28 P1 v0 设计选择:不匹配时**不**抛 error,而是 warn 然后
/// fall through。生产用时可能升级成"throw FlowError::BoundaryNotFound",
/// 但 v0 保持宽容 — 避免一个 unrelated throw 让整个 flow 崩。
#[test]
fn thrown_ref_with_no_matching_boundary_falls_through_with_warn() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_ok</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:boundaryEvent id="b" attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e_err</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="normal_end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="task"/>
        <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="normal_end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let reg = registry_with_throw("task", "wrong_ref");
    let outcome = traverse(def, FlowContext::new("r"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(
                t.ctx().current_node_id.as_deref(),
                Some("normal_end"),
                "no matching boundary → activity's normal outgoing should win"
            );
        }
        other => panic!("expected Completed at normal_end, got {other:?}"),
    }
}

/// **Test 4: 一个 activity 上挂多个 boundary → first matching wins**
///
/// task 抛 `"boom"`,flow 有两个 attached boundary:
/// - `b1` errorRef="other"
/// - `b2` errorRef="boom"
///
/// 文档顺序:b1 在前。b1 不匹配,b2 匹配 → route to `e_err2` → err2_end。
#[test]
fn multiple_attached_boundaries_first_match_wins() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_ok</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:boundaryEvent id="b1" attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="other">
          <bpmn:outgoing>e_err1</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:boundaryEvent id="b2" attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e_err2</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="normal_end"/>
        <bpmn:endEvent id="err1_end"/>
        <bpmn:endEvent id="err2_end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="task"/>
        <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="normal_end"/>
        <bpmn:sequenceFlow id="e_err1" sourceRef="b1" targetRef="err1_end"/>
        <bpmn:sequenceFlow id="e_err2" sourceRef="b2" targetRef="err2_end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let reg = registry_with_throw("task", "boom");
    let outcome = traverse(def, FlowContext::new("r"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(
                t.ctx().current_node_id.as_deref(),
                Some("err2_end"),
                "b2 (errorRef=boom) should match; b1 (errorRef=other) doesn't"
            );
        }
        other => panic!("expected Completed at err2_end, got {other:?}"),
    }
}

/// **Test 5: boundary 没 attachedToRef → sibling-style (V5.27 back-compat)**
///
/// boundary 既不在 sequenceFlow 里,也没有 attachedToRef。Parser
/// 不应该把它加到 attached_boundaries map。如果 flow 真去 visit 它
/// (这里我们不让它 visited,因为不在 sequenceFlow 里),executor
/// 仍然处理。
///
/// 这个 test 验证 parser 不会因为 attached_to = None 把 boundary
/// 错加到 map 里 — 我们通过 `def.attached_boundaries` 直接 assert。
#[test]
fn unattached_boundary_does_not_appear_in_attached_boundaries_map() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:boundaryEvent id="b" ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e_b</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    assert!(
        def.attached_boundaries.is_empty(),
        "boundary without attachedToRef should not be in attached_boundaries; got {:?}",
        def.attached_boundaries
    );
}

/// **Test 6: parser 正确构建 attached_boundaries reverse-lookup**
///
/// 一个 task 挂两个 boundary。Parser 应该把 task 映射到 `[b1, b2]`。
/// 这是 V5.28 P1.1 (parser) 的契约 — test 在 P1.3 集成测试层
/// assert。
#[test]
fn parser_builds_attached_boundaries_with_multiple_boundaries() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_ok</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:boundaryEvent id="b1" attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="other">
          <bpmn:outgoing>e1_b</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:boundaryEvent id="b2" attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e2_b</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="task"/>
        <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="end"/>
        <bpmn:sequenceFlow id="e1_b" sourceRef="b1" targetRef="end"/>
        <bpmn:sequenceFlow id="e2_b" sourceRef="b2" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let attached = def.attached_boundaries.get("task").expect("task has attached boundaries");
    assert_eq!(attached, &vec!["b1".to_string(), "b2".to_string()]);
    // 顺序 = 文档顺序
    assert_eq!(attached[0], "b1");
    assert_eq!(attached[1], "b2");
}

/// **Test 7: 默认 errorRef = "error"**
///
/// boundary 没设 `ruleforge:errorRef`,默认是 `"error"`(跟
/// `BoundaryEventKind::from_attrs` 的 default 一致)。activity
/// 抛 `"error"`,应该匹配。
#[test]
fn attached_boundary_default_error_ref_matches_thrown_error() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_ok</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:boundaryEvent id="b" attachedToRef="task"
                            ruleforge:eventType="error">
          <bpmn:outgoing>e_err</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="normal_end"/>
        <bpmn:endEvent id="error_end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="task"/>
        <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="normal_end"/>
        <bpmn:sequenceFlow id="e_err" sourceRef="b" targetRef="error_end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let reg = registry_with_throw("task", "error");
    let outcome = traverse(def, FlowContext::new("r"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(
                t.ctx().current_node_id.as_deref(),
                Some("error_end"),
                "default errorRef should be 'error' and match thrown 'error'"
            );
        }
        other => panic!("expected Completed at error_end, got {other:?}"),
    }
}

/// **Test 8: thrown_error 在 step() 中被清空**
///
/// 一个 task 抛 error,被 boundary 接走。如果 task 之后还有
/// 另一个 serviceTask(在 boundary 的 outgoing 上),它不应该
/// 看到旧的 thrown_error — step() 已经 `.take()` 了。
///
/// Flow: s → task (throws) → boundary → mid_task → end
///       task ──► normal_end (skip via boundary)
///       boundary → mid_task → end
///
/// After boundary routes to mid_task, thrown_error should be None.
/// mid_task doesn't throw (ThrowActionExecutor matched on "task"
/// not "mid_task"),so it goes to end normally.
#[test]
fn thrown_error_is_cleared_after_boundary_consumes_it() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_ok</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:boundaryEvent id="b" attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e_err</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:serviceTask id="mid_task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_mid</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:endEvent id="end"/>
        <bpmn:endEvent id="normal_end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="task"/>
        <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="normal_end"/>
        <bpmn:sequenceFlow id="e_err" sourceRef="b" targetRef="mid_task"/>
        <bpmn:sequenceFlow id="e_mid" sourceRef="mid_task" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let reg = registry_with_throw("task", "boom");
    let outcome = traverse(def, FlowContext::new("r"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(
                t.ctx().current_node_id.as_deref(),
                Some("end"),
                "after boundary consumes thrown_error, mid_task should run normally → end"
            );
            assert!(
                t.ctx().thrown_error.is_none(),
                "thrown_error should be cleared after boundary routing; still {:?}",
                t.ctx().thrown_error
            );
        }
        other => panic!("expected Completed at end, got {other:?}"),
    }
}

/// **Test 9: Sibling-style boundary 仍然 suspend(V5.27 back-compat)**
///
/// 验证 V5.27 风格(boundary 在 sequenceFlow 上 + attachedToRef)
/// 不被 V5.28 P1 破坏:boundary 被 visited,Suspended(不是
/// 直接路由)。Flow:
///   s → be (suspended as error:boom) → end
///   be attachedToRef="s" (但 startEvent 不抛,所以这条无意义)
#[test]
fn sibling_style_boundary_still_suspends_when_visited_via_sequence_flow() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:boundaryEvent id="be" attachedToRef="s"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="be"/>
        <bpmn:sequenceFlow id="e2" sourceRef="be" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let outcome = traverse(def, FlowContext::new("r"), base_registry());
    match outcome {
        TraverseOutcome::Suspended(_, info) => {
            // s 抛 nothing(throw_exec 不匹配 "s" because throws_for="task")
            // 然后 visit be → BoundaryEventExecutor suspends with error:boom
            assert_eq!(info.wait_ref, "error:boom");
        }
        other => panic!("expected Suspended (sibling-style boundary still works), got {other:?}"),
    }
}

/// **Test 10: parser 接受 namespaced `bpmn:attachedToRef`**
///
/// BPMN 2.0 标准里 `attachedToRef` 是 BPMN-core attribute
/// (namespace = BPMN 20100524 MODEL)。我们的 parser 用
/// `el.attribute((NS_BPMN, "attachedToRef"))` 拿它 — 这个
/// test 验证 namespaced 形式也能 parse 进 attached_boundaries。
#[test]
fn parser_handles_namespaced_bpmn_attached_to_ref() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_ok</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:boundaryEvent id="b" bpmn:attachedToRef="task"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>e_err</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="task"/>
        <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="end"/>
        <bpmn:sequenceFlow id="e_err" sourceRef="b" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let attached = def.attached_boundaries.get("task");
    assert!(
        attached.is_some(),
        "namespaced bpmn:attachedToRef should still populate attached_boundaries"
    );
    assert_eq!(attached.unwrap(), &vec!["b".to_string()]);
}

/// **Test 11: 没有 attached boundary 的 activity throw → fall through**
///
/// activity 抛 error,但 `attached_boundaries[activity_id]` 是 None
/// (没有 attached boundary)。step() 看到 None 就 fall through。
#[test]
fn activity_with_no_attached_boundary_throws_falls_through() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="task" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e_ok</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:endEvent id="normal_end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="task"/>
        <bpmn:sequenceFlow id="e_ok" sourceRef="task" targetRef="normal_end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let reg = registry_with_throw("task", "boom");
    let outcome = traverse(def, FlowContext::new("r"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(
                t.ctx().current_node_id.as_deref(),
                Some("normal_end"),
                "no attached boundary → throw is silently dropped (warn logged), flow continues normally"
            );
        }
        other => panic!("expected Completed at normal_end, got {other:?}"),
    }
}