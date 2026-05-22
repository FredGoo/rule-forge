package com.ruleforge.parse.flow;

import com.ruleforge.model.flow.RuleNode;
import org.dom4j.Element;
import org.springframework.util.StringUtils;

/**
 * @author Jacky.gao
 * @since 2015年4月21日
 */
public class RuleNodeParser extends FlowNodeParser<RuleNode> {
    @Override
    public RuleNode parse(Element element) {
        RuleNode node = new RuleNode(element.attributeValue("name"));
        node.setFile(element.attributeValue("file"));
        node.setVersion(element.attributeValue("version"));
        node.setX(element.attributeValue("x"));
        node.setY(element.attributeValue("y"));
        node.setWidth(element.attributeValue("width"));
        node.setHeight(element.attributeValue("height"));
        node.setEventBean(element.attributeValue("event-bean"));
        node.setPackageName(element.attributeValue("packageName"));
        if (StringUtils.hasText(element.attributeValue("index"))) {
            node.setIndex(Integer.valueOf(element.attributeValue("index")));
        }
        node.setConnections(parseConnections(element));
        return node;
    }

    @Override
    public boolean support(String name) {
        return name.equals("rule");
    }
}
