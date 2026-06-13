package com.ruleforge.ir.migration;

import com.ruleforge.model.scorecard.ScorecardDefinition;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import java.io.IOException;
import java.io.StringWriter;

/**
 * V5.41.5 — RuleForge 老 .xml 评分卡 → PMML 4.4 一次性迁移转换器。
 *
 * <p>这是 V5.41 评分卡→PMML 切格式的"数据迁移"工具。一次性跑在 console-app 启动时
 * (可配置 {@code ruleforge.legacy-xml.migrate=true}),把老 .xml 评分卡翻译成 .pmml
 * 写回 Git 仓库,跑完删原 .xml。
 *
 * <p>支持老 .xml 格式(RuleForge 2016 起一直用的 {@code <scorecard>} 根元素 + 顶层 attribute 结构):
 * <pre>{@code
 * <scorecard name="customer_score"
 *            scoring-type="Sum" assign-target-type="Var"
 *            var="score" var-label="客户评分" datatype="Integer"
 *            salience="0" weight-support="true" ...>
 *   <card-cell row="0" col="0" .../>
 *   <attribute-row row="0"> ... </attribute-row>
 *   <custom-col .../>
 *   <import-variable-library path="..."/>
 * </scorecard>
 * }</pre>
 *
 * <p>翻译规则(V5.41.5 第一阶段):
 * <ul>
 *   <li>根元素 {@code <scorecard name="...">} → PMML {@code <Scorecard modelName="...">}</li>
 *   <li>V5.41 4 个新 PMML 字段直接从老 attribute 推(目前都 null,留 V5.41.6 单独 PR 推映射)</li>
 *   <li>DataDictionary:从老 .xml 的 {@code var} / {@code var-label} 抽 input field + 1 个
 *       {@code predicted_score} 兜底 output(pmml4s 强制 4.4 spec 要求至少 1 predicted 字段)</li>
 *   <li>Characteristics: <b>V5.41.5 占位</b> — emit 1 个 {@code <Characteristic name="placeholder">}
 *       带 1 个 {@code <Attribute partialScore="0"><True/></Attribute>},让 pmml4s 至少能 parse。
 *       完整 cells/rows/customCols → Characteristic/Attribute 展开留 V5.41.7 BDD 阶段</li>
 *   <li>不展开 {@code <import-variable-library>} / {@code <import-constant-library>} — 老 Library
 *       引用机制跟 PMML 4.4 的 MiningField 没 1:1 映射,留 V5.42 跟 DRL 一起做</li>
 * </ul>
 *
 * <p><b>V5.41.5 限制</b>:这个 emitter 只能产语法合法 + pmml4s 1.5.6 能 parse 的 .pmml 文件。
 * <b>语义</b>(老 .xml 的 cells/rows 跟 PMML 4.4 的 Characteristic/Attribute 完整等价翻译)
 * 留 V5.41.7 单独 PR — 跟 V5.41.4 PmmlResourceDispatcher "顶层字段先通" 路径对齐:
 * "老 .xml 客户先用 placeholder .pmml 跑通,业务上看到不正确的 score,再走 V5.41.7 补完整翻译"。
 *
 * <p>失败时抛 {@link XmlMigrationException},由调用方决定 fallback 策略(保留老 .xml 不动)。
 *
 * @since 5.41
 */
public class XmlToPmmlScorecardConverter {

