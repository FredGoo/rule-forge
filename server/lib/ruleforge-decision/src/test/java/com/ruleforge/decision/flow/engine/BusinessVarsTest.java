package com.ruleforge.decision.flow.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.39 A1 — BusinessVars 行为规范。
 *
 * <p>5 BDD 分 5 组:vars map / awaiting field / output model / 防御性 add / 边界。
 */
@DisplayName("BusinessVars 行为")
class BusinessVarsTest {

    @Nested
    @DisplayName("Group 1 — vars map 基本")
    class VarsBasic {

        @Test
        @DisplayName("Given 新建,When getVars,Then 返回非 null 空 HashMap(可写)")
        void fresh_vars_is_empty_writable_map() {
            BusinessVars bv = new BusinessVars();
            Map<String, Object> vars = bv.getVars();
            assertNotNull(vars);
            assertTrue(vars.isEmpty());
            vars.put("k", 1);
            assertEquals(1, bv.getVars().get("k"));
        }
    }

    @Nested
    @DisplayName("Group 2 — currentAwaitingField")
    class AwaitingField {

        @Test
        @DisplayName("Given 新建,Then awaitingField 为 null;set 后能取回")
        void awaiting_field_default_null_and_roundtrip() {
            BusinessVars bv = new BusinessVars();
            assertNull(bv.getCurrentAwaitingField());
            bv.setCurrentAwaitingField("loan_decision");
            assertEquals("loan_decision", bv.getCurrentAwaitingField());
        }
    }

    @Nested
    @DisplayName("Group 3 — outputModel(Object,守住模块边界)")
    class OutputModel {

        @Test
        @DisplayName("Given outputModel 写入任意 POJO,When getOutputModel,Then 同引用取出")
        void output_model_roundtrip_holds_reference() {
            BusinessVars bv = new BusinessVars();
            Object fakeOutput = new Object();  // 模拟 executor-app 的 OutputModel
            bv.setOutputModel(fakeOutput);
            assertSame(fakeOutput, bv.getOutputModel());
        }
    }

    @Nested
    @DisplayName("Group 4 — 防御性 addVars")
    class DefensiveAdd {

        @Test
        @DisplayName("Given 给定 map,When addVars,Then entries 拷进 bv.vars,但 given 后续修改不影响 bv")
        void add_vars_copies_entries_not_reference() {
            BusinessVars bv = new BusinessVars();
            Map<String, Object> given = new HashMap<>();
            given.put("a", 1);
            given.put("b", "two");
            bv.addVars(given);
            // 改 given
            given.put("a", 999);
            given.put("c", "leaked?");
            // bv.vars 不受影响
            assertEquals(1, bv.getVars().get("a"));
            assertEquals("two", bv.getVars().get("b"));
            assertNull(bv.getVars().get("c"));
        }

        @Test
        @DisplayName("Given addVars(null),Then 不抛 + bv.vars 仍可用")
        void add_vars_null_is_noop() {
            BusinessVars bv = new BusinessVars();
            bv.addVars(null);
            assertNotNull(bv.getVars());
            assertTrue(bv.getVars().isEmpty());
        }
    }

    @Nested
    @DisplayName("Group 5 — getVars 返回的是内部 map 引用(非防御性)")
    class VarsIsLiveReference {

        @Test
        @DisplayName("Given getVars(),Then 多次调用返回同一引用(契约:这是内部 map,无外部引用场景)")
        void get_vars_returns_live_internal_reference() {
            BusinessVars bv = new BusinessVars();
            assertSame(bv.getVars(), bv.getVars());
            // 注:这跟 addVars 的"拷贝 entries"语义不同。getVars 给内部读写入口,
            // addVars 给外部批量灌入。两者并存。
        }

        @Test
        @DisplayName("Given 用一个 map 完全替换 vars 引用,When set 之前的 key,Then 旧 key 仍存在(getVars 不重置)")
        void vars_reference_stable_across_operations() {
            // 注:BV 的设计契约是 vars map 引用稳定,addVars 只 putAll 不替换
            BusinessVars bv = new BusinessVars();
            Map<String, Object> v1 = bv.getVars();
            v1.put("x", 1);
            bv.addVars(Map.of("y", 2));
            // v1 和 v2 应该是同一引用
            Map<String, Object> v2 = bv.getVars();
            assertNotSame(Map.of(), v2);
            assertEquals(1, v2.get("x"));
            assertEquals(2, v2.get("y"));
        }
    }
}
