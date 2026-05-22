package com.ruleforge.parse.deserializer;

import com.ruleforge.model.flow.FlowDefinition;
import com.ruleforge.parse.flow.FlowDefinitionParser;
import org.dom4j.Element;

/**
 * @author Jacky.gao
 * @date 2014年12月23日
 */
public class FlowDeserializer implements Deserializer<FlowDefinition> {
    public static final String BEAN_ID = "ruleforge.flowDeserializer";
    private FlowDefinitionParser flowDefinitionParser;

    @Override
    public FlowDefinition deserialize(Element root) {
        return deserialize(root, false);
    }

    @Override
    public FlowDefinition deserialize(Element root, boolean isContainSnapshot) {
        return flowDefinitionParser.parse(root);
    }

    @Override
    public boolean support(Element root) {
        if (flowDefinitionParser.support(root.getName())) {
            return true;
        } else {
            return false;
        }
    }

    public void setFlowDefinitionParser(FlowDefinitionParser flowDefinitionParser) {
        this.flowDefinitionParser = flowDefinitionParser;
    }
}
