package com.ruleforge.ir.migration;

import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleSet;

import java.util.List;

/**
 * V5.42.3b — {@code .ul → .drl} 一次性 emit 工具。
 *
 * <p>用法:把 .ul 老文件跑 {@link com.ruleforge.dsl.DSLRuleSetBuilder} 得到
 * {@link RuleSet},再 {@code UlToDrlConverter.emit(ruleSet)} → {@code String .drl 文本}。
 * 给运维手工 review / 批量迁移用。
 *
 * <p>保留并行(plan 锁定):.ul 老路径**不**删,本工具只是"emit 辅助" —
 * caller 拿到 String 后自己决定:1) 写到 .drl 文件,2) 用 DrlResourceBuilder 跑,
 * 3) 仅 review 不入库。
 *
 * <p>第一版范围(V5.42.3b):
 * <ul>
 *   <li>顶层 rule metadata 全 emit: name / salience / agenda-group / activation-group /
 *       ruleflow-group / auto-focus / no-loop / lock-on-active / enabled /
 *       date-effective / date-expires</li>
 *   <li>D2 化 else: {@code Rule.withElse + elseRule != null} → {@code extends "<elseRule.name>"}</li>
 *   <li>lhs / rhs 内部 **不** emit — 留 TODO 注释(V5.42 D4 老 CellCondition
 *       决定:复杂 lhs 内容运维手动 rewrite,本工具不强行 reverse engineering)</li>
 *   <li>输出含迁移标识注释 {@code // V5.42.3b ...}</li>
 * </ul>
 *
 * @since 5.42
 */
public class UlToDrlConverter {

    public String emit(RuleSet ruleSet) {
        if (ruleSet == null) {
            throw new IllegalArgumentException("RuleSet 不能为 null");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("// V5.42.3b — 一次性 emit from .ul 老 RuleSet\n");
        sb.append("// 注:本输出是 DRL 化第一版,顶层 metadata 已 emit;lhs / rhs 内部未自动 emit。\n");
        sb.append("// 运维需手动 review + 补 lhs 内容(参考 DRL grammar)。\n");
        sb.append("\n");
        List<Rule> rules = ruleSet.getRules();
        if (rules == null || rules.isEmpty()) {
            return sb.toString();
        }
        for (Rule r : rules) {
            appendRule(sb, r);
            sb.append("\n");
        }
        return sb.toString();
    }

    private void appendRule(StringBuilder sb, Rule r) {
        if (r == null || r.getName() == null) {
            return;
        }
        sb.append("rule \"").append(r.getName()).append("\"");
        // D2 extends
        if (r.isWithElse() && r.getElseRule() != null && r.getElseRule().getName() != null) {
            sb.append(" extends \"").append(r.getElseRule().getName()).append("\"");
        }
        // attributes
        appendAttributes(sb, r);
        sb.append("\n");
        // when
        sb.append("    when\n");
        sb.append("        // TODO: V5.42.3b lhs 内部未自动 emit,需运维手动 rewrite\n");
        // then
        sb.append("    then\n");
        sb.append("        // TODO: V5.42.3b rhs 内部未自动 emit,需运维手动 rewrite\n");
        sb.append("end");
    }

    private void appendAttributes(StringBuilder sb, Rule r) {
        boolean first = true;
        // boolean 跟数值字段
        if (r.getSalience() != null) {
            sb.append(first ? " [" : ", ").append("salience ").append(r.getSalience());
            first = false;
        }
        if (r.getAgendaGroup() != null) {
            sb.append(first ? " [" : ", ").append("agenda-group \"").append(r.getAgendaGroup()).append("\"");
            first = false;
        }
        if (r.getActivationGroup() != null) {
            sb.append(first ? " [" : ", ").append("activation-group \"").append(r.getActivationGroup()).append("\"");
            first = false;
        }
        if (r.getRuleflowGroup() != null) {
            sb.append(first ? " [" : ", ").append("ruleflow-group \"").append(r.getRuleflowGroup()).append("\"");
            first = false;
        }
        if (r.getAutoFocus() != null) {
            sb.append(first ? " [" : ", ").append("auto-focus ").append(r.getAutoFocus());
            first = false;
        }
        if (r.getLoop() != null) {
            // 语义反转:Rule.loop=true(可循环) → no-loop false
            //         Rule.loop=false(不循环) → no-loop true
            sb.append(first ? " [" : ", ").append("no-loop ").append(!r.getLoop());
            first = false;
        }
        if (r.getEnabled() != null) {
            sb.append(first ? " [" : ", ").append("enabled ").append(r.getEnabled());
            first = false;
        }
        if (r.getEffectiveDate() != null) {
            sb.append(first ? " [" : ", ").append("date-effective \"")
                .append(formatDate(r.getEffectiveDate())).append("\"");
            first = false;
        }
        if (r.getExpiresDate() != null) {
            sb.append(first ? " [" : ", ").append("date-expires \"")
                .append(formatDate(r.getExpiresDate())).append("\"");
            first = false;
        }
        if (!first) {
            sb.append("]");
        }
    }

    private static String formatDate(java.util.Date d) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(d);
    }
}
