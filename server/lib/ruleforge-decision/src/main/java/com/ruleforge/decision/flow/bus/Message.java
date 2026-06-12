package com.ruleforge.decision.flow.bus;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * V5.38 C0 — Bus 传输层不可变消息。
 *
 * <p>7 字段:
 * <ul>
 *   <li>{@code name} — 逻辑名(e.g. {@code "loan_approved"})</li>
 *   <li>{@code channel} — 路由 key(由 {@link MessageKind#channelFor} 拼出)</li>
 *   <li>{@code payload} — 业务数据,不可变({@code Map.copyOf})</li>
 *   <li>{@code correlationKey} — BPMN 2.0 correlation,v0 不参与 routing,留 forward compat</li>
 *   <li>{@code sourcePool} — 发布方所在 pool(v0 单池场景为 {@code null})</li>
 *   <li>{@code sourceNodeId} — 发布节点 id(SendTask / throwEvent / cross-pool flow end)</li>
 *   <li>{@code timestamp} — 发布时间,默认 {@code Instant.now()}</li>
 * </ul>
 *
 * <p>不可变约束:payload 构造时 {@code Map.copyOf} 防御性复制;调用方 put 改原 map 不影响 Message。
 * 其余 6 字段都是 final,只能通过 builder/构造器一次性设。
 */
public final class Message {

    private final String name;
    private final String channel;
    private final Map<String, Object> payload;
    private final String correlationKey;
    private final String sourcePool;
    private final String sourceNodeId;
    private final Instant timestamp;

    private Message(Builder b) {
        this.name = Objects.requireNonNull(b.name, "Message.name is required");
        this.channel = Objects.requireNonNull(b.channel, "Message.channel is required");
        this.payload = b.payload == null
            ? Collections.emptyMap()
            : Map.copyOf(b.payload);
        this.correlationKey = b.correlationKey;
        this.sourcePool = b.sourcePool;
        this.sourceNodeId = b.sourceNodeId;
        this.timestamp = b.timestamp != null ? b.timestamp : Instant.now();
    }

    /** 简版工厂:三必填,其余默认空。 */
    public static Message of(String name, String channel, Map<String, Object> payload) {
        return new Builder()
            .name(name)
            .channel(channel)
            .payload(payload)
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String name() { return name; }
    public String channel() { return channel; }
    public Map<String, Object> payload() { return payload; }
    public String correlationKey() { return correlationKey; }
    public String sourcePool() { return sourcePool; }
    public String sourceNodeId() { return sourceNodeId; }
    public Instant timestamp() { return timestamp; }

    /** 兼容外部代码用 {@code getXxx()} 风格读字段(JavaBean 习惯)。 */
    public String getName() { return name; }
    public String getChannel() { return channel; }
    public Map<String, Object> getPayload() { return payload; }
    public String getCorrelationKey() { return correlationKey; }
    public String getSourcePool() { return sourcePool; }
    public String getSourceNodeId() { return sourceNodeId; }
    public Instant getTimestamp() { return timestamp; }

    /** 7 字段 Builder — caller 链式设。 */
    public static final class Builder {
        private String name;
        private String channel;
        private Map<String, Object> payload;
        private String correlationKey;
        private String sourcePool;
        private String sourceNodeId;
        private Instant timestamp;

        public Builder name(String v) { this.name = v; return this; }
        public Builder channel(String v) { this.channel = v; return this; }
        public Builder payload(Map<String, Object> v) { this.payload = v; return this; }
        public Builder correlationKey(String v) { this.correlationKey = v; return this; }
        public Builder sourcePool(String v) { this.sourcePool = v; return this; }
        public Builder sourceNodeId(String v) { this.sourceNodeId = v; return this; }
        public Builder timestamp(Instant v) { this.timestamp = v; return this; }

        public Message build() {
            return new Message(this);
        }
    }

    @Override
    public String toString() {
        return "Message{name=" + name + ", channel=" + channel
            + ", sourcePool=" + sourcePool + ", sourceNodeId=" + sourceNodeId
            + ", correlationKey=" + correlationKey
            + ", payloadKeys=" + (payload == null ? 0 : new HashMap<>(payload).size())
            + ", timestamp=" + timestamp + "}";
    }
}
