package com.ruleforge.ir.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.41.5 — LegacyXmlMigrator 顶层分派 BDD。
 *
 * <p>5 BDD:1 happy path(scorecard)+ 1 happy path(tree)+ 3 error path(unsupported root /
 * empty / malformed)。
 */
@DisplayName("V5.41.5 — LegacyXmlMigrator BDD")
class LegacyXmlMigratorTest {

    private final LegacyXmlMigrator migrator = new LegacyXmlMigrator();

    @Test
    @DisplayName("Given 老 .xml scorecard,When migrate,Then 走 XmlToPmmlScorecardConverter 路径 + 标 targetFormat=\"pmml\"")
    void migratesScorecard() {
        String xml = ""
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rule-config><scorecard name=\"sc1\" scoring-type=\"Sum\" "
            + "assign-target-type=\"Var\" var=\"score\"></scorecard></rule-config>";
        LegacyXmlMigrator.MigrationResult r = migrator.migrate(xml);
        assertNotNull(r);
        assertEquals("pmml", r.getTargetFormat());
        assertTrue(r.getContent().contains("<Scorecard"), "expected <Scorecard> in output");
    }

    @Test
    @DisplayName("Given 老 .xml decision-tree,When migrate,Then 走 XmlToPmmlTreeConverter 路径 + 标 targetFormat=\"pmml\"")
    void migratesDecisionTree() {
        String xml = ""
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rule-config><decision-tree name=\"t1\" salience=\"0\"></decision-tree></rule-config>";
        LegacyXmlMigrator.MigrationResult r = migrator.migrate(xml);
        assertNotNull(r);
        assertEquals("pmml", r.getTargetFormat());
        assertTrue(r.getContent().contains("<TreeModel"), "expected <TreeModel> in output");
    }

    @Test
    @DisplayName("Given 老 .xml decision-table,When migrate,Then 走 XmlToDmnTableConverter + 标 targetFormat=\"dmn\"")
    void migratesDecisionTable() {
        String xml = ""
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rule-config><decision-table name=\"tier\" salience=\"0\">\n"
            + "  <columns><column num=\"0\" name=\"age\" type=\"Criteria\" datatype=\"Integer\"/></columns>\n"
            + "  <rows><row num=\"0\"><cell col=\"0\" value=\"-\"/></row></rows>\n"
            + "</decision-table></rule-config>";
        LegacyXmlMigrator.MigrationResult r = migrator.migrate(xml);
        assertNotNull(r);
        assertEquals("dmn", r.getTargetFormat());
        assertTrue(r.getContent().contains("<definitions"), "expected DMN <definitions> in output");
    }

    @Test
    @DisplayName("Given 老 .xml rule(root=rule),When migrate,Then V5.43.1 走 XmlToDrlRuleConverter + targetFormat=\"drl\"")
    void migratesRuleRoot() {
        // V5.41.5 留的 V5.42 TODO 在 V5.43.1 填了 — 不再抛 XmlMigrationException,
        // 走 XmlToDrlRuleConverter 产出 drl
        String xml = "<?xml version=\"1.0\"?><rule-config><rule name=\"r1\"></rule></rule-config>";
        LegacyXmlMigrator.MigrationResult r = migrator.migrate(xml);
        assertNotNull(r);
        assertEquals("drl", r.getTargetFormat());
        assertTrue(r.getContent().contains("rule \"r1\""),
            "expected rule \"r1\" in DRL output, got: " + r.getContent());
    }

    @Test
    @DisplayName("Given 未识别 root 元素,When migrate,Then 抛 XmlMigrationException")
    void rejectsUnrecognizedRoot() {
        String xml = "<?xml version=\"1.0\"?><rule-config><unknown-root/></rule-config>";
        assertThrows(XmlMigrationException.class, () -> migrator.migrate(xml));
    }

    @Test
    @DisplayName("Given null/空字符串,When migrate,Then 抛 XmlMigrationException")
    void rejectsEmptyContent() {
        assertThrows(XmlMigrationException.class, () -> migrator.migrate(null));
        assertThrows(XmlMigrationException.class, () -> migrator.migrate(""));
    }
}
