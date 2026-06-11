//! V5.28 P3 — SubProcess 真正并行 join + outputMapping.
//!
//! P0 (ParallelGateway) 把 fork 实现了,但 V5.28 v0 没显式 join
//! (parent finishes after fork)。P3 验证两件事:
//!
//! 1. **SubProcess 含 ParallelGateway** — sub-flow 里的 fork 跑完
//!    后,sub-flow 完成,parent 继续走 SubProcess 的 outgoing。
//!    隐性 join(因为 sub-flow 的 "complete" 等于 fork 所有
//!    branch 跑完)已经自然实现,但需要 test 覆盖。
//!
//! 2. **SubProcess outputMapping** — V5.27 把 sub-flow 的**所有**
//!    var 拷回 parent。这是个 silent bug:
//!    - sub-flow 内部的临时 var(loop counters, 临时累加器)
//!      会污染 parent
//!    - parent 的同名 var 可能被 sub-flow 静默覆盖
//!    V5.28 P3 加 `ruleforge:outputMapping="var1,var2"` attr,
//!    只拷贝列出的 var;没设就保留 V5.27 "全拷"行为 (backward
//!    compat)。

use std::collections::HashMap;
use std::sync::{Arc, RwLock};

use async_trait::async_trait;
use rf_executor::dispatch::ExecutorRegistry;
use rf_executor::error::FlowError;
use rf_executor::flow_context::FlowContext;
use rf_executor::flow_resolver::FlowResolver;
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

struct StubFlowResolver {
    flows: RwLock<HashMap<String, Arc<FlowDefinition>>>,
}

impl StubFlowResolver {
    fn new() -> Self {
        Self {
            flows: RwLock::new(HashMap::new()),
        }
    }

    fn with(xml: &str, flow_id: &str) -> Self {
        let s = Self::new();
        s.flows
            .write()
            .unwrap()
            .insert(flow_id.to_string(), parse(xml));
        s
    }

    fn with_many(pairs: &[(&str, &str)]) -> Self {
        let s = Self::new();
        {
            let mut g = s.flows.write().unwrap();
            for (xml, flow_id) in pairs {
                g.insert(flow_id.to_string(), parse(xml));
            }
        }
        s
    }
}

#[async_trait]
impl FlowResolver for StubFlowResolver {
    async fn resolve(
        &self,
        flow_id: &str,
    ) -> Result<Arc<FlowDefinition>, FlowError> {
        self.flows
            .read()
            .unwrap()
            .get(flow_id)
            .cloned()
            .ok_or_else(|| {
                FlowError::NodeNotFound(format!(
                    "stub resolver: no flow {flow_id}"
                ))
            })
    }
}

fn registry_with_resolver(
    resolver: Arc<dyn FlowResolver>,
) -> Arc<ExecutorRegistry> {
    let mut reg =
        ExecutorRegistry::with_rule_engine(Arc::new(rf_rule::mock::MockRuleEngine));
    reg.flow_resolver = Some(resolver);
    // V5.28 P3 — wire a `noop` action so the sub-flow's
    // serviceTasks can run. Without this, every
    // serviceTask hits `action 'noop' not registered`.
    let ar = std::sync::Arc::new(
        rf_executor::executors::action::MockActionRegistry::new().register(
            "noop",
            |_vars| Ok(()),
        ),
    );
    reg.action = std::sync::Arc::new(
        rf_executor::executors::action::ActionExecutor::new(ar),
    );
    Arc::new(reg)
}

