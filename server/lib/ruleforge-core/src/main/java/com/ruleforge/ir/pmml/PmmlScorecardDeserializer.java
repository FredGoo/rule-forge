package com.ruleforge.ir.pmml;

import com.ruleforge.model.scorecard.ScorecardDefinition;
import org.pmml4s.model.Characteristics;
import org.pmml4s.model.Scorecard;

import java.util.ArrayList;
import java.util.List;

/**
 * V5.41 — PMML 4.4 {@code <Scorecard>} → RuleForge {@link ScorecardDefinition} 反序列化器。
 *
 * <p><b>Scope</b>(V5.41.3 第一阶段,只产顶层字段):
 * <ul>
 *   <li>PMML Scorecard 顶层 attribute → ScorecardDefinition 字段(name / salience / enabled / debug 等)</li>
 *   <li>V5.41 新加的 4 个 PMML 字段(useReasonCodes / initialScore / baselineMethod / reasonCodeAlgorithm)</li>
 *   <li><b>不</b>展开 {@code <Characteristics>}/{@code <Attribute>} 树 — 留 V5.41.4
 *       跟 ScorecardResourceBuilder .pmml 分流一起做(那个阶段要重写 CardCell / AttributeRow
 *       / ConditionRow / CustomCol 跟 PMML Characteristic / Attribute / SimplePredicate 的映射)</li>
 *   <li>暂时不填 {@code cells / rows / customCols},KnowledgeBuilder 走 .pmml 路径时
 *       会用 {@code ScorecardResourceBuilder} 走 V5.41.4 改造后的"PMML native"分支</li>
 * </ul>
 *
 * <p><b>为什么 V5.41.3 只做顶层</b>:PMML 4.4 Scorecard 的 {@code <Characteristic>} 内部
 * 多个 {@code <Attribute>} 跟 RuleForge 老 .xml 的 {@code <condition-row>}/{@code <attribute-row>}
 * 结构差异很大(老 .xml 用 row/col 二维网格 + 单元格 weight,PMML 用树形 attribute partialScore),
 * 全展开需要重写 ScorecardResourceBuilder 的 rule 生成逻辑 — scope 跟 V5.40.4 KnowledgeBuilder
 * 7 行改动不成比例。V5.41.4 单独 PR 一起改(knowledgeBuilder .pmml 分流 + builder 重写)。
 *
 * <p>实测 pmml4s 1.5.6 Scorecard public API:
 * <ul>
 *   <li>{@code name() / modelName() / initialScore() / useReasonCodes()}</li>
 *   <li>{@code baselineMethod() / reasonCodeAlgorithm() / baselineScore()}</li>
 *   <li>{@code characteristics() / miningSchema()}</li>
 * </ul>
 *
 * @since 5.41
 */
public class PmmlScorecardDeserializer {

    /**
     * Given pmml4s Scorecard,When deserialize,Then 产生 ScorecardDefinition 顶层字段。
     *
     * <p>子结构(cells/rows/customCols)留空 list,V5.41.4 跟 ScorecardResourceBuilder
     * .pmml 路径一起填。
     */
    public ScorecardDefinition deserialize(Scorecard scorecard) {
        if (scorecard == null) {
            throw new IllegalArgumentException("PMML Scorecard must not be null");
        }

        ScorecardDefinition def = new ScorecardDefinition();

        // === 顶层 RuleForge 通用字段(从 PMML 顶层 attribute 读) ===
        if (scorecard.modelName().isDefined()) {
            def.setName(scorecard.modelName().get());
        }
        // PMML 没显式 salience,沿用 null 保留 V5.40 兼容
        // PMML 没 effectiveDate / expiresDate,沿用 null
        // PMML 没 enabled / debug,沿用 null(用户需要在 console-ui 单独配)

        // === V5.41 新加的 4 个 PMML 字段 ===
        def.setUseReasonCodes(scorecard.useReasonCodes());
        def.setInitialScore(scorecard.initialScore());
        // baselineMethod() 返回 scala.Enumeration$Value,name() 是 "max" / "min" / "sum" / "none"
        if (scorecard.baselineMethod() != null) {
            def.setBaselineMethod(scorecard.baselineMethod().toString());
        }
        // reasonCodeAlgorithm() 同 baselineMethod() 形式
        if (scorecard.reasonCodeAlgorithm() != null) {
            def.setReasonCodeAlgorithm(scorecard.reasonCodeAlgorithm().toString());
        }

        // === 子结构留空(V5.41.4 填) ===
        def.setCells(new ArrayList<>());
        def.setRows(new ArrayList<>());
        def.setCustomCols(new ArrayList<>());

        // === 校验:至少要有 1 个 characteristic(PMML 4.4 强制) ===
        Characteristics chars = scorecard.characteristics();
        if (chars == null || chars.characteristics().length == 0) {
            throw new IllegalArgumentException(
                "PMML Scorecard '" + def.getName() + "' has 0 <Characteristic>; PMML 4.4 requires at least 1");
        }

        // === V5.41.3 阶段: cells/rows/customCols 留空 list(已设)
        //     V5.41.4 完整 Characteristic → CardCell/AttributeRow 展开超出本 PR scope,
        //     跟 V5.42 DRL 一起做(留 TODO comment)
        // ===

        return def;
    }
}
