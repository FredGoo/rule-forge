package com.ruleforge.decision.flow.engine;

import com.ruleforge.decision.flow.ir.BpmnDefinition;
import com.ruleforge.decision.flow.ir.FlowDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V5.39 A1 — 决策流执行上下文(facade over 4 typed handles)。
 *
 * <p>V5.39 之前是 20 字段 / 7+ 层调用方 / 6 处 {@code currentToken} 委派 shim 的
 * god-object。V5.39 拆成 4 个 role-focused 子上下文 + 7 个 transient/worklist 字段。
 *
 * <h2>4 个 typed handle(per-flowRunId 状态)</h2>
 * <ul>
 *   <li>{@link #identity()} — {@link FlowIdentity} 不可变 record:flowRunId / flowId / currentPoolId</li>
 *   <li>{@link #vars()} — {@link BusinessVars}:vars map / currentAwaitingField / outputModel</li>
 *   <li>{@link #rete()} — {@link ReteSession}:KnowledgeSession / insertedEntities</li>
 *   <li>{@link #suspend()} — {@link SuspendRegistry}:bus 订阅生命周期</li>
 * </ul>
 *
 * <h2>7 个 transient/worklist 字段(V5.40+ 计划移走,本版本保留在 ctx 上)</h2>
 * <ul>
 *   <li>{@link #activeTokens()} / {@link #currentToken()} — multi-token worklist
 *       (V5.33 A0,Runner traverse 维护)</li>
 *   <li>{@link #joinArrivals()} / {@link #joinedTokens()} — fork/join 计数(V5.33 A0)</li>
 *   <li>{@link #currentDef()} / {@link #currentBpmn()} — traverse-time 临时引用
 *       (Runner 在 dispatch 前 set)</li>
 * </ul>
 *
 * <h2>per-token 状态(已迁出 FlowContext)</h2>
 * <ul>
 *   <li>{@code currentNodeId} / {@code thrownError} / {@code compensationStack} /
 *       {@code compensatedHandlers} — 全部走 {@link Token#getCurrentNodeId()} 等,
 *       不再走 FlowContext 的 6 处委派 shim</li>
 *   <li>{@code vars} — 走 {@link Token#getVars()}(per-fork 隔离)</li>
 * </ul>
 *
 * <p>每条 evaluate 独立一个 FlowContext,不可跨请求共享。
 */
public class FlowContext {

    // ----- 4 typed handles (V5.39) -----
    private final FlowIdentity identity;
    private final BusinessVars businessVars;
    private final ReteSession reteSession;
    private final SuspendRegistry suspendRegistry;

    // ----- transient / worklist (V5.40+ 计划移走) -----
    private final List<Token> activeTokens = new ArrayList<>();
    private Token currentToken;
    private final Map<String, Integer> joinArrivals = new HashMap<>();
    private final Map<String, List<Token>> joinedTokens = new HashMap<>();
    private FlowDefinition currentDef;
    private BpmnDefinition currentBpmn;

    public FlowContext(FlowIdentity identity, BusinessVars businessVars,
                       ReteSession reteSession, SuspendRegistry suspendRegistry) {
        if (identity == null) throw new IllegalArgumentException("identity is required");
        if (businessVars == null) throw new IllegalArgumentException("businessVars is required");
        if (reteSession == null) throw new IllegalArgumentException("reteSession is required");
        if (suspendRegistry == null) throw new IllegalArgumentException("suspendRegistry is required");
        this.identity = identity;
        this.businessVars = businessVars;
        this.reteSession = reteSession;
        this.suspendRegistry = suspendRegistry;
    }

    // ----- 4 typed handle accessors -----

    public FlowIdentity identity() { return identity; }
    public BusinessVars vars() { return businessVars; }
    public ReteSession rete() { return reteSession; }
    public SuspendRegistry suspend() { return suspendRegistry; }

    // ----- currentPoolId transient (V5.37 B0 multi-pool traverse 会 set) -----
    // 注:不在 FlowIdentity 里 — 那里是 startup-time 不可变;这里是 traverse-time 可变。
    // executor 读 ctx.currentPoolId(),优先取 transient,缺省回落到 identity。

    private String currentPoolId;

    public String currentPoolId() {
        return currentPoolId != null ? currentPoolId : identity.currentPoolId();
    }

    public void setCurrentPoolId(String currentPoolId) {
        this.currentPoolId = currentPoolId;
    }

    /**
     * V5.39 A1 — 实际承载 per-token vars 的 map(per-fork 隔离语义)。
     *
     * <p>当 {@link #currentToken()} 有值时,返回 {@code currentToken.getVars()};
     * 否则返回 {@link BusinessVars#getVars()}。
     *
     * <p>executor 调 {@code ctx.effectiveVars()} 时,BusinessVars 直接返回内部
     * map — 这个方法专治 traverse 期间"vars 写到 currentToken,不是写到 BusinessVars"
     * 的契约。Fork 时 Runner 拍 currentToken.vars 快照;join 时 union-merge 回
     * currentToken.vars;traverse-end 再 merge 回 BusinessVars。
     *
     * <p>不要乱用 — 这是给 executor "读/写当前活动 vars map" 的窄口。
     */
    public java.util.Map<String, Object> effectiveVars() {
        if (currentToken != null && currentToken.getVars() != null) {
            return currentToken.getVars();
        }
        return businessVars.getVars();
    }

    // ----- transient / worklist accessors -----

    public List<Token> activeTokens() { return activeTokens; }

    public Token currentToken() { return currentToken; }

    /**
     * V5.39 A1 — setCurrentToken 共享 vars map 引用(根 token 视图)。
     *
     * <p>设计:BusinessVars.vars 是 per-flowRunId 入口 vars,Token.vars 是 per-fork
     * 隔离拷贝。Traverse 启动时,根 token 应直接看到 BusinessVars 的入口 vars。
     * 但 Token ctor 给的是空 HashMap,如果只是"复制 values",后续 caller 往
     * BusinessVars.put 的修改不会反映到 currentToken(导致 executor 读不到)。
     *
     * <p>这里采用<b>引用共享</b>:让 token.getVars() 跟 businessVars.getVars() 指向
     * 同一个 map。后续 put 到 BusinessVars 或 currentToken.getVars() 都改同一份。
     * Fork 时,Token.fork() 会显式 new 一个 HashMap(parent.vars) — 隔离生效。
     *
     * <p>若 caller 显式 setVars 过(传入的 token.getVars() 不是默认空 HashMap),
     * 保留 caller 的值,不覆盖。
     */
    public void setCurrentToken(Token currentToken) {
        this.currentToken = currentToken;
        if (currentToken != null && currentToken.getVars() != null) {
            // 共享 BusinessVars 的 map 引用,除非 caller 已经塞了非空 map
            if (currentToken.getVars().isEmpty()) {
                currentToken.setVars(businessVars.getVars());
            }
        }
    }

    public Map<String, Integer> joinArrivals() { return joinArrivals; }

    public Map<String, List<Token>> joinedTokens() { return joinedTokens; }

    public FlowDefinition currentDef() { return currentDef; }
    public void setCurrentDef(FlowDefinition currentDef) { this.currentDef = currentDef; }

    public BpmnDefinition currentBpmn() { return currentBpmn; }
    public void setCurrentBpmn(BpmnDefinition currentBpmn) { this.currentBpmn = currentBpmn; }

    // ----- 便利工厂 -----

    /**
     * 用 UUID 生成 flowRunId,其它字段 placeholder;适合测试 / 单池 stateless 场景。
     */
    public static FlowContext newDefault(String flowId) {
        return new FlowContext(
            new FlowIdentity(java.util.UUID.randomUUID().toString(), flowId, null),
            new BusinessVars(),
            new ReteSession(),
            new SuspendRegistry()
        );
    }

    /**
     * 测试用工厂:显式指定 flowRunId(避免依赖 UUID),其它字段 placeholder。
     * 通常 test helper 拿这个省得每次写 4 参构造。
     */
    public static FlowContext newForTest(String flowRunId, String flowId) {
        return new FlowContext(
            new FlowIdentity(flowRunId, flowId, null),
            new BusinessVars(),
            new ReteSession(),
            new SuspendRegistry()
        );
    }

    /**
     * 测试用工厂:连 vars 一起传入。{@code null} vars 走空 BusinessVars。
     * 替代原来 {@code new FlowContext() + setFlowRunId + setVars} 的两步走。
     */
    public static FlowContext forFlow(String flowRunId, String flowId, java.util.Map<String, Object> vars) {
        return new FlowContext(
            new FlowIdentity(flowRunId, flowId, null),
            BusinessVars.from(vars),
            new ReteSession(),
            new SuspendRegistry()
        );
    }
}
