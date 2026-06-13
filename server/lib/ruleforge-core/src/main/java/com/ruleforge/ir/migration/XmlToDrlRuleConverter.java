package com.ruleforge.ir.migration;

import com.ruleforge.ir.drl.DrlParseException;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleSet;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.model.rule.Rhs;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.util.ArrayList;
import java.util.List;

/**
 * V5.42.6 — 一次性 .xml → .drl 迁移工具。
 *
 * <p>第一版:用 dom4j 自己解析 {@code <rule name="..." salience="N"/>} 简单节点,
 * 灌进 {@link Rule} 对象,再走 V5.42.3b {@link UlToDrlConverter} emit 顶层 metadata。
 *
 * <p>为什么不调老 {@code RuleSetParser}:老 parser 链路依赖 {@code RulesRebuilder} +
 * 大量 Spring bean,集成成本大于收益;第一版范围是"运维一次性导出",简单 .xml 节点
 * 自解析足够,复杂 rule 内容(老 .xml 含 lhs / actions / Library 引用等)留 V5.42.8+。
 *
 * <p>本类**不**是老 .xml 的 hot path — 老 {@code RuleSetResourceBuilder} 仍走老
 * parser 给 runtime 用,V5.42.6 只是给运维一个"老 .xml 文本 → DRL 文本"review 工具。
 *
 * @since 5.42
 */
public class XmlToDrlRuleConverter {

    /**
     * 主入口 — 输入老 .xml 文本,输出 V5.42.6 标识 + 顶层 DRL 文本。
     *
     * @throws DrlParseException null / 空 / dom4j 解析失败时
     */
    public String convert(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            throw new DrlParseException(".xml 文本不能为 null / 空");
        }
        Document doc;
        try {
            SAXReader reader = new SAXReader();
            doc = reader.read(new java.io.StringReader(xml));
        } catch (DocumentException e) {
            throw new DrlParseException(".xml 解析失败: " + e.getMessage(), e);
        }
        Element root = doc.getRootElement();
        List<Rule> rules = new ArrayList<>();
        // V5.43.1 — 如果 root 本身就是 <rule>,直接 parse 它(老 .xml 也允许 <rule> 单独成根)
        if ("rule".equals(root.getName())) {
            rules.add(parseRuleElement(root));
        } else {
            // 否则找所有 rule 子节点(无论 root 是 rule-set / ruleflow / rules 等形式)
            for (Element ele : (List<Element>) root.elements()) {
                if ("rule".equals(ele.getName())) {
                    rules.add(parseRuleElement(ele));
                }
            }
        }
        RuleSet rs = new RuleSet();
        rs.setRules(rules);
        // 走 V5.42.3b emit + V5.42.6 标识(改 header)
        String body = new UlToDrlConverter().emit(rs);
        return "// V5.42.6 — 一次性 .xml → .drl 迁移工具(老 .xml 顶层 1:1 emit)\n" +
            "// 注:本输出是 DRL 化第一版,顶层 metadata 已 emit;lhs / rhs 内部未自动 emit。\n" +
            "// 运维需手动 review + 补 lhs 内容。\n" +
            "\n" +
            stripHeader(body);
    }

    private static Rule parseRuleElement(Element ele) {
        Rule r = new Rule();
        r.setName(ele.attributeValue("name"));
        if (r.getName() == null) {
            throw new DrlParseException(".xml rule 节点缺 name 属性");
        }
        // 简单 attribute 映射
        tryParse(r, ele, "salience", "salience");
        // 字符串 attribute
        r.setAgendaGroup(ele.attributeValue("agenda-group"));
        r.setActivationGroup(ele.attributeValue("activation-group"));
        r.setRuleflowGroup(ele.attributeValue("ruleflow-group"));
        // boolean attribute(Rule 是 Boolean 包装)
        String autoFocus = ele.attributeValue("auto-focus");
        if (autoFocus != null) {
            r.setAutoFocus(parseBoolean(autoFocus));
        }
        String enabled = ele.attributeValue("enabled");
        if (enabled != null) {
            r.setEnabled(parseBoolean(enabled));
        }
        // else 关系
        String elseName = ele.attributeValue("else");
        if (elseName != null) {
            Rule elseRule = new Rule();
            elseRule.setName(elseName);
            r.setWithElse(true);
            r.setElseRule(elseRule);
        }
        // lhs / rhs 第一版留空(emit 留 TODO 注释)
        r.setLhs(new Lhs());
        r.setRhs(new Rhs());
        return r;
    }

    private static void tryParse(Rule r, Element ele, String attr, String field) {
        String v = ele.attributeValue(attr);
        if (v == null) return;
        try {
            if ("salience".equals(field)) {
                r.setSalience(Integer.parseInt(v));
            }
        } catch (NumberFormatException e) {
            throw new DrlParseException("attribute '" + attr + "' 应是整数,实际: '" + v + "'");
        }
    }

    private static Boolean parseBoolean(String s) {
        if ("true".equalsIgnoreCase(s)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s)) return Boolean.FALSE;
        return null;
    }

    /** 去掉 V5.42.3b header(本类自带 V5.42.6 header) */
    private static String stripHeader(String body) {
        // 找到第一个 "rule \"" 出现位置
        int idx = body.indexOf("rule \"");
        if (idx < 0) {
            return body; // 没 rule,直接返回
        }
        return body.substring(idx);
    }
}
