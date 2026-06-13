package com.ruleforge.builder.table;

import com.ruleforge.builder.table.ScriptDecisionTableToDrlConverter;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.table.Column;
import com.ruleforge.model.table.ColumnType;
import com.ruleforge.model.table.Row;
import com.ruleforge.model.table.ScriptCell;
import com.ruleforge.model.table.ScriptDecisionTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * V5.44.2 — ScriptDecisionTable → DRL 4 转换器 BDD。
 *
 * <p>锁 6 件事:
 * <ol>
 *   <li>null table 抛 RuleException</li>
 *   <li>空 ScriptDecisionTable 产合法 DRL(无 rule 段,头注释保留)</li>
 *   <li>单 cell 简单 LHS 产 1 rule + eval()</li>
 *   <li>单 cell 简单 RHS 产 1 rule + 完整 then 段</li>
 *   <li>多 row 表产多 rule,按 row num 升序</li>
 *   <li>复杂 cell script 表达式(包含变量 / 算术)原样嵌入</li>
 * </ol>
 *
 * <p>本测试不跑 DrlDeserializer 端解析(避免跟 V5.42 DrlDeserializer 强耦合),只
 * 锁 DRL 字符串形态 — 实际 DRL 是否合法由 DrlEndToEndTest 链路覆盖。
 *
 * @since 5.44
 */
@DisplayName("V5.44.2 — ScriptDecisionTable → DRL 4 转换器 BDD")
class ScriptDecisionTableToDrlConverterTest {

    private ScriptDecisionTableToDrlConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ScriptDecisionTableToDrlConverter();
    }

    // ============================================================
    // === V5.44.2 BDD 1 — null 守卫 ===
    // ============================================================

    @Nested
    @DisplayName("Given null ScriptDecisionTable,When 调 convert,Then 抛 RuleException")
    class NullGuard {

        @Test
        @DisplayName("null ScriptDecisionTable 抛 RuleException")
        void nullTableThrows() {
            try {
                converter.convert(null);
                fail("期望 RuleException");
            } catch (RuleException e) {
                assertNotNull(e.getMessage());
                assertTrue(e.getMessage().contains("ScriptDecisionTable"));
            }
        }
    }

    // ============================================================
    // === V5.44.2 BDD 2 — 空表 产合法 DRL 头 ===
    // ============================================================

    @Nested
    @DisplayName("Given 空 ScriptDecisionTable,When 调 convert,Then 产合法 DRL 头(无 rule)")
    class EmptyTable {

        @Test
        @DisplayName("空表产 DRL 头 + 0 rule")
        void emptyTableEmitsHeaderOnly() {
            ScriptDecisionTable empty = new ScriptDecisionTable();
            String drl = converter.convert(empty);
            assertNotNull(drl);
            assertTrue(drl.contains("V5.44.2 — auto-generated from ScriptDecisionTable"),
                "空表应保留 V5.44.2 头注释,实际:" + drl);
            // 0 rule — 没有 'rule "..."' 段
            assertEquals(0, countOccurrences(drl, "rule \""),
                "空表应产 0 rule,实际 DRL:\n" + drl);
        }

        @Test
        @DisplayName("空 cellMap + 空 rows/columns 跟 null 同样产空 DRL")
        void emptyCollectionsSameAsNew() {
            ScriptDecisionTable empty = new ScriptDecisionTable();
            empty.setRows(null);
            empty.setColumns(null);
            empty.setLibraries(null);
            String drl = converter.convert(empty);
            assertNotNull(drl);
            assertEquals(0, countOccurrences(drl, "rule \""));
        }
    }

    // ============================================================
    // === V5.44.2 BDD 3 — 单 cell LHS (Criteria column) ===
    // ============================================================

    @Nested
    @DisplayName("Given 单 cell LHS,When convert,Then 产 1 rule + eval()")
    class SingleLhsCell {

        @Test
        @DisplayName("单 cell + Criteria column → eval(script) 在 LHS")
        void singleLhs() {
            ScriptDecisionTable table = new ScriptDecisionTable();
            Column col = makeColumn(1, ColumnType.Criteria);
            table.addColumn(col);
            table.addCell(makeCell(0, 1, "Applicant.age > 18"));

            String drl = converter.convert(table);
            assertEquals(1, countOccurrences(drl, "rule \""),
                "1 cell 应产 1 rule,实际 DRL:\n" + drl);
            assertTrue(drl.contains("eval(Applicant.age > 18)"),
                "LHS cell 应被包成 eval(...),实际 DRL:\n" + drl);
            assertTrue(drl.contains("rule \"ScriptTable_r0\""),
                "row 0 应产 ScriptTable_r0 rule name,实际 DRL:\n" + drl);
        }
    }

    // ============================================================
    // === V5.44.2 BDD 4 — 单 cell RHS (Assignment column) ===
    // ============================================================

    @Nested
    @DisplayName("Given 单 cell RHS,When convert,Then 产 1 rule + 完整 then 段")
    class SingleRhsCell {

        @Test
        @DisplayName("单 cell + Assignment column → script 原样进 then 段")
        void singleRhs() {
            ScriptDecisionTable table = new ScriptDecisionTable();
            Column col = makeColumn(1, ColumnType.Assignment);
            table.addColumn(col);
            table.addCell(makeCell(0, 1, "approve($applicant)"));

            String drl = converter.convert(table);
            assertEquals(1, countOccurrences(drl, "rule \""));
            assertTrue(drl.contains("approve($applicant);"),
                "RHS cell script 应原样进 then 段并加 ;,实际 DRL:\n" + drl);
            // 无 LHS cell,转换器给个恒真 eval
            assertTrue(drl.contains("eval(true)"),
                "无 LHS cell 时转换器应给 eval(true) 兜底,实际 DRL:\n" + drl);
        }
    }

    // ============================================================
    // === V5.44.2 BDD 5 — 多 row ===
    // ============================================================

    @Nested
    @DisplayName("Given 多 row 表,When convert,Then 产多 rule 按 row num 升序")
    class MultiRowTable {

        @Test
        @DisplayName("3 row 表产 3 rule,row 0/1/2 升序")
        void threeRows() {
            ScriptDecisionTable table = new ScriptDecisionTable();
            Column col = makeColumn(1, ColumnType.Criteria);
            table.addColumn(col);
            table.addRow(makeRow(0));
            table.addRow(makeRow(1));
            table.addRow(makeRow(2));
            table.addCell(makeCell(0, 1, "age > 18"));
            table.addCell(makeCell(1, 1, "age > 21"));
            table.addCell(makeCell(2, 1, "age > 65"));

            String drl = converter.convert(table);
            assertEquals(3, countOccurrences(drl, "rule \""),
                "3 row 应产 3 rule,实际 DRL:\n" + drl);
            assertTrue(drl.contains("rule \"ScriptTable_r0\""));
            assertTrue(drl.contains("rule \"ScriptTable_r1\""));
            assertTrue(drl.contains("rule \"ScriptTable_r2\""));
        }
    }

    // ============================================================
    // === V5.44.2 BDD 6 — 复杂 cell script 表达式 ===
    // ============================================================

    @Nested
    @DisplayName("Given 复杂 cell script 表达式,When convert,Then 原样嵌入")
    class ComplexScript {

        @Test
        @DisplayName("复杂变量 + 算术 + method chain 表达式原样嵌入")
        void complexScriptEmbedded() {
            ScriptDecisionTable table = new ScriptDecisionTable();
            table.addColumn(makeColumn(1, ColumnType.Criteria));
            table.addColumn(makeColumn(2, ColumnType.Assignment));
            // cell(0,1) LHS:复杂表达式
            table.addCell(makeCell(0, 1, "Applicant.income > 5000 && Applicant.age >= 21"));
            // cell(0,2) RHS:method chain + 变量引用
            table.addCell(makeCell(0, 2, "approve($applicant); update($applicant, score)"));

            String drl = converter.convert(table);
            assertEquals(1, countOccurrences(drl, "rule \""));
            assertTrue(drl.contains("eval(Applicant.income > 5000 && Applicant.age >= 21)"),
                "复杂 LHS 表达式应原样嵌入,实际 DRL:\n" + drl);
            assertTrue(drl.contains("approve($applicant); update($applicant, score);"),
                "复杂 RHS 多语句应原样嵌入并各加 ;,实际 DRL:\n" + drl);
        }
    }

    // ============================================================
    // === helpers ===
    // ============================================================

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static Column makeColumn(int num, ColumnType type) {
        Column col = new Column();
        col.setNum(num);
        col.setType(type);
        col.setVariableName("v" + num);
        return col;
    }

    private static Row makeRow(int num) {
        Row row = new Row();
        row.setNum(num);
        row.setHeight(20);
        return row;
    }

    private static ScriptCell makeCell(int row, int col, String script) {
        ScriptCell cell = new ScriptCell();
        cell.setRow(row);
        cell.setCol(col);
        cell.setRowspan(1);
        cell.setScript(script);
        return cell;
    }
}
