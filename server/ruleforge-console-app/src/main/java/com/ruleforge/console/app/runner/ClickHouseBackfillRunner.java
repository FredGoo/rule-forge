package com.ruleforge.console.app.runner;

import com.ruleforge.console.app.mapper.clickhouse.ChDecisionAnalysisMapper;
import com.ruleforge.decision.entity.DecisionFlowLog;
import com.ruleforge.decision.entity.DecisionRuleLog;
import com.ruleforge.decision.mapper.clickhouse.ChDecisionFlowLogMapper;
import com.ruleforge.decision.mapper.clickhouse.ChDecisionRuleLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 8: MySQL → ClickHouse 历史数据回填工具.
 *
 * <p>用法: {@code java -jar ruleforge-console-app.jar --spring.profiles.active=backfill}
 *
 * <p>按 id 批量读 MySQL nd_decision_flow_log,写入 ClickHouse。
 * ReplacingMergeTree 天然去重,重跑安全。
 */
@Slf4j
@Component
@Profile("backfill")
@RequiredArgsConstructor
public class ClickHouseBackfillRunner implements CommandLineRunner {

    private static final int BATCH_SIZE = 1000;

    private final DataSource ruleforgeDataSource;
    private final ChDecisionFlowLogMapper chFlowLogMapper;
    private final ChDecisionRuleLogMapper chRuleLogMapper;

    @Override
    public void run(String... args) {
        log.info("=== ClickHouse backfill start ===");
        long totalFlow = 0;
        long totalRule = 0;
        long lastId = 0;

        try {
            while (true) {
                List<DecisionFlowLog> batch = readFlowLogBatch(lastId);
                if (batch.isEmpty()) {
                    break;
                }
                int flowCount = 0;
                int ruleCount = 0;
                for (DecisionFlowLog flowLog : batch) {
                    try {
                        chFlowLogMapper.insert(flowLog);
                        flowCount++;
                    } catch (Exception e) {
                        log.warn("Backfill flow_log failed: id={}: {}", flowLog.getId(), e.getMessage());
                    }
                }
                totalFlow += flowCount;
                lastId = batch.get(batch.size() - 1).getId();
                log.info("Backfill progress: synced {} flow logs (lastId={})", totalFlow, lastId);
            }
            log.info("=== ClickHouse backfill done: {} flow logs ===", totalFlow);
        } catch (Exception e) {
            log.error("Backfill failed", e);
        }
    }

    private List<DecisionFlowLog> readFlowLogBatch(long lastId) throws Exception {
        List<DecisionFlowLog> result = new ArrayList<>();
        try (Connection conn = ruleforgeDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, user_id, order_no, flow_id, flow_version, " +
                     "  rule_package_path, rule_package_version, execution_status, " +
                     "  reject_reason, reject_code, node_names, " +
                     "  execution_time_ms, total_time_ms, load_knowledge_time_ms, flow_execution_time_ms, " +
                     "  total_matched_rules, total_fired_rules, total_loaded_fields, " +
                     "  error_message, error_stack_trace, " +
                     "  is_gray, gray_strategy_id, gray_git_tag, created_at " +
                     "FROM nd_decision_flow_log WHERE id > ? ORDER BY id ASC LIMIT " + BATCH_SIZE)) {
            ps.setLong(1, lastId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DecisionFlowLog log = new DecisionFlowLog();
                    log.setId(rs.getLong("id"));
                    log.setUserId(rs.getString("user_id"));
                    log.setOrderNo(rs.getString("order_no"));
                    log.setFlowId(rs.getString("flow_id"));
                    log.setFlowVersion(rs.getString("flow_version"));
                    log.setRulePackagePath(rs.getString("rule_package_path"));
                    log.setRulePackageVersion(rs.getString("rule_package_version"));
                    log.setExecutionStatus(rs.getString("execution_status"));
                    log.setRejectReason(rs.getString("reject_reason"));
                    log.setRejectCode(rs.getString("reject_code"));
                    log.setNodeNames(rs.getString("node_names"));
                    log.setExecutionTimeMs(rs.getObject("execution_time_ms", Long.class));
                    log.setTotalTimeMs(rs.getObject("total_time_ms", Long.class));
                    log.setLoadKnowledgeTimeMs(rs.getObject("load_knowledge_time_ms", Long.class));
                    log.setFlowExecutionTimeMs(rs.getObject("flow_execution_time_ms", Long.class));
                    log.setTotalMatchedRules(rs.getObject("total_matched_rules", Integer.class));
                    log.setTotalFiredRules(rs.getObject("total_fired_rules", Integer.class));
                    log.setTotalLoadedFields(rs.getObject("total_loaded_fields", Integer.class));
                    log.setErrorMessage(rs.getString("error_message"));
                    log.setErrorStackTrace(rs.getString("error_stack_trace"));
                    log.setIsGray(rs.getBoolean("is_gray"));
                    log.setGrayStrategyId(rs.getObject("gray_strategy_id", Long.class));
                    log.setGrayGitTag(rs.getString("gray_git_tag"));
                    log.setCreatedAt(rs.getTimestamp("created_at"));
                    result.add(log);
                }
            }
        }
        return result;
    }
}
