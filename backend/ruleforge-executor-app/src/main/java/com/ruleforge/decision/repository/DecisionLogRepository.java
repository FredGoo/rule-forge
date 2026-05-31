package com.ruleforge.decision.repository;

import com.ruleforge.decision.entity.DecisionFlowLog;
import com.ruleforge.decision.entity.DecisionFlowParams;
import com.ruleforge.decision.entity.DecisionMessageLog;
import com.ruleforge.decision.entity.DecisionNodeLog;
import com.ruleforge.decision.entity.DecisionRuleLog;
import com.ruleforge.decision.entity.ShadowFlowLog;
import com.ruleforge.decision.entity.ShadowFlowParams;
import com.ruleforge.decision.entity.ShadowMessageLog;
import com.ruleforge.decision.entity.ShadowNodeLog;
import com.ruleforge.decision.entity.ShadowRuleLog;

import java.util.List;

/**
 * Decision log data access repository.
 * Encapsulates all DB operations for decision flow and shadow flow logs.
 */
public interface DecisionLogRepository {

    // ===== Decision logs =====

    /**
     * Insert a decision flow log and return the entity with generated id.
     */
    DecisionFlowLog insertFlowLog(DecisionFlowLog entity);

    /**
     * Insert decision flow parameters.
     */
    void insertFlowParams(DecisionFlowParams entity);

    /**
     * Insert a single decision node log.
     */
    void insertNodeLog(DecisionNodeLog entity);

    /**
     * Insert a single decision rule log.
     */
    void insertRuleLog(DecisionRuleLog entity);

    /**
     * Insert a single decision message log.
     */
    void insertMessageLog(DecisionMessageLog entity);

    /**
     * Batch insert decision message logs.
     */
    void batchInsertMessageLogs(List<DecisionMessageLog> entities);

    /**
     * Batch insert decision rule logs.
     */
    void batchInsertRuleLogs(List<DecisionRuleLog> entities);

    /**
     * Batch insert decision node logs.
     */
    void batchInsertNodeLogs(List<DecisionNodeLog> entities);

    // ===== Shadow decision logs =====

    /**
     * Insert a shadow flow log and return the entity with generated id.
     */
    ShadowFlowLog insertShadowFlowLog(ShadowFlowLog entity);

    /**
     * Insert shadow flow parameters.
     */
    void insertShadowFlowParams(ShadowFlowParams entity);

    /**
     * Insert a single shadow node log.
     */
    void insertShadowNodeLog(ShadowNodeLog entity);

    /**
     * Insert a single shadow rule log.
     */
    void insertShadowRuleLog(ShadowRuleLog entity);

    /**
     * Insert a single shadow message log.
     */
    void insertShadowMessageLog(ShadowMessageLog entity);

    /**
     * Batch insert shadow message logs.
     */
    void batchInsertShadowMessageLogs(List<ShadowMessageLog> entities);

    /**
     * Batch insert shadow rule logs.
     */
    void batchInsertShadowRuleLogs(List<ShadowRuleLog> entities);

    /**
     * Batch insert shadow node logs.
     */
    void batchInsertShadowNodeLogs(List<ShadowNodeLog> entities);
}
