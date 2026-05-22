package com.ruleforge.model.flow;

import com.ruleforge.model.flow.ins.FlowContext;
import com.ruleforge.model.flow.ins.FlowInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jacky.gao
 * @since 2015年4月20日
 */
public class ForkNode extends FlowNode {
    private FlowNodeType type = FlowNodeType.Fork;

    public ForkNode() {
    }

    public ForkNode(String name) {
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
        List<Connection> forkConnections = new ArrayList<Connection>();
        for (Connection connection : connections) {
            if (connection.evaluate(context)) {
                forkConnections.add(connection);
            }
        }
        executeNodeEvent(EventType.leave, context, instance);
        int childCount = forkConnections.size();
        for (Connection connection : forkConnections) {
            FlowInstance newChildInstance = instance.newChildInstance(childCount);
            connection.execute(context, newChildInstance);
        }
    }
}
