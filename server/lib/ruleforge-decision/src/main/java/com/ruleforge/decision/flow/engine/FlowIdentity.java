package com.ruleforge.decision.flow.engine;

/**
 * V5.39 A1 — Flow execution 不可变身份三元组。
 *
 * <p>3 字段:
 * <ul>
 *   <li>{@code flowRunId} — 单次执行 UUID,关联 {@code nd_decision_flow_state}
 *       / {@code nd_decision_flow_log}</li>
 *   <li>{@code flowId} — 决策流定义 id(如 {@code "loan-approval-v1"}),用于从
 *       {@link FlowDefinitionRepo} 拉 definition</li>
 *   <li>{@code currentPoolId} — BPMN 协作中的当前 participantId;{@code null}
 *       表示不在 pool 上下文(单池 / 顶层)</li>
 * </ul>
 *
 * <p>immutable record — 创建后不修改。扩展 identity 字段请加新 record / 加新 ctor,
 * 不要给 FlowIdentity 加 setter。
 */
public record FlowIdentity(String flowRunId, String flowId, String currentPoolId) {

    public FlowIdentity {
        if (flowRunId == null || flowRunId.isEmpty()) {
            throw new IllegalArgumentException("flowRunId is required");
        }
        if (flowId == null || flowId.isEmpty()) {
            throw new IllegalArgumentException("flowId is required");
        }
        // currentPoolId 允许 null(单池场景)
    }
}
