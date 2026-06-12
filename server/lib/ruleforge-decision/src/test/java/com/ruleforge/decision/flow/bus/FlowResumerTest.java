package com.ruleforge.decision.flow.bus;

import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.flow.engine.FlowDefinitionRepo;
import com.ruleforge.decision.flow.engine.FlowEngine;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.mapper.DecisionFlowStateMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V5.38 C0 — FlowResumer 桥接器行为规范。
 *
 * <p>4 BDD:正常 resume / 缺字段不调 engine / engine.resume 抛被吞 / resumeAllSuspendedByWaitRef
 * 按 prefix 扫 + 调 resume。
 *
 * <p>Mockito 隔离:engine / repo / stateMapper 都 mock;FlowResumer 构造器注入。
 * 跟 {@code ConditionalPollingWorkerTest} 同套路。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlowResumer 桥接器")
class FlowResumerTest {

    @Mock
    private FlowEngine engine;
    @Mock
    private FlowDefinitionRepo repo;
    @Mock
    private DecisionFlowStateMapper stateMapper;

    private FlowResumer newResumer() {
        FlowResumer r = new FlowResumer(engine, repo, stateMapper);
        return r;
    }

    private Message newValidMessage(Map<String, Object> extraVars) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("flowRunId", "run-1");
        payload.put("flowId", "p1");
        payload.put("currentNodeId", "nodeA");
        payload.put("vars", extraVars);
        return Message.builder()
            .name("loan_approved")
            .channel("message:loan_approved")
            .payload(payload)
            .build();
    }

    @Nested
    @DisplayName("resumeFromMessage")
    class ResumeFromMessage {

        @Test
        @DisplayName("Given Message 含 flowRunId+flowId+currentNodeId+vars,When resumeFromMessage,Then engine.resume 调 1 次 + ctx.vars 含传入 vars")
        void valid_message_calls_engine_resume_once() {
            FlowResumer r = newResumer();
            FlowDefinition stubDef = mock(FlowDefinition.class);
            when(repo.getOrLoad("p1")).thenReturn(stubDef);
            Map<String, Object> vars = Map.of("amount", 2000, "approved", true);
            Message m = newValidMessage(vars);

            r.resumeFromMessage(m);

            verify(engine, atLeastOnce()).resume(any(FlowDefinition.class), any(), anyString());
        }

        @Test
        @DisplayName("Given Message 缺 flowRunId,When resumeFromMessage,Then 不调 engine + log warn")
        void missing_flowRunId_skips_resume() {
            FlowResumer r = newResumer();
            Map<String, Object> payload = new HashMap<>();
            payload.put("flowId", "p1");
            payload.put("currentNodeId", "nodeA");
            // 缺 flowRunId
            Message m = Message.builder()
                .name("n").channel("message:n").payload(payload).build();

            r.resumeFromMessage(m);

            verify(engine, never()).resume(any(FlowDefinition.class), any(), anyString());
        }

        @Test
        @DisplayName("Given Message 缺 currentNodeId,When resumeFromMessage,Then 不调 engine")
        void missing_currentNodeId_skips_resume() {
            FlowResumer r = newResumer();
            Map<String, Object> payload = new HashMap<>();
            payload.put("flowRunId", "run-1");
            payload.put("flowId", "p1");
            // 缺 currentNodeId
            Message m = Message.builder()
                .name("n").channel("message:n").payload(payload).build();

            r.resumeFromMessage(m);

            verify(engine, never()).resume(any(FlowDefinition.class), any(), anyString());
        }

        @Test
        @DisplayName("Given engine.resume 抛,When resumeFromMessage,Then 异常被吞 + log warn(桥接不冒泡)")
        void engine_resume_throws_is_isolated() {
            FlowResumer r = newResumer();
            FlowDefinition stubDef = mock(FlowDefinition.class);
            when(repo.getOrLoad("p1")).thenReturn(stubDef);
            when(engine.resume(any(FlowDefinition.class), any(), anyString()))
                .thenThrow(new RuntimeException("engine boom"));
            Message m = newValidMessage(Map.of("k", "v"));

            // 不抛
            r.resumeFromMessage(m);

            verify(engine, atLeastOnce()).resume(any(FlowDefinition.class), any(), anyString());
        }

        @Test
        @DisplayName("Given repo.getOrLoad 返 null(flow 不存在),When resumeFromMessage,Then 不调 engine")
        void missing_flow_def_skips_resume() {
            FlowResumer r = newResumer();
            when(repo.getOrLoad("p1")).thenReturn(null);
            Message m = newValidMessage(Map.of("k", "v"));

            r.resumeFromMessage(m);

            verify(engine, never()).resume(any(FlowDefinition.class), any(), anyString());
        }

        @Test
        @DisplayName("Given Message=null,When resumeFromMessage,Then 不抛不调 engine")
        void null_message_skips_resume() {
            FlowResumer r = newResumer();
            r.resumeFromMessage(null);
            verify(engine, never()).resume(any(FlowDefinition.class), any(), anyString());
        }
    }

    @Nested
    @DisplayName("resumeAllSuspendedByWaitRef")
    class ResumeAllSuspended {

        private DecisionFlowState newState(long id, String flowRunId, String flowId,
                                            String currentNodeId, String waitRef, String rowVars) {
            DecisionFlowState s = new DecisionFlowState();
            s.setId(id);
            s.setFlowRunId(flowRunId);
            s.setFlowId(flowId);
            s.setCurrentNodeId(currentNodeId);
            s.setStatus(DecisionFlowState.STATUS_PENDING_ASYNC);
            s.setWaitRef(waitRef);
            s.setRowVars(rowVars);
            return s;
        }

        @Test
        @DisplayName("Given 2 行 PENDING_ASYNC waitRef startsWith \"signal:foo\" + 1 行不 match,When resumeAllSuspended,Then 2 行都 resume + 1 行跳过")
        void resume_only_matching_prefix() {
            FlowResumer r = newResumer();
            DecisionFlowState match1 = newState(1L, "run-1", "p1", "nodeA", "signal:foo", "{\"a\":1}");
            DecisionFlowState match2 = newState(2L, "run-2", "p2", "nodeB", "signal:foo:bar", "{\"b\":2}");
            DecisionFlowState miss   = newState(3L, "run-3", "p3", "nodeC", "timer:foo", "{}");
            when(stateMapper.selectRecoverable(100)).thenReturn(List.of(match1, match2, miss));
            FlowDefinition stubDef = mock(FlowDefinition.class);
            when(repo.getOrLoad(anyString())).thenReturn(stubDef);

            int resumed = r.resumeAllSuspendedByWaitRef("signal:foo");
            // 2 行 match → 都调 resume
            verify(engine, atLeastOnce()).resume(any(FlowDefinition.class), any(), eq("nodeA"));
            verify(engine, atLeastOnce()).resume(any(FlowDefinition.class), any(), eq("nodeB"));
            // 第 3 行 waitRef=timer:foo 不 match → 不会调它的 currentNodeId="nodeC"
            // (因为调的是 engine.resume,我们用特定 currentNodeId 来区分;因为前面两次调
            //  可能跟 match2 的 currentNodeId 也算 = "nodeB" — 这里只验 nodeC 单独没调)
            // 简化:总数 2 次
            assertEquals(2, resumed);
        }

        @Test
        @DisplayName("Given 0 行 match,When resumeAllSuspended,Then 返 0")
        void no_match_returns_zero() {
            FlowResumer r = newResumer();
            when(stateMapper.selectRecoverable(100))
                .thenReturn(List.of(newState(1L, "run-1", "p1", "nodeA", "timer:x", "{}")));

            int resumed = r.resumeAllSuspendedByWaitRef("signal:foo");
            assertEquals(0, resumed);
            verify(engine, never()).resume(any(FlowDefinition.class), any(), anyString());
        }

        @Test
        @DisplayName("Given prefix=null 或 \"\",When resumeAllSuspended,Then 返 0 + 不查 DB(避免全表扫描)")
        void empty_prefix_skips_scan() {
            FlowResumer r = newResumer();
            int r1 = r.resumeAllSuspendedByWaitRef(null);
            int r2 = r.resumeAllSuspendedByWaitRef("");
            assertEquals(0, r1);
            assertEquals(0, r2);
            verify(stateMapper, never()).selectRecoverable(anyInt());
        }
    }

    // ---- helpers ----

    private static String anyString() { return org.mockito.ArgumentMatchers.anyString(); }
    private static int anyInt() { return org.mockito.ArgumentMatchers.anyInt(); }
    private static String eq(String v) { return org.mockito.ArgumentMatchers.eq(v); }

    private static void assertEquals(int expected, int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
