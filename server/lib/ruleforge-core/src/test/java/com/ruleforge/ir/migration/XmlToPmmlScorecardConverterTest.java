package com.ruleforge.ir.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.41.5 — 老 .xml 评分卡 → PMML 4.4 转换器 BDD。
 *
 * <p>6 BDD 分 2 组:happy path / error path。
 */
@DisplayName("V5.41.5 — XmlToPmmlScorecardConverter BDD")
class XmlToPmmlScorecardConverterTest {

    private final XmlToPmmlScorecardConverter converter = new XmlToPmmlScorecardConverter();

    @Test
    @DisplayName("Given 标准老 .xml 评分卡,When convert,Then 产生含 Scorecard 根 + DataDictionary 的 PMML 4.4")
    void convertsBasicScorecard() {
        String xml = ""
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rule-config>\n"
            + "    <scorecard name=\"customer_score\" scoring-type=\"Sum\" "
            + "assign-target-type=\"Var\" var=\"score\" var-label=\"客户评分\">\n"
            + "    </scorecard>\n"
            + "</rule-config>";
        String pmml = converter.convert(xml);
        assertNotNull(pmml);
        assertTrue(pmml.contains("<PMML"), "expected <PMML> root");
        assertTrue(pmml.contains("http://www.dmg.org/PMML-4_4"), "expected PMML 4.4 namespace");
        assertTrue(pmml.contains("<Scorecard"), "expected <Scorecard> element");
        assertTrue(pmml.contains("modelName=\"customer_score\""), "expected modelName attr");
        assertTrue(pmml.contains("<DataDictionary"), "expected DataDictionary");
        assertTrue(pmml.contains("predicted_score"), "expected predicted_score field");
        assertTrue(pmml.contains("<Characteristics"), "expected Characteristics (placeholder)");
    }

    @Test
    @DisplayName("Given PMML 4.4 namespace,When convert,Then 顶层 attribute 齐全(initialScore/useReasonCodes/baselineMethod)")
    void emitsPmmlTopLevelAttributes() {
        String xml = "<rule-config><scorecard name=\"sc1\" scoring-type=\"Sum\" "
            + "assign-target-type=\"Var\" var=\"score\"></score-card></rule-config>";
        // 注:上面故意写错 </score-card> 让 case 失败,改用对的
        String xml2 = ""
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rule-config><scorecard name=\"sc1\" scoring-type=\"Sum\" "
            + "assign-target-type=\"Var\" var=\"score\"></scorecard></rule-config>";
        String pmml = converter.convert(xml2);
        assertTrue(pmml.contains("initialScore="), "expected initialScore attr");
        assertTrue(pmml.contains("useReasonCodes="), "expected useReasonCodes attr");
        assertTrue(pmml.contains("baselineMethod="), "expected baselineMethod attr");
        // V5.41.5 缺省值对齐 V5.41.3 deserializer null
        assertTrue(pmml.contains("baselineMethod=\"max\""), "expected baselineMethod=\"max\" default");
    }

    @Test
    @DisplayName("Given 老 .xml 缺 name attribute,When convert,Then 抛 XmlMigrationException")
    void rejectsMissingName() {
        String xml = "<?xml version=\"1.0\"?><rule-config>"
            + "<scorecard scoring-type=\"Sum\" assign-target-type=\"Var\"></scorecard>"
            + "</rule-config>";
        assertThrows(XmlMigrationException.class, () -> converter.convert(xml));
    }

    @Test
    @DisplayName("Given 老 .xml 不含 scorecard 元素,When convert,Then 抛 XmlMigrationException")
    void rejectsMissingScorecardElement() {
        String xml = "<?xml version=\"1.0\"?><rule-config><other/></rule-config>";
        assertThrows(XmlMigrationException.class, () -> converter.convert(xml));
    }

    @Test
    @DisplayName("Given null/空字符串,When convert,Then 抛 XmlMigrationException")
    void rejectsEmptyContent() {
        assertThrows(XmlMigrationException.class, () -> converter.convert(null));
        assertThrows(XmlMigrationException.class, () -> converter.convert(""));
    }

    @Test
    @DisplayName("Given 不合法 XML 字符串,When convert,Then 抛 XmlMigrationException(包 DocumentException)")
    void rejectsMalformedXml() {
        assertThrows(XmlMigrationException.class,
            () -> converter.convert("<<<not xml>>>"));
    }

    @Test
    @DisplayName("Given 老 .xml 评分卡,When peekTopLevel,Then 抽出 name/var/var-label/salience")
    void peekTopLevelExtractsNameAndVar() {
        String xml = ""
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rule-config>"
            + "<scorecard name=\"customer_score\" scoring-type=\"Sum\" "
            + "assign-target-type=\"Var\" var=\"score\" var-label=\"客户评分\" salience=\"5\">"
            + "</scorecard></rule-config>";
        var def = converter.peekTopLevel(xml);
        assertNotNull(def);
        assertEquals("customer_score", def.getName());
        assertEquals("score", def.getVariableName());
        assertEquals("客户评分", def.getVariableLabel());
        assertEquals(Integer.valueOf(5), def.getSalience());
    }
}
