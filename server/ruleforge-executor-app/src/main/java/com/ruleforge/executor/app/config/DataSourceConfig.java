package com.ruleforge.executor.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Executor 数据源配置 — 仅需 ruleforge_db
 */
@Configuration
public class DataSourceConfig {

    @Primary
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.ruleforge")
    public DataSource ruleforgeDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * Flowable 专用数据源 — 单独一库,避免和 Flyway 抢 ruleforge_db。
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.flowable")
    public DataSource flowableDataSource() {
        return DataSourceBuilder.create().build();
    }
}
