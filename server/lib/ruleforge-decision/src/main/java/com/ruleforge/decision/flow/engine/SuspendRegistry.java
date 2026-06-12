package com.ruleforge.decision.flow.engine;

import com.ruleforge.decision.flow.bus.MessageBus;

import java.util.ArrayList;
import java.util.List;

/**
 * V5.39 A1 — Bus 订阅生命周期管理。
 *
 * <p>用法:
 * <ul>
 *   <li>ReceiveTask / MessageFlowStart {@link #register(MessageBus.Subscription) register}
 *       订阅</li>
 *   <li>SUSPEND 时**保留**(等 resume)</li>
 *   <li>COMPLETED / FAIL 时 Runner 调 {@link #closeAll()} 关闭</li>
 * </ul>
 *
 * <p>跟 {@link BusinessVars} / {@link ReteSession} 不同,这个容器是 engine-side 的
 * 订阅池,不是 per-flowRunId 业务状态。放在 {@code FlowContext} 上让 lifecycle
 * 跟随 ctx 一起,无需另起 manager bean。
 */
public class SuspendRegistry {

    private final List<MessageBus.Subscription> subscriptions = new ArrayList<>();

    /**
     * 注册一个订阅。{@code null} 是 no-op(防御性)。
     */
    public void register(MessageBus.Subscription s) {
        if (s != null) subscriptions.add(s);
    }

    /**
     * 当前所有活跃订阅(原顺序,live 引用)。Runner 在 COMPLETED/FAIL 前读这个,
     * 序列化路径上不需要(订阅不持久化,resume 时由 ReceiveTask 重新 register)。
     */
    public List<MessageBus.Subscription> all() {
        return subscriptions;
    }

    /**
     * 关闭所有订阅并清空 list。遍历顺序固定,异常隔离 — 一个 sub.close() 抛
     * 不影响其他 sub 的 close。
     */
    public void closeAll() {
        for (MessageBus.Subscription s : subscriptions) {
            try {
                s.close();
            } catch (Exception ignored) {
                // 隔离单个 sub.close() 异常,不影响其他 sub
            }
        }
        subscriptions.clear();
    }

    public int size() {
        return subscriptions.size();
    }
}
