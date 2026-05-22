package com.ruleforge.model.flow;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.flow.ins.FlowContext;
import com.ruleforge.model.flow.ins.FlowInstance;

/**
 * @author Jacky.gao
 * @since 2015年4月20日
 */
public class JoinNode extends FlowNode {
    private FlowNodeType type = FlowNodeType.Join;

    public JoinNode() {
    }

    public JoinNode(String name) {
        super(name);
    }

    @Override
    public FlowNodeType getType() {
        return type;
    }

    @Override
    public void enterNode(FlowContext context, FlowInstance instance) {
        instance.setCurrentNode(this);
        executeNodeEvent(EventType.enter, context, instance);
        FlowInstance parentInstance = instance.getParent();
        if (parentInstance == null) {
            throw new RuleException("Invalid flow instance.");
        }
        String id = parentInstance.getId();
        int arrivedChild = 1;
        if (context.getVariable(id) == null) {
            context.addVariable(id, arrivedChild);
        } else {
            arrivedChild = (Integer) context.getVariable(id);
            arrivedChild++;
            context.addVariable(id, arrivedChild);
        }
        executeNodeEvent(EventType.leave, context, instance);
        int childCount = instance.getParallelInstanceCount();
        if (arrivedChild >= childCount) {
            context.removeVariable(id);
            leave(null, context, parentInstance);
        }
    }
}
