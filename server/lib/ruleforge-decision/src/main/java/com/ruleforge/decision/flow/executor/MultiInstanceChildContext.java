package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.flow.engine.BusinessVars;
import com.ruleforge.decision.flow.engine.FlowContext;

import java.util.HashMap;
import java.util.Map;

/**
 * V5.39 A1 — Multi-Instance parallel inner child context。
 *
 * <p>V5.33 A1 引入,原来 extends {@link FlowContext} + override {@code getVars/setVars}
 * 走 childVars。V5.39 改成 extends 仍保留(避免 NodeExecutor 签名变化),
 * 其它 handle(identity/rete/suspend)全部从父委派。
 *
 * <p><b>v0.1 简化</b>:inner 的 vars 写直接落到父的 BusinessVars(共享 map 引用)。
 * 原因:MI parallel 的"隔离"语义由分支各自的 item + outputs 收集承担 —
 * 父 vars 不是 fork/join worklist(fork/join 关心 currentToken.vars,
 * 而 inner executor 走 effectiveVars() 直接命中 currentToken.vars = 父共享的
 * BusinessVars 引用)。inner 写到父 vars 反而是 caller 的预期("item", "outputs"
 * 等共享变量在 parent 上看得到)。
 *
 * <p>Sequential MI **不**用这个类 — 顺序在同一 ctx 上跑,vars 累积写。
 *
 * <p>V5.40+ 计划:改为 has-a,引入 {@code FlowScope} 接口让 NodeExecutor 接受
 * (FlowContext | MultiInstanceChildContext) — 本版本先兼容 extends。
 */
public class MultiInstanceChildContext extends FlowContext {

    private final FlowContext parent;

    public MultiInstanceChildContext(FlowContext parent, Map<String, Object> childVars) {
        super(parent.identity(), parent.vars(),  // 共享父 BusinessVars(vars 写直接落到父)
              parent.rete(), parent.suspend());
        this.parent = parent;
        // 透传 transient 字段 — inner executor 看到 parent 的 worklist
        super.setCurrentDef(parent.currentDef());
        super.setCurrentBpmn(parent.currentBpmn());
        super.setCurrentToken(parent.currentToken());
        super.activeTokens().addAll(parent.activeTokens());
        super.joinArrivals().putAll(parent.joinArrivals());
        super.joinedTokens().putAll(parent.joinedTokens());
        // childVars 兼容:如果有传入的预填充 vars,merge 进父(目前 caller 几乎都是传空 map)
        if (childVars != null && !childVars.isEmpty()) {
            parent.vars().getVars().putAll(childVars);
        }
    }

    public FlowContext parent() { return parent; }
}
