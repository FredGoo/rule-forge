package com.ruleforge.decision.datasource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5.23 — ClassLoaderPool 行为规范。
 */
@DisplayName("ClassLoaderPool — 隔离 classloader 池")
class ClassLoaderPoolTest {

    private static final String SOURCE = """
            package com.ruleforge.pool;
            import com.ruleforge.decision.datasource.IJavaDataSource;
            import java.util.Map;
            public class Pooled implements IJavaDataSource {
                @Override public String getName() { return "pooled"; }
                @Override public Object fetchField(String e, String f, Map<String, String> c) {
                    return "hi".equals(f) ? "hello" : null;
                }
            }
            """;

    @Nested
    @DisplayName("Scenario: load + reuse")
    class LoadAndReuse {

        @Test
        @DisplayName("Given 合法 .class 字节 When getOrLoad Then 返 Class 且后续调用返同一 Class")
        void shouldCacheById() {
            ClassLoaderPool pool = new ClassLoaderPool();
            JavaSourceCompiler.CompileResult cr = new JavaSourceCompiler().compile(SOURCE);
            assertThat(cr.success).as("compile err=" + cr.error).isTrue();

            Class<?> c1 = pool.getOrLoad(1L, cr.fqcn, cr.classBytes);
            Class<?> c2 = pool.getOrLoad(1L, cr.fqcn, cr.classBytes);
            assertThat(c1).isSameAs(c2); // 同一 id 复用 Class
            assertThat(c1.getName()).isEqualTo("com.ruleforge.pool.Pooled");
            assertThat(IJavaDataSource.class.isAssignableFrom(c1)).isTrue();

            pool.clear();
        }

        @Test
        @DisplayName("Given 不同 datasourceId When getOrLoad Then 各自返独立 Class(隔离)")
        void shouldIsolateById() throws Exception {
            ClassLoaderPool pool = new ClassLoaderPool();
            JavaSourceCompiler.CompileResult cr = new JavaSourceCompiler().compile(SOURCE);
            assertThat(cr.success).isTrue();

            Class<?> c1 = pool.getOrLoad(1L, cr.fqcn, cr.classBytes);
            Class<?> c2 = pool.getOrLoad(2L, cr.fqcn, cr.classBytes);
            assertThat(c1).isNotSameAs(c2); // 不同 id 隔离
            assertThat(c1.getClassLoader()).isNotSameAs(c2.getClassLoader());

            // 各自实例化都能跑
            IJavaDataSource i1 = (IJavaDataSource) c1.getDeclaredConstructor().newInstance();
            IJavaDataSource i2 = (IJavaDataSource) c2.getDeclaredConstructor().newInstance();
            assertThat(i1.getName()).isEqualTo("pooled");
            assertThat(i2.getName()).isEqualTo("pooled");
            assertThat(i1.fetchField("u1", "hi", java.util.Map.of())).isEqualTo("hello");

            pool.clear();
        }
    }

    @Nested
    @DisplayName("Scenario: 异常路径")
    class ErrorPaths {

        @Test
        @DisplayName("Given 非法 .class 字节 When getOrLoad Then 抛 LinkageError 系")
        void shouldThrowOnInvalidBytes() {
            ClassLoaderPool pool = new ClassLoaderPool();
            byte[] garbage = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
            // ClassFormatError extends LinkageError — verify by Throwable
            Throwable thrown = null;
            try {
                pool.getOrLoad(1L, "com.x.Broken", garbage);
            } catch (Throwable t) {
                thrown = t;
            }
            assertThat(thrown).isNotNull();
            assertThat(thrown).isInstanceOf(LinkageError.class);
            pool.clear();
        }
    }

    @Nested
    @DisplayName("Scenario: 生命周期")
    class Lifecycle {

        @Test
        @DisplayName("Given 已加载 1 个 id When close(1) Then size=0;close(null id) 是 no-op")
        void shouldCloseAndEvict() {
            ClassLoaderPool pool = new ClassLoaderPool();
            JavaSourceCompiler.CompileResult cr = new JavaSourceCompiler().compile(SOURCE);
            assertThat(cr.success).isTrue();
            pool.getOrLoad(1L, cr.fqcn, cr.classBytes);
            assertThat(pool.size()).isEqualTo(1);

            pool.close(1L);
            assertThat(pool.size()).isEqualTo(0);

            // 重复 close / 不存在的 id 不抛
            pool.close(1L);
            pool.close(999L);
        }
    }
}
