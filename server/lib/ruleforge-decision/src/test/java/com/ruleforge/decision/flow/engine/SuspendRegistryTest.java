package com.ruleforge.decision.flow.engine;

import com.ruleforge.decision.flow.bus.MessageBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.39 A1 — SuspendRegistry 行为规范。
 *
 * <p>5 BDD:注册 / 顺序 / closeAll / closeAll 后 register / 重复 closeAll 幂等。
 */
@DisplayName("SuspendRegistry 行为")
class SuspendRegistryTest {

    /** Fake subscription — 记录 close 次数,可断言 closeAll 真的调了 close。 */
    private static final class FakeSub implements MessageBus.Subscription {
        final String channel;
        final AtomicInteger closeCount = new AtomicInteger();
        FakeSub(String channel) { this.channel = channel; }
        @Override public void close() { closeCount.incrementAndGet(); }
        @Override public String channel() { return channel; }
    }

    @Nested
    @DisplayName("Group 1 — 注册")
    class Register {

        @Test
        @DisplayName("Given 新建,When register 一个 sub,Then size=1 + all() 含它")
        void register_one_sub() {
            SuspendRegistry reg = new SuspendRegistry();
            FakeSub s = new FakeSub("message:x");
            reg.register(s);
            assertEquals(1, reg.size());
            assertSame(s, reg.all().get(0));
        }

        @Test
        @DisplayName("Given register(null),Then 不抛 + size 不变(null 防御)")
        void register_null_is_noop() {
            SuspendRegistry reg = new SuspendRegistry();
            reg.register(null);
            assertEquals(0, reg.size());
            assertNotNull(reg.all());
        }
    }

    @Nested
    @DisplayName("Group 2 — 顺序保留")
    class OrderPreserved {

        @Test
        @DisplayName("Given N 个 sub 按 a/b/c 顺序 register,When all(),Then 顺序不变")
        void registered_order_preserved() {
            SuspendRegistry reg = new SuspendRegistry();
            FakeSub a = new FakeSub("a");
            FakeSub b = new FakeSub("b");
            FakeSub c = new FakeSub("c");
            reg.register(a);
            reg.register(b);
            reg.register(c);
            List<MessageBus.Subscription> all = reg.all();
            assertEquals(List.of(a, b, c), all);
        }
    }

    @Nested
    @DisplayName("Group 3 — closeAll 真的关")
    class CloseAll {

        @Test
        @DisplayName("Given 3 个 sub,When closeAll,Then 每个 sub.close() 都被调 1 次 + list 清空")
        void close_all_invokes_close_on_each() {
            SuspendRegistry reg = new SuspendRegistry();
            FakeSub a = new FakeSub("a");
            FakeSub b = new FakeSub("b");
            FakeSub c = new FakeSub("c");
            reg.register(a);
            reg.register(b);
            reg.register(c);
            reg.closeAll();
            assertEquals(1, a.closeCount.get());
            assertEquals(1, b.closeCount.get());
            assertEquals(1, c.closeCount.get());
            assertEquals(0, reg.size());
        }

        @Test
        @DisplayName("Given 1 个 sub close 抛异常,When closeAll,Then 后续 sub 仍被 close(异常隔离)")
        void close_all_isolates_exceptions() {
            SuspendRegistry reg = new SuspendRegistry();
            FakeSub first = new FakeSub("first");
            // 包装 close 抛异常的 sub
            MessageBus.Subscription exploding = new MessageBus.Subscription() {
                @Override public void close() { throw new RuntimeException("boom"); }
                @Override public String channel() { return "boom"; }
            };
            FakeSub last = new FakeSub("last");
            reg.register(first);
            reg.register(exploding);
            reg.register(last);
            reg.closeAll();
            assertEquals(1, first.closeCount.get());
            assertEquals(1, last.closeCount.get());
            assertEquals(0, reg.size());
        }
    }

    @Nested
    @DisplayName("Group 4 — closeAll 后 register 不报错")
    class ReuseAfterClose {

        @Test
        @DisplayName("Given closeAll 后,When register 新 sub,Then size=1 + all() 正常")
        void register_after_close_works() {
            SuspendRegistry reg = new SuspendRegistry();
            reg.register(new FakeSub("old"));
            reg.closeAll();
            FakeSub fresh = new FakeSub("fresh");
            reg.register(fresh);
            assertEquals(1, reg.size());
            assertSame(fresh, reg.all().get(0));
        }
    }

    @Nested
    @DisplayName("Group 5 — 重复 closeAll 幂等")
    class IdempotentCloseAll {

        @Test
        @DisplayName("Given closeAll 2 次,Then 第二次不抛 + sub.close() 不会调第二次")
        void double_close_all_does_not_re_close() {
            SuspendRegistry reg = new SuspendRegistry();
            FakeSub s = new FakeSub("x");
            reg.register(s);
            reg.closeAll();
            reg.closeAll();
            // close() 只在第一次 closeAll 调 1 次(因为第二次 list 已空)
            assertEquals(1, s.closeCount.get());
        }

        @Test
        @DisplayName("Given 空 registry,When closeAll,Then 不抛")
        void close_all_on_empty_is_noop() {
            SuspendRegistry reg = new SuspendRegistry();
            reg.closeAll();
            reg.closeAll();
            assertEquals(0, reg.size());
            assertTrue(reg.all().isEmpty());
        }

        @Test
        @DisplayName("Given 大量 sub,When closeAll,Then 全部 close + 不漏不重")
        void many_subs_all_closed_exactly_once() {
            SuspendRegistry reg = new SuspendRegistry();
            List<FakeSub> subs = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                FakeSub s = new FakeSub("c" + i);
                subs.add(s);
                reg.register(s);
            }
            reg.closeAll();
            for (FakeSub s : subs) {
                assertEquals(1, s.closeCount.get(), () -> "sub " + s.channel + " close count");
            }
            assertEquals(0, reg.size());
        }
    }
}
