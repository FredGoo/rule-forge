package com.ruleforge.decision.flow.bus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.38 C0 — Message 不可变 + builder 行为规范。
 *
 * <p>4 BDD:三参 factory 字段可读 / payload 不可变 / builder 7 字段全填 / correlationKey 可省。
 */
@DisplayName("Message 不可变 + Builder")
class MessageTest {

    @Test
    @DisplayName("Given name+channel+payload,When Message.of,Then 字段可读")
    void three_arg_factory_reads_back() {
        Map<String, Object> p = Map.of("amount", 2000);
        Message m = Message.of("loan_approved", "message:loan_approved", p);
        assertEquals("loan_approved", m.name());
        assertEquals("message:loan_approved", m.channel());
        assertEquals(2000, m.payload().get("amount"));
    }

    @Test
    @DisplayName("Given payload 含 null entry,When of,Then 不可变 put 不影响原 map(Message 防御性 copy)")
    void payload_is_defensively_copied() {
        // 改 caller 自己的 map,不影响 Message 内部的 payload
        Map<String, Object> original = new HashMap<>();
        original.put("k", "v1");
        Message m = Message.of("n", "message:n", original);
        // caller 改原 map
        original.put("k", "v2");
        original.put("k2", "v3");
        // Message 内 payload 不受影响
        assertEquals("v1", m.payload().get("k"));
        assertEquals(1, m.payload().size());
        // 同时 put 到 Message 暴露的 payload 也应抛(因为 Map.copyOf 返回 immutable map)
        assertThrows(UnsupportedOperationException.class,
            () -> m.payload().put("evil", "should not work"));
    }

    @Nested
    @DisplayName("Builder 7 字段")
    class BuilderAllFields {

        @Test
        @DisplayName("Given builder 7 字段全填,When build,Then 全部读出 + timestamp 默认为 now")
        void builder_all_seven_fields() {
            Instant ts = Instant.parse("2026-06-12T10:00:00Z");
            Map<String, Object> p = Map.of("x", 1);
            Message m = Message.builder()
                .name("n").channel("message:n").payload(p)
                .correlationKey("corr-1")
                .sourcePool("credit")
                .sourceNodeId("sendNode1")
                .timestamp(ts)
                .build();
            assertEquals("n", m.name());
            assertEquals("message:n", m.channel());
            assertEquals(1, m.payload().get("x"));
            assertEquals("corr-1", m.correlationKey());
            assertEquals("credit", m.sourcePool());
            assertEquals("sendNode1", m.sourceNodeId());
            assertEquals(ts, m.timestamp());
        }

        @Test
        @DisplayName("Given builder 不填 timestamp,When build,Then timestamp 默认为 Instant.now()")
        void builder_default_timestamp_is_now() {
            Instant before = Instant.now();
            Message m = Message.builder()
                .name("n").channel("message:n")
                .build();
            Instant after = Instant.now();
            assertNotNull(m.timestamp());
            // m.timestamp ∈ [before, after]
            assertTrue(!m.timestamp().isBefore(before) && !m.timestamp().isAfter(after),
                "timestamp should default to now; got " + m.timestamp());
        }
    }

    @Nested
    @DisplayName("缺省值 + 必填校验")
    class DefaultsAndRequired {

        @Test
        @DisplayName("Given 不填 correlationKey/sourcePool/sourceNodeId,When build,Then 三个都是 null")
        void optional_fields_default_null() {
            Message m = Message.builder()
                .name("n").channel("message:n")
                .build();
            assertNull(m.correlationKey());
            assertNull(m.sourcePool());
            assertNull(m.sourceNodeId());
        }

        @Test
        @DisplayName("Given 不填 name,When build,Then 抛 NPE(name 必填)")
        void name_is_required() {
            assertThrows(NullPointerException.class, () ->
                Message.builder().channel("message:n").build());
        }

        @Test
        @DisplayName("Given 不填 channel,When build,Then 抛 NPE(channel 必填)")
        void channel_is_required() {
            assertThrows(NullPointerException.class, () ->
                Message.builder().name("n").build());
        }

        @Test
        @DisplayName("Given payload=null,When of,Then 走空 Map 不抛")
        void null_payload_becomes_empty_map() {
            Message m = Message.of("n", "message:n", null);
            assertNotNull(m.payload());
            assertSame(java.util.Collections.emptyMap(), m.payload());
        }
    }
}
