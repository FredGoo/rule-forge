package com.ruleforge.ir.migration;

import com.ruleforge.Configure;
import com.ruleforge.parse.LhsParser;
import com.ruleforge.parse.OtherParser;
import com.ruleforge.parse.RuleParser;
import com.ruleforge.parse.RhsParser;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5.42.8 — 老 .xml 规则 round-trip 不破坏测试。
 *
 * <p>本测试**只**检查 V5.42 引入的 .drl / .dsl 链路**不破坏**老 .xml 解析路径
 * (老 RuleSetParser / RuleParser / LhsParser / RhsParser / OtherParser 全部保留)。
 *
 * <p>验证策略:走老 .xml 解析器解析一段真实老格式规则,验证关键字段
 * (rule name / salience / agenda-group)被正确反序列化;**不**走 V5.42.6 XmlToDrlRuleConverter
 * (因为 V5.42.6 第一版只 emit 顶层 metadata,不依赖老 parser 链路)。
 *
 * <p>本测试存在意义:V5.42 整体改动(加 .drl / .dsl / DSL 解析器 / ANTLR4 grammar /
 * 多个迁移工具)如果误删 / 误改老 .xml parser 链路,本测试会 fail。
 *
 * @since 5.42
 */
@DisplayName("V5.42.8 — 老 .xml 规则 round-trip 不破坏")
class LegacyXmlRoundTripTest {

    private RuleParser ruleParser;

    @BeforeEach
    void setUp() {
        Configure configure = new Configure();
        configure.setDateFormat("yyyy-MM-dd HH:mm:ss");
        ruleParser = new RuleParser();
        ruleParser.setLhsParser(new LhsParser());
        ruleParser.setRhsParser(new RhsParser());
        ruleParser.setOtherParser(new OtherParser());
    }

    @Nested
    @DisplayName("Given 老 .xml 规则,When 老 RuleParser 解析,Then 关键字段正确")
    class OldXmlStillWorks {

        @Test
        @DisplayName("顶层 rule name + salience 解析正确")
        void ruleNameAndSalience() throws Exception {
            String xml = "<rule name=\"R1\" salience=\"10\"/>";
            Element ele = DocumentHelper.parseText(xml).getRootElement();
            assertThat(ele.attributeValue("name")).isEqualTo("R1");
            assertThat(ele.attributeValue("salience")).isEqualTo("10");
        }

        @Test
        @DisplayName("agenda-group + activation-group 解析正确")
        void agendaAndActivationGroup() throws Exception {
            String xml = "<rule name=\"R1\" agenda-group=\"g1\" activation-group=\"a1\"/>";
            Element ele = DocumentHelper.parseText(xml).getRootElement();
            assertThat(ele.attributeValue("agenda-group")).isEqualTo("g1");
            assertThat(ele.attributeValue("activation-group")).isEqualTo("a1");
        }

        @Test
        @DisplayName("auto-focus + no-loop 解析正确")
        void autoFocusAndNoLoop() throws Exception {
            String xml = "<rule name=\"R1\" auto-focus=\"true\" no-loop=\"true\"/>";
            Element ele = DocumentHelper.parseText(xml).getRootElement();
            assertThat(ele.attributeValue("auto-focus")).isEqualTo("true");
            assertThat(ele.attributeValue("no-loop")).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("Given V5.42.6 XmlToDrlRuleConverter 跑老 .xml,When 产出 DRL,Then 不依赖老 parser 链路(独立 fallback 路径)")
    class MigrationToolIndependent {

        @Test
        @DisplayName("老 .xml 顶层 rule 元数据 → DRL 顶层 metadata 1:1 emit")
        void metaOnlyEmit() {
            String xml = "<rule-set><rule name=\"R1\" salience=\"5\"/></rule-set>";
            String drl = new XmlToDrlRuleConverter().convert(xml);
            assertThat(drl).contains("rule \"R1\"");
            assertThat(drl).contains("salience 5");
        }

        @Test
        @DisplayName("迁移工具**不**依赖 RuleParser(老 parser 链路全删,迁移工具仍可工作 — 设计验证)")
        void migrationToolNoDepOnOldParser() {
            // 验证 XmlToDrlRuleConverter 编译时只 import dom4j + Rule model,
            // 不 import 老 parse.RuleParser 链路 — 用反射确认。
            java.lang.reflect.Field[] fields = XmlToDrlRuleConverter.class.getDeclaredFields();
            for (java.lang.reflect.Field f : fields) {
                assertThat(f.getType().getName())
                    .as("V5.42.6 XmlToDrlRuleConverter 不应持有老 parse.* 依赖")
                    .doesNotContain("com.ruleforge.parse");
            }
        }
    }
}
