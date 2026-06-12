package com.ruleforge.decision.flow.engine;

import com.ruleforge.decision.flow.ir.FlowDefinition;

import java.util.Map;

/**
 * V5.39 B0 — 决策流"有状态长生命周期"接口。
 *
 * <p>start / resume 双阶段契约:
 * <ul>
 *   <li>{@link #start(FlowDefinition, Map)}:启动一个有状态决策流,返回 {@code flowRunId};引擎内部
 *       会写一行 {@code nd_decision_flow_state}(PENDING → RUNNING → …);遇到 UserTask / Receive /
 *       Multi-Instance / 定时器时,会异步挂起,状态变 {@code WAITING_CALLBACK} / {@code PENDING_ASYNC},
 *       方法照常返回(调用方拿 flowRunId 轮询或监听回调)</li>
 *   <li>{@link #resume(String, String, String, Map)}:从指定节点继续已挂起的流;常由 MessageBus 回调
 *       (Receive Task 收到消息)/ 人工决策提交 (User Task) / 定时器轮询 触发</li>
 * </ul>
 *
 * <p>典型用途:生产 decision API / 异步工作流引擎接入。
 *
 * <p>与 {@link StatelessDecisionExecutor} 的区别:
 * <ul>
 *   <li>Stateful:长生命周期,start 返回 flowRunId,异步挂起,DB 持久化</li>
 *   <li>Stateless:一次性求值,无 flowRunId,无 DB 写</li>
 * </ul>
 *
 * <p>实现方需要维护:
 * <ul>
 *   <li>{@code nd_decision_flow_state} 表的 PENDING / RUNNING / WAITING_CALLBACK / COMPLETED / FAILED 状态机</li>
 *   <li>Join 到达计数(在 DB 的 rowVars JSON 里)</li>
 *   <li>MessageBus 订阅生命周期(SUSPEND 时注册,COMPLETED / FAIL 时关)</li>
 *   <li>flowRunId 全局唯一(UUID)</li>
 * </ul>
 */
public interface StatefulDecisionFlow {

    /**
     * 启动一个有状态决策流。
     *
     * <p>返回的 flowRunId 可用于:
     * <ul>
     *   <li>后续 {@link #resume(String, String, String, Map)} 从挂起点继续</li>
     *   <li>查询 {@code nd_decision_flow_state} 行状态</li>
     *   <li>关联到业务系统侧的 orderNo / 申请单号</li>
     * </ul>
     *
     * @param def  IR 流程定义;若未注册会抛 FlowExecutionException
     * @param vars 业务输入 vars(只读;实现方应做防御性复制)
     * @return 全局唯一 flowRunId(UUID 字符串)
     * @throws com.ruleforge.decision.exception.FlowExecutionException
     *         启动失败(def 不存在 / 节点求值失败 / 业务异常)
     */
    String start(FlowDefinition def, Map<String, Object> vars);

    /**
     * 从挂起点恢复一个已存在的有状态流。
     *
     * <p>调用方需要负责:
     * <ul>
     *   <li>提供正确的 {@code currentNodeId}(对应 {@code nd_decision_flow_state.current_node_id})</li>
     *   <li>把回调载荷(decision / 消息内容 / 定时器信号)塞进 {@code vars} map</li>
     *   <li>同一 flowRunId + nodeId 的重复 resume 是幂等的(引擎会基于 rowVars 重新 load ctx)</li>
     * </ul>
     *
     * <p>注:resume 需要 {@code flowId} 才能从 repo 加载 def(无法仅凭 flowRunId 推出 flowId,
     * 因为 V5.33+ 的 flowRunId 是 UUID,与 flowId 解耦)。调用方在调 resume 之前需要
     * 先从 DB 加载 state,拿到 {@code flowId} 字段再传入。
     *
     * @param flowRunId     要恢复的 flowRunId(必须已存在)
     * @param flowId        state 行中的 flowId(用于 repo.getOrLoad)
     * @param currentNodeId 挂起时的当前节点 id(从 DB 读)
     * @param vars          恢复时合并的 vars(写入到当前 token.vars)
     * @throws com.ruleforge.decision.exception.FlowExecutionException
     *         恢复失败(flowRunId 不存在 / 节点已走完 / 业务异常)
     */
    void resume(String flowRunId, String flowId, String currentNodeId, Map<String, Object> vars);
}