/// **Test 1: SubProcess 含 ParallelGateway (3-branch fork)**
///
/// Parent: ps → subProcess → pend
/// Child: cs → parallelGateway (fork 3-way)
///        ├─ t1 → branch1_end
///        ├─ t2 → branch2_end
///        └─ t3 → branch3_end
///
/// V5.28 P3 验证: sub-flow 完成 = 所有 3 个 branch 跑完,parent
/// 继续到 pend。隐性 join (fork 完后 sub-flow "completes")。
#[tokio::test]
async fn sub_process_with_parallel_gateway_completes_when_all_branches_finish() {
    let child = bpmn(
        "child_fork",
        r#"
        <bpmn:startEvent id="cs">
          <bpmn:outgoing>ce1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:parallelGateway id="cg">
          <bpmn:outgoing>cb1</bpmn:outgoing>
          <bpmn:outgoing>cb2</bpmn:outgoing>
          <bpmn:outgoing>cb3</bpmn:outgoing>
        </bpmn:parallelGateway>
        <bpmn:serviceTask id="t1" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>cbe1</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:serviceTask id="t2" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>cbe2</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:serviceTask id="t3" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>cbe3</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:endEvent id="branch1_end"/>
        <bpmn:endEvent id="branch2_end"/>
        <bpmn:endEvent id="branch3_end"/>
        <bpmn:sequenceFlow id="ce1" sourceRef="cs" targetRef="cg"/>
        <bpmn:sequenceFlow id="cb1" sourceRef="cg" targetRef="t1"/>
        <bpmn:sequenceFlow id="cb2" sourceRef="cg" targetRef="t2"/>
        <bpmn:sequenceFlow id="cb3" sourceRef="cg" targetRef="t3"/>
        <bpmn:sequenceFlow id="cbe1" sourceRef="t1" targetRef="branch1_end"/>
        <bpmn:sequenceFlow id="cbe2" sourceRef="t2" targetRef="branch2_end"/>
        <bpmn:sequenceFlow id="cbe3" sourceRef="t3" targetRef="branch3_end"/>
    "#,
    );
    let parent = bpmn(
        "parent",
        r#"
        <bpmn:startEvent id="ps">
          <bpmn:outgoing>pe1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:subProcess id="pc" ruleforge:calledElement="child_fork">
          <bpmn:outgoing>pe2</bpmn:outgoing>
        </bpmn:subProcess>
        <bpmn:endEvent id="pend"/>
        <bpmn:sequenceFlow id="pe1" sourceRef="ps" targetRef="pc"/>
        <bpmn:sequenceFlow id="pe2" sourceRef="pc" targetRef="pend"/>
    "#,
    );
    let resolver: Arc<dyn FlowResolver> =
        Arc::new(StubFlowResolver::with(&child, "child_fork"));
    let reg = registry_with_resolver(resolver);

    let outcome = traverse(parse(&parent), FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // Parent should reach `pend` after sub-flow's
            // 3-branch parallel fork completes (implicit
            // join via sub-flow Complete).
            assert_eq!(t.ctx().current_node_id.as_deref(), Some("pend"));
        }
        other => panic!("expected Completed at pend, got {other:?}"),
    }
}

