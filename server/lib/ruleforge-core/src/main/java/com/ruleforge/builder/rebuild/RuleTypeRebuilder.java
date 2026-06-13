package com.ruleforge.builder.rebuild;

import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rule.Rule;

import java.util.List;
import java.util.Map;

/**
 * V5.48 — 规则类型分发的 SPI。
 *
 * <p>{@link com.ruleforge.builder.RulesRebuilder} 把 714 行的具体逻辑拆成 5 个
 * {@code RuleTypeRebuilder} 实现(向导式/表/树/评分卡/DRL)。每个实现负责一类
 * 规则的 rebuild,facade 走 {@code instanceof} 链分发。
 *
 * <p>为什么是 interface 而不是 abstract class:单元测试可以 mock 单个 rebuilder,
 * 验证 facade 的路由顺序(plan 风险 R7);production 也不需要继承字段。
 *
 * <p>supports() 必须稳定 — 多个 rebuilder 同时 supports 会抛
 * IllegalStateException(facade 防御性检查)。优先级:DrlRuleRebuilder 放最后
 * (fallback,supports(Rule) 永远 true),其他放前面。
 */
public interface RuleTypeRebuilder {

    /**
     * 本 rebuilder 是否能处理该 rule 实例。
     *
     * <p>判别依据:rule instance 的实际 class({@code RuleType} enum 只有
     * {@code DECISION_RULE} 一个值,不能用 enum 路由 — plan 修正点 2)。
     */
    boolean supports(Rule rule);

    /**
     * 对单个 rule 做 rebuild(per-rule try/catch 在 facade 外面包)。
     *
     * <p>实现应处理本类型规则特有的扩展点(LoopRule 的 LoopTarget/LoopStart/LoopEnd 等),
     * 通用 LHS/RHS rebuild 调 {@link com.ruleforge.builder.RulesRebuilder} 的
     * {@code rebuildCriterion} / {@code rebuildAction} / {@code rebuildValue}。
     */
    void rebuild(Rule rule, ResourceLibrary resLibraries, Map<String, String> namedMap, boolean forDSL);
}
