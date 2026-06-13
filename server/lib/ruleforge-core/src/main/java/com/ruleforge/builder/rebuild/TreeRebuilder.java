package com.ruleforge.builder.rebuild;

import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rule.Rule;

import java.util.Map;

/**
 * V5.48 — 决策树规则 rebuilder stub。
 *
 * <p>DecisionTree 走 {@code decisionTreeRulesBuilder.buildRules(tree)} →
 * 产 Rule → 跟其他 Rule 一起进 RulesRebuilder,per-rule 走 DrlRuleRebuilder。
 */
public class TreeRebuilder implements RuleTypeRebuilder {

    @Override
    public boolean supports(Rule rule) {
        return false;
    }

    @Override
    public void rebuild(Rule rule, ResourceLibrary resLibraries, Map<String, String> namedMap, boolean forDSL) {
        throw new UnsupportedOperationException("TreeRebuilder V5.48 stub — tree rules fall through to DrlRuleRebuilder");
    }
}
