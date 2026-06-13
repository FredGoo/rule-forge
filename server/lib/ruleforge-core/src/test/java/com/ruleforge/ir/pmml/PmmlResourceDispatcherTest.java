package com.ruleforge.ir.pmml;

import com.ruleforge.model.decisiontree.DecisionTree;
import com.ruleforge.model.scorecard.ScorecardDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.41 — PmmlResourceDispatcher 路径分发 BDD。
 *
 * <p>4 BDD 分 2 组:Scorecard / TreeModel / error path。
 */
@DisplayName("V5.41 — PmmlResourceDispatcher BDD")
class PmmlResourceDispatcherTest {

    @Test
    @DisplayName("Given .pmml Scorecard 文件,When dispatch,Then 拿到 ScorecardDefinition")
    void dispatchesScorecard() throws Exception {
        String content = readFixture("simple-scorecard.pmml");
        PmmlResourceDispatcher d = new PmmlResourceDispatcher();
        Object result = d.dispatch("rules/customer-score.pmml", content);
        assertNotNull(result);
        assertTrue(result instanceof ScorecardDefinition,
            "Expected ScorecardDefinition, got " + result.getClass().getName());
        assertEquals("customer_score", ((ScorecardDefinition) result).getName());
    }

    @Test
    @DisplayName("Given .pmml TreeModel 文件,When dispatch,Then 拿到 DecisionTree")
    void dispatchesTreeModel() throws Exception {
        String content = readFixture("simple-tree.pmml");
        PmmlResourceDispatcher d = new PmmlResourceDispatcher();
        Object result = d.dispatch("rules/loan-classifier.pmml", content);
        assertNotNull(result);
        assertTrue(result instanceof DecisionTree,
            "Expected DecisionTree, got " + result.getClass().getName());
    }

    @Test
    @DisplayName("Given 非 .pmml 路径(如 .dmn),When dispatch,Then 抛 IllegalArgumentException")
    void rejectsNonPmmlPath() throws Exception {
        PmmlResourceDispatcher d = new PmmlResourceDispatcher();
        assertThrows(IllegalArgumentException.class,
            () -> d.dispatch("rules/customer.dmn", "<not-pmml/>"));
    }

    @Test
    @DisplayName("Given null/empty content,When dispatch,Then 抛 IllegalArgumentException")
    void rejectsEmptyContent() {
        PmmlResourceDispatcher d = new PmmlResourceDispatcher();
        assertThrows(IllegalArgumentException.class,
            () -> d.dispatch("rules/x.pmml", (String) null));
        assertThrows(IllegalArgumentException.class,
            () -> d.dispatch("rules/x.pmml", ""));
    }

    private String readFixture(String name) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/ir/fixtures/" + name)) {
            assertNotNull(is, "fixture not found: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
