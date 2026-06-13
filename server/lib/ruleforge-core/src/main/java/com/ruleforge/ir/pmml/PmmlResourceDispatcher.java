package com.ruleforge.ir.pmml;

import com.ruleforge.model.decisiontree.DecisionTree;
import com.ruleforge.model.scorecard.ScorecardDefinition;
import org.pmml4s.model.Model;
import org.pmml4s.model.Scorecard;
import org.pmml4s.model.TreeModel;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * V5.41 — PMML 4.4 资源分流器(给 KnowledgeBuilder 用)。
 *
 * <p>资源路径以 {@code .pmml} 结尾时,走此 dispatcher 单点解析 + 反序列化。
 * 跟 V5.40.4 {@code DmnResourceDispatcher} 同款设计:
 * <ul>
 *   <li>输入:{@code path} + {@code byte[] content}</li>
 *   <li>输出:RuleForge model(ScorecardDefinition 或 DecisionTree),由 KnowledgeBuilder 走相应 builder</li>
 *   <li>不直接调 {@code ScorecardResourceBuilder} / {@code DecisionTreeResourceBuilder},
 *       避免循环依赖</li>
 * </ul>
 *
 * <p>PMML 顶层 model 决定具体 deserializer:
 * <ul>
 *   <li>{@code <Scorecard>...</Scorecard>} → PmmlScorecardDeserializer</li>
 *   <li>{@code <TreeModel>...</TreeModel>} → PmmlTreeDeserializer</li>
 *   <li>其他 PMML model type(NeuralNet / RuleSet / RegressionModel / ...)→ V5.41 不支持,
 *       抛 IllegalArgumentException(留给未来 PR)</li>
 * </ul>
 *
 * <p><b>V5.41.3 limitation</b>:两个 deserializer 当前只填 model 顶层字段(ScorecardDefinition
 * 顶层 + DecisionTree 顶层),子结构(cells/rows + variableTreeNode 树)留空 — 完整展开
 * 是 V5.41.4.1 单独 PR 工作(超出 V5.41 顶层字段的 scope)。本 dispatcher 走 .pmml 路径后,
 * 子结构空 → KnowledgeBuilder 看到的 model 跟 V5.40 老 .xml 一个"无 row/无 cell"空表一样,
 * 生成 0 rule,不抛异常(为后续 PR 留展开空间,保持 KnowledgeBuilder 接口不变)。
 *
 * @since 5.41
 */
public class PmmlResourceDispatcher {

    private final PmmlScorecardDeserializer scorecardDeserializer = new PmmlScorecardDeserializer();
    private final PmmlTreeDeserializer treeDeserializer = new PmmlTreeDeserializer();

    /**
     * Given .pmml 资源,When dispatch,Then 解析顶层 model type + 反序列化到 RuleForge model。
     *
     * <p>返回类型是 {@code Object} 因为 KnowledgeBuilder 接收多种 model type。
     * 实际返回 {@link ScorecardDefinition} 或 {@link DecisionTree}。
     */
    public Object dispatch(String path, String content) {
        if (path == null || !path.toLowerCase().endsWith(".pmml")) {
            throw new IllegalArgumentException(
                "PmmlResourceDispatcher only accepts .pmml paths, got: " + path);
        }
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("PMML content must not be empty");
        }

        Model pmmlModel;
        try (InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            pmmlModel = Model.fromInputStream(is);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Failed to parse PMML: " + e.getMessage(), e);
        }

        if (pmmlModel instanceof Scorecard) {
            return scorecardDeserializer.deserialize((Scorecard) pmmlModel);
        }
        if (pmmlModel instanceof TreeModel) {
            return treeDeserializer.deserialize((TreeModel) pmmlModel);
        }
        throw new IllegalArgumentException(
            "Unsupported PMML model type: " + pmmlModel.getClass().getName()
            + " (V5.41 supports Scorecard + TreeModel; others留给未来 PR)");
    }
}
