package com.ruleforge.ir.migration;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

/**
 * V5.41.5 — RuleForge 老 .xml 决策树 → PMML 4.4 一次性迁移转换器。
 *
 * <p>支持老 .xml 格式(RuleForge 2016 起一直用的 {@code <decision-tree>} 根元素):
 * <pre>{@code
 * <decision-tree name="loan_classifier" salience="0">
 *   <variable-tree-node ...>
 *     <condition-tree-node op="GreaterThen" value="700">
 *       <action-tree-node>...</action-tree-node>
 *     </condition-tree-node>
 *   </variable-tree-node>
 * </decision-tree>
 * }</pre>
 *
 * <p>翻译规则(V5.41.5 第一阶段):
 * <ul>
 *   <li>根 {@code <decision-tree name="...">} → PMML {@code <TreeModel modelName="...">}</li>
 *   <li>V5.41 4 个新 PMML 字段从 {@code missingValueStrategy} 推,其他都走默认
 *       (pmml4s 1.5.6 enum 限制) — {@code missingValueStrategy="lastPrediction"},
 *       {@code noTrueChildStrategy="returnLastPrediction"}</li>
 *   <li>DataDictionary:从老 .xml 的 {@code <variable-tree-node name="...">} 抽 input field
 *       + 1 个 {@code predicted_label} 兜底 output</li>
 *   <li>{@code <Node>} 树: <b>V5.41.5 占位</b> — emit 1 个 root + 1 个 leaf 兜底,让 pmml4s
 *       至少能 parse。完整 variableTreeNode 树 → Node 树翻译留 V5.41.7 BDD 阶段</li>
 * </ul>
 *
 * <p>跟 {@link XmlToPmmlScorecardConverter} 同款"先通顶层,完整展开留 V5.41.7"策略。
 *
 * @since 5.41
 */
public class XmlToPmmlTreeConverter {

