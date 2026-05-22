package com.ruleforge.runtime.event.impl;

import com.ruleforge.model.flow.ins.ProcessInstance;
import com.ruleforge.runtime.KnowledgeSession;

/**
 * @author Jacky.gao
 * @since 2015年7月21日
 */
public class ProcessBeforeStartedEventImpl extends DefaultProcessEvent{
	public ProcessBeforeStartedEventImpl(ProcessInstance processInstance,KnowledgeSession knowledgeSession) {
		super(processInstance, knowledgeSession);
	}
}
