package com.ruleforge.console.app.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Feature: ClickHouse backfill runner 跨模块依赖修复
 *
 * <p>背景:Phase 8 引入的 {@link ClickHouseBackfillRunner} 写错两件事:
 * <ol>
 *   <li><b>跨模块依赖</b>: console-app 引用 executor-app 的
 *       {@code com.ruleforge.decision.entity.DecisionFlowLog} 和
 *       {@code com.ruleforge.decision.mapper.clickhouse.ChDecisionFlowLogMapper},
 *       console-app 的 pom 没声明 executor 依赖,导致 {@code mvn -pl
 *       ruleforge-console-app package} 失败</li>
 *   <li><b>用错 DataSource</b>: runner 注入 {@code ruleforgeDataSource} 读
 *       {@code nd_decision_flow_log},但这表在 {@code app_db}(V5.16 创建),
 *       实际是 {@code appDataSource}</li>
 * </ol>
 *
 * <p>修复方案:runner 自包含,raw JDBC,本地 record DTO,只依赖两个 DataSource
 * (appDataSource + clickhouseDataSource)。本测试锁住这两个边界,防止
 * 后续 PR 又把跨模块依赖塞回来。
 */
@DisplayName("ClickHouseBackfillRunner - Phase 8 跨模块依赖修复")
class ClickHouseBackfillRunnerTest {

    @Nested
    @DisplayName("Given runner constructor signature")
    class ConstructorSignature {

        @Test
        @DisplayName("Then 只注入 appDataSource + clickhouseDataSource,没有跨模块 entity/mapper")
        void noCrossModuleDependencies() throws Exception {
            Constructor<ClickHouseBackfillRunner> ctor =
                    ClickHouseBackfillRunner.class.getDeclaredConstructor(DataSource.class, DataSource.class);
            assertThat(ctor.getParameterCount())
                    .as("runner 应该只注入 2 个 DataSource,不借 executor-app 的 entity/mapper")
                    .isEqualTo(2);
            for (Class<?> paramType : ctor.getParameterTypes()) {
                assertThat(paramType)
                        .as("构造参数必须是 javax.sql.DataSource,不是业务 mapper/entity")
                        .isEqualTo(DataSource.class);
            }
        }

        @Test
        @DisplayName("Then 没有 instance 字段引用 executor-app 的类(com.ruleforge.decision.*)")
        void noExecutorAppFields() {
            // 扫所有 declared 字段,确保没有跨模块引用。
            // 这条规则:console-app 里的类,字段类型不应出现 com.ruleforge.decision.*。
            Field[] fields = ClickHouseBackfillRunner.class.getDeclaredFields();
            for (Field f : fields) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                assertThat(f.getType().getName())
                        .as("字段 '%s' 不应跨模块引用 executor-app 类型", f.getName())
                        .doesNotStartWith("com.ruleforge.decision.");
            }
        }
    }

    @Nested
    @DisplayName("Given runner 构造注入")
    class Wiring {

        @Test
        @DisplayName("Then appDataSource 和 clickhouseDataSource 都能被 @RequiredArgsConstructor 注入")
        void canBeInstantiatedWithTwoDataSources() throws Exception {
            DataSource app = mock(DataSource.class);
            DataSource ch = mock(DataSource.class);
            // @RequiredArgsConstructor 生成的构造器应接受这两个参数
            ClickHouseBackfillRunner runner = new ClickHouseBackfillRunner(app, ch);
            assertThat(runner).isNotNull();
        }
    }

    @Nested
    @DisplayName("Given runner 的 INSERT SQL")
    class InsertSql {

        @Test
        @DisplayName("Then 列顺序和数量与 CH DDL 一致(24 列) — schema 改了这里要一起改")
        void insertSqlHasCorrectColumns() throws Exception {
            // 通过反射读 private static final FLOW_LOG_INSERT_SQL
            Field f = ClickHouseBackfillRunner.class.getDeclaredField("FLOW_LOG_INSERT_SQL");
            f.setAccessible(true);
            String sql = (String) f.get(null);

            // 24 个 ? 占位符 = 24 列
            long placeholders = sql.chars().filter(c -> c == '?').count();
            assertThat(placeholders)
                    .as("INSERT 必须有 24 个占位符,匹配 nd_decision_flow_log 的 24 列")
                    .isEqualTo(24L);

            // 关键列必须在(防止以后重命名/漏列悄悄过)
            assertThat(sql)
                    .contains("nd_decision_flow_log")
                    .contains("id, user_id, order_no, flow_id, flow_version")
                    .contains("is_gray, gray_strategy_id, gray_git_tag, created_at");
        }
    }
}
