package com.ruleforge.decision.datasource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5.23 — JavaSourceCompiler 行为规范。
 *
 * <p>用真 javac 跑(ProcessBuilder forkJavac),不 mock 编译器本身。
 * 5s 真实 timeout 测试不跑(怕 CI 慢)— 只验有效路径。
 */
@DisplayName("JavaSourceCompiler — 编译 LLM 生成的 Java 源码")
class JavaSourceCompilerTest {

    private static final String VALID_SOURCE = """
            package com.ruleforge.user;
            import com.ruleforge.decision.datasource.IJavaDataSource;
            import java.util.Map;
            public class Phase7Credit implements IJavaDataSource {
                @Override public String getName() { return "phase7_credit"; }
                @Override public Map<String, String> getSchema() { return Map.of("score", "number"); }
                @Override public Object fetchField(String entityId, String fieldName, Map<String, String> ctx) {
                    return "score".equals(fieldName) ? 720 : null;
                }
            }
            """;

    private final JavaSourceCompiler compiler = new JavaSourceCompiler();

    @Nested
    @DisplayName("Scenario: 编译成功")
    class CompileValid {

        @Test
        @DisplayName("Given LLM 风格合法源码 When compile Then 返回 fqcn + 字节数 + magic bytes 0xCAFEBABE")
        void shouldCompileValidSource() {
            JavaSourceCompiler.CompileResult r = compiler.compile(VALID_SOURCE);

            assertThat(r.success).as("compile should succeed; err=" + r.error).isTrue();
            assertThat(r.fqcn).isEqualTo("com.ruleforge.user.Phase7Credit");
            assertThat(r.publicClassName).isEqualTo("Phase7Credit");
            assertThat(r.classBytes).isNotEmpty();
            assertThat(r.classBytes.length).isGreaterThan(50);
            // Magic bytes
            assertThat(r.classBytes[0] & 0xFF).isEqualTo(0xCA);
            assertThat(r.classBytes[1] & 0xFF).isEqualTo(0xFE);
            assertThat(r.classBytes[2] & 0xFF).isEqualTo(0xBA);
            assertThat(r.classBytes[3] & 0xFF).isEqualTo(0xBE);
        }

        @Test
        @DisplayName("Given 无 package 的源码 When compile Then fqcn = 简单类名")
        void shouldHandleNoPackage() {
            String src = """
                    import com.ruleforge.decision.datasource.IJavaDataSource;
                    import java.util.Map;
                    public class NoPackage implements IJavaDataSource {
                        @Override public String getName() { return "x"; }
                        @Override public Object fetchField(String e, String f, Map<String, String> c) { return 1; }
                    }
                    """;
            JavaSourceCompiler.CompileResult r = compiler.compile(src);
            assertThat(r.success).as("err=" + r.error).isTrue();
            assertThat(r.fqcn).isEqualTo("NoPackage");
            assertThat(r.publicClassName).isEqualTo("NoPackage");
        }

        @Test
        @DisplayName("Given 源码中带 public inner class When compile Then 只取顶层 public 类名")
        void shouldPickTopLevelClass() {
            // 注意 javac 严格 — public class 名必须 = 文件名。我们写 Source.java,
            // 所以源码里的 public class 也得叫 Source(规则强制)。
            String src = """
                    package com.ruleforge.demo;
                    import com.ruleforge.decision.datasource.IJavaDataSource;
                    import java.util.Map;
                    public class Source implements IJavaDataSource {
                        public class Inner { }
                        @Override public String getName() { return "source"; }
                        @Override public Object fetchField(String e, String f, Map<String, String> c) { return null; }
                    }
                    """;
            JavaSourceCompiler.CompileResult r = compiler.compile(src);
            assertThat(r.success).as("err=" + r.error).isTrue();
            assertThat(r.publicClassName).isEqualTo("Source");
            assertThat(r.fqcn).isEqualTo("com.ruleforge.demo.Source");
        }
    }

    @Nested
    @DisplayName("Scenario: 编译失败")
    class CompileFailure {

        @Test
        @DisplayName("Given 空源码 When compile Then success=false + 错误信息")
        void shouldRejectEmpty() {
            JavaSourceCompiler.CompileResult r1 = compiler.compile("");
            assertThat(r1.success).isFalse();
            assertThat(r1.error).contains("empty");

            JavaSourceCompiler.CompileResult r2 = compiler.compile(null);
            assertThat(r2.success).isFalse();
        }

        @Test
        @DisplayName("Given 没 public class 的源码 When compile Then success=false + 'no public' 错误")
        void shouldRejectNoPublicClass() {
            String src = """
                    package com.ruleforge.user;
                    // 没有 public class — 只有 package-private
                    class Hidden {
                        int x = 1;
                    }
                    """;
            JavaSourceCompiler.CompileResult r = compiler.compile(src);
            assertThat(r.success).isFalse();
            assertThat(r.error).contains("no public");
        }

        @Test
        @DisplayName("Given 语法错误源码 When compile Then success=false + javac 错误在 error 字段")
        void shouldSurfaceJavacError() {
            String src = """
                    package com.ruleforge.user;
                    public class Broken {
                        // 故意语法错 — 缺分号 + 缺括号
                        public String bad( { return "x"
                    """;
            JavaSourceCompiler.CompileResult r = compiler.compile(src);
            assertThat(r.success).isFalse();
            assertThat(r.error).isNotBlank();
            // javac 输出通常包含 'error'
            assertThat(r.error.toLowerCase()).containsAnyOf("error", "javac");
        }
    }

    @Nested
    @DisplayName("Scenario: helpers")
    class Helpers {

        @Test
        @DisplayName("extractPublicClassName: 取 public 顶层类名,不取 inner")
        void shouldExtractPublicClassName() {
            assertThat(JavaSourceCompiler.extractPublicClassName("public class Foo { }")).isEqualTo("Foo");
            assertThat(JavaSourceCompiler.extractPublicClassName("public final class Bar { }")).isEqualTo("Bar");
            assertThat(JavaSourceCompiler.extractPublicClassName("public abstract class Baz { }")).isEqualTo("Baz");
            assertThat(JavaSourceCompiler.extractPublicClassName("public interface I { }")).isEqualTo("I");
            assertThat(JavaSourceCompiler.extractPublicClassName("public enum E { A; }")).isEqualTo("E");
            assertThat(JavaSourceCompiler.extractPublicClassName("class Hidden { }")).isNull();
            assertThat(JavaSourceCompiler.extractPublicClassName(null)).isNull();
        }

        @Test
        @DisplayName("extractPackageName: 解析 package x.y.z;,空时返空串")
        void shouldExtractPackageName() {
            assertThat(JavaSourceCompiler.extractPackageName("package com.ruleforge.user;"))
                .isEqualTo("com.ruleforge.user");
            assertThat(JavaSourceCompiler.extractPackageName("// no package\npublic class X { }"))
                .isEmpty();
            assertThat(JavaSourceCompiler.extractPackageName(null)).isEmpty();
        }
    }
}
