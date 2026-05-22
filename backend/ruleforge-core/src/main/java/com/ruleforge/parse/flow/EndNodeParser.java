package com.ruleforge.parse.flow;

import org.dom4j.Element;

import com.ruleforge.model.flow.EndNode;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class EndNodeParser extends FlowNodeParser<EndNode> {
	public EndNode parse(Element element) {
		EndNode end=new EndNode(element.attributeValue("name"));
		end.setConnections(parseConnections(element));
		end.setEventBean(element.attributeValue("event-bean"));
		return end;
	}
	public boolean support(String name) {
		return name.equals("end");
	}
}
