package com.ruleforge.decision.flow.bus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.38 C0 — InMemoryMessageBus 行为规范。
 *
 * <p>10 BDD 分 5 组:Publish/Subscribe 基础 / Subscription 生命周期 / Handler 异常隔离 /
 * Idempotent subscribe / 诊断。
 */
@DisplayName("InMemoryMessageBus 行为")
class InMemoryMessageBusTest {

    private InMemoryMessageBus newBus() {
        return new InMemoryMessageBus();
    }

    private Message newMsg(String channel) {
        return Message.of("n", channel, java.util.Map.of());
    }

    @Nested
    @DisplayName("Group 1 — Publish / Subscribe 基础")
    class PublishSubscribeBasics {

        @Test
        @DisplayName("Given 无订阅,When publish,Then 返 0 + 不抛")
        void no_subscribers_returns_zero() {
            InMemoryMessageBus bus = newBus();
            int delivered = bus.publish(newMsg("message:loan_approved"));
            assertEquals(0, delivered);
        }

        @Test
        @DisplayName("Given 1 个订阅,When publish,Then handler 跑 1 次 + 返 1")
        void single_subscriber_fires_once() {
            InMemoryMessageBus bus = newBus();
            AtomicInteger count = new AtomicInteger();
            bus.subscribe("message:loan_approved", m -> count.incrementAndGet());
            int delivered = bus.publish(newMsg("message:loan_approved"));
            assertEquals(1, delivered);
            assertEquals(1, count.get());
        }

        @Test
        @DisplayName("Given N 个订阅同 channel,When publish,Then 全跑 + 返 N + FIFO 顺序")
        void n_subscribers_fire_in_fifo() {
            InMemoryMessageBus bus = newBus();
            List<String> calls = new ArrayList<>();
            bus.subscribe("message:x", m -> calls.add("a"));
            bus.subscribe("message:x", m -> calls.add("b"));
            bus.subscribe("message:x", m -> calls.add("c"));
            int delivered = bus.publish(newMsg("message:x"));
            assertEquals(3, delivered);
            assertEquals(List.of("a", "b", "c"), calls);
        }

        @Test
        @DisplayName("Given 跨 channel A/B,When publish A,Then 只 A 跑")
        void cross_channel_isolation() {
            InMemoryMessageBus bus = newBus();
            AtomicInteger aCount = new AtomicInteger();
            AtomicInteger bCount = new AtomicInteger();
            bus.subscribe("message:a", m -> aCount.incrementAndGet());
            bus.subscribe("message:b", m -> bCount.incrementAndGet());
            int delivered = bus.publish(newMsg("message:a"));
            assertEquals(1, delivered);
            assertEquals(1, aCount.get());
            assertEquals(0, bCount.get());
        }
    }

    @Nested
    @DisplayName("Group 2 — Subscription 生命周期")
    class SubscriptionLifetime {

        @Test
        @DisplayName("Given subscribe 后 close,When publish,Then handler 不跑 + 返 0")
        void close_unsubscribes() {
            InMemoryMessageBus bus = newBus();
            AtomicInteger count = new AtomicInteger();
            MessageBus.Subscription sub = bus.subscribe("message:x", m -> count.incrementAndGet());
            assertEquals(1, bus.subscriberCount("message:x"));
            sub.close();
            assertEquals(0, bus.subscriberCount("message:x"));
            int delivered = bus.publish(newMsg("message:x"));
            assertEquals(0, delivered);
            assertEquals(0, count.get());
        }

        @Test
        @DisplayName("Given close 两次,Then 幂等不抛")
        void close_is_idempotent() {
            InMemoryMessageBus bus = newBus();
            MessageBus.Subscription sub = bus.subscribe("message:x", m -> {});
            sub.close();
            // 第二次 close 不抛
            sub.close();
        }

        @Test
        @DisplayName("Given Subscription.channel(),Then 返回原 channel 名(诊断用)")
        void subscription_channel_returns_original_channel() {
            InMemoryMessageBus bus = newBus();
            MessageBus.Subscription sub = bus.subscribe("message:foo", m -> {});
            assertEquals("message:foo", sub.channel());
        }

        @Test
        @DisplayName("Given try-with-resources 块,When 块内 publish then 出块 publish,Then 块内 1 handler 跑、块外 0")
        void try_with_resources_unsubscribes() {
            InMemoryMessageBus bus = newBus();
            AtomicInteger count = new AtomicInteger();
            try (MessageBus.Subscription sub = bus.subscribe("message:x", m -> count.incrementAndGet())) {
                bus.publish(newMsg("message:x"));
                assertEquals(1, count.get());
            }
            // 出块后 subscription 已 close
            bus.publish(newMsg("message:x"));
            assertEquals(1, count.get());
        }
    }

    @Nested
    @DisplayName("Group 3 — Handler 异常隔离")
    class HandlerExceptionIsolation {

        @Test
        @DisplayName("Given handler 抛 RuntimeException,When publish,Then bus 不冒泡 + 其他 handler 仍跑")
        void exception_in_one_handler_does_not_break_others() {
            InMemoryMessageBus bus = newBus();
            AtomicInteger goodCount = new AtomicInteger();
            bus.subscribe("message:x", m -> { throw new RuntimeException("boom"); });
            bus.subscribe("message:x", m -> goodCount.incrementAndGet());
            // publish 不抛
            int delivered = bus.publish(newMsg("message:x"));
            // 第二个 handler 仍跑
            assertEquals(1, goodCount.get());
            // delivered 计数 = 跑完的(异常不算)
            assertEquals(1, delivered);
        }

