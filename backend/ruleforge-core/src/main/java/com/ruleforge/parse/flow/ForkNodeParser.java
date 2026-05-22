package com.ruleforge.parse.flow;

import org.dom4j.Element;

import com.ruleforge.model.flow.ForkNode;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class ForkNodeParser extends FlowNodeParser<ForkNode> {
	public ForkNode parse(Element element) {
		ForkNode fork=new ForkNode(element.attributeValue("name"));
		fork.setConnections(parseConnections(element));
		fork.setEventBean(element.attributeValue("event-bean"));
		fork.setX(element.attributeValue("x"));
		fork.setY(element.attributeValue("y"));
		fork.setWidth(element.attributeValue("width"));
		fork.setHeight(element.attributeValue("height"));
		return fork;
	}
	public boolean support(String name) {
		return name.equals("fork");
	}
}
