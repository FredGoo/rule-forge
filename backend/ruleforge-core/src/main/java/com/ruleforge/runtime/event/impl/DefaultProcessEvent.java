package com.ruleforge.runtime.event.impl;

import com.ruleforge.model.flow.ins.ProcessInstance;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.event.ProcessEvent;

/**
 * @author Jacky.gao
 * 2015年7月21日
 */
public class DefaultProcessEvent implements ProcessEvent {
    private ProcessInstance processInstance;
    private KnowledgeSession knowledgeSession;

    public DefaultProcessEvent(ProcessInstance processInstance, KnowledgeSession knowledgeSession) {
        this.processInstance = processInstance;
        this.knowledgeSession = knowledgeSession;
    }

    @Override
    public KnowledgeSession getKnowledgeSession() {
        return knowledgeSession;
    }

    @Override
    public ProcessInstance getProcessInstance() {
        return processInstance;
    }
}
