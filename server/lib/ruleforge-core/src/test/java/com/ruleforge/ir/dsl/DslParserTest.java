package com.ruleforge.ir.dsl;

import com.ruleforge.ir.drl.DrlParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.42.3a — Drools 6 .dsl/.dslrd 解析器 BDD。
 *
 * <p>Drools 6 .dsl 是 "DSL mapping 文件":每一行 {@code [condition]This is {name}=...}
 * 形式;{@code [condition]} 是 scope 标签(when= lhs,then= rhs,*= 通用,*= 没 scope 时通用)。
 * {@code {name}} 是 placeholder,左出现展开成右边的 template。
 *
 * <p>4 组 BDD:
 * <ul>
 *   <li>解析 .dsl mapping entry:scope / key / template 拆对</li>
 *   <li>错误路径:空 key 抛 DrlParseException + 行号;缺 '=' 抛错</li>
 *   <li>列表语义:同 scope 多 entry 顺序保留;scope 大小写不敏感</li>
 *   <li>placeholder 名语法:V5.42.3a 要求 {name} 是 [a-zA-Z_][a-zA-Z0-9_]*,非合规 → 抛错</li>
 * </ul>
 *
 * <p>本类**不**测 ${} 展开器,那在 {@link PlaceholderExpanderTest}。
 *
 * @since 5.42
 */
@DisplayName("V5.42.3a — Drools 6 .dsl mapping 解析器 BDD")
class DslParserTest {

    private DslParser parser;

    @BeforeEach
    void setUp() {
        parser = new DslParser();
    }

    // ============================================================
    // === 解析 .dsl mapping entry ===
    // ============================================================

    @Nested
    @DisplayName("Given .dsl 文本,When parse,Then 产出 DslEntry 列表(scope + key + template)")
    class ParseDslEntry {

        @Test
        @DisplayName("最简单 1 行 when scope")
        void simplestWhen() {
            List<DslEntry> entries = parser.parse(
                "[when]This is {name}=Applicant(name == \"{name}\")");
            assertEquals(1, entries.size());
            DslEntry e = entries.get(0);
            assertEquals(DslScope.WHEN, e.getScope());
            assertEquals("This is {name}", e.getKey());
            assertEquals("Applicant(name == \"{name}\")", e.getTemplate());
        }

        @Test
        @DisplayName("then scope + 复杂模板(多 placeholder + 字符串内嵌)")
        void thenScopeComplex() {
            List<DslEntry> entries = parser.parse(
                "[then]Log message {msg} to {target}=System.out.println(\"{msg}: \" + {target});");
            assertEquals(1, entries.size());
            DslEntry e = entries.get(0);
            assertEquals(DslScope.THEN, e.getScope());
            assertEquals("Log message {msg} to {target}", e.getKey());
            assertEquals("System.out.println(\"{msg}: \" + {target});", e.getTemplate());
        }

        @Test
        @DisplayName("* scope(通用)→ DslScope.ANY")
        void anyScope() {
            List<DslEntry> entries = parser.parse(
                "[*]Common {x}=X(x == {x})");
            assertEquals(1, entries.size());
            assertEquals(DslScope.ANY, entries.get(0).getScope());
        }

        @Test
        @DisplayName("key 内 placeholder 名带下划线跟数字 — 合规")
        void placeholderWithUnderscoreDigit() {
            List<DslEntry> entries = parser.parse(
                "[when]Tag is {tag_name_1}=Applicant(tags contains {tag_name_1})");
            assertEquals(1, entries.size());
            assertEquals("Tag is {tag_name_1}", entries.get(0).getKey());
            // template 透传,无重写
            assertEquals("Applicant(tags contains {tag_name_1})", entries.get(0).getTemplate());
        }

        @Test
        @DisplayName("key 内 placeholder 名字母开头,合规")
        void placeholderLetterStart() {
            List<DslEntry> entries = parser.parse(
                "[when]Name is {Name}=Applicant(name == {Name})");
            assertEquals(1, entries.size());
        }

        @Test
        @DisplayName("多 entry 顺序保留")
        void multipleEntriesOrdered() {
            String src = "[when]A is {a}=A1\n" +
                "[when]B is {b}=B1\n" +
                "[then]C is {c}=C1\n";
            List<DslEntry> entries = parser.parse(src);
            assertEquals(3, entries.size());
            assertEquals("A is {a}", entries.get(0).getKey());
            assertEquals("B is {b}", entries.get(1).getKey());
            assertEquals("C is {c}", entries.get(2).getKey());
            assertEquals(DslScope.WHEN, entries.get(0).getScope());
            assertEquals(DslScope.WHEN, entries.get(1).getScope());
            assertEquals(DslScope.THEN, entries.get(2).getScope());
        }

        @Test
        @DisplayName("空行 / 纯注释行(#) / 纯空白行 跳过")
        void skipBlankAndComment() {
            String src = "# this is a comment\n" +
                "\n" +
                "[when]X is {y}=Y\n" +
                "   \n" +
                "# another comment\n" +
                "[then]Z is {w}=W\n";
            List<DslEntry> entries = parser.parse(src);
            assertEquals(2, entries.size());
            assertEquals("X is {y}", entries.get(0).getKey());
            assertEquals("Z is {w}", entries.get(1).getKey());
        }