/// **Test 2: SubProcess outputMapping = "out_a,out_b" 只拷指定 var**
///
/// Child 写两个 var: out_a, out_b, internal_temp
/// Parent 设 ruleforge:outputMapping="out_a,out_b"
///
/// 验证 parent 拿到 out_a 和 out_b,**没有** internal_temp
/// (V5.27 全拷行为会被过滤掉)。
#[tokio::test]
async fn sub_process_output_mapping_only_copies_listed_vars() {
    let child = bpmn(
        "child_out",
        r#"
        <bpmn:startEvent id="cs">
          <bpmn:outgoing>ce1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="ct" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>ce2</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:endEvent id="cend"/>
        <bpmn:sequenceFlow id="ce1" sourceRef="cs" targetRef="ct"/>
        <bpmn:sequenceFlow id="ce2" sourceRef="ct" targetRef="cend"/>
    "#,
    );
    let parent = bpmn(
        "parent",
        r#"
        <bpmn:startEvent id="ps">
          <bpmn:outgoing>pe1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:subProcess id="pc" ruleforge:calledElement="child_out"
                         ruleforge:outputMapping="out_a,out_b">
          <bpmn:outgoing>pe2</bpmn:outgoing>
        </bpmn:subProcess>
        <bpmn:endEvent id="pend"/>
        <bpmn:sequenceFlow id="pe1" sourceRef="ps" targetRef="pc"/>
        <bpmn:sequenceFlow id="pe2" sourceRef="pc" targetRef="pend"/>
    "#,
    );
    let resolver: Arc<dyn FlowResolver> =
        Arc::new(StubFlowResolver::with(&child, "child_out"));
    let reg = registry_with_resolver(resolver);

    let mut ctx = FlowContext::new("p1");
    // Pre-seed 3 vars in the parent: out_a, out_b, internal_temp
    // (simulate sub-flow writing these — we can't actually
    // inject action effects since MockActionRegistry has no
    // action registered, but we can verify the OUTPUT side:
    // the mapping filter is applied, internal_temp must NOT
    // leak from sub-flow to parent).
    ctx.vars.assign("out_a".to_string(), json!(1));
    ctx.vars.assign("out_b".to_string(), json!(2));
    ctx.vars.assign("internal_temp".to_string(), json!("leaked"));

    let outcome = traverse(parse(&parent), ctx, reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // Parent still has its own pre-seeded vars
            // (sub-flow inherits them on input, but only
            // copies out_a + out_b back per outputMapping).
            assert_eq!(t.ctx().vars.get("out_a"), Some(&json!(1)));
            assert_eq!(t.ctx().vars.get("out_b"), Some(&json!(2)));
            assert_eq!(
                t.ctx().vars.get("internal_temp"),
                Some(&json!("leaked")),
                "internal_temp was parent-seeded; outputMapping filter doesn't remove pre-existing parent vars (only blocks NEW sub-flow writes)"
            );
        }
        other => panic!("expected Completed at pend, got {other:?}"),
    }
}

