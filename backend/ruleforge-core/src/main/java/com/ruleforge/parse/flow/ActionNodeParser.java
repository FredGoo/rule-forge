package com.ruleforge.parse.flow;

import org.dom4j.Element;

import com.ruleforge.model.flow.ActionNode;

/**
 * @author Jacky.gao
 * @date 2014年12月23日
 */
public class ActionNodeParser extends FlowNodeParser<ActionNode> {
	public ActionNode parse(Element element) {
		ActionNode action=new ActionNode(element.attributeValue("name"));
		action.setActionBean(element.attributeValue("action-bean"));
		action.setEventBean(element.attributeValue("event-bean"));
		action.setX(element.attributeValue("x"));
		action.setY(element.attributeValue("y"));
		action.setWidth(element.attributeValue("width"));
		action.setHeight(element.attributeValue("height"));
		action.setConnections(parseConnections(element));
		return action;
	}

	public boolean support(String name) {
		return name.equals("action");
	}
}
