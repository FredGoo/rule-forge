package com.ruleforge.decision.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flowable 专用 Flyway 迁移,跑在 flowable_db 上。
 *
 * <p>为什么需要这个:
 * <ul>
 *   <li>Flowable 8.0.0 自带的 init SQL(create index 在 create table 之前)
 *       在 Spring Boot 4 + MySQL 8 下顺序错,直接 init 失败</li>
 *   <li>FlowableProperties(8.0.0)没有 datasource 字段,yml 里的
 *       flowable.datasource=flowable 被忽略,真正决定走哪个 DataSource
 *       的是 {@link FlowableConfig}</li>
 *   <li>所以我们让 Flyway 接管 act_* 表的初始化,关掉 Flowable 自 init</li>
 * </ul>
 *
 * <p>迁移目录: {@code classpath:db/migration-flowable} (V1__flowable_engine_mysql.sql
 * 是把 Flowable jar 里的 create engine + create history SQL 拼成单个文件,
 * 避开原版 split 后的 create index 引用尚未 create 的表问题)
 */
@Configuration
public class FlowableFlywayConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlowableFlywayConfig.class);

    @Bean
    public Flyway flowableFlyway(@Qualifier("flowable") DataSource flowableDataSource) {
        logger.info("Initializing Flowable schema via Flyway on flowable DataSource");
        Flyway flyway = Flyway.configure()
                .dataSource(flowableDataSource)
                .locations("classpath:db/migration-flowable")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .table("flowable_flyway_history")
                .load();
        flyway.migrate();
        return flyway;
    }
}
