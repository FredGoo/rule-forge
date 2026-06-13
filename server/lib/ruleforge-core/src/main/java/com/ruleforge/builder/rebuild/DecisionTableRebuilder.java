package com.ruleforge.builder.rebuild;

import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rule.Rule;

import java.util.Map;

/**
 * V5.48 — 决策表规则 rebuilder stub。
 *
 * <p>当前 DecisionTable 走 {@code decisionTableRulesBuilder.buildRules(table)}
 * → 产 List&lt;Rule&gt; → 跟其他 Rule 一起进 RulesRebuilder.rebuildRules,
 * per-rule 路径走 DrlRuleRebuilder(LHS/RHS/LoopRule 都是 generic)。
 *
 * <p>V5.49+ 如果有 DecisionTable-specific 的 rebuild 优化(比如增量 rebuild 只
 * 改 column header 不动 rules),这里加 supports() + 优化逻辑。
 */
public class DecisionTableRebuilder implements RuleTypeRebuilder {

    @Override
    public boolean supports(Rule rule) {
        return false;
    }

    @Override
    public void rebuild(Rule rule, ResourceLibrary resLibraries, Map<String, String> namedMap, boolean forDSL) {
        throw new UnsupportedOperationException("DecisionTableRebuilder V5.48 stub — table rules fall through to DrlRuleRebuilder");
    }
}
