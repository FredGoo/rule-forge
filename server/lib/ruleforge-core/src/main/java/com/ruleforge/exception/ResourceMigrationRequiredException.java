package com.ruleforge.exception;

/**
 * V5.43.4 — KnowledgeBuilder 守卫抛的异常,显式告诉运维"该资源是老格式,需跑迁移"。
 *
 * <p>V5.43 删老 .xml rule 解析链 + 老 .ul DSL 解析链后,KnowledgeBuilder 加载规则库
 * 遇到老 .xml(&lt;rule&gt; / &lt;rule-set&gt; / &lt;ruleflow&gt; 根)/ .ul / .dsl /
 * .dslrd 资源时,不能 silent 跳过(产 0 rule 业务停摆),改为抛本异常。
 *
 * <p>运维升级 runbook:
 * <ol>
 *   <li>在 console-app 启动前跑 {@code LegacyXmlMigrator.migrate()} 一遍(覆盖
 *       &lt;rule&gt; / &lt;rule-set&gt; / &lt;ruleflow&gt; 根 + .ul / .dsl / .dslrd)</li>
 *   <li>迁移完成后老资源变成 .drl,KnowledgeBuilder 正常加载</li>
 *   <li>本异常**不**应出现在产线环境 — 出现即代表漏跑迁移</li>
 * </ol>
 *
 * @since 5.43
 */
public class ResourceMigrationRequiredException extends RuleException {

    public ResourceMigrationRequiredException(String message) {
        super(message);
    }
}
