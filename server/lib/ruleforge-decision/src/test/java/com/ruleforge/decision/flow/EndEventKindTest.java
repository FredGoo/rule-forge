package com.ruleforge.decision.flow;

import com.ruleforge.decision.exception.FlowExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.34 A2 — EndEventKind 行为规范。
 *
 * <p>Mirror Rust V5.30 {@code end_event.rs} 4 variant:None/Error/Escalation/Terminate。
 * 1:1 行为对齐。
 */
@DisplayName("EndEventKind 行为")
class EndEventKindTest {

    private static Map<String, String> attrs(String... pairs) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    @Nested
    @DisplayName("fromAttrs 工厂")
    class FromAttrs {

        @Test
        @DisplayName("Given 无 endType,When fromAttrs,Then 默认 None")
        void none_when_no_end_type() {
            EndEventKind kind = EndEventKind.fromAttrs(attrs());
            assertEquals(EndEventKind.None.INSTANCE, kind);
        }

        @Test
        @DisplayName("Given endType=error 无 ref,When fromAttrs,Then Error{errorRef=\"error\"} 默认值")
        void error_default_ref() {
            EndEventKind kind = EndEventKind.fromAttrs(attrs("ruleforge:endType", "error"));
            assertTrue(kind instanceof EndEventKind.Error,
                "Expected Error variant, got: " + kind.getClass().getSimpleName());
            assertEquals("error", ((EndEventKind.Error) kind).errorRef());
        }

        @Test
        @DisplayName("Given endType=error + errorRef=X,When fromAttrs,Then Error{errorRef=X}")
        void error_with_ref() {
            EndEventKind kind = EndEventKind.fromAttrs(attrs(
                "ruleforge:endType", "error",
                "ruleforge:errorRef", "REF_X"));
            assertTrue(kind instanceof EndEventKind.Error);
            assertEquals("REF_X", ((EndEventKind.Error) kind).errorRef());
        }

        @Test
        @DisplayName("Given endType=escalation + escalationRef=Y,When fromAttrs,Then Escalation{escalationRef=Y}")
        void escalation_with_ref() {
            EndEventKind kind = EndEventKind.fromAttrs(attrs(
                "ruleforge:endType", "escalation",
                "ruleforge:escalationRef", "ESC_Y"));
            assertTrue(kind instanceof EndEventKind.Escalation);
            assertEquals("ESC_Y", ((EndEventKind.Escalation) kind).escalationRef());
        }

        @Test
        @DisplayName("Given endType=terminate,When fromAttrs,Then Terminate")
        void terminate() {
            EndEventKind kind = EndEventKind.fromAttrs(attrs("ruleforge:endType", "terminate"));
            assertEquals(EndEventKind.Terminate.INSTANCE, kind);
        }
    }

    @Nested
    @DisplayName("缺失必填字段")
    class MissingFields {

        @Test
        @DisplayName("Given endType=error + 无 errorRef,When fromAttrs,Then 默认 ref=\"error\" (V5.30 行为)")
        void error_missing_ref_uses_default() {
            // V5.30 mirror:Rust 端缺 errorRef 用字面量 "error" 作为兜底
            EndEventKind kind = EndEventKind.fromAttrs(attrs(
                "ruleforge:endType", "error"));
            assertTrue(kind instanceof EndEventKind.Error);
            assertEquals("error", ((EndEventKind.Error) kind).errorRef());
        }

        @Test
        @DisplayName("Given endType=escalation + 空 escalationRef,When fromAttrs,Then 抛 FlowExecutionException")
        void escalation_missing_ref_errors() {
            assertThrows(FlowExecutionException.class,
                () -> EndEventKind.fromAttrs(attrs(
                    "ruleforge:endType", "escalation",
                    "ruleforge:escalationRef", "")));
        }

