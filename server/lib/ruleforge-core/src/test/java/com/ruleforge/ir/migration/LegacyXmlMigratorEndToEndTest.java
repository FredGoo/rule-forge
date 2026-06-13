package com.ruleforge.ir.migration;

import com.ruleforge.ir.pmml.PmmlResourceDispatcher;
import com.ruleforge.model.decisiontree.DecisionTree;
import com.ruleforge.model.scorecard.ScorecardDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.41 — 老 .xml → migrator → .pmml → pmml4s parse → dispatcher → model 全链路 BDD。
 *
 * <p>验证 V5.41.5 {@link LegacyXmlMigrator} 产出的 .pmml 字符串能被 V5.41.4
 * {@link PmmlResourceDispatcher} 解析回 model 顶层字段(完整 cell/row 展开留 V5.41.7,
 * 本 BDD 验证"老 .xml 客户跑 migrator 至少能拿到顶层填好的 ScorecardDefinition /
 * DecisionTree,不会抛 exception")。
 *
 * <p>3 BDD 链路:scorecard / decision-tree / decision-table 路由。
 */
@DisplayName("V5.41 — LegacyXmlMigrator 全链路 BDD")
class LegacyXmlMigratorEndToEndTest {

    private final LegacyXmlMigrator migrator = new LegacyXmlMigrator();
    private final PmmlResourceDispatcher dispatcher = new PmmlResourceDispatcher();

    @Test
    @DisplayName("Given 老 .xml scorecard,When migrate→dispatch,Then 拿到 ScorecardDefinition 顶层字段")
    void scorecardFullChain() {
        String xml = ""
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rule-config>"
            + "<scorecard name=\"customer_score\" scoring-type=\"Sum\" "
            + "assign-target-type=\"Var\" var=\"score\" var-label=\"客户评分\" salience=\"3\">"
            + "</scorecard></rule-config>";

        LegacyXmlMigrator.MigrationResult migrated = migrator.migrate(xml);
        assertNotNull(migrated);
        assertEquals("pmml", migrated.getTargetFormat());
        assertTrue(migrated.getContent().contains("<Scorecard"),
            "expected <Scorecard> in migrated .pmml");

        // 走 PmmlResourceDispatcher 反向解析,验证产出 .pmml 能被 pmml4s 1.5.6 正确加载
        Object result = dispatcher.dispatch("rules/customer-score.pmml", migrated.getContent());
        assertNotNull(result);
        assertTrue(result instanceof ScorecardDefinition,
            "Expected ScorecardDefinition, got " + result.getClass().getName());
        ScorecardDefinition def = (ScorecardDefinition) result;
        assertEquals("customer_score", def.getName());
    }

    @Test
    @DisplayName("Given 老 .xml decision-tree,When migrate→dispatch,Then 拿到 DecisionTree 顶层字段")
    void decisionTreeFullChain() {
        String xml = ""
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rule-config><decision-tree name=\"loan_classifier\" salience=\"0\">"
            + "<variable-tree-node name=\"age\"/></decision-tree></rule-config>";

        LegacyXmlMigrator.MigrationResult migrated = migrator.migrate(xml);
        assertNotNull(migrated);
        assertEquals("pmml", migrated.getTargetFormat());
        assertTrue(migrated.getContent().contains("<TreeModel"),
            "expected <TreeModel> in migrated .pmml");

        Object result = dispatcher.dispatch("rules/loan-classifier.pmml", migrated.getContent());
        assertNotNull(result);
        assertTrue(result instanceof DecisionTree,
            "Expected DecisionTree, got " + result.getClass().getName());
    }

    @Test
    @DisplayName("Given 老 .xml decision-table,When migrate,Then 标 targetFormat=dmn(dispatcher 走 .dmn 不归本 BDD 校验)")
    void decisionTableRouteCorrectly() {
        String xml = ""
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rule-config><decision-table name=\"tier\" salience=\"0\">\n"
            + "  <columns><column num=\"0\" name=\"age\" type=\"Criteria\" datatype=\"Integer\"/></columns>\n"
            + "  <rows><row num=\"0\"><cell col=\"0\" value=\"-\"/></row></rows>\n"
            + "</decision-table></rule-config>";

        LegacyXmlMigrator.MigrationResult migrated = migrator.migrate(xml);
        assertNotNull(migrated);
        assertEquals("dmn", migrated.getTargetFormat(),
            "decision-table 走 V5.40.5 XmlToDmnTableConverter,不是 .pmml");
        assertTrue(migrated.getContent().contains("<definitions"),
            "expected DMN <definitions> in output");
    }
}
