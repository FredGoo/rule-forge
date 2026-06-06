-- Phase 8: ClickHouse analytics DDL
-- Run: clickhouse-client --user default --password changeme < clickhouse-init.sql
-- Or:  curl "http://192.168.3.36:8123/?user=default&password=changeme" --data-binary @clickhouse-init.sql

CREATE DATABASE IF NOT EXISTS ruleforge_analytics;

-- 决策流执行日志 (analytics-optimized, ReplacingMergeTree 按 id 去重)
CREATE TABLE IF NOT EXISTS ruleforge_analytics.nd_decision_flow_log
(
    id                      UInt64,
    user_id                 Nullable(String),
    order_no                Nullable(String),
    flow_id                 String,
    flow_version            Nullable(String),
    rule_package_path       String,
    rule_package_version    Nullable(String),
    execution_status        String,
    reject_reason           Nullable(String),
    reject_code             Nullable(String),
    node_names              Nullable(String),
    execution_time_ms       Nullable(Int64),
    total_time_ms           Nullable(Int64),
    load_knowledge_time_ms  Nullable(Int64),
    flow_execution_time_ms  Nullable(Int64),
    total_matched_rules     Nullable(UInt32),
    total_fired_rules       Nullable(UInt32),
    total_loaded_fields     Nullable(UInt32),
    error_message           Nullable(String),
    error_stack_trace       Nullable(String),
    is_gray                 UInt8 DEFAULT 0,
    gray_strategy_id        Nullable(UInt64),
    gray_git_tag            Nullable(String),
    created_at              DateTime
)
ENGINE = ReplacingMergeTree()
ORDER BY (rule_package_path, flow_id, created_at, id)
PARTITION BY toYYYYMM(created_at)
TTL created_at + INTERVAL 365 DAY
SETTINGS index_granularity = 8192;

-- 规则执行日志 (JOIN flow_log 用于规则覆盖率分析)
CREATE TABLE IF NOT EXISTS ruleforge_analytics.nd_decision_rule_log
(
    id                UInt64,
    flow_log_id       UInt64,
    user_id           Nullable(String),
    rule_node_index   Nullable(UInt32),
    duration_ms       Nullable(Int64),
    rule_type         String DEFAULT '',
    rule_index        Nullable(UInt32),
    rule_name         String,
    salience          Nullable(Int32),
    activation_group  Nullable(String),
    agenda_group      Nullable(String),
    ruleflow_group    Nullable(String),
    lhs_condition     Nullable(String),
    rhs_actions       Nullable(String),
    created_at        DateTime
)
ENGINE = ReplacingMergeTree()
ORDER BY (rule_name, rule_type, created_at, id)
PARTITION BY toYYYYMM(created_at)
TTL created_at + INTERVAL 365 DAY
SETTINGS index_granularity = 8192;
