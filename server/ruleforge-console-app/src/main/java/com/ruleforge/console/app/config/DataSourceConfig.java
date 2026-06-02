package com.ruleforge.console.app.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 多数据源配置：app 与 ruleforge
 */
@Configuration
public class DataSourceConfig {

    /**
     * 业务库数据源，作为默认数据源
     */
    @Primary
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.app")
    public DataSource appDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * RuleForge 专用数据源，供 ruleforge-console 的 MybatisPlusConfig 注入使用
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.ruleforge")
    public DataSource ruleforgeDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * Flowable 专用数据源 — 单独一库,不让 Flowable 自 init 的 SQL 脚本
     * 跟 Flyway 管理的 ruleforge_db 抢同一张 schema。
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.flowable")
    public DataSource flowableDataSource() {
        return DataSourceBuilder.create().build();
    }
}