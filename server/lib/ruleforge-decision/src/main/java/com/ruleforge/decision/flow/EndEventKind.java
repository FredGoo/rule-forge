package com.ruleforge.decision.flow;

import com.ruleforge.decision.exception.FlowExecutionException;

import java.util.Map;

/**
 * V5.34 A2 + V5.36 A6 — EndEvent 节点种类(SEALED 模拟 Rust enum)。
 *
 * <p>Mirror Rust V5.30 + V5.30 补充 {@code end_event.rs} 8 variant 契约:
 * <ul>
 *   <li>{@link None} — 正常 end,traverse COMPLETED</li>
 *   <li>{@link Error} — errorRef → ctx.thrownError + FlowExecutionException</li>
 *   <li>{@link Escalation} — escalationRef → ctx.thrownError + FlowExecutionException</li>
 *   <li>{@link Terminate} — V5.30 v0 跟 Error 同 path,V5.31 P1 才加 token-kill</li>
 *   <li><b>V5.36 A6</b> {@link Cancel} — 同 terminate,抛 "Cancelled"</li>
 *   <li><b>V5.36 A6</b> {@link Compensation} — 调 CompensationRunner.runHandlersForActivity(attachedTo)</li>
 *   <li><b>V5.36 A6</b> {@link MessageEnd} — thrownError="message:&lt;name&gt;" + FlowExecutionException("MessageEnd")</li>
 *   <li><b>V5.36 A6</b> {@link SignalEnd} — thrownError="signal:&lt;name&gt;" + FlowExecutionException("SignalEnd")</li>
 * </ul>
 *
 * <p>{@code fromAttrs} 工厂规则:
 * <ul>
 *   <li>无 {@code ruleforge:endType} → None</li>
 *   <li>{@code endType=error} → Error(无 {@code errorRef} 报错,空串也报错)</li>
 *   <li>{@code endType=escalation} → Escalation(无 {@code escalationRef} 报错)</li>
 *   <li>{@code endType=terminate} → Terminate</li>
 *   <li><b>A6</b> {@code endType=cancel} → Cancel</li>
 *   <li><b>A6</b> {@code endType=compensation} → Compensation(无 {@code attachedTo} 报错)</li>
 *   <li><b>A6</b> {@code endType=messageEnd} → MessageEnd(无 {@code eventName} 报错)</li>
 *   <li><b>A6</b> {@code endType=signalEnd} → SignalEnd(无 {@code eventName} 报错)</li>
 *   <li>其他值 → 报错</li>
 * </ul>
 */
