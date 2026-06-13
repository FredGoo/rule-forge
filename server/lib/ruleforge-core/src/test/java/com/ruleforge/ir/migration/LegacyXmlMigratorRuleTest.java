package com.ruleforge.ir.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V5.43.1 — LegacyXmlMigrator 接 V5.42.6 XmlToDrlRuleConverter BDD。
 *
 * <p>V5.41.5 时 LegacyXmlMigrator 给 {@code <decision-table>} / {@code <scorecard>} /
 * {@code <decision-tree>} 三个根分派到对应 converter,留 TODO:&lt;rule&gt; /
 * &lt;rule-set&gt; / &lt;ruleflow&gt; 根 V5.42 DRL 转换器尚未实现。
 *
 * <p>V5.43.1 填 TODO:把这三个根分派到 V5.42.6 {@link XmlToDrlRuleConverter}。
 * 输出 {@link MigrationResult#targetFormat} 应是 {@code "drl"}(跟 V5.40 / V5.41 的
 * {@code "dmn"} / {@code "pmml"} 一致风格)。
 *
 * <p>BDD 范围:
 * <ul>
 *   <li>&lt;rule&gt; 根(单 rule)→ drl</li>
 *   <li>&lt;rule-set&gt; 根(多 rule)→ drl</li>
 *   <li>&lt;ruleflow&gt; 根 → drl</li>
 *   <li>已有 &lt;decision-table&gt; / &lt;scorecard&gt; / &lt;decision-tree&gt; 不破坏(回归基线)</li>
 *   <li>未实现根 / null / 空 → XmlMigrationException</li>
 *   <li>无效 .xml → XmlMigrationException(透传底层 DrlParseException)</li>
 * </ul>
 *
 * @since 5.43
 */
@DisplayName("V5.43.1 — LegacyXmlMigrator.rule/rule-set/ruleflow → XmlToDrlRuleConverter")
class LegacyXmlMigratorRuleTest {

    private LegacyXmlMigrator migrator;

    @BeforeEach
    void setUp() {
        migrator = new LegacyXmlMigrator();
    }

    @Nested
    @DisplayName("Given 老 .xml <rule> 根,When migrate,Then targetFormat='drl' + 走 XmlToDrlRuleConverter")
    class RuleRoot {

        @Test
        @DisplayName("单 <rule> 根 → drl,顶层 metadata 1:1 emit")
        void singleRule() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<rule name=\"R1\" salience=\"5\"/>\n";
            LegacyXmlMigrator.MigrationResult r = migrator.migrate(xml);
            assertThat(r.getTargetFormat()).isEqualTo("drl");
            assertThat(r.getContent()).contains("rule \"R1\"");
            assertThat(r.getContent()).contains("salience 5");
            // V5.42.6 标识(V5.43.1 透传,不改 header)
            assertThat(r.getContent()).contains("// V5.42.6");
        }
    }

    @Nested
    @DisplayName("Given 老 .xml <rule-set> 根,When migrate,Then drl,多 rule 顺序保留")
    class RuleSetRoot {

        @Test
        @DisplayName("多 rule <rule-set> → drl,顺序 R1 < R2 < R3")
        void multipleRules() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<rule-set>\n" +
                "  <rule name=\"R1\" salience=\"1\"/>\n" +
                "  <rule name=\"R2\" salience=\"2\"/>\n" +
                "  <rule name=\"R3\" salience=\"3\"/>\n" +
                "</rule-set>\n";
            LegacyXmlMigrator.MigrationResult r = migrator.migrate(xml);
            assertThat(r.getTargetFormat()).isEqualTo("drl");
            String body = r.getContent();
            int i1 = body.indexOf("rule \"R1\"");
            int i2 = body.indexOf("rule \"R2\"");
            int i3 = body.indexOf("rule \"R3\"");
            assertThat(i1).isGreaterThan(0);
            assertThat(i2).isGreaterThan(i1);
            assertThat(i3).isGreaterThan(i2);
        }
    }

    @Nested
    @DisplayName("Given 老 .xml <ruleflow> 根,When migrate,Then drl")
    class RuleflowRoot {

        @Test
        @DisplayName("<ruleflow> 根 → drl(V5.42.6 XmlToDrlRuleConverter 已知透传 rule 子节点)")
        void ruleflowRoot() {
            // XmlToDrlRuleConverter 找 root.elements()("rule"),<ruleflow> 根内
            // 直接放 <rule> 子元素会被识别;若 <ruleflow> 包一层复杂结构,V5.43.1
            // scope 不展开 — 只验证分派走通,不验证完整 <ruleflow> 子树
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<ruleflow name=\"RF1\">\n" +
                "  <rule name=\"R1\" salience=\"5\"/>\n" +
                "</ruleflow>\n";
            LegacyXmlMigrator.MigrationResult r = migrator.migrate(xml);
            assertThat(r.getTargetFormat()).isEqualTo("drl");
            // V5.42.6 走的 convert 应该 emit 至少 V5.42.6 头
            assertThat(r.getContent()).contains("// V5.42.6");
        }
    }

    @Nested
    @DisplayName("Given V5.40 / V5.41 已有根,When migrate,Then 行为不变(回归基线)")
    class ExistingRootsUnchanged {

        @Test
        @DisplayName("<decision-table> → dmn(走 XmlToDmnTableConverter,不变)")
        void decisionTable() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<decision-table name=\"DT1\"/>\n";
            LegacyXmlMigrator.MigrationResult r = migrator.migrate(xml);
            assertThat(r.getTargetFormat()).isEqualTo("dmn");
        }

        @Test
        @DisplayName("<scorecard> → pmml(走 XmlToPmmlScorecardConverter,不变)")
        void scorecard() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<scorecard name=\"SC1\"/>\n";
            LegacyXmlMigrator.MigrationResult r = migrator.migrate(xml);
            assertThat(r.getTargetFormat()).isEqualTo("pmml");
        }

        @Test
        @DisplayName("<decision-tree> → pmml(走 XmlToPmmlTreeConverter,不变)")
        void decisionTree() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<decision-tree name=\"DT1\"/>\n";
            LegacyXmlMigrator.MigrationResult r = migrator.migrate(xml);
            assertThat(r.getTargetFormat()).isEqualTo("pmml");
        }
    }

    @Nested
    @DisplayName("Given 非法输入,When migrate,Then XmlMigrationException")
    class InvalidInputs {

        @Test
        @DisplayName("未识别根元素 → XmlMigrationException")
        void unknownRoot() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<unknown-thing name=\"X\"/>\n";
            assertThrows(XmlMigrationException.class, () -> migrator.migrate(xml));
        }

        @Test
        @DisplayName("null / 空 → XmlMigrationException")
        void nullOrEmpty() {
            assertThrows(XmlMigrationException.class, () -> migrator.migrate(null));
            assertThrows(XmlMigrationException.class, () -> migrator.migrate(""));
        }

        @Test
        @DisplayName("无根元素(裸文本)→ XmlMigrationException")
        void noRootElement() {
            assertThrows(XmlMigrationException.class, () -> migrator.migrate("plain text"));
        }
    }
}