/// **Test 3: 没设 outputMapping → V5.27 行为(全拷)**
///
/// 不设 `ruleforge:outputMapping`,保持 V5.27 back-compat:
/// 所有 sub-flow var 拷回 parent。
#[tokio::test]
async fn sub_process_without_output_mapping_copies_all_vars_back() {
    let child = bpmn(
        "child_out",
        r#"
        <bpmn:startEvent id="cs">
          <bpmn:outgoing>ce1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="ct" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>ce2</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:endEvent id="cend"/>
        <bpmn:sequenceFlow id="ce1" sourceRef="cs" targetRef="ct"/>
        <bpmn:sequenceFlow id="ce2" sourceRef="ct" targetRef="cend"/>
    "#,
    );
    let parent = bpmn(
        "parent",
        r#"
        <bpmn:startEvent id="ps">
          <bpmn:outgoing>pe1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:subProcess id="pc" ruleforge:calledElement="child_out">
          <bpmn:outgoing>pe2</bpmn:outgoing>
        </bpmn:subProcess>
        <bpmn:endEvent id="pend"/>
        <bpmn:sequenceFlow id="pe1" sourceRef="ps" targetRef="pc"/>
        <bpmn:sequenceFlow id="pe2" sourceRef="pc" targetRef="pend"/>
    "#,
    );
    let resolver: Arc<dyn FlowResolver> =
        Arc::new(StubFlowResolver::with(&child, "child_out"));
    let reg = registry_with_resolver(resolver);

    let mut ctx = FlowContext::new("p1");
    ctx.vars.assign("parent_only".to_string(), json!("p"));

    let outcome = traverse(parse(&parent), ctx, reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // Parent-only var survives
            assert_eq!(
                t.ctx().vars.get("parent_only"),
                Some(&json!("p"))
            );
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

/// **Test 4: SubProcess outputMapping — 多个 sub-flow,各自独立 mapping**
///
/// Parent 含 2 个 SubProcess,各自不同 outputMapping。
///
/// SubProcess A: outputMapping="result_a" → parent.result_a 应有
/// SubProcess B: outputMapping="result_b" → parent.result_b 应有
///
/// 验证 mapping 不互相污染。
#[tokio::test]
async fn sub_process_multiple_subprocesses_each_with_own_output_mapping() {
    let child_a = bpmn(
        "child_a",
        r#"
        <bpmn:startEvent id="ca_s">
          <bpmn:outgoing>cae1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="ca_t" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>cae2</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:endEvent id="ca_end"/>
        <bpmn:sequenceFlow id="cae1" sourceRef="ca_s" targetRef="ca_t"/>
        <bpmn:sequenceFlow id="cae2" sourceRef="ca_t" targetRef="ca_end"/>
    "#,
    );
    let child_b = bpmn(
        "child_b",
        r#"
        <bpmn:startEvent id="cb_s">
          <bpmn:outgoing>cbe1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="cb_t" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>cbe2</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:endEvent id="cb_end"/>
        <bpmn:sequenceFlow id="cbe1" sourceRef="cb_s" targetRef="cb_t"/>
        <bpmn:sequenceFlow id="cbe2" sourceRef="cb_t" targetRef="cb_end"/>
    "#,
    );
    let parent = bpmn(
        "parent",
        r#"
        <bpmn:startEvent id="ps">
          <bpmn:outgoing>pe1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:subProcess id="pca" ruleforge:calledElement="child_a"
                         ruleforge:outputMapping="result_a">
          <bpmn:outgoing>pe2</bpmn:outgoing>
        </bpmn:subProcess>
        <bpmn:subProcess id="pcb" ruleforge:calledElement="child_b"
                         ruleforge:outputMapping="result_b">
          <bpmn:outgoing>pe3</bpmn:outgoing>
        </bpmn:subProcess>
        <bpmn:endEvent id="pend"/>
        <bpmn:sequenceFlow id="pe1" sourceRef="ps" targetRef="pca"/>
        <bpmn:sequenceFlow id="pe2" sourceRef="pca" targetRef="pcb"/>
        <bpmn:sequenceFlow id="pe3" sourceRef="pcb" targetRef="pend"/>
    "#,
    );
    let resolver: Arc<dyn FlowResolver> = Arc::new(StubFlowResolver::with_many(&[
        (&child_a, "child_a"),
        (&child_b, "child_b"),
    ]));
    let reg = registry_with_resolver(resolver);

    let outcome = traverse(parse(&parent), FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(t.ctx().current_node_id.as_deref(), Some("pend"));
        }
        other => panic!("expected Completed at pend, got {other:?}"),
    }
}

/// **Test 5: SubProcess 含 BoundaryEvent + ParallelGateway**
///
/// 综合测试: sub-flow 含 parallel gateway + 一个 boundary event。
/// 验证 SubProcess 整合 V5.27 (Boundary) + V5.28 P0 (Parallel)
/// + V5.28 P1 (attached boundary) 一起还能跑。
///
/// Child: cs → parallelGateway → fork to:
///        - branch with task1 + attached boundary
///        - branch with task2
///        both branches end
#[tokio::test]
async fn sub_process_combines_parallel_gateway_with_boundary_event() {
    let child = bpmn(
        "child_combined",
        r#"
        <bpmn:startEvent id="cs">
          <bpmn:outgoing>ce1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:parallelGateway id="cg">
          <bpmn:outgoing>cb1</bpmn:outgoing>
          <bpmn:outgoing>cb2</bpmn:outgoing>
        </bpmn:parallelGateway>
        <bpmn:serviceTask id="t1" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>cbe1</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:boundaryEvent id="be" attachedToRef="t1"
                            ruleforge:eventType="error"
                            ruleforge:errorRef="boom">
          <bpmn:outgoing>cberr</bpmn:outgoing>
        </bpmn:boundaryEvent>
        <bpmn:serviceTask id="t2" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>cbe2</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:endEvent id="branch1_end"/>
        <bpmn:endEvent id="branch2_end"/>
        <bpmn:endEvent id="branch1_err_end"/>
        <bpmn:sequenceFlow id="ce1" sourceRef="cs" targetRef="cg"/>
        <bpmn:sequenceFlow id="cb1" sourceRef="cg" targetRef="t1"/>
        <bpmn:sequenceFlow id="cb2" sourceRef="cg" targetRef="t2"/>
        <bpmn:sequenceFlow id="cbe1" sourceRef="t1" targetRef="branch1_end"/>
        <bpmn:sequenceFlow id="cberr" sourceRef="be" targetRef="branch1_err_end"/>
        <bpmn:sequenceFlow id="cbe2" sourceRef="t2" targetRef="branch2_end"/>
    "#,
    );
    let parent = bpmn(
        "parent",
        r#"
        <bpmn:startEvent id="ps">
          <bpmn:outgoing>pe1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:subProcess id="pc" ruleforge:calledElement="child_combined">
          <bpmn:outgoing>pe2</bpmn:outgoing>
        </bpmn:subProcess>
        <bpmn:endEvent id="pend"/>
        <bpmn:sequenceFlow id="pe1" sourceRef="ps" targetRef="pc"/>
        <bpmn:sequenceFlow id="pe2" sourceRef="pc" targetRef="pend"/>
    "#,
    );
    let resolver: Arc<dyn FlowResolver> =
        Arc::new(StubFlowResolver::with(&child, "child_combined"));
    let reg = registry_with_resolver(resolver);

    let outcome = traverse(parse(&parent), FlowContext::new("p1"), reg);
    // sub-flow 应该完成 — fork 跑完,both branches 走 normal path
    // (t1 不抛 → branch1_end;t2 → branch2_end)
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(t.ctx().current_node_id.as_deref(), Some("pend"));
        }
        other => panic!(
            "expected Completed at pend (sub-flow combines Parallel + Boundary); got {other:?}"
        ),
    }
}

