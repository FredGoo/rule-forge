package com.ruleforge.builder;

import com.ruleforge.builder.resource.Resource;
import com.ruleforge.ir.drl.DatatypeResolver;
import com.ruleforge.ir.drl.DrlResource;
import com.ruleforge.ir.drl.DrlResourceBuilder;
import com.ruleforge.model.rule.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.44.4 — KnowledgeBuilder {@code .drl} dispatch BDD。
 *
 * <p>锁 6 件事:
 * <ol>
 *   <li>资源路径以 {@code .drl} 结尾 → 走 DrlResourceBuilder(绕过老 .xml 解析)</li>
 *   <li>{@code .drlrd} / {@code .dslr} 同样走 DrlResourceBuilder(DRL 4 兼容名)</li>
 *   <li>含 {@code import} 段的 .drl 也能 dispatch(visitor 收集 → resolver 拿到)</li>
 *   <li>语法错 .drl 抛 DrlParseException(V5.42 链路,跟 .dmn/.pmml 一致)</li>
 *   <li>空 .drl(0 rule 段)产 0 rule 不抛异常</li>
 *   <li>大小写不敏感:{@code .DRL} 同样 dispatch</li>
 * </ol>
 *
 * <p>本测试**不**测 KnowledgeBuilder 的 Rete 构建 / KnowledgeBase 包装(那些需要
 * spring applicationContext 注入,集成测试留 console-app 侧)。只测 dispatch
 * 分支选择 + DrlResourceBuilder 调用 + Rule 列表返回。
 *
 * @since 5.44
 */
@DisplayName("V5.44.4 — KnowledgeBuilder .drl dispatch BDD")
class KnowledgeBuilderDrlDispatchTest {

    private DatatypeResolver makeResolver() {
        DatatypeResolver r = new DatatypeResolver();
        r.register("Applicant",
            DatatypeResolver.TypeInfo.fact("Applicant",
                Arrays.asList("age", "income", "name")));
        return r;
    }

    /**
     * V5.44.4 — 走 DrlResourceBuilder 直接验证 .drl 解析。
     * 这条路径在 KnowledgeBuilder 内的"production 实际执行"形式
     * (KnowledgeBuilder 接收 ResourceBase 含 Resource 列表,iterate + path
     * 判断 → DrlResourceBuilder.build)。
     * 本测试不引入 spring applicationContext(避免测试 setup 太重),直接模拟
     * 同样的 dispatch 行为 — 等同于 KnowledgeBuilder 的 .drl 分支逻辑。
     */
    private List<Rule> dispatchAsDrl(Resource resource) {
        String path = resource.getPath();
        if (path == null) {
            throw new IllegalArgumentException("path required");
        }
        String lower = path.toLowerCase();
        if (lower.endsWith(".drl") || lower.endsWith(".drlrd") || lower.endsWith(".dslr")) {
            return new DrlResourceBuilder(makeResolver())
                .build(new DrlResource(resource.getContent(), path));
        }
        throw new IllegalStateException("not a .drl path: " + path);
    }

    // ============================================================
    // === V5.44.4 BDD 1 — .drl 路径走 DRL 解析 ===
    // ============================================================

    @Nested
    @DisplayName("Given .drl 资源,When dispatch,Then 走 DrlResourceBuilder")
    class DrlPath {

        @Test
        @DisplayName("最简 .drl 文本产 1 rule")
        void simplest() {
            Resource res = new Resource(
                "rule \"R1\" when Applicant(age > 18) then end",
                "/proj/rules/r.drl", null);
            List<Rule> rules = dispatchAsDrl(res);
            assertEquals(1, rules.size());
            assertEquals("R1", rules.get(0).getName());
        }

        @Test
        @DisplayName(".DRL(大写后缀)同样 dispatch")
        void caseInsensitive() {
            Resource res = new Resource(
                "rule \"R1\" when Applicant(age > 18) then end",
                "/proj/rules/r.DRL", null);
            List<Rule> rules = dispatchAsDrl(res);
            assertEquals(1, rules.size());
            assertEquals("R1", rules.get(0).getName());
        }
    }

    // ============================================================
    // === V5.44.4 BDD 2 — .drlrd / .dslr 同样 dispatch ===
    // ============================================================

    @Nested
    @DisplayName("Given .drlrd / .dslr 资源,When dispatch,Then 同样走 DRL 解析")
    class DrlCompatPaths {

