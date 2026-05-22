package com.ruleforge.builder.resource;

import org.dom4j.Element;

import com.ruleforge.model.flow.FlowDefinition;
import com.ruleforge.parse.deserializer.FlowDeserializer;

/**
 * @author Jacky.gao
 * @since 2014年12月22日
 */
public class FlowResourceBuilder implements ResourceBuilder<FlowDefinition> {
	private FlowDeserializer flowDeserializer;
	public FlowDefinition build(Element root) {
		return flowDeserializer.deserialize(root);
	}
	public ResourceType getType() {
		return ResourceType.Flow;
	}
	public boolean support(Element root) {
		return flowDeserializer.support(root);
	}
	public void setFlowDeserializer(FlowDeserializer flowDeserializer) {
		this.flowDeserializer = flowDeserializer;
	}
}
