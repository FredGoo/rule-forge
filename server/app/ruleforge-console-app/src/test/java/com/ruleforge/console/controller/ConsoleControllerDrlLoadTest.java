package com.ruleforge.console.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.44.4 — CommonController.loadDrl 解析逻辑 BDD。
 *
 * <p>锁 3 件事:
 * <ol>
 *   <li>{@code parseDrlSummary} 解析合法 DRL 文本返 import 列表 + rule 名称列表</li>
 *   <li>语法错 DRL 不抛异常,返空 list(编辑场景常见 — 打开未保存的半成品 DRL)</li>
 *   <li>含 import 段的 DRL 在 imports 字段返对应路径(顺带 V5.44.3 grammar 验证)</li>
 * </ol>
 *
 * <p>本测试**不**测 HTTP 层(loadDrl 端点的 404 / 400 行为)— 那是 MockMvc 集成测试
 * 范围,本类只测解析逻辑的纯函数(避免拉起整个 CommonController + 10+ bean 依赖)。
 * 端点契约(controller 路径 + 参数)由 console-app @SpringBootTest 链路覆盖。
 *
 * @since 5.44
 */
@DisplayName("V5.44.4 — CommonController loadDrl 解析逻辑 BDD")
class ConsoleControllerDrlLoadTest {

    // ============================================================
    // === V5.44.4 BDD 1 — 合法 DRL 解析 ===
    // ============================================================

    @Nested
    @DisplayName("Given 合法 DRL 文本,When parseDrlSummary,Then 返 import + rule 名称列表")
    class ValidDrl {

        @Test
        @DisplayName("最简 DRL 文本 → 0 import + 1 rule name")
        void simplest() {
            CommonController.DrlFileSummary summary = CommonController.parseDrlSummary(
                "rule \"R1\" when Applicant(age > 18) then end");
            assertNotNull(summary);
            assertEquals(0, summary.getImports().size(), "无 import 段,imports 列表空");
            assertEquals(1, summary.getRuleNames().size());
            assertEquals("R1", summary.getRuleNames().get(0));
        }

        @Test
        @DisplayName("多 rule + 1 import → 1 import + N rule names")
        void multipleRulesWithImport() {
            String drl =
                "import \"libs/variables.drl\";\n"
                + "rule \"R1\" when Applicant(age > 18) then end\n"
                + "rule \"R2\" when Applicant(income > 5000) then end";
            CommonController.DrlFileSummary summary = CommonController.parseDrlSummary(drl);
            assertEquals(1, summary.getImports().size());
            assertEquals("libs/variables.drl", summary.getImports().get(0));
            assertEquals(2, summary.getRuleNames().size());
            assertTrue(summary.getRuleNames().contains("R1"));
            assertTrue(summary.getRuleNames().contains("R2"));
        }
    }

    // ============================================================
    // === V5.44.4 BDD 2 — 语法错 DRL 不抛,返空 ===
    // ============================================================

    @Nested
    @DisplayName("Given 语法错 DRL,When parseDrlSummary,Then 返空 list 不抛")
    class SyntaxErrorDrl {

        @Test
        @DisplayName("rule 缺 end 不抛,返空 list")
        void missingEnd() {
            CommonController.DrlFileSummary summary = CommonController.parseDrlSummary(
                "rule \"R1\" when Applicant(age > 18) then");
            // 不抛异常
            assertNotNull(summary);
            assertEquals(0, summary.getImports().size());
            assertEquals(0, summary.getRuleNames().size());
        }

        @Test
        @DisplayName("完全空文本返空 list 不抛")
        void emptyText() {
            CommonController.DrlFileSummary summary = CommonController.parseDrlSummary("");
            assertNotNull(summary);
            assertEquals(0, summary.getImports().size());
            assertEquals(0, summary.getRuleNames().size());
        }
    }

    // ============================================================
    // === V5.44.4 BDD 3 — V5.44.3 import 段在 loadDrl 端可见 ===
    // ============================================================

    @Nested
    @DisplayName("Given DRL 含 V5.44.3 import 段,When parseDrlSummary,Then imports 字段返路径")
    class DrlImportSegment {

        @Test
        @DisplayName("3 个 import 段按顺序保留在 imports 字段")
        void threeImports() {
            String drl =
                "import \"libs/variables.drl\";\n"
                + "import \"libs/actions.drl\";\n"
                + "import \"libs/constants.drl\";\n"
                + "rule \"R1\" when Applicant(age > 18) then end";
            CommonController.DrlFileSummary summary = CommonController.parseDrlSummary(drl);
            List<String> imports = summary.getImports();
            assertEquals(3, imports.size());
            assertEquals("libs/variables.drl", imports.get(0));
            assertEquals("libs/actions.drl", imports.get(1));
            assertEquals("libs/constants.drl", imports.get(2));
        }
    }
}
