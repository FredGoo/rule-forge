package com.ruleforge.console.app.agent.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 审计 mapper (V5.22.2)
 *
 * <p>权限域:app_db (由 appDataSource 注入,通过 appSqlSessionFactory)
 */
@Mapper
public interface AgentAuditMapper extends BaseMapper<AgentAuditEntity> {
}