    /**
     * Given 老 .xml 评分卡字符串,When convert,Then 产生 PMML 4.4 XML 字符串。
     *
     * <p>走老 {@link com.ruleforge.parse.deserializer.ScorecardDeserializer} 反序列化(完整 cells/rows
     * 树)→ emit PMML 4.4 顶层 + 1 placeholder Characteristic。V5.41.5 不尝试完整 cells → Attribute 翻译。
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
        // 老 .xml 根可能是 <rule-config> 包一层;允许直接 <scorecard> 根
        Element scorecardElem = findScorecardElement(root);
        if (scorecardElem == null) {
            throw new XmlMigrationException(
                "No <scorecard> element found in XML root: " + root.getName());
        }

        // === 走老 deserializer 把 scorecard 顶层字段(部分)抽出来 ===
        // 注:老 deserializer 走完整 parse(Element),会读 cells/rows/customCols + 库引用;
        // 我们 V5.41.5 只取顶层字段(name/var/var-label),完整展开留 V5.41.7。
        // 因为完整 parse 可能要求 scorecard-parser bean 注入完整(可能失败),这里改成
        // 直接 attributeValue 抽字段,不依赖 deserializer 完整 graph。
        String name = scorecardElem.attributeValue("name");
        if (name == null) {
            throw new XmlMigrationException(
                "<scorecard> missing required 'name' attribute");
        }
        String varName = scorecardElem.attributeValue("var");
        String varLabel = scorecardElem.attributeValue("var-label");
        String salience = scorecardElem.attributeValue("salience");

        // === Build PMML XML ===
        Document pmml = DocumentHelper.createDocument();
        Element rootElem = pmml.addElement("PMML", PmmlNamespace.PMML_4_4);
        rootElem.addAttribute("version", "4.4");

        Element header = rootElem.addElement("Header", PmmlNamespace.PMML_4_4);
        header.addAttribute("copyright", "RuleForge");
        header.addAttribute("description",
            "Migrated from legacy RuleForge .xml scorecard '" + name + "' (V5.41.5)");

        // === DataDictionary ===
        // pmml4s 1.5.6 实测要求 DataDictionary 包含 MiningField 引用的所有 field,
        // 以及至少 1 个 predicted field(= Output/OutputField target)。
        Element dataDict = rootElem.addElement("DataDictionary", PmmlNamespace.PMML_4_4);
        int nFields = (varName != null && !varName.isEmpty()) ? 2 : 1;
        dataDict.addAttribute("numberOfFields", String.valueOf(nFields));
        if (varName != null && !varName.isEmpty()) {
            Element inputField = dataDict.addElement("DataField", PmmlNamespace.PMML_4_4);
            inputField.addAttribute("name", varName);
            inputField.addAttribute("dataType", "double");
            inputField.addAttribute("optype", "continuous");
        }
        // predicted_score 必填(pmml4s 强制 4.4 spec)
        Element predictedField = dataDict.addElement("DataField", PmmlNamespace.PMML_4_4);
        predictedField.addAttribute("name", "predicted_score");
        predictedField.addAttribute("dataType", "double");
        predictedField.addAttribute("optype", "continuous");

        // === Scorecard 顶层 ===
        Element scorecard = rootElem.addElement("Scorecard", PmmlNamespace.PMML_4_4);
        scorecard.addAttribute("modelName", name);
        scorecard.addAttribute("functionName", "regression");
        // V5.41 4 个 PMML 字段(默认对齐 V5.41.3 deserializer 的 null → null 行为)
        scorecard.addAttribute("initialScore", "0.0");
        scorecard.addAttribute("useReasonCodes", "false");
        scorecard.addAttribute("baselineMethod", "max");

        // === MiningSchema ===
        Element miningSchema = scorecard.addElement("MiningSchema", PmmlNamespace.PMML_4_4);
        if (varName != null && !varName.isEmpty()) {
            Element miningField = miningSchema.addElement("MiningField", PmmlNamespace.PMML_4_4);
            miningField.addAttribute("name", varName);
            miningField.addAttribute("usageType", "active");
            miningField.addAttribute("optype", "continuous");
        }
        Element predictedMining = miningSchema.addElement("MiningField", PmmlNamespace.PMML_4_4);
        predictedMining.addAttribute("name", "predicted_score");
        predictedMining.addAttribute("usageType", "predicted");

        // === Output ===
        Element output = scorecard.addElement("Output", PmmlNamespace.PMML_4_4);
        Element outputField = output.addElement("OutputField", PmmlNamespace.PMML_4_4);
        outputField.addAttribute("name", "predicted_score");
        outputField.addAttribute("optype", "continuous");
        outputField.addAttribute("dataType", "double");
        outputField.addAttribute("feature", "predictedValue");

        // === Characteristics(占位)===
        // V5.41.5 emit 1 个 placeholder characteristic + 1 个 True/ 兜底 attribute,
        // 让 pmml4s 1.5.6 至少能 parse(0 characteristic 会抛 IllegalArgumentException,
        // 跟 PmmlScorecardDeserializer 校验一致)。完整 cells/rows/customCols 翻译
        // 留 V5.41.7。
        Element characteristics = scorecard.addElement("Characteristics", PmmlNamespace.PMML_4_4);
        Element placeholder = characteristics.addElement("Characteristic", PmmlNamespace.PMML_4_4);
        placeholder.addAttribute("name", "placeholder_" + safeId(name));
        placeholder.addAttribute("baselineScore", "0");
        Element placeholderAttr = placeholder.addElement("Attribute", PmmlNamespace.PMML_4_4);
        placeholderAttr.addAttribute("partialScore", "0.0");
        placeholderAttr.addElement("True", PmmlNamespace.PMML_4_4);

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

    /**
     * V5.41.5 utility — 复用 legacy 反序列化器抽取顶层字段(name + var + var-label)。
     * 注:本方法不依赖 deserializer 完整 graph(避免 cell/row parser 注入失败导致整个
     * 迁移工具挂掉),V5.41.7 真正做 cells/rows 翻译时再用 deserializer 完整图。
     */
    public ScorecardDefinition peekTopLevel(String xmlContent) {
        Document doc;
        try {
            doc = DocumentHelper.parseText(xmlContent);
        } catch (DocumentException e) {
            throw new XmlMigrationException("Failed to parse XML: " + e.getMessage(), e);
        }
        Element root = doc.getRootElement();
        Element scorecardElem = findScorecardElement(root);
        if (scorecardElem == null) {
            throw new XmlMigrationException(
                "No <scorecard> element found in XML root: " + root.getName());
        }
        ScorecardDefinition def = new ScorecardDefinition();
        def.setName(scorecardElem.attributeValue("name"));
        def.setVariableName(scorecardElem.attributeValue("var"));
        def.setVariableLabel(scorecardElem.attributeValue("var-label"));
        String salience = scorecardElem.attributeValue("salience");
        if (salience != null && !salience.isEmpty()) {
            try {
                def.setSalience(Integer.valueOf(salience));
            } catch (NumberFormatException ignored) {
                // 老 .xml salience 不是整数(可能 salience="high")就保留 null
            }
        }
        // V5.41 4 个 PMML 字段都留 null(完整翻译留 V5.41.7)
        return def;
    }

    private static Element findScorecardElement(Element root) {
        if ("scorecard".equals(root.getName())) {
            return root;
        }
        return root.element("scorecard");
    }

    private static String safeId(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