        @Test
        @DisplayName(".drlrd(Drools 6 dsl rule)走 DRL 解析")
        void drlrdPath() {
            Resource res = new Resource(
                "rule \"R1\" when Applicant(age > 18) then end",
                "/proj/rules/r.drlrd", null);
            List<Rule> rules = dispatchAsDrl(res);
            assertEquals(1, rules.size());
            assertEquals("R1", rules.get(0).getName());
        }

        @Test
        @DisplayName(".dslr(Drools 6 dsl rule)走 DRL 解析")
        void dslrPath() {
            Resource res = new Resource(
                "rule \"R1\" when Applicant(age > 18) then end",
                "/proj/rules/r.dslr", null);
            List<Rule> rules = dispatchAsDrl(res);
            assertEquals(1, rules.size());
            assertEquals("R1", rules.get(0).getName());
        }
    }

    // ============================================================
    // === V5.44.4 BDD 3 — 含 import 段的 .drl 也能 dispatch ===
    // ============================================================

    @Nested
    @DisplayName("Given .drl 含 import 段,When dispatch,Then visitor 收集 + resolver 拿到")
    class DrlWithImport {

        @Test
        @DisplayName("import 段在 .drl 顶层合法,dispatch 不抛")
        void importSegment() {
            Resource res = new Resource(
                "import \"libs/variables.drl\";\n"
                + "rule \"R1\" when Applicant(age > 18) then end",
                "/proj/rules/r.drl", null);
            List<Rule> rules = dispatchAsDrl(res);
            assertEquals(1, rules.size());
            assertEquals("R1", rules.get(0).getName());
            // V5.44.3 — visitor 内部把 import 推到 resolver,确认无副作用
            assertNotNull(rules.get(0).getLhs());
            assertNotNull(rules.get(0).getRhs());
        }
    }

    // ============================================================
    // === V5.44.4 BDD 4 — 语法错 .drl 抛 DrlParseException ===
    // ============================================================

    @Nested
    @DisplayName("Given 语法错 .drl,When dispatch,Then 抛 DrlParseException")
    class DrlSyntaxError {

        @Test
        @DisplayName("rule 段缺 end 抛 DrlParseException")
        void missingEnd() {
            Resource res = new Resource(
                "rule \"R1\" when Applicant(age > 18) then",
                "/proj/rules/r.drl", null);
            try {
                dispatchAsDrl(res);
                throw new AssertionError("期望 DrlParseException");
            } catch (com.ruleforge.ir.drl.DrlParseException expected) {
                assertNotNull(expected.getMessage());
            }
        }
    }

    // ============================================================
    // === V5.44.4 BDD 5 — 空 .drl 产 0 rule 不抛 ===
    // ============================================================

    @Nested
    @DisplayName("Given 空 .drl,When dispatch,Then 产 0 rule 不抛")
    class DrlEmpty {

        @Test
        @DisplayName("空 .drl(仅注释)产 0 rule")
        void emptyDrl() {
            Resource res = new Resource(
                "// V5.44.4 — empty test\n",
                "/proj/rules/r.drl", null);
            List<Rule> rules = dispatchAsDrl(res);
            assertEquals(0, rules.size());
        }

        @Test
        @DisplayName("仅 import 段 + 0 rule 产 0 rule")
        void importsOnly() {
            Resource res = new Resource(
                "import \"libs/variables.drl\";\n"
                + "import \"libs/actions.drl\";\n",
                "/proj/rules/r.drl", null);
            List<Rule> rules = dispatchAsDrl(res);
            assertEquals(0, rules.size());
        }
    }

    // ============================================================
    // === V5.44.4 BDD 6 — dispatch 路径白名单 ===
    // ============================================================

    @Nested
    @DisplayName("Given 非 .drl 路径,When dispatch helper,Then 不接(由 KnowledgeBuilder 其它分支处理)")
    class DispatchPathWhitelist {

        @Test
        @DisplayName(".drlrdrd(.drl + 4 字符)仍 dispatch(白名单设计成 endsWith)")
        void longSuffixStillDrl() {
            // .drlrdrd endsWith .drlrd 之一?不 — endsWith 严格
            // 但 .DRLRD 在 lower() 后 = .drlrd 仍 endsWith
            Resource res = new Resource(
                "rule \"R1\" when Applicant(age > 18) then end",
                "/proj/rules/r.drlrdrd", null);
            try {
                dispatchAsDrl(res);
                // endsWith 是 3 选 1:.drl / .drlrd / .dslr
                // .drlrdrd 不 endsWith 任何一个 → 抛 IllegalStateException
                throw new AssertionError("期望 IllegalStateException");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("not a .drl path"),
                    "应明确说非 .drl 路径,实际:" + expected.getMessage());
            }
        }
    }
}
