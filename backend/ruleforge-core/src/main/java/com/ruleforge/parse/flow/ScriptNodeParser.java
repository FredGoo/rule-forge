package com.ruleforge.parse.flow;

import org.dom4j.Element;

import com.ruleforge.model.flow.ScriptNode;

/**
 * @author Jacky.gao
 * @since 2015年4月22日
 */
public class ScriptNodeParser extends FlowNodeParser<ScriptNode> {
	@Override
	public ScriptNode parse(Element element) {
		ScriptNode node =new ScriptNode();
		node.setName(element.attributeValue("name"));
		node.setEventBean(element.attributeValue("event-bean"));
		node.setX(element.attributeValue("x"));
		node.setY(element.attributeValue("y"));
		node.setWidth(element.attributeValue("width"));
		node.setHeight(element.attributeValue("height"));
		node.setConnections(parseConnections(element));
		String script=element.getStringValue();
		node.setScript(script);
		return node;
	}
	public boolean support(String name) {
		return name.equals("script");
	};
}
