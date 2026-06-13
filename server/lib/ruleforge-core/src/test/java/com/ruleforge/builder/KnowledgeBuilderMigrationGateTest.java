package com.ruleforge.builder;

import com.ruleforge.builder.resource.Resource;
import com.ruleforge.exception.ResourceMigrationRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V5.43.4 — KnowledgeBuilder 老 .ul / .xml rule 路径守卫 BDD。
 *
 * <p>V5.43 删老 RuleSetResourceBuilder / DSLRuleSetBuilder 的 .xml rule / .ul DSL 解析路径后,
 * 运维**必须**先跑 {@code LegacyXmlMigrator.migrate()} 把 .xml rule / .ul 转成 .drl,
 * 否则 KnowledgeBuilder 加载规则库会 silent 0 rule(全部资源被吞)。
 *
 * <p>V5.43.4 加守卫:遇到 .ul 资源 / .xml rule-rule-set-ruleflow 根元素 → 抛
 * {@link ResourceMigrationRequiredException} 显式失败,运维知道要跑迁移。
 *
 * <p>守卫**不**拦:library .xml(action-library / variable-library / constant-library /
 * parameter-library)、table / scorecard / tree / crosstab .xml、.dmn、.pmml — 这些是
 * V5.40 / V5.41 / V5.43 仍兜底 / V5.43 仍支持。
 *
 * <p>本测试**只**覆盖守卫逻辑,不依赖 KnowledgeBuilder 完整实例化(避免 Spring context
 * 复杂度)— 用 {@link com.ruleforge.builder.AbstractBuilder#gateLegacyPath} 公开方法
 * 反射测。
 *
 * @since 5.43
 */
@DisplayName("V5.43.4 — KnowledgeBuilder 老 .ul / .xml rule 路径守卫")
class KnowledgeBuilderMigrationGateTest {

    private KnowledgeBuilder knowledgeBuilder;

    @BeforeEach
    void setUp() {
        knowledgeBuilder = new KnowledgeBuilder();
    }

    @Nested
    @DisplayName("Given .ul 资源,When gate,Then 抛 ResourceMigrationRequiredException")
    class UlResources {

        @Test
        @DisplayName(".ul 后缀直接抛")
        void ulSuffix() {
            Resource r = resource("rules/r1.ul", "<rule-set/>");
            assertThatThrownBy(() -> invokeGate(r))
                .isInstanceOf(ResourceMigrationRequiredException.class)
                .hasMessageContaining("legacy .ul DSL format");
        }
    }

    @Nested
    @DisplayName("Given .xml rule / rule-set / ruleflow 根,When gate,Then 抛 ResourceMigrationRequiredException")
    class XmlRuleRoots {

        @Test
        @DisplayName("<rule> 根 → 抛")
        void ruleRoot() {
            String xml = "<rule name=\"R1\"/>";
            Resource r = resource("rules/r1.xml", xml);
            assertThatThrownBy(() -> invokeGate(r))
                .isInstanceOf(ResourceMigrationRequiredException.class)
                .hasMessageContaining("legacy <rule> XML format");
        }

        @Test
        @DisplayName("<rule-set> 根 → 抛")
        void ruleSetRoot() {
            String xml = "<rule-set><rule name=\"R1\"/></rule-set>";
            Resource r = resource("rules/r1.xml", xml);
            assertThatThrownBy(() -> invokeGate(r))
                .isInstanceOf(ResourceMigrationRequiredException.class)
                .hasMessageContaining("legacy <rule-set> XML format");
        }

        @Test
        @DisplayName("<ruleflow> 根 → 抛")
        void ruleflowRoot() {
            String xml = "<ruleflow name=\"RF1\"><rule name=\"R1\"/></ruleflow>";
            Resource r = resource("rules/rf1.xml", xml);
            assertThatThrownBy(() -> invokeGate(r))
                .isInstanceOf(ResourceMigrationRequiredException.class)
                .hasMessageContaining("legacy <ruleflow> XML format");
        }
    }

    @Nested
    @DisplayName("Given library .xml 资源,When gate,Then 不抛(V5.43 保留)")
    class LibraryXmlKept {

        @Test
        @DisplayName("action-library .xml → 不抛")
        void actionLibrary() {
            String xml = "<action-library><spring-beans/></action-library>";
            Resource r = resource("libs/actions.xml", xml);
            invokeGate(r); // 不抛即过
        }

        @Test
        @DisplayName("variable-library .xml → 不抛")
        void variableLibrary() {
            String xml = "<variable-library><variables/></variable-library>";
            Resource r = resource("libs/variables.xml", xml);
            invokeGate(r);
        }
    }

    @Nested
    @DisplayName("Given table / scorecard / tree .xml 资源,When gate,Then 不抛(V5.40 / V5.41 兜底)")
    class TableChainXmlKept {

        @Test
        @DisplayName("decision-table .xml → 不抛")
        void decisionTable() {
            String xml = "<decision-table name=\"DT1\"/>";
            Resource r = resource("tables/dt1.xml", xml);
            invokeGate(r);
        }

        @Test
        @DisplayName("scorecard .xml → 不抛")
        void scorecard() {
            String xml = "<scorecard name=\"SC1\"/>";
            Resource r = resource("scorecards/sc1.xml", xml);
            invokeGate(r);
        }

        @Test
        @DisplayName("decision-tree .xml → 不抛")
        void decisionTree() {
            String xml = "<decision-tree name=\"DT1\"/>";
            Resource r = resource("trees/dt1.xml", xml);
            invokeGate(r);
        }
    }

    @Nested
    @DisplayName("Given .dmn / .pmml 资源,When gate,Then 不抛(不是 .xml 资源,守卫不看后缀外的路径)")
    class NonXmlResources {

        @Test
        @DisplayName(".dmn 后缀 → 不抛")
        void dmn() {
            Resource r = resource("decisions/d1.dmn", "<definitions/>");
            invokeGate(r);
        }

        @Test
        @DisplayName(".pmml 后缀 → 不抛")
        void pmml() {
            Resource r = resource("models/m1.pmml", "<PMML/>");
            invokeGate(r);
        }

        @Test
        @DisplayName(".drl 后缀 → 不抛(新格式,守卫不拦)")
        void drl() {
            Resource r = resource("rules/r1.drl", "rule \"R1\" when then end");
            invokeGate(r);
        }
    }

    // ============================================================
    // === 工具方法 ===
    // ============================================================

    private static Resource resource(String path, String content) {
        // Resource 是 immutable @AllArgsConstructor(content, path, projectVersion)
        // 守卫**只**看 path 跟 dom4j root element name(content 给守卫 parse 用)
        return new Resource(
            "<?xml version=\"1.0\"?>" + content,
            path,
            null
        );
    }

    private void invokeGate(Resource r) {
        try {
            java.lang.reflect.Method m = KnowledgeBuilder.class.getDeclaredMethod("gateLegacyPath", Resource.class);
            m.setAccessible(true);
            m.invoke(knowledgeBuilder, r);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
