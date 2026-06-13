package com.ruleforge.ir.pmml;

import com.ruleforge.model.decisiontree.DecisionTree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.pmml4s.model.Model;
import org.pmml4s.model.TreeModel;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V5.41 — PmmlTreeDeserializer 反序列化 BDD。
 *
 * <p>5 BDD 分 2 组:基本反序列化 / V5.41 字段填充 / error path。
 */
@DisplayName("V5.41 — PmmlTreeDeserializer BDD")
class PmmlTreeDeserializerTest {

    private TreeModel loadFixture() throws Exception {
        try (InputStream is = getClass().getResourceAsStream(
            "/ir/fixtures/simple-tree.pmml")) {
            return (TreeModel) Model.fromInputStream(is);
        }
    }

    @Nested
    @DisplayName("Group 1 — 基本反序列化")
    class Basic {

        @Test
        @DisplayName("Given PMML TreeModel,When deserialize,Then 拿到 DecisionTree 非 null")
        void deserializesToNonNull() throws Exception {
            PmmlTreeDeserializer d = new PmmlTreeDeserializer();
            DecisionTree tree = d.deserialize(loadFixture());
            assertNotNull(tree);
        }

        @Test
        @DisplayName("Given V5.41.3 阶段只填顶层,When deserialize,Then variableTreeNode 留 null(待 V5.41.4 填)")
        void childrenLeftNull() throws Exception {
            PmmlTreeDeserializer d = new PmmlTreeDeserializer();
            DecisionTree tree = d.deserialize(loadFixture());
            assertNull(tree.getVariableTreeNode(),
                "V5.41.3 scope: variableTreeNode left null, V5.41.4 fills from PMML <Node> tree");
        }
    }

    @Nested
    @DisplayName("Group 2 — V5.41 PMML 字段填充")
    class PmmlFields {

        @Test
        @DisplayName("Given fixture missingValueStrategy=lastPrediction,noTrueChildStrategy=returnLastPrediction,"
            + "When deserialize,Then missingValueStrategy + defaultChild + splitCharacteristic 正确"
            + "(pmml4s 1.5.6 splitCharacteristic 默认 'multiSplit',非 null)"
            + "(functionName 留 null,pmml4s TreeModel 无 public method)")
        void copiesAllPmmlFields() throws Exception {
            PmmlTreeDeserializer d = new PmmlTreeDeserializer();
            DecisionTree tree = d.deserialize(loadFixture());
            assertEquals("lastPrediction", tree.getMissingValueStrategy());
            // noTrueChildStrategy=returnLastPrediction → defaultChild=returnLastPrediction
            assertEquals("returnLastPrediction", tree.getDefaultChild());
            // fixture 不传 splitCharacteristic,pmml4s 1.5.6 默认 'multiSplit'(PMML 4.4 spec 是 'multiSplit' / 'binarySplit')
            assertEquals("multiSplit", tree.getSplitCharacteristic());
            assertNull(tree.getFunctionName(),
                "pmml4s TreeModel 1.5.6 不暴露 public functionName()");
        }

        @Test
        @DisplayName("Given V5.40 老 RuleForge XML 反序列化的 DecisionTree,When 不写 V5.41 字段,Then 4 字段全 null")
        void v540CompatDefaults() {
            DecisionTree tree = new DecisionTree();
            assertNull(tree.getMissingValueStrategy());
            assertNull(tree.getDefaultChild());
            assertNull(tree.getFunctionName());
            assertNull(tree.getSplitCharacteristic());
        }
    }

    @Nested
    @DisplayName("Group 3 — Error path")
    class Errors {

        @Test
        @DisplayName("Given null TreeModel,When deserialize,Then 抛 IllegalArgumentException")
        void rejectsNull() {
            PmmlTreeDeserializer d = new PmmlTreeDeserializer();
            assertThrows(IllegalArgumentException.class, () -> d.deserialize(null));
        }
    }
}
