package com.ruleforge.parse.flow;

import org.dom4j.Element;

import com.ruleforge.model.flow.StartNode;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class StartNodeParser extends FlowNodeParser<StartNode> {
	public StartNode parse(Element element) {
		StartNode start=new StartNode(element.attributeValue("name"));
		start.setConnections(parseConnections(element));
		start.setEventBean(element.attributeValue("event-bean"));
		start.setX(element.attributeValue("x"));
		start.setY(element.attributeValue("y"));
		start.setWidth(element.attributeValue("width"));
		start.setHeight(element.attributeValue("height"));
		return start;
	}
	public boolean support(String name) {
		return name.equals("start");
	}
}
