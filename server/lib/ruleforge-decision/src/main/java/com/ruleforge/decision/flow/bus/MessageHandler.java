package com.ruleforge.decision.flow.bus;

/**
 * V5.38 C0 — Bus 订阅者回调。
 *
 * <p>同步 void:发布线程上跑 handler,handler 抛异常由 {@link MessageBus} 实现负责隔离
 * (catch + log,不冒泡挂 publish 调用方)。
 *
 * <p>v0 简化:不上 async/thread pool — B0 跨池、C1 SendTask、C2 catch message 都是
 * "publish 后等 handler 跑完"的语义。future external bus adapter(Kafka / NATS)把
 * async 做成 impl 内部细节,不污染 SPI。
 */
@FunctionalInterface
public interface MessageHandler {

    /**
     * 同步处理一条 message。
     *
     * <p>实现契约:handler 抛 RuntimeException 会被 bus 隔离,不影响兄弟 handler;
     * 抛 checked exception 必须包成 RuntimeException。
     *
     * @param message 不可变消息,handler 不能修改 payload
     */
    void handle(Message message);
}
