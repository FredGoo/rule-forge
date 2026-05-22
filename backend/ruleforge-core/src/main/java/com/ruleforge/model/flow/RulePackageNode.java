package com.ruleforge.model.flow;

import com.ruleforge.model.flow.ins.FlowContext;
import com.ruleforge.model.flow.ins.FlowInstance;

/**
 * @author Jacky.gao
 * @since 2015年4月20日
 */
public class RulePackageNode extends BindingNode {
	private FlowNodeType type=FlowNodeType.RulePackage;
	private String packageId;
	private String project;
	public RulePackageNode() {
	}
	public RulePackageNode(String name) {
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
		executeKnowledgePackage(context, instance);
		executeNodeEvent(EventType.leave, context, instance);
		leave(null, context, instance);
	}

	public String getProject() {
		return project;
	}
	public void setProject(String project) {
		this.project = project;
	}
	public String getPackageId() {
		return packageId;
	}

	public void setPackageId(String packageId) {
		this.packageId = packageId;
	}
}
