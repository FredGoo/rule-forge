package com.ruleforge.ir.migration;

import com.ruleforge.ir.dsl.DslMappingSet;
import com.ruleforge.ir.dsl.DslParser;
import com.ruleforge.ir.drl.DrlParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.42.6 — 一次性 .xml/.dsl → .drl 迁移工具 BDD。
 *
 * <p>两个 converter,范围:
 * <ul>
 *   <li>{@link XmlToDrlRuleConverter} — 老 .xml 字符串 → DRL 文本
 *       (走老 RuleSetParser/Deserializer 拿 RuleSet,再走 V5.42.3b UlToDrlConverter emit)</li>
 *   <li>{@link DslToDrlConverter} — .dsl 映射 + .dslrd 文本(带 natural language 段)
 *       → DRL 文本(替换 mapping key → DRL template)</li>
 * </ul>
 *
 * <p>第一版范围(V5.42.6):
 * <ul>
 *   <li>XmlToDrl:支持简单 {@code <rule name="X" salience="N">} 老结构 — 走老 parser 拿
 *       RuleSet,emit 顶层 metadata;lhs 留 TODO 注释(同 V5.42.3b)</li>
 *   <li>DslToDrl:.dslrd 文本里出现 "This is {age}" 这种 natural language 段时,
 *       查 DslMappingSet,替换成 DRL template;找不到 → DrlParseException</li>
 *   <li>语法错 / 老 .xml 解析失败 → DrlParseException</li>
 * </ul>
 *
 * @since 5.42
 */
@DisplayName("V5.42.6 — 一次性 .xml/.dsl → .drl 迁移工具 BDD")
class MigrationConvertersTest {

    private XmlToDrlRuleConverter xmlConverter;
    private DslToDrlConverter dslConverter;

    @BeforeEach
    void setUp() {
        xmlConverter = new XmlToDrlRuleConverter();
        dslConverter = new DslToDrlConverter();
    }

    // ============================================================
    // === XmlToDrlRuleConverter ===
    // ============================================================

    @Nested
    @DisplayName("Given 老 .xml 文本,When XmlToDrlRuleConverter 跑,Then 产出 DRL 文本")
    class XmlToDrl {

        @Test
        @DisplayName("最简老 .xml rule 节点 → 顶层 metadata 正确 + lhs 留 TODO")
        void simplest() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<rule-set>\n" +
                "  <rule name=\"R1\" salience=\"5\"/>\n" +
                "</rule-set>\n";
            String drl = xmlConverter.convert(xml);
            assertTrue(drl.contains("rule \"R1\""), "应含 rule \"R1\",实际:" + drl);
            assertTrue(drl.contains("salience 5"), "应含 salience 5,实际:" + drl);
            // lhs 留 TODO 注释(同 V5.42.3b 决定)
            assertTrue(drl.contains("TODO"), "lhs 留 TODO,实际:" + drl);
            assertTrue(drl.contains("// V5.42.6"), "应含 V5.42.6 标识,实际:" + drl);
        }

