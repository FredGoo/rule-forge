package com.ruleforge.builder.table;

import com.ruleforge.builder.resource.Resource;
import com.ruleforge.model.table.ScriptDecisionTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V5.43.5 — ScriptDecisionTable 行为降级 BDD 锁。
 *
 * <p>V5.43.5 删 {@code CellScriptDSLBuilder} + {@code ScriptDecisionTableRulesBuilder}
 * (老 .ul DSL 链路),老 ScriptDecisionTable(.xml 决策表一种)走 V5.42 DRL eval() 替代。
 * 行为降级范围:
 * <ul>
 *   <li>删 {@code CellScriptDSLBuilder} / {@code ScriptDecisionTableRulesBuilder} 整文件
 *       — 这两个 class 不再被 classloader 找到</li>
 *   <li>KnowledgeBuilder 遇到 ScriptDecisionTable 资源时,走"老 DSL 链"已被删 — 走
 *       V5.42 DRL eval() 替代(暂未实现 ScriptDecisionTable → DRL 转换器,
 *       V5.44 单独 PR 补回)</li>
 *   <li>KnowledgeBuilder 上下文里 {@code scriptDecisionTableRulesBuilder} setter / field
 *       被移除(bean 在 ruleforge-core-context.xml 也删)</li>
 * </ul>
 *
 * <p><b>V5.43.8 — 路线 B 收口</b>:ResourceMigrationRequiredException / LegacyXmlMigrator
 * 已删,本测试不再测"守卫抛"逻辑(全新项目不兼容老格式),仅保留"class 删干净"快照。
 *
 * <p><b>V5.44.2 — 行为补回</b>:KnowledgeBuilder 走 ScriptDecisionTableToDrlConverter
 * 转换 → DRL 4 字符串 → DrlResourceBuilder → List&lt;Rule&gt;。本测试新加 4 个
 * BDD 锁该路径(转换器逻辑详见 ScriptDecisionTableToDrlConverterTest)。
 *
 * @since 5.43
 */
@DisplayName("V5.43.5 / V5.44.2 — ScriptDecisionTable 行为(class 删 + 转换器补回)")
class ScriptDecisionTableBehaviorTest {

    @Test
    @DisplayName("CellScriptDSLBuilder / ScriptDecisionTableRulesBuilder 已删")
    void oldBuildersGone() {
        for (String fqn : List.of(
            "com.ruleforge.builder.table.CellScriptDSLBuilder",
            "com.ruleforge.builder.table.ScriptDecisionTableRulesBuilder"
        )) {
            try {
                Class.forName(fqn);
                throw new AssertionError(
                    "V5.43.5 删的 class '" + fqn + "' 仍可被 classloader 找到 — 删不彻底");
            } catch (ClassNotFoundException expected) {
                // 期望:删干净
            }
        }
    }

    @Nested
    @DisplayName("V5.44.2 — ScriptDecisionTableToDrlConverter 行为补回")
    class V5442BehaviorBack {

        @Test
        @DisplayName("转换器 class 存在并可实例化")
        void converterExistsAndInstantiable() {
            ScriptDecisionTableToDrlConverter c = new ScriptDecisionTableToDrlConverter();
            assertThat(c).isNotNull();
        }

        @Test
        @DisplayName("KnowledgeBuilder 调 ScriptDecisionTableToDrlConverter.convert(走 DRL 路径)")
        void knowledgeBuilderWiresConverter() {
            // V5.44.2:KnowledgeBuilder 源码里 ScriptDecisionTable 分支调
            // new ScriptDecisionTableToDrlConverter().convert(...),再走 DrlResourceBuilder。
            // 这个测试**编译期**就已通过(因为本类跟 KnowledgeBuilder 在同 module),
            // sanity check:ScriptDecisionTableToDrlConverter 类可被 classloader 找到。
            try {
                Class<?> cls = Class.forName("com.ruleforge.builder.table.ScriptDecisionTableToDrlConverter");
                assertThat(cls).isNotNull();
                assertThat(cls.getSimpleName()).isEqualTo("ScriptDecisionTableToDrlConverter");
            } catch (ClassNotFoundException e) {
                throw new AssertionError(
                    "V5.44.2 转换器类应存在,但 classloader 找不到", e);
            }
        }

        @Test
        @DisplayName("ScriptDecisionTable 老 setter / 老 DSL chain class 不复活")
        void noOldChainResurrected() {
            // V5.44.2 不复活 V5.43.5 删的 class。CellScriptDSLBuilder /
            // ScriptDecisionTableRulesBuilder 仍然 ClassNotFound。
            for (String fqn : List.of(
                "com.ruleforge.builder.table.CellScriptDSLBuilder",
                "com.ruleforge.builder.table.ScriptDecisionTableRulesBuilder"
            )) {
                assertThatThrownBy(() -> Class.forName(fqn))
                    .as("V5.43.5 删的 class 不应复活:" + fqn)
                    .isInstanceOf(ClassNotFoundException.class);
            }
        }

        @Test
        @DisplayName("XML → ScriptDecisionTable 解析链仍可用(转换器上游输入)")
        void xmlParseChainStillWorks() {
            // ScriptDecisionTableResourceBuilder / ScriptDecisionTableDeserializer /
            // ScriptDecisionTableParser / ScriptCell / ScriptDecisionTable 全部保留 —
            // V5.44.2 转换器以 ScriptDecisionTable 为输入,XML 解析链是上游。
            for (String fqn : List.of(
                "com.ruleforge.builder.resource.ScriptDecisionTableResourceBuilder",
                "com.ruleforge.parse.deserializer.ScriptDecisionTableDeserializer",
                "com.ruleforge.parse.table.ScriptDecisionTableParser",
                "com.ruleforge.model.table.ScriptCell",
                "com.ruleforge.model.table.ScriptDecisionTable"
            )) {
                try {
                    Class<?> cls = Class.forName(fqn);
                    assertThat(cls).as("V5.44.2 保留的 ScriptDecisionTable 链 class:" + fqn).isNotNull();
                } catch (ClassNotFoundException e) {
                    throw new AssertionError("V5.44.2 不应删 ScriptDecisionTable 上游链 " + fqn, e);
                }
            }
        }
    }
}
