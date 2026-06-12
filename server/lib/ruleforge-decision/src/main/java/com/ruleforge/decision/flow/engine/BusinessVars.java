package com.ruleforge.decision.flow.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * V5.39 A1 — Per-flowRunId 业务状态容器。
 *
 * <p>存 3 类 per-flowRunId 状态:
 * <ul>
 *   <li>{@link #getVars()} — 业务变量 map(per-flowRunId 根 vars,fork 时拍快照到子
 *       {@link Token};join 时 union-merge 回根)</li>
 *   <li>{@link #getCurrentAwaitingField()} / {@link #setCurrentAwaitingField(String)} —
 *       UserTask 等待写入的决策字段名,GatewayNodeExecutor 路由时读</li>
 *   <li>{@link #getOutputModel()} / {@link #setOutputModel(Object)} — 业务侧 POJO,
 *       {@code Object} 类型守住 executor-app 模块边界</li>
 * </ul>
 *
 * <p>per-token 状态({@code thrownError} / {@code compensationStack} /
 * {@code compensatedHandlers})不属于这里 — 那些走 {@link Token}。V5.39 之前
 * {@code FlowContext} 的 6 处 {@code currentToken} 委派 shim 全部删除。
 */
public class BusinessVars {

    private final Map<String, Object> vars = new HashMap<>();
    private String currentAwaitingField;
    private Object outputModel;

    /**
     * 返回内部 vars map(live 引用,非防御性)。caller 可直接 put/remove。
     * 序列化用 {@link #addVars(Map)} 做防御性拷贝(防止 caller 后改污染)。
     */
    public Map<String, Object> getVars() {
        return vars;
    }

    /**
     * 把 given 的 entries 拷进 vars。given 后续修改不影响本容器。
     * {@code null} given 是 no-op。
     */
    public void addVars(Map<String, Object> given) {
        if (given != null) this.vars.putAll(given);
    }

    public String getCurrentAwaitingField() {
        return currentAwaitingField;
    }

    public void setCurrentAwaitingField(String currentAwaitingField) {
        this.currentAwaitingField = currentAwaitingField;
    }

    public Object getOutputModel() {
        return outputModel;
    }

    public void setOutputModel(Object outputModel) {
        this.outputModel = outputModel;
    }
}
