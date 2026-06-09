package com.ruleforge.datasource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5.23 — module skeleton smoke test.
 *
 * <p>Verifies the lib compiles + tests run with no Spring Boot, no MySQL, no Spring context.
 * If this test fails to even run, the module boundary (no DB deps) is broken.
 */
@DisplayName("ruleforge-datasource — module skeleton")
class PackageInfoTest {

    @Nested
    @DisplayName("Scenario: 加载 lib 类")
    class LoadLibClass {

        @Test
        @DisplayName("Given PackageInfo When Class.forName Then 找到")
        void shouldLoadPackageInfo() throws ClassNotFoundException {
            Class<?> clazz = Class.forName("com.ruleforge.datasource.PackageInfo");
            assertThat(clazz).isNotNull();
            assertThat(clazz.getPackageName()).isEqualTo("com.ruleforge.datasource");
        }
    }

    @Nested
    @DisplayName("Scenario: 模块边界自检")
    class ModuleBoundary {

        @Test
        @DisplayName("Given 当前线程 classloader When 检查 When 不应加载 MySQL 驱动")
        void shouldNotLoadMysqlDriver() {
            // 如果 lib 误引了 mysql-connector-j, 这个检查会失败 — 模块边界破了
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                assertThat(false).as("mysql-connector-j 不应该出现在 lib classpath").isTrue();
            } catch (ClassNotFoundException expected) {
                // 期望: lib 不引 MySQL
                assertThat(expected).hasMessageContaining("com.mysql.cj.jdbc.Driver");
            }
        }

        @Test
        @DisplayName("Given 当前线程 classloader When 检查 When 不应加载 MyBatis")
        void shouldNotLoadMybatis() {
            try {
                Class.forName("com.baomidou.mybatisplus.core.MybatisConfiguration");
                assertThat(false).as("mybatis-plus 不应该出现在 lib classpath").isTrue();
            } catch (ClassNotFoundException expected) {
                assertThat(expected).hasMessageContaining("MybatisConfiguration");
            }
        }

        @Test
        @DisplayName("Given 当前线程 classloader When 检查 When 不应加载 Spring Boot runtime")
        void shouldNotLoadSpringBootRuntime() {
            // spring-context 在, spring-boot 不应在
            try {
                Class.forName("org.springframework.boot.SpringApplication");
                assertThat(false).as("spring-boot 不应该出现在 lib classpath(用 spring-context 而不是 starter)").isTrue();
            } catch (ClassNotFoundException expected) {
                assertThat(expected).hasMessageContaining("SpringApplication");
            }
        }
    }
}
