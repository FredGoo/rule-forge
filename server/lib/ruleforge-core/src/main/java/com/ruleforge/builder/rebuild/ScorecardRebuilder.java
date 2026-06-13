package com.ruleforge.builder.rebuild;

import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rule.Rule;

import java.util.Map;

/**
 * V5.48 — 评分卡规则 rebuilder stub。
 *
 * <p>ScoreRule 走 {@code ScorecardResourceBuilder} → rebuildRules →
 * per-rule 走 DrlRuleRebuilder。Scorecard 走 .pmml 路径的(pmml4s 编译)
 * 在 KnowledgeBuilder V5.41 走单独 dispatch,不走 RulesRebuilder。
 */
public class ScorecardRebuilder implements RuleTypeRebuilder {

    @Override
    public boolean supports(Rule rule) {
        return false;
    }

    @Override
    public void rebuild(Rule rule, ResourceLibrary resLibraries, Map<String, String> namedMap, boolean forDSL) {
        throw new UnsupportedOperationException("ScorecardRebuilder V5.48 stub — scorecard rules fall through to DrlRuleRebuilder");
    }
}