    /**
     * Given 老 .xml 决策树字符串,When convert,Then 产生 PMML 4.4 XML 字符串。
     */
    public String convert(String xmlContent) {
        if (xmlContent == null || xmlContent.isEmpty()) {
            throw new XmlMigrationException("XML content must not be empty");
        }

        Document doc;
        try {
            doc = DocumentHelper.parseText(xmlContent);
        } catch (DocumentException e) {
            throw new XmlMigrationException("Failed to parse XML: " + e.getMessage(), e);
        }

        Element root = doc.getRootElement();
        Element treeElem = findDecisionTreeElement(root);
        if (treeElem == null) {
            throw new XmlMigrationException(
                "No <decision-tree> element found in XML root: " + root.getName());
        }

        String name = treeElem.attributeValue("name");
        if (name == null) {
            throw new XmlMigrationException(
                "<decision-tree> missing required 'name' attribute");
        }
        // 抽 variable-tree-node 的 name(顶层 split feature,老 RuleForge 决策树约定)
        String rootVarName = firstVariableName(treeElem);

        // === Build PMML XML ===
        Document pmml = DocumentHelper.createDocument();
        Element rootElem = pmml.addElement("PMML", PmmlNamespace.PMML_4_4);
        rootElem.addAttribute("version", "4.4");

        Element header = rootElem.addElement("Header", PmmlNamespace.PMML_4_4);
        header.addAttribute("copyright", "RuleForge");
        header.addAttribute("description",
            "Migrated from legacy RuleForge .xml decision-tree '" + name + "' (V5.41.5)");

        // === DataDictionary ===
        Element dataDict = rootElem.addElement("DataDictionary", PmmlNamespace.PMML_4_4);
        int nFields = (rootVarName != null && !rootVarName.isEmpty()) ? 2 : 1;
        dataDict.addAttribute("numberOfFields", String.valueOf(nFields));
        if (rootVarName != null && !rootVarName.isEmpty()) {
            Element inputField = dataDict.addElement("DataField", PmmlNamespace.PMML_4_4);
            inputField.addAttribute("name", rootVarName);
            inputField.addAttribute("dataType", "double");
            inputField.addAttribute("optype", "continuous");
        }
        // predicted_label 兜底 output
        Element predictedField = dataDict.addElement("DataField", PmmlNamespace.PMML_4_4);
        predictedField.addAttribute("name", "predicted_label");
        predictedField.addAttribute("dataType", "string");
        predictedField.addAttribute("optype", "categorical");

        // === TreeModel 顶层 ===
        Element treeModel = rootElem.addElement("TreeModel", PmmlNamespace.PMML_4_4);
        treeModel.addAttribute("modelName", name);
        treeModel.addAttribute("functionName", "classification");
        // V5.41 4 个 PMML 字段(pmml4s 1.5.6 兼容范围)
        treeModel.addAttribute("missingValueStrategy", "lastPrediction");
        treeModel.addAttribute("noTrueChildStrategy", "returnLastPrediction");
        // splitCharacteristic / functionName 留 null(pmml4s enum 限制)

        // === MiningSchema ===
        Element miningSchema = treeModel.addElement("MiningSchema", PmmlNamespace.PMML_4_4);
        if (rootVarName != null && !rootVarName.isEmpty()) {
            Element miningField = miningSchema.addElement("MiningField", PmmlNamespace.PMML_4_4);
            miningField.addAttribute("name", rootVarName);
            miningField.addAttribute("usageType", "active");
            miningField.addAttribute("optype", "continuous");
        }
        Element predictedMining = miningSchema.addElement("MiningField", PmmlNamespace.PMML_4_4);
        predictedMining.addAttribute("name", "predicted_label");
        predictedMining.addAttribute("usageType", "predicted");

        // === Output ===
        Element output = treeModel.addElement("Output", PmmlNamespace.PMML_4_4);
        Element outputField = output.addElement("OutputField", PmmlNamespace.PMML_4_4);
        outputField.addAttribute("name", "predicted_label");
        outputField.addAttribute("optype", "categorical");
        outputField.addAttribute("dataType", "string");
        outputField.addAttribute("feature", "predictedValue");

        // === Node 树(占位)===
        // V5.41.5 emit 1 个 root <Node> + 1 个 leaf 子节点(用 True/ 兜底),让 pmml4s
        // 1.5.6 至少能 parse。完整 variableTreeNode → Node 树翻译留 V5.41.7。
        Element rootNode = treeModel.addElement("Node", PmmlNamespace.PMML_4_4);
        rootNode.addAttribute("id", "1");
        rootNode.addAttribute("score", "placeholder");
        // placeholder root 不加 predicate,直接放 1 个 True leaf
        Element leaf = rootNode.addElement("Node", PmmlNamespace.PMML_4_4);
        leaf.addAttribute("id", "2");
        leaf.addAttribute("score", "placeholder_leaf");
        leaf.addElement("True", PmmlNamespace.PMML_4_4);

        // === 序列化 ===
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding("UTF-8");
        StringWriter sw = new StringWriter();
        try {
            XMLWriter writer = new XMLWriter(sw, format);
            writer.write(pmml);
            writer.close();
        } catch (IOException e) {
            throw new XmlMigrationException("Failed to serialize PMML XML", e);
        }
        return sw.toString();
    }

    private static Element findDecisionTreeElement(Element root) {
        if ("decision-tree".equals(root.getName())) {
            return root;
        }
        return root.element("decision-tree");
    }

    private static String firstVariableName(Element treeElem) {
        // 找第一个 <variable-tree-node> 抽 name attribute
        Element varNode = findFirstElement(treeElem, "variable-tree-node");
        if (varNode == null) {
            return null;
        }
        return varNode.attributeValue("name");
    }

    private static Element findFirstElement(Element parent, String name) {
        if (name.equals(parent.getName())) {
            return parent;
        }
        @SuppressWarnings("unchecked")
        List<Element> children = parent.elements();
        for (Element child : children) {
            Element found = findFirstElement(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
