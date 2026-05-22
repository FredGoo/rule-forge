package com.ruleforge.model.flow.ins;

import java.util.List;

import com.ruleforge.model.flow.FlowNode;
import com.ruleforge.model.flow.ProcessDefinition;

/**
 * @author Jacky.gao
 * @since 2015年7月20日
 */
public interface ProcessInstance {
	ProcessDefinition getProcessDefinition();
	List<ProcessInstance> getChildren();
	int getParallelInstanceCount();
	String getId();
	FlowNode getCurrentNode();
	ProcessInstance getParent();
}