        @Test
        @DisplayName("多 rule 顺序保留")
        void multipleRules() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<rule-set>\n" +
                "  <rule name=\"R1\" salience=\"1\"/>\n" +
                "  <rule name=\"R2\" salience=\"2\"/>\n" +
                "  <rule name=\"R3\" salience=\"3\"/>\n" +
                "</rule-set>\n";
            String drl = xmlConverter.convert(xml);
            int i1 = drl.indexOf("rule \"R1\"");
            int i2 = drl.indexOf("rule \"R2\"");
            int i3 = drl.indexOf("rule \"R3\"");
            assertTrue(i1 > 0 && i2 > i1 && i3 > i2,
                "rule 顺序 R1 < R2 < R3,实际位置: " + i1 + "," + i2 + "," + i3);
        }

        @Test
        @DisplayName("null / 空字符串 → DrlParseException")
        void nullOrEmpty() {
            assertThrows(DrlParseException.class, () -> xmlConverter.convert(null));
            assertThrows(DrlParseException.class, () -> xmlConverter.convert(""));
        }

        @Test
        @DisplayName("语法错(.xml 不合法)→ DrlParseException")
        void malformedXml() {
            String bad = "<?xml version=\"1.0\"?>\n<not-closed>";
            assertThrows(DrlParseException.class, () -> xmlConverter.convert(bad));
        }
    }

    // ============================================================
    // === DslToDrlConverter ===
    // ============================================================

    @Nested
    @DisplayName("Given .dsl mapping + .dslrd 文本,When DslToDrlConverter 跑,Then 产出 DRL")
    class DslToDrl {

        @Test
        @DisplayName("简单 when mapping:Applicant is at least {age} → 替换成 DRL template")
        void simpleWhen() {
            String dsl = "[when]Applicant is at least {age}=Applicant(age >= {age})\n";
            DslMappingSet mapping = new DslParser().parseAsSet(dsl);
            String dslrd = "rule \"R1\" when Applicant is at least 21 then end\n";
            String drl = dslConverter.convert(dslrd, mapping);
            assertTrue(drl.contains("Applicant(age >= 21)"),
                "natural language 应替换成 DRL template,实际:" + drl);
            assertTrue(drl.contains("rule \"R1\""), "应保留 rule name,实际:" + drl);
        }

        @Test
        @DisplayName("多个 mapping 都触发")
        void multipleMappings() {
            String dsl = "[when]Applicant is at least {age}=Applicant(age >= {age})\n" +
                "[when]Income is {n}=Applicant(income == {n})\n";
            DslMappingSet mapping = new DslParser().parseAsSet(dsl);
            String dslrd = "rule \"R1\" when Applicant is at least 21 and Income is 5000 then end\n";
            String drl = dslConverter.convert(dslrd, mapping);
            assertTrue(drl.contains("Applicant(age >= 21)"));
            assertTrue(drl.contains("Applicant(income == 5000)"));
        }

        @Test
        @DisplayName("then 段也替换")
        void thenReplacement() {
            String dsl = "[then]Approve=approve();\n" +
                "[when]Income is {n}=Applicant(income == {n})\n";
            DslMappingSet mapping = new DslParser().parseAsSet(dsl);
            String dslrd = "rule \"R1\" when Income is 5000 then Approve end\n";
            String drl = dslConverter.convert(dslrd, mapping);
            assertTrue(drl.contains("approve();"), "then 段应替换,实际:" + drl);
        }

        @Test
        @DisplayName("DSL 找不到匹配 key → 抛 DrlParseException(防止 .dsl 漏 mapping 静默丢)")
        void unmappedKey() {
            String dsl = "[when]Income is {n}=Applicant(income == {n})\n";
            DslMappingSet mapping = new DslParser().parseAsSet(dsl);
            String dslrd = "rule \"R1\" when Unknown pattern here then end\n";
            assertThrows(DrlParseException.class, () -> dslConverter.convert(dslrd, mapping));
        }

        @Test
        @DisplayName("ANY scope 在 when 查询时被命中(* 段作为通用 mapping)")
        void anyScopeFallback() {
            String dsl = "[*]Common {x}=X(x == {x})\n";
            DslMappingSet mapping = new DslParser().parseAsSet(dsl);
            String dslrd = "rule \"R1\" when Common 100 then end\n";
            String drl = dslConverter.convert(dslrd, mapping);
            assertTrue(drl.contains("X(x == 100)"),
                "ANY scope 在 when 查询应命中,实际:" + drl);
        }

        @Test
        @DisplayName("null / 空 → DrlParseException")
        void nullOrEmpty() {
            DslMappingSet mapping = new DslParser().parseAsSet("");
            assertThrows(DrlParseException.class,
                () -> dslConverter.convert(null, mapping));
            assertThrows(DrlParseException.class,
                () -> dslConverter.convert("", mapping));
            assertThrows(DrlParseException.class,
                () -> dslConverter.convert("rule \"R1\" when X then end", null));
        }
    }

    // ============================================================
    // === 输出格式稳定性 ===
    // ============================================================

    @Nested
    @DisplayName("Given 转换输出,When 检查,Then 格式稳定")
    class OutputFormat {

        @Test
        @DisplayName("DslToDrl 输出含 V5.42.6 标识注释")
        void dslToDrlMarker() {
            String dsl = "[when]X=Applicant()\n";
            DslMappingSet mapping = new DslParser().parseAsSet(dsl);
            String drl = dslConverter.convert("rule \"R1\" when X then end\n", mapping);
            assertTrue(drl.contains("// V5.42.6"), "应含 V5.42.6 标识,实际:" + drl);
        }
    }
}
