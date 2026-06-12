package com.ruleforge.decision.flow.bus;

/**
 * V5.38 C0 — Message 种类分类 + channel 前缀协议。
 *
 * <p>3 variant(Java 17 sealed):
 * <ul>
 *   <li>{@link Message} — 普通 BPMN message 事件(C1 Send/Receive Task、C2 catch message 走这)</li>
 *   <li>{@link Signal} — BPMN signal 广播(transport 层走 {@code FlowResumer.resumeAllSuspendedByWaitRef}
 *       DB scan,不经 bus — 但 channel prefix 仍走这里给桥接 caller 用)</li>
 *   <li>{@link PoolMessage} — V5.37 B0 跨池 message flow(从 fromPool 池到 toPool 池的 message flow)</li>
 * </ul>
 *
 * <p>channel 命名约定:
 * <pre>
 *   Message     → "message:&lt;name&gt;"
 *   Signal      → "signal:&lt;name&gt;"
 *   PoolMessage → "pool:&lt;fromPool&gt;_to_&lt;toPool&gt;:&lt;name&gt;"
 * </pre>
 *
 * <p>集中维护 prefix 规则 — 所有 SubscribeSite / PublishSite 调 {@link #channelFor(MessageKind, String)}
 * 生成 channel,不直接拼字符串。
 */
public sealed interface MessageKind
    permits MessageKind.Message, MessageKind.Signal, MessageKind.PoolMessage {

    /** 返回该 kind 用的 channel 前缀(不含具体 name)。 */
    String channelPrefix();

    /**
     * 给定 kind + name 算出完整 channel。
     *
     * <p>PoolMessage 是唯一需要 kind 之外的参数(fromPool/toPool)的;
     * Message / Signal 直接 prefix + name。
     */
    static String channelFor(MessageKind kind, String name) {
        if (kind instanceof PoolMessage p) {
            return "pool:" + p.fromPool() + "_to_" + p.toPool() + ":" + name;
        }
        return kind.channelPrefix() + name;
    }

    /** 普通 message: channel prefix = {@code "message:"}。 */
    final class Message implements MessageKind {
        public static final Message INSTANCE = new Message();
        private Message() {}
        @Override public String channelPrefix() { return "message:"; }
    }

    /** Signal: channel prefix = {@code "signal:"}。v0 signal 不走 bus transport,留 prefix 给桥接日志用。 */
    final class Signal implements MessageKind {
        public static final Signal INSTANCE = new Signal();
        private Signal() {}
        @Override public String channelPrefix() { return "signal:"; }
    }

    /**
     * V5.37 B0 跨池 message flow。channel = {@code "pool:<from>_to_<to>:<name>"}。
     * fromPool / toPool 是 BPMN {@code <bpmn:participant>} id。
     */
    final class PoolMessage implements MessageKind {
        private final String fromPool;
        private final String toPool;
        public PoolMessage(String fromPool, String toPool) {
            this.fromPool = fromPool;
            this.toPool = toPool;
        }
        public String fromPool() { return fromPool; }
        public String toPool()   { return toPool;   }
        /** PoolMessage 的 prefix 含 from+to 留位,实际 channel 在 {@link #channelFor} 里拼。 */
        @Override public String channelPrefix() { return "pool:" + fromPool + "_to_" + toPool + ":"; }
    }
}