public sealed interface EndEventKind permits EndEventKind.None,
                                            EndEventKind.Error,
                                            EndEventKind.Escalation,
                                            EndEventKind.Terminate,
                                            EndEventKind.Cancel,
                                            EndEventKind.Compensation,
                                            EndEventKind.MessageEnd,
                                            EndEventKind.SignalEnd {

    /** 正常 end,无特殊语义。 */
    final class None implements EndEventKind {
        public static final None INSTANCE = new None();
        private None() {}
        /** 为对称性,None 永远返回 null;避免 pattern match 强转。 */
        public String errorRef() { return null; }
    }

    /** endType=error 严格化 end。 */
    final class Error implements EndEventKind {
        private final String errorRef;
        public Error(String errorRef) { this.errorRef = errorRef; }
        public String errorRef() { return errorRef; }
    }

    /** endType=escalation 严格化 end。 */
    final class Escalation implements EndEventKind {
        private final String escalationRef;
        public Escalation(String escalationRef) { this.escalationRef = escalationRef; }
        public String errorRef() { return escalationRef; }
        public String escalationRef() { return escalationRef; }
    }

    /** endType=terminate — V5.30 v0 跟 Error 同 path,token-kill 留 V5.31 P1。 */
    final class Terminate implements EndEventKind {
        public static final Terminate INSTANCE = new Terminate();
        private Terminate() {}
        public String errorRef() { return null; }
    }

    /** V5.36 A6 — endType=cancel,跟 terminate 同语义("Cancelled" message)。 */
    final class Cancel implements EndEventKind {
        public static final Cancel INSTANCE = new Cancel();
        private Cancel() {}
        public String errorRef() { return null; }
    }

    /** V5.36 A6 — endType=compensation 触发 SAGA:跑 {@code attachedTo} activity 的 handlers。 */
    final class Compensation implements EndEventKind {
        private final String attachedTo;
        public Compensation(String attachedTo) { this.attachedTo = attachedTo; }
        public String attachedTo() { return attachedTo; }
        public String errorRef() { return null; }
    }

    /** V5.36 A6 — endType=messageEnd → thrownError="message:&lt;name&gt;"。 */
    final class MessageEnd implements EndEventKind {
        private final String name;
        public MessageEnd(String name) { this.name = name; }
        public String name() { return name; }
        public String errorRef() { return "message:" + name; }
    }

    /** V5.36 A6 — endType=signalEnd → thrownError="signal:&lt;name&gt;"。 */
    final class SignalEnd implements EndEventKind {
        private final String name;
        public SignalEnd(String name) { this.name = name; }
        public String name() { return name; }
        public String errorRef() { return "signal:" + name; }
    }

    /** 通用 errorRef getter(NONE/ERROR/ESCALATION/TERMINATE 都有)。 */
    String errorRef();

    // -------- factory --------

    /**
     * 从 BPMN 节点的 extensionAttrs 解析 EndEventKind。
     *
     * @param attrs {@code key=value} map,key 形式如 {@code ruleforge:endType} / {@code ruleforge:errorRef}
     * @return 8 variant 之一
     * @throws FlowExecutionException 缺必填字段或不识别 endType
     */
    static EndEventKind fromAttrs(Map<String, String> attrs) {
        if (attrs == null) return None.INSTANCE;
        String endType = attrs.get("ruleforge:endType");
        if (endType == null || endType.isBlank()) {
            return None.INSTANCE;
        }
        return switch (endType) {
            case "error" -> {
                String ref = attrs.get("ruleforge:errorRef");
                if (ref != null && ref.isBlank()) {
                    throw new FlowExecutionException(
                        "EndEvent endType=error has blank ruleforge:errorRef (omit it for default \"error\")");
                }
                // V5.30:无 ref 时默认 "error" (mirror Rust: 缺 errorRef 用字面量 "error")
                yield new Error(ref == null ? "error" : ref);
            }
            case "escalation" -> {
                String ref = attrs.get("ruleforge:escalationRef");
                if (ref == null || ref.isBlank()) {
                    throw new FlowExecutionException(
                        "EndEvent endType=escalation requires non-empty ruleforge:escalationRef");
                }
                yield new Escalation(ref);
            }
            case "terminate" -> Terminate.INSTANCE;
            // ---- V5.36 A6 — 4 补充 variant ----
            case "cancel" -> Cancel.INSTANCE;
            case "compensation" -> {
                String attachedTo = attrs.get("ruleforge:attachedTo");
                if (attachedTo == null || attachedTo.isBlank()) {
                    throw new FlowExecutionException(
                        "EndEvent endType=compensation requires non-empty ruleforge:attachedTo");
                }
                yield new Compensation(attachedTo);
            }
            case "messageEnd" -> {
                String name = attrs.get("ruleforge:eventName");
                if (name == null || name.isBlank()) {
                    throw new FlowExecutionException(
                        "EndEvent endType=messageEnd requires non-empty ruleforge:eventName");
                }
                yield new MessageEnd(name);
            }
            case "signalEnd" -> {
                String name = attrs.get("ruleforge:eventName");
                if (name == null || name.isBlank()) {
                    throw new FlowExecutionException(
                        "EndEvent endType=signalEnd requires non-empty ruleforge:eventName");
                }
                yield new SignalEnd(name);
            }
            default -> throw new FlowExecutionException(
                "Unknown EndEvent endType=" + endType);
        };
    }
}
