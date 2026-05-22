package com.ruleforge.parse.flow;

import org.dom4j.Element;

import com.ruleforge.model.flow.JoinNode;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class JoinNodeParser extends FlowNodeParser<JoinNode> {
	public JoinNode parse(Element element) {
		JoinNode join=new JoinNode(element.attributeValue("name"));
		join.setConnections(parseConnections(element));
		join.setEventBean(element.attributeValue("event-bean"));
		join.setX(element.attributeValue("x"));
		join.setY(element.attributeValue("y"));
		join.setWidth(element.attributeValue("width"));
		join.setHeight(element.attributeValue("height"));
		return join;
	}
	public boolean support(String name) {
		return name.equals("join");
	}
}