        @Test
        @DisplayName("scope 大小写不敏感(WHEN / When / when 都接受)")
        void scopeCaseInsensitive() {
            String src = "[WHEN]A={a}=X\n" +
                "[When]B={b}=Y\n" +
                "[when]C={c}=Z\n" +
                "[*]D={d}=W\n" +
                "[ANY]E={e}=V\n";
            List<DslEntry> entries = parser.parse(src);
            assertEquals(5, entries.size());
            assertEquals(DslScope.WHEN, entries.get(0).getScope());
            assertEquals(DslScope.WHEN, entries.get(1).getScope());
            assertEquals(DslScope.WHEN, entries.get(2).getScope());
            assertEquals(DslScope.ANY, entries.get(3).getScope());
            assertEquals(DslScope.ANY, entries.get(4).getScope());
        }
    }

    // ============================================================
    // === 错误路径 ===
    // ============================================================

    @Nested
    @DisplayName("Given 错误 .dsl 文本,When parse,Then 抛 DrlParseException")
    class ParseError {

        @Test
        @DisplayName("缺 '=' 分隔符:只有 key 没 template")
        void missingEquals() {
            DrlParseException ex = assertThrows(DrlParseException.class,
                () -> parser.parse("[when]This is {name}"));
            assertTrue(ex.getMessage().contains("=")
                    || ex.getMessage().contains("'='")
                    || ex.getMessage().toLowerCase().contains("separator"),
                "错误信息应提及 '=' 分隔符,实际:" + ex.getMessage());
        }

        @Test
        @DisplayName("缺 scope 标签:[xxx]...")
        void missingScope() {
            assertThrows(DrlParseException.class,
                () -> parser.parse("This is {name}=Applicant(name == {name})"));
        }

        @Test
        @DisplayName("非法 scope 标签:[where]...")
        void unknownScope() {
            // V5.42.3a scope 限定 when / then / * / any 之一
            DrlParseException ex = assertThrows(DrlParseException.class,
                () -> parser.parse("[where]Foo={bar}=Baz"));
            assertTrue(ex.getMessage().toLowerCase().contains("scope")
                    || ex.getMessage().contains("[")
                    || ex.getMessage().contains("when")
                    || ex.getMessage().contains("then"),
                "错误信息应提示 scope 合法值,实际:" + ex.getMessage());
        }

        @Test
        @DisplayName("key 内 placeholder 名非法:含连字符 {a-b}")
        void invalidPlaceholderName() {
            assertThrows(DrlParseException.class,
                () -> parser.parse("[when]Foo {a-b}=Bar"));
        }

        @Test
        @DisplayName("key 内 placeholder 名以数字开头 {1a}")
        void placeholderStartsWithDigit() {
            assertThrows(DrlParseException.class,
                () -> parser.parse("[when]Foo {1a}=Bar"));
        }

        @Test
        @DisplayName("DrlParseException 带 line 信息")
        void exceptionCarriesLine() {
            // 第 2 行缺 '='
            String src = "[when]OK={a}=X\n" +
                "broken line no equals\n";
            DrlParseException ex = assertThrows(DrlParseException.class,
                () -> parser.parse(src));
            // line 应该是 2
            assertNotNull(ex.getLine(),
                "异常应带 line 信息(用于 console-ui 跳转),实际 null");
            assertEquals(2, ex.getLine(),
                "应指向第 2 行,实际:" + ex.getLine());
        }

        @Test
        @DisplayName("空 scope 标签([]) → DrlParseException")
        void emptyScopeLabel() {
            assertThrows(DrlParseException.class,
                () -> parser.parse("[]X={y}=Z"));
        }
    }

    // ============================================================
    // === getEntry(scope, key) 查找语义 ===
    // ============================================================

    @Nested
    @DisplayName("Given 已 parse 的 DslMappingSet,When lookup entry by (scope, key),Then 返回匹配")
    class Lookup {

        @Test
        @DisplayName("精确匹配:scope=WHEN + key 全文匹配")
        void exactMatch() {
            DslMappingSet set = parser.parseAsSet(
                "[when]A is {x}=X1\n" +
                "[when]A is {x} then {y}=X2\n" +
                "[then]A is {x}=X3\n");
            // when 段找 "A is {x}" 应返回 X1
            assertEquals("X1", set.lookup(DslScope.WHEN, "A is {x}").getTemplate());
            // when 段找 "A is {x} then {y}" 应返回 X2
            assertEquals("X2", set.lookup(DslScope.WHEN, "A is {x} then {y}").getTemplate());
            // then 段找 "A is {x}" 应返回 X3
            assertEquals("X3", set.lookup(DslScope.THEN, "A is {x}").getTemplate());
        }

        @Test
        @DisplayName("未找到 → null(不抛错,让 caller 决定怎么处理)")
        void notFoundReturnsNull() {
            DslMappingSet set = parser.parseAsSet(
                "[when]A={x}=X1\n");
            assertNull(set.lookup(DslScope.WHEN, "B={y}"),
                "未找到 entry 应返回 null,不抛错");
        }

        @Test
        @DisplayName("ANY scope 在 WHEN 查询时被检索(任何 scope 包含 query scope 时匹配)")
        void anyScopeMatchedForWhen() {
            DslMappingSet set = parser.parseAsSet(
                "[*]Common {x}=X1\n" +
                "[when]Specific {x}=X2\n");
            // [when] Specific 优先
            assertEquals("X2", set.lookup(DslScope.WHEN, "Specific {x}").getTemplate());
            // [when] Common 找 [when] 没注册,fallback 到 [*] ANY
            assertEquals("X1", set.lookup(DslScope.WHEN, "Common {x}").getTemplate());
        }

        @Test
        @DisplayName("空 mapping set 查找任何 key → null")
        void emptySetLookup() {
            DslMappingSet set = parser.parseAsSet("");
            assertNull(set.lookup(DslScope.WHEN, "Any"));
            assertEquals(0, set.size());
        }
    }
}
