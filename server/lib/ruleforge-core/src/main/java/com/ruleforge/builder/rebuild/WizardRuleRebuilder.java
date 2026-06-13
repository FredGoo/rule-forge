package com.ruleforge.builder.rebuild;

import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rule.Rule;

import java.util.Map;

/**
 * V5.48 — 向导式规则 rebuilder stub。
 *
 * <p>当前 V5.47 还没有 {@code WizardRule} 实体类(wizard 数据走 .xml 老路径,
 * 经 DecisionTable / DecisionTree 走 RulesRebuilder rebuildRules — 不区分向导式
 * 还是表/树)。{@link com.ruleforge.builder.RulesRebuilder} 的 per-rule 逻辑
 * 是 generic 的(DrlRuleRebuilder covers all),所以这个 stub 不 active。
 *
 * <p>等 V5.49+ 引入独立 WizardRule class 时,这里改 supports() + 加 wizard
 * 特有的 wizard-variable binding / wizard-only action 处理。
 */
public class WizardRuleRebuilder implements RuleTypeRebuilder {

    @Override
    public boolean supports(Rule rule) {
        // V5.48 stub:向导式规则无独立 class,fallback 到 DrlRuleRebuilder
        return false;
    }

    @Override
    public void rebuild(Rule rule, ResourceLibrary resLibraries, Map<String, String> namedMap, boolean forDSL) {
        // should never be called when supports() == false
        throw new UnsupportedOperationException("WizardRuleRebuilder V5.48 stub — wizard rules fall through to DrlRuleRebuilder");
    }
}