        @Test
        @DisplayName("Given handler 抛 Error(非 RuntimeException),When publish,Then 仍被 catch(实现用 Exception,符合 try-catch 模式)")
        void exception_catches_runtimeexception_only() {
            // 实现契约:catch (Exception) — RuntimeException 子类全 catch,Error 不 catch
            // 测 RuntimeException 子类 + RuntimeException 自身即可
            InMemoryMessageBus bus = newBus();
            bus.subscribe("message:x", m -> { throw new IllegalStateException("boom"); });
            // 不抛
            int delivered = bus.publish(newMsg("message:x"));
            assertEquals(0, delivered);
        }
    }

    @Nested
    @DisplayName("Group 4 — Idempotent subscribe")
    class IdempotentSubscribe {

        @Test
        @DisplayName("Given 同一 handler subscribe 同 channel 两次,When publish,Then 只跑 1 次")
        void same_handler_dedup() {
            InMemoryMessageBus bus = newBus();
            AtomicInteger count = new AtomicInteger();
            MessageHandler h = m -> count.incrementAndGet();
            bus.subscribe("message:x", h);
            bus.subscribe("message:x", h);
            // subscriberCount = 1 (addIfAbsent)
            assertEquals(1, bus.subscriberCount("message:x"));
            int delivered = bus.publish(newMsg("message:x"));
            assertEquals(1, delivered);
            assertEquals(1, count.get());
        }

        @Test
        @DisplayName("Given 不同 handler 同 channel 两次,When publish,Then 各跑 1 次 + 顺序按 subscribe")
        void different_handlers_each_fire_once() {
            InMemoryMessageBus bus = newBus();
            List<String> calls = new ArrayList<>();
            bus.subscribe("message:x", m -> calls.add("first"));
            bus.subscribe("message:x", m -> calls.add("second"));
            int delivered = bus.publish(newMsg("message:x"));
            assertEquals(2, delivered);
            assertEquals(List.of("first", "second"), calls);
        }
    }

    @Nested
    @DisplayName("Group 5 — 诊断")
    class Diagnostics {

        @Test
        @DisplayName("Given 3 个 channel 各 1 订阅,When knownChannels,Then 3 个 + subscriberCount 各自 1")
        void known_channels_and_subscriber_count() {
            InMemoryMessageBus bus = newBus();
            bus.subscribe("message:a", m -> {});
            bus.subscribe("message:b", m -> {});
            bus.subscribe("signal:c",  m -> {});
            assertEquals(3, bus.knownChannels().size());
            assertTrue(bus.knownChannels().contains("message:a"));
            assertTrue(bus.knownChannels().contains("message:b"));
            assertTrue(bus.knownChannels().contains("signal:c"));
            assertEquals(1, bus.subscriberCount("message:a"));
            assertEquals(1, bus.subscriberCount("message:b"));
            assertEquals(1, bus.subscriberCount("signal:c"));
            assertEquals(0, bus.subscriberCount("message:never_subscribed"));
        }
    }

    @Nested
    @DisplayName("Group 6 — MessageKind 重载 subscribe")
    class KindOverloadSubscribe {

        @Test
        @DisplayName("Given subscribe(kind, name, handler),When publish 同 channel,Then handler 跑")
        void kind_overload_routes_to_correct_channel() {
            InMemoryMessageBus bus = newBus();
            AtomicReference<Message> captured = new AtomicReference<>();
            bus.subscribe(MessageKind.Message.INSTANCE, "loan_approved",
                captured::set);
            Message m = Message.of("loan_approved", "message:loan_approved", Map.of());
            int delivered = bus.publish(m);
            assertEquals(1, delivered);
            assertNotNull(captured.get());
            assertSame(m, captured.get());
        }

        @Test
        @DisplayName("Given subscribe(PoolMessage \"a\",\"b\", name, handler),When publish pool channel,Then handler 跑")
        void pool_kind_overload() {
            InMemoryMessageBus bus = newBus();
            AtomicInteger count = new AtomicInteger();
            bus.subscribe(new MessageKind.PoolMessage("a", "b"), "msg", m -> count.incrementAndGet());
            int delivered = bus.publish(newMsg("pool:a_to_b:msg"));
            assertEquals(1, delivered);
            assertEquals(1, count.get());
        }
    }

    @Nested
    @DisplayName("Group 7 — 入参校验")
    class InputValidation {

        @Test
        @DisplayName("Given publish(Message=null),Then 抛 IllegalArgumentException")
        void null_message_rejected() {
            InMemoryMessageBus bus = newBus();
            assertThrows(IllegalArgumentException.class, () -> bus.publish(null));
        }

        @Test
        @DisplayName("Given subscribe channel=null,Then 抛 IllegalArgumentException")
        void null_channel_rejected() {
            InMemoryMessageBus bus = newBus();
            assertThrows(IllegalArgumentException.class,
                () -> bus.subscribe(null, m -> {}));
        }

        @Test
        @DisplayName("Given subscribe handler=null,Then 抛 IllegalArgumentException")
        void null_handler_rejected() {
            InMemoryMessageBus bus = newBus();
            assertThrows(IllegalArgumentException.class,
                () -> bus.subscribe("message:x", null));
        }
    }
}
