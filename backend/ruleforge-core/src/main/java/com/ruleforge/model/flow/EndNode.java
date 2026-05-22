package com.ruleforge.model.flow;

import com.ruleforge.model.flow.ins.FlowContext;
import com.ruleforge.model.flow.ins.FlowInstance;

/**
 * @author Jacky.gao
 * @since 2015年4月20日
 */
public class EndNode extends FlowNode {
    private FlowNodeType type = FlowNodeType.End;

    public EndNode() {
    }

    @Override
    public FlowNodeType getType() {
        return type;
    }

    public EndNode(String name) {
        super(name);
    }

    @Override
    public void enterNode(FlowContext context, FlowInstance instance) {
        executeNodeEvent(EventType.enter, context, instance);
        instance.setCurrentNode(this);
        executeNodeEvent(EventType.leave, context, instance);
    }
}
