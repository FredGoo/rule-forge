package com.ruleforge.runtime.event.impl;

import com.ruleforge.model.flow.FlowNode;
import com.ruleforge.model.flow.ins.ProcessInstance;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.event.ProcessBeforeNodeTriggeredEvent;

/**
 * @author Jacky.gao
 * @since 2015年7月21日
 */
public class ProcessBeforeNodeTriggeredEventImpl extends DefaultProcessEvent implements ProcessBeforeNodeTriggeredEvent{
	private FlowNode flowNode;
	public ProcessBeforeNodeTriggeredEventImpl(FlowNode flowNode,ProcessInstance processInstance,KnowledgeSession knowledgeSession) {
		super(processInstance, knowledgeSession);
		this.flowNode=flowNode;
	}
	@Override
	public FlowNode getFlowNode() {
		return flowNode;
	}
}
