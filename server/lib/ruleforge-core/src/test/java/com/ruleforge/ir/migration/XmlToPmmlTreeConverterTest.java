package com.ruleforge.ir.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.41.5 — 老 .xml 决策树 → PMML 4.4 转换器 BDD。
 *
 * <p>5 BDD:happy path + 3 error path + 1 placeholder structure 校验。
 */
@DisplayName("V5.41.5 — XmlToPmmlTreeConverter BDD")
class XmlToPmmlTreeConverterTest {

    private final XmlToPmmlTreeConverter converter = new XmlToPmmlTreeConverter();

    @Test
    @DisplayName("Given 标准老 .xml 决策树,When convert,Then 产生含 TreeModel 根 + placeholder Node 树的 PMML 4.4")
    void convertsBasicTree() {
        String xml = ""
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rule-config>\n"
            + "    <decision-tree name=\"loan_classifier\" salience=\"0\">\n"
            + "        <variable-tree-node name=\"age\" op=\"GreaterThen\" value=\"30\"/>\n"
            + "    </decision-tree>\n"
            + "</rule-config>";
        String pmml = converter.convert(xml);
        assertNotNull(pmml);
        assertTrue(pmml.contains("<PMML"), "expected <PMML> root");
        assertTrue(pmml.contains("http://www.dmg.org/PMML-4_4"));
        assertTrue(pmml.contains("<TreeModel"), "expected <TreeModel> element");
        assertTrue(pmml.contains("modelName=\"loan_classifier\""));
        assertTrue(pmml.contains("missingValueStrategy="), "expected missingValueStrategy attr");
        assertTrue(pmml.contains("noTrueChildStrategy="), "expected noTrueChildStrategy attr");
        assertTrue(pmml.contains("<DataDictionary"));
        assertTrue(pmml.contains("predicted_label"), "expected predicted_label field");
    }

    @Test
    @DisplayName("Given 老 .xml 决策树,When convert,Then emit 1 个 root Node + 1 个 leaf Node 占位")
    void emitsPlaceholderNodeTree() {
        String xml = ""
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rule-config><decision-tree name=\"t1\" salience=\"0\">"
            + "<variable-tree-node name=\"x\"/></decision-tree></rule-config>";
        String pmml = converter.convert(xml);
        // 占位结构:1 个 root Node(无 predicate)+ 1 个 leaf(带 <True/>)
        int rootNodeCount = countOccurrences(pmml, "<Node ");
        assertTrue(rootNodeCount >= 2,
            "expected ≥2 <Node> elements (root + leaf placeholder), got " + rootNodeCount);
        assertTrue(pmml.contains("<True/>"), "expected <True/> leaf predicate");
    }

    @Test
    @DisplayName("Given 老 .xml 缺 name attribute,When convert,Then 抛 XmlMigrationException")
    void rejectsMissingName() {
        String xml = "<?xml version=\"1.0\"?><rule-config>"
            + "<decision-tree salience=\"0\"></decision-tree></rule-config>";
        assertThrows(XmlMigrationException.class, () -> converter.convert(xml));
    }

    @Test
    @DisplayName("Given 老 .xml 不含 decision-tree 元素,When convert,Then 抛 XmlMigrationException")
    void rejectsMissingDecisionTreeElement() {
        String xml = "<?xml version=\"1.0\"?><rule-config><other/></rule-config>";
        assertThrows(XmlMigrationException.class, () -> converter.convert(xml));
    }

    @Test
    @DisplayName("Given null/空字符串,When convert,Then 抛 XmlMigrationException")
    void rejectsEmptyContent() {
        assertThrows(XmlMigrationException.class, () -> converter.convert(null));
        assertThrows(XmlMigrationException.class, () -> converter.convert(""));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
