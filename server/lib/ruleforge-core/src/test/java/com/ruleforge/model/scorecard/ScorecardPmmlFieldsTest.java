package com.ruleforge.model.scorecard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * V5.41 — ScorecardDefinition 新增 PMML 4.4 字段的 BDD 验证。
 *
 * <p>3 BDD 分 2 组:默认值 / setter-getter 往返。
 * <p>目的:确保 V5.41 加的 4 个新字段{@code useReasonCodes / initialScore /
 * baselineMethod / reasonCodeAlgorithm}默认是 {@code null}(保持 V5.40 兼容),
 * 走老 .xml 反序列化路径时这些字段不被填,走 V5.41 .pmml 反序列化时由
 * PmmlScorecardDeserializer 填。
 */
@DisplayName("V5.41 — ScorecardDefinition PMML 字段")
class ScorecardPmmlFieldsTest {

    @Nested
    @DisplayName("Group 1 — V5.40 兼容默认值")
    class Defaults {

        @Test
        @DisplayName("Given 新建 ScorecardDefinition,When 读 4 个 V5.41 字段,Then 全部 null(老用法兼容)")
        void newInstanceHasNullPmmlFields() {
            ScorecardDefinition def = new ScorecardDefinition();
            assertNull(def.getUseReasonCodes(), "useReasonCodes must default null");
            assertNull(def.getInitialScore(), "initialScore must default null");
            assertNull(def.getBaselineMethod(), "baselineMethod must default null");
            assertNull(def.getReasonCodeAlgorithm(), "reasonCodeAlgorithm must default null");
        }
    }

    @Nested
    @DisplayName("Group 2 — setter/getter 往返")
    class RoundTrip {

        @Test
        @DisplayName("Given 设 useReasonCodes=true,initialScore=0.0,baselineMethod=max,reasonCodeAlgorithm=pointsAbove,"
            + "When 读,Then 全部正确")
        void setterGetterRoundTrip() {
            ScorecardDefinition def = new ScorecardDefinition();
            def.setUseReasonCodes(true);
            def.setInitialScore(0.0);
            def.setBaselineMethod("max");
            def.setReasonCodeAlgorithm("pointsAbove");

            assertEquals(true, def.getUseReasonCodes());
            assertEquals(0.0, def.getInitialScore());
            assertEquals("max", def.getBaselineMethod());
            assertEquals("pointsAbove", def.getReasonCodeAlgorithm());
        }

        @Test
        @DisplayName("Given 已设字段再清成 null,When 读,Then null(V5.41 PmmlScorecardDeserializer 不写可清空)")
        void clearFieldsToNull() {
            ScorecardDefinition def = new ScorecardDefinition();
            def.setUseReasonCodes(true);
            def.setUseReasonCodes(null);
            def.setBaselineMethod("max");
            def.setBaselineMethod(null);

            assertNull(def.getUseReasonCodes());
            assertNull(def.getBaselineMethod());
        }
    }
}