        @Test
        @DisplayName("Given 不识别的 endType,When fromAttrs,Then 抛 FlowExecutionException,msg 含 'endType'")
        void unknown_end_type_errors() {
            FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> EndEventKind.fromAttrs(attrs("ruleforge:endType", "bogus")));
            assertTrue(ex.getMessage().toLowerCase().contains("endtype")
                    || ex.getMessage().toLowerCase().contains("end type"),
                "msg should mention endType, got: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("Given None variant,When 访问 errorRef,Then null")
    void none_has_null_error_ref() {
        // sanity:None 不带 ref,getter 不会抛 NPE
        assertNull(((EndEventKind.None) EndEventKind.None.INSTANCE).errorRef());
    }

    // -------- V5.36 A6 — Cancel/Compensation/MessageEnd/SignalEnd 4 variant --------

    @Nested
    @DisplayName("V5.36 A6 — 4 补充 variant")
    class A6Variants {

        @Test
        @DisplayName("Given endType=cancel,When fromAttrs,Then Cancel(无 ref)")
        void cancel_variant() {
            EndEventKind kind = EndEventKind.fromAttrs(attrs("ruleforge:endType", "cancel"));
            assertTrue(kind instanceof EndEventKind.Cancel,
                "Expected Cancel, got: " + kind.getClass().getSimpleName());
        }

        @Test
        @DisplayName("Given endType=compensation + attachedTo=actA,When fromAttrs,Then Compensation{attachedTo=actA}")
        void compensation_variant() {
            EndEventKind kind = EndEventKind.fromAttrs(attrs(
                "ruleforge:endType", "compensation",
                "ruleforge:attachedTo", "actA"));
            assertTrue(kind instanceof EndEventKind.Compensation);
            assertEquals("actA", ((EndEventKind.Compensation) kind).attachedTo());
        }

        @Test
        @DisplayName("Given endType=messageEnd + eventName=foo,When fromAttrs,Then MessageEnd{name=foo}")
        void message_end_variant() {
            EndEventKind kind = EndEventKind.fromAttrs(attrs(
                "ruleforge:endType", "messageEnd",
                "ruleforge:eventName", "foo"));
            assertTrue(kind instanceof EndEventKind.MessageEnd,
                "Expected MessageEnd, got: " + kind.getClass().getSimpleName());
            assertEquals("foo", ((EndEventKind.MessageEnd) kind).name());
        }

        @Test
        @DisplayName("Given endType=signalEnd + eventName=bar,When fromAttrs,Then SignalEnd{name=bar}")
        void signal_end_variant() {
            EndEventKind kind = EndEventKind.fromAttrs(attrs(
                "ruleforge:endType", "signalEnd",
                "ruleforge:eventName", "bar"));
            assertTrue(kind instanceof EndEventKind.SignalEnd);
            assertEquals("bar", ((EndEventKind.SignalEnd) kind).name());
        }

        @Test
        @DisplayName("Given endType=compensation 缺 attachedTo,When fromAttrs,Then 抛错")
        void compensation_missing_attached_to_throws() {
            FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> EndEventKind.fromAttrs(attrs("ruleforge:endType", "compensation")));
            assertTrue(ex.getMessage().toLowerCase().contains("compensation")
                    && ex.getMessage().toLowerCase().contains("attachedto"),
                "msg should mention compensation+attachedTo, got: " + ex.getMessage());
        }

        @Test
        @DisplayName("Given endType=messageEnd 缺 eventName,When fromAttrs,Then 抛错")
        void message_end_missing_event_name_throws() {
            FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> EndEventKind.fromAttrs(attrs("ruleforge:endType", "messageEnd")));
            assertTrue(ex.getMessage().toLowerCase().contains("messageend")
                    && ex.getMessage().toLowerCase().contains("eventname"),
                "msg should mention messageEnd+eventName, got: " + ex.getMessage());
        }

        @Test
        @DisplayName("Given endType=signalEnd 缺 eventName,When fromAttrs,Then 抛错")
        void signal_end_missing_event_name_throws() {
            assertThrows(FlowExecutionException.class,
                () -> EndEventKind.fromAttrs(attrs("ruleforge:endType", "signalEnd")));
        }
    }
}
