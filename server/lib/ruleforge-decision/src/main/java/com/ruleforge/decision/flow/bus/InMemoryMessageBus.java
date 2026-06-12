package com.ruleforge.decision.flow.bus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * V5.38 C0 — v0 默认 in-process bus 实现。
 *
 * <p>数据结构: {@code ConcurrentHashMap<channel, CopyOnWriteArrayList<handler>>}
 * <ul>
 *   <li>subscribe / unsubscribe 走 {@code CopyOnWriteArrayList.addIfAbsent / remove}
 *       — O(n) over handler list,handler 数量小(v0 几十级别)可接受</li>
 *   <li>publish 走 list 同步 iterate(无锁),handler 异常 catch + log 隔离</li>
 *   <li>handler 去重:addIfAbsent 保证同 handler 重复 subscribe 同 channel 只一份</li>
 * </ul>
 *
 * <p>线程模型:不引线程池,publish 在 caller 线程上同步跑所有 handler。
 * Callers(SendTask / B0 跨池 flow end)控并发。
 *
 * <p>future 切到外部 bus(Kafka / NATS)时,只需替换这个 {@code @Component},
 * {@link MessageBus} 接口消费方(SendTask / ReceiveTask / B0 pool inbox)
 * 不感知。
 */
@Slf4j
@Component
public class InMemoryMessageBus implements MessageBus {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<MessageHandler>> handlers
        = new ConcurrentHashMap<>();

    @Override
    public int publish(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message is null");
        }
        CopyOnWriteArrayList<MessageHandler> list = handlers.get(message.channel());
        if (list == null || list.isEmpty()) {
            log.debug("[BUS] publish channel={} → 0 handlers", message.channel());
            return 0;
        }
        int delivered = 0;
        for (MessageHandler h : list) {
            try {
                h.handle(message);
                delivered++;
            } catch (Exception e) {
                // 隔离:单个 handler 抛不影响兄弟 handler,不冒泡挂 publish 调用方
                log.warn("[BUS] handler threw on channel={} name={} : {}",
                    message.channel(), message.name(), e.getMessage(), e);
            }
        }
        log.debug("[BUS] publish channel={} → {} handlers delivered", message.channel(), delivered);
        return delivered;
    }

    @Override
    public Subscription subscribe(String channel, MessageHandler handler) {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("channel is required");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler is required");
        }
        CopyOnWriteArrayList<MessageHandler> list = handlers.computeIfAbsent(
            channel, k -> new CopyOnWriteArrayList<>());
        // addIfAbsent — 同 handler 重复 subscribe 同 channel 只一份
        list.addIfAbsent(handler);
        log.info("[BUS] subscribe channel={} (total handlers on channel={})",
            channel, list.size());
        return new InMemorySubscription(channel, handler, list);
    }

    @Override
    public int subscriberCount(String channel) {
        CopyOnWriteArrayList<MessageHandler> list = handlers.get(channel);
        return list == null ? 0 : list.size();
    }

    @Override
    public Set<String> knownChannels() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    /** 订阅句柄实现:close() remove handler,idempotent。 */
    private static final class InMemorySubscription implements Subscription {
        private final String channel;
        private final MessageHandler handler;
        private final CopyOnWriteArrayList<MessageHandler> list;
        private volatile boolean closed = false;

        InMemorySubscription(String channel, MessageHandler handler,
                              CopyOnWriteArrayList<MessageHandler> list) {
            this.channel = channel;
            this.handler = handler;
            this.list = list;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            boolean removed = list.remove(handler);
            log.info("[BUS] unsubscribe channel={} removed={}", channel, removed);
            // 不清空 list 本身(可能还有别的 handler);让 GC 自然收
        }

        @Override
        public String channel() {
            return channel;
        }
    }
}
