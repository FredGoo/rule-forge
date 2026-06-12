package com.ruleforge.decision.flow.engine;

import com.ruleforge.decision.flow.ir.FlowDefinition;

import java.util.Map;

/**
 * V5.39 B0 — 决策流"无状态评估"接口。
 *
 * <p>纯函数式契约:
 * <ul>
 *   <li>输入:不可变的 {@link FlowDefinition} + 业务 vars map</li>
 *   <li>输出:执行后 vars map(包含写入的 output / 决策结果)</li>
 *   <li>无副作用:不写 {@code nd_decision_flow_state} 表 / 不订阅 MessageBus / 不持久化任何东西</li>
 *   <li>无挂起:遇到 UserTask / Receive Task / Multi-Instance 时直接抛异常(因为没有 flowRunId 可以 resume)</li>
 *   <li>可重入:同一 def 多次 execute 互不干扰</li>
 * </ul>
 *
 * <p>典型用途:shadow 执行 / 批测 dry-run / 纯函数决策(决策表/规则集)/ 单元测试。
 *
 * <p>与 {@link StatefulDecisionFlow} 的区别:
 * <ul>
 *   <li>Stateful:长生命周期,start 返回 flowRunId,异步挂起,DB 持久化</li>
 *   <li>Stateless:一次性求值,无 flowRunId,无 DB 写</li>
 * </ul>
 *
 * <p>实现方需在内部:
 * <ol>
 *   <li>构造临时 {@link FlowContext}(identity 内的 flowRunId 是临时的,无需全局唯一)</li>
 *   <li>调 {@code FlowNodeRunner.traverse(def, ctx, startNodeId)} 跑到 EndEvent</li>
 *   <li>返回 {@link BusinessVars#getVars()} map 的副本</li>
 * </ol>
 */
public interface StatelessDecisionExecutor {

    /**
     * 对给定 def 跑一次完整决策流,返回结果 vars。
     *
     * <p>约束:vars 视为只读输入;实现方可以修改内部 map 但调用方传入的引用应保持不变(防御性复制)。
     * 也不应在结果 map 中返回实现方持有的内部引用,以防调用方再修改后污染。
     *
     * @param def  不可变的 IR 流程定义
     * @param vars 业务输入 vars(只读,实现方应做防御性复制)
     * @return 决策结果 vars(输出变量、_firedRules 等元数据、outputModel 字段)
     * @throws com.ruleforge.decision.exception.FlowExecutionException
     *         业务异常 — def 解析失败 / 节点求值失败 / 遇到异步挂起节点(因为无 flowRunId 不能 resume)
     */
    Map<String, Object> execute(FlowDefinition def, Map<String, Object> vars);
}