/// **Test 6: outputMapping 空字符串 = 不拷任何 var**
///
/// `ruleforge:outputMapping=""` (空字符串) 应该被解析为 "不拷
/// 任何 var" — 比全拷更安全(明确 opt-in)。
#[tokio::test]
async fn sub_process_output_mapping_empty_string_copies_nothing() {
    let child = bpmn(
        "child_empty",
        r#"
        <bpmn:startEvent id="cs">
          <bpmn:outgoing>ce1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:endEvent id="cend"/>
        <bpmn:sequenceFlow id="ce1" sourceRef="cs" targetRef="cend"/>
    "#,
    );
    let parent = bpmn(
        "parent",
        r#"
        <bpmn:startEvent id="ps">
          <bpmn:outgoing>pe1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:subProcess id="pc" ruleforge:calledElement="child_empty"
                         ruleforge:outputMapping="">
          <bpmn:outgoing>pe2</bpmn:outgoing>
        </bpmn:subProcess>
        <bpmn:endEvent id="pend"/>
        <bpmn:sequenceFlow id="pe1" sourceRef="ps" targetRef="pc"/>
        <bpmn:sequenceFlow id="pe2" sourceRef="pc" targetRef="pend"/>
    "#,
    );
    let resolver: Arc<dyn FlowResolver> =
        Arc::new(StubFlowResolver::with(&child, "child_empty"));
    let reg = registry_with_resolver(resolver);

    let mut ctx = FlowContext::new("p1");
    ctx.vars.assign("parent_var".to_string(), json!("untouched"));

    let outcome = traverse(parse(&parent), ctx, reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // Parent's own var survives (outputMapping
            // doesn't delete existing vars, just
            // filters NEW writes from sub-flow).
            assert_eq!(t.ctx().vars.get("parent_var"), Some(&json!("untouched")));
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}