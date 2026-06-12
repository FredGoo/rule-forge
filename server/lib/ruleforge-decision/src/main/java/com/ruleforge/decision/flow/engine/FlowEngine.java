package com.ruleforge.decision.flow.engine;

import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * V5.39 B0 — 决策流执行入口,同时实现 {@link StatelessDecisionExecutor} 与
 * {@link StatefulDecisionFlow} 两个角色化接口,调用方按语义选择注入。
 *
 * <p>引擎契约分层:
 * <ul>
 *   <li><b>内部/引擎直连 API</b>({@code start(flowId, ctx)} / {@code start(flowId, pool, ctx)} /
 *       {@code resume(def, ctx, nodeId)} / {@code resume(flowId, ctx, nodeId)}):
 *       engine-side 调用方(FlowResumer / FlowStateRecoveryJob / 已有 executor-app 路径)
 *       沿用旧形参,保持 compatibility。</li>
 *   <li><b>StatelessDecisionExecutor#execute</b>:
 *       一次性纯函数求值,无 DB 写 / 无 bus 订阅,shadow / test 走这条。</li>
 *   <li><b>StatefulDecisionFlow#start / #resume</b>:
 *       业务侧拿 {@code flowRunId} 做异步编排。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowEngine implements StatelessDecisionExecutor, StatefulDecisionFlow {

    private final FlowDefinitionRepo repo;
    private final FlowNodeRunner runner;

    // ============================================================================
    // 引擎直连 API — 内部调用方(FlowResumer / FlowStateRecoveryJob / 旧 controller)
    // ============================================================================

    /**
     * V5.21+: 走自建执行器跑一条单 process 决策流。
     * ctx 必须已经填好 flowRunId(via {@code FlowContext.identity().flowRunId()})。
     */
    public DecisionFlowState start(String flowId, FlowContext ctx) {
        if (ctx.identity().flowRunId() == null) {
            throw new FlowExecutionException("FlowContext.flowRunId is required");
        }
        FlowDefinition def = repo.getOrLoad(flowId);
        return runner.traverse(def, ctx, def.getStartNodeId());
    }

    /**
     * V5.37 B0 — 多池启动入口。participant → process,delegate 到 traverse。
     */
    public DecisionFlowState start(String flowId, String participantId, FlowContext ctx) {
        if (ctx.identity().flowRunId() == null) {
            throw new FlowExecutionException("FlowContext.flowRunId is required");
        }
        if (participantId == null || participantId.isBlank()) {
            throw new FlowExecutionException("participantId is required for multi-pool start");
        }
        com.ruleforge.decision.flow.ir.BpmnDefinition bpmn = repo.getOrLoadBpmn(flowId);
        com.ruleforge.decision.flow.ir.Participant p = bpmn.findParticipant(participantId)
            .orElseThrow(() -> new FlowExecutionException(
                "Participant not found: " + participantId
                + (bpmn.collaboration() != null ? " in collaboration " + bpmn.collaboration().getId() : "")));
        FlowDefinition def = bpmn.requireProcess(p.getProcessRef());
        return runner.traverse(bpmn, ctx, participantId, def.getStartNodeId());
    }

    /** V5.21+ 内部 resume — 从指定节点继续(def 已在外层加载,避免再查 repo)。 */
    public DecisionFlowState resume(FlowDefinition def, FlowContext ctx, String resumeNodeId) {
        if (resumeNodeId == null) {
            throw new FlowExecutionException("resumeNodeId is required");
        }
        return runner.traverse(def, ctx, resumeNodeId);
    }

    /** V5.21+ 内部 resume — 给定 flowId 自动 load。 */
    public DecisionFlowState resume(String flowId, FlowContext ctx, String resumeNodeId) {
        FlowDefinition def = repo.getOrLoad(flowId);
        return resume(def, ctx, resumeNodeId);
    }

    // ============================================================================
    // V5.39 B0 — StatelessDecisionExecutor
    // ============================================================================

    /**
     * 一次性纯函数求值:不写 DB / 不订阅 bus,无副作用。
     *
     * <p>实现细节:
     * <ol>
     *   <li>构造临时 {@link FlowContext}(flowRunId 是临时 UUID,不需要持久化)</li>
     *   <li>调 {@code runner.traverse(def, ctx, def.getStartNodeId())}</li>
     *   <li>如果遇到 {@link AsyncNodeSuspendException} — 抛 {@link FlowExecutionException}
     *       告知"stateless 路径不能挂起" — 这是合法的"角色越界"信号</li>
     *   <li>正常完成:返回 ctx.vars.getVars() 的副本</li>
     * </ol>
     */
    @Override
    public Map<String, Object> execute(FlowDefinition def, Map<String, Object> vars) {
        if (def == null) {
            throw new FlowExecutionException("FlowDefinition is null");
        }
        if (def.getStartNodeId() == null) {
            throw new FlowExecutionException("Flow has no start node: " + def.getProcessId());
        }
        // 防御性复制:不污染调用方传入的 vars
        BusinessVars businessVars = new BusinessVars();
        if (vars != null) {
            businessVars.getVars().putAll(vars);
        }
        FlowContext ctx = new FlowContext(
            new FlowIdentity(UUID.randomUUID().toString(), def.getProcessId(), null),
            businessVars,
            new ReteSession(),
            new SuspendRegistry()
        );
        try {
            DecisionFlowState state = runner.traverse(def, ctx, def.getStartNodeId());
            // Stateless 路径不能挂起:若 traverse 异步挂起(WAITING_CALLBACK / PENDING_ASYNC),
            // 抛业务异常告知 caller — 因为没有持久化的 flowRunId 可以 resume。
            if (state != null
                && (DecisionFlowState.STATUS_WAITING_CALLBACK.equals(state.getStatus())
                    || DecisionFlowState.STATUS_PENDING_ASYNC.equals(state.getStatus()))) {
                throw new FlowExecutionException(
                    "Stateless executor cannot suspend at node " + state.getCurrentNodeId()
                    + " (waitType=" + state.getWaitType() + "). "
                    + "Use StatefulDecisionFlow if your flow contains USER_TASK / RECEIVE_TASK / MULTI_INSTANCE.");
            }
        } catch (AsyncNodeSuspendException ex) {
            // 极少数路径:AsyncNodeSuspendException 漏到 runner 外(不该发生,保险)
            throw new FlowExecutionException(
                "Stateless executor cannot suspend at node " + ex.getCurrentNodeId()
                + " (waitType=" + ex.getWaitType() + "). "
                + "Use StatefulDecisionFlow if your flow contains USER_TASK / RECEIVE_TASK / MULTI_INSTANCE.");
        }
        // 返回 map 副本,避免调用方修改污染内部 state
        return new HashMap<>(businessVars.getVars());
    }

    // ============================================================================
    // V5.39 B0 — StatefulDecisionFlow
    // ============================================================================

    /**
     * 启动一个有状态决策流。返回 flowRunId(全局唯一 UUID),供后续 resume / 查询使用。
     *
     * <p>实现细节:
     * <ol>
     *   <li>生成 flowRunId</li>
     *   <li>构造 4 参 ctx(BusinessVars / ReteSession / SuspendRegistry)</li>
     *   <li>调 {@code start(flowId, ctx)} 让 runner 跑 PENDING → RUNNING → ...
     *       (异步挂起时 status 变 WAITING_CALLBACK / PENDING_ASYNC,traverse 正常返回)</li>
     *   <li>返回 flowRunId</li>
     * </ol>
     *
     * <p>注意:此入口只适用于单 process 启动。多池启动仍走
     * {@link #start(String, String, FlowContext)} 旧入口(V5.37 B0 既有路径),
     * 该路径在调用方先建好 FlowContext,本接口不替它。
     */
    @Override
    public String start(FlowDefinition def, Map<String, Object> vars) {
        if (def == null) {
            throw new FlowExecutionException("FlowDefinition is null");
        }
        String flowRunId = UUID.randomUUID().toString();
        BusinessVars businessVars = new BusinessVars();
        if (vars != null) {
            businessVars.getVars().putAll(vars);
        }
        FlowContext ctx = new FlowContext(
            new FlowIdentity(flowRunId, def.getProcessId(), null),
            businessVars,
            new ReteSession(),
            new SuspendRegistry()
        );
        // 直接用 caller 传入的 def,不走 repo 重新加载(避免冗余 HTTP/parse)
        runner.traverse(def, ctx, def.getStartNodeId());
        return flowRunId;
    }

    /**
     * 从挂起点恢复。调用方需要提供 flowId(flowRunId 无法单独推出 flowId)。
     *
     * <p>实现细节:
     * <ol>
     *   <li>用 flowId 加载 def</li>
     *   <li>构造 4 参 ctx(BusinessVars 持有恢复时合并的 vars)</li>
     *   <li>调 {@code resume(flowId, ctx, currentNodeId)} 让 runner 继续推进</li>
     * </ol>
     */
    @Override
    public void resume(String flowRunId, String flowId, String currentNodeId, Map<String, Object> vars) {
        if (flowRunId == null || flowRunId.isBlank()) {
            throw new FlowExecutionException("flowRunId is required");
        }
        if (flowId == null || flowId.isBlank()) {
            throw new FlowExecutionException("flowId is required");
        }
        if (currentNodeId == null || currentNodeId.isBlank()) {
            throw new FlowExecutionException("currentNodeId is required");
        }
        BusinessVars businessVars = new BusinessVars();
        if (vars != null) {
            businessVars.getVars().putAll(vars);
        }
        FlowContext ctx = new FlowContext(
            new FlowIdentity(flowRunId, flowId, null),
            businessVars,
            new ReteSession(),
            new SuspendRegistry()
        );
        // 用 flowId 从 repo 加载 def(caller 没传 def)
        FlowDefinition def = repo.getOrLoad(flowId);
        runner.traverse(def, ctx, currentNodeId);
    }
}
