package com.ruleforge.parse.flow;

import org.dom4j.Element;

import com.ruleforge.model.flow.RulePackageNode;

/**
 * @author Jacky.gao
 * @since 2015年4月21日
 */
public class RulePackageNodeParser extends FlowNodeParser<RulePackageNode> {
	@Override
	public RulePackageNode parse(Element element) {
		RulePackageNode node=new RulePackageNode(element.attributeValue("name"));
		node.setConnections(parseConnections(element));
		node.setProject(element.attributeValue("project"));
		node.setPackageId(element.attributeValue("package-id"));
		node.setX(element.attributeValue("x"));
		node.setY(element.attributeValue("y"));
		node.setWidth(element.attributeValue("width"));
		node.setHeight(element.attributeValue("height"));
		return node;
	}
	@Override
	public boolean support(String name) {
		return name.equals("rule-package");
	}
}
