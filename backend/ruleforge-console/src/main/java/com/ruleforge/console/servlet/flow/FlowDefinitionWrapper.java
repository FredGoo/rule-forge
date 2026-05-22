package com.ruleforge.console.servlet.flow;

import com.ruleforge.model.flow.FlowDefinition;

public class FlowDefinitionWrapper {

    private final FlowDefinition flowDefinition;

    public FlowDefinitionWrapper(FlowDefinition flowDefinition) {
        this.flowDefinition = flowDefinition;
    }

    public FlowDefinition getFlowDefinition() {
        return flowDefinition;
    }
}
