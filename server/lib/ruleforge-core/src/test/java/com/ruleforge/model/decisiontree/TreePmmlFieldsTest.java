package com.ruleforge.model.decisiontree;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * V5.41 — DecisionTree 新增 PMML 4.4 TreeModel 字段的 BDD 验证。
 *
 * <p>3 BDD 分 2 组:默认值 / setter-getter 往返。
 */
@DisplayName("V5.41 — DecisionTree PMML 字段")
class TreePmmlFieldsTest {

    @Nested
    @DisplayName("Group 1 — V5.40 兼容默认值")
    class Defaults {

        @Test
        @DisplayName("Given 新建 DecisionTree,When 读 4 个 V5.41 字段,Then 全部 null(老用法兼容)")
        void newInstanceHasNullPmmlFields() {
            DecisionTree tree = new DecisionTree();
            assertNull(tree.getMissingValueStrategy(), "missingValueStrategy must default null");
            assertNull(tree.getDefaultChild(), "defaultChild must default null");
            assertNull(tree.getFunctionName(), "functionName must default null");
            assertNull(tree.getSplitCharacteristic(), "splitCharacteristic must default null");
        }
    }

    @Nested
    @DisplayName("Group 2 — setter/getter 往返")
    class RoundTrip {

        @Test
        @DisplayName("Given 设 missingValueStrategy=lastPrediction,defaultChild=node_3,functionName=classification,"
            + "splitCharacteristic=informationGain,When 读,Then 全部正确")
        void setterGetterRoundTrip() {
            DecisionTree tree = new DecisionTree();
            tree.setMissingValueStrategy("lastPrediction");
            tree.setDefaultChild("node_3");
            tree.setFunctionName("classification");
            tree.setSplitCharacteristic("informationGain");

            assertEquals("lastPrediction", tree.getMissingValueStrategy());
            assertEquals("node_3", tree.getDefaultChild());
            assertEquals("classification", tree.getFunctionName());
            assertEquals("informationGain", tree.getSplitCharacteristic());
        }
    }
}
