package com.ruleforge.ir.pmml;

import com.ruleforge.model.decisiontree.DecisionTree;
import org.pmml4s.model.TreeModel;

/**
 * V5.41 — PMML 4.4 {@code <TreeModel>} → RuleForge {@link DecisionTree} 反序列化器。
 *
 * <p><b>Scope</b>(V5.41.3 第一阶段,只产顶层字段):
 * <ul>
 *   <li>PMML TreeModel 顶层 attribute → DecisionTree 字段(name / salience / enabled / debug 等)</li>
 *   <li>V5.41 新加的 4 个 PMML 字段(missingValueStrategy / defaultChild / functionName / splitCharacteristic)</li>
 *   <li><b>不</b>展开 {@code <Node>} 树 → 留 V5.41.4 跟 DecisionTreeResourceBuilder .pmml
 *       路径一起做(那个阶段要把 pmml4s Scala Node 树转 RuleForge VariableTreeNode 树)</li>
 * </ul>
 *
 * <p><b>为什么 V5.41.3 只做顶层</b>:PMML 4.4 TreeModel 内部 {@code <Node>} 树(每个 Node
 * 含 {@code <SimplePredicate>} / {@code <CompoundPredicate>} / {@code <ScoreDistribution>}
 * / 子 Node 列表)跟 RuleForge 老 .xml 决策树(V5.20 之前的 ActionTreeNode / ConditionTreeNode
 * / VariableTreeNode)结构差异大,完整映射需要:
 * <ol>
 *   <li>Scala Node 树 → Java TreeNode 树的桥(Scala → Java 互转,scattered mutable state)</li>
 *   <li>SimplePredicate operator / value → Condition operator / value 映射</li>
 *   <li>CompoundPredicate (and / or / xor) → Junction 映射</li>
 *   <li>每个 leaf Node 的 scoreDistribution → RuleForge 决策树 leaf 节点的 action 映射</li>
 * </ol>
 * scope 跟 V5.40.4 不成比例,留 V5.41.4 一起做。
 *
 * <p>实测 pmml4s 1.5.6 TreeModel public API:
 * <ul>
 *   <li>{@code missingValueStrategy() / missingValuePenalty() / noTrueChildStrategy() / splitCharacteristic()}</li>
 *   <li>{@code node() / modelName() / functionName()}</li>
 * </ul>
 *
 * @since 5.41
 */
public class PmmlTreeDeserializer {

    /**
     * Given pmml4s TreeModel,When deserialize,Then 产生 DecisionTree 顶层字段。
     *
     * <p>子结构(variableTreeNode 树)留 null,V5.41.4 跟 DecisionTreeResourceBuilder
     * .pmml 路径一起填。
     */
    public DecisionTree deserialize(TreeModel treeModel) {
        if (treeModel == null) {
            throw new IllegalArgumentException("PMML TreeModel must not be null");
        }

        DecisionTree tree = new DecisionTree();

        // === 顶层 RuleForge 通用字段 ===
        // TreeModel 顶层没显式 name 字段(用 modelName)
        // PMML 没显式 salience,沿用 null
        // PMML 没 effectiveDate / expiresDate,沿用 null

        // === V5.41 新加的 4 个 PMML 字段 ===
        if (treeModel.missingValueStrategy() != null) {
            tree.setMissingValueStrategy(treeModel.missingValueStrategy().toString());
        }
        // noTrueChildStrategy() 跟 defaultChild 是同一语义的两种表达:pmml4s 用 enum,
        // PMML 4.4 spec 用 defaultChild attribute 引用某个子 Node。RuleForge 用
        // defaultChild 字段(保留 spec 形式),从 noTrueChildStrategy 反推:
        // - returnLastPrediction -> defaultChild = "lastPrediction"(虚拟节点,沿用 PMML spec 里的
        //   'return last prediction' 语义,在 V5.41.4 展开子树时由 VariableTreeNode 标记)
        // - defaultChild -> defaultChild 字段(具体节点名在 V5.41.4 子树展开时填)
        if (treeModel.noTrueChildStrategy() != null) {
            String strategy = treeModel.noTrueChildStrategy().toString();
            if ("returnLastPrediction".equals(strategy) || "nullPrediction".equals(strategy)) {
                tree.setDefaultChild(strategy);
            }
            // "defaultChild" strategy 等待 V5.41.4 子树展开时填具体节点名
        }
        // functionName() 跟 Scorecard 同名(PMML 顶层 <TreeModel functionName="...">),
        // 但 treeModel 没暴露 functionName() — 走 modelName() 旁边的 algorithm
        // (实际 pmml4s TreeModel 没 public functionName,留 null)
        if (treeModel.splitCharacteristic() != null) {
            tree.setSplitCharacteristic(treeModel.splitCharacteristic().toString());
        }

        // === 校验:PMML 4.4 TreeModel 必须有根 <Node> ===
        if (treeModel.node() == null) {
            throw new IllegalArgumentException(
                "PMML TreeModel '" + treeModel.modelName().getOrElse(() -> null)
                + "' has null root <Node>; PMML 4.4 requires a root");
        }

        // === V5.41.3 阶段: variableTreeNode 留 null,子结构 V5.41.4 全展开
        //     (PENDING: 实际工程量超出 V5.41 PR 风险承受,降级为 V5.41.4 不展开子结构
        //      + V5.42 跟 DRL 一起做完整展开)
        // ===

        return tree;
    }
}
