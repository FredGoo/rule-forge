package com.ruleforge.decision.flow.bus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * V5.38 C0 — MessageKind channel prefix 协议。
 *
 * <p>4 BDD:Message / Signal / PoolMessage 三种 channelFor + channelPrefix 互异。
 */
@DisplayName("MessageKind channel 前缀协议")
class MessageKindTest {

    @Test
    @DisplayName("Given kind=Message,When channelFor(\"foo\"),Then \"message:foo\"")
    void message_kind_channel() {
        String ch = MessageKind.channelFor(MessageKind.Message.INSTANCE, "foo");
        assertEquals("message:foo", ch);
    }

    @Test
    @DisplayName("Given kind=Signal,When channelFor(\"bar\"),Then \"signal:bar\"")
    void signal_kind_channel() {
        String ch = MessageKind.channelFor(MessageKind.Signal.INSTANCE, "bar");
        assertEquals("signal:bar", ch);
    }

    @Test
    @DisplayName("Given kind=PoolMessage(\"credit\",\"underwriting\"),When channelFor(\"loan_approved\"),Then \"pool:credit_to_underwriting:loan_approved\"")
    void pool_message_kind_channel() {
        MessageKind kind = new MessageKind.PoolMessage("credit", "underwriting");
        String ch = MessageKind.channelFor(kind, "loan_approved");
        assertEquals("pool:credit_to_underwriting:loan_approved", ch);
    }

    @Test
    @DisplayName("Given 三种 kind,When channelPrefix,Then 三种 prefix 各异 + 单例语义")
    void three_kinds_have_distinct_prefixes() {
        assertEquals("message:", MessageKind.Message.INSTANCE.channelPrefix());
        assertEquals("signal:", MessageKind.Signal.INSTANCE.channelPrefix());
        assertEquals("pool:credit_to_underwriting:",
            new MessageKind.PoolMessage("credit", "underwriting").channelPrefix());

        // 互异
        assertNotEquals(MessageKind.Message.INSTANCE.channelPrefix(),
                        MessageKind.Signal.INSTANCE.channelPrefix());

        // Message / Signal 是单例
        assertSame(MessageKind.Message.INSTANCE, MessageKind.Message.INSTANCE);
        assertSame(MessageKind.Signal.INSTANCE, MessageKind.Signal.INSTANCE);
    }
}
