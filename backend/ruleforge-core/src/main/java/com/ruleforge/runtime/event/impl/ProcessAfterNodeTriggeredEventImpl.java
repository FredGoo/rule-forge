package com.ruleforge.runtime.event.impl;

import com.ruleforge.model.flow.FlowNode;
import com.ruleforge.model.flow.ins.ProcessInstance;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.event.ProcessAfterNodeTriggeredEvent;

/**
 * @author Jacky.gao
 * 2015年7月21日
 */
public class ProcessAfterNodeTriggeredEventImpl extends DefaultProcessEvent implements ProcessAfterNodeTriggeredEvent {
    private FlowNode flowNode;

    public ProcessAfterNodeTriggeredEventImpl(FlowNode flowNode, ProcessInstance processInstance, KnowledgeSession knowledgeSession) {
        super(processInstance, knowledgeSession);
        this.flowNode = flowNode;
    }

    @Override
    public FlowNode getFlowNode() {
        return flowNode;
    }
}
