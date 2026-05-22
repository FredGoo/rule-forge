package com.ruleforge.runtime.event;
/**
 * @author Jacky.gao
 * @since 2015年7月20日
 */
public interface ProcessEventListener extends KnowledgeEventListener{
	/**
	 * 规则流开始之前触发
	 * @param event ProcessBeforeStartedEvent对象
	 */
	void beforeProcessStarted(ProcessBeforeStartedEvent event);
	/**
	 * 规则流开始之后触发，执行完开始节点后触发
	 * @param event ProcessAfterStartedEvent对象
	 */
	void afterProcessStarted(ProcessAfterStartedEvent event);
	/**
	 * 规则流结束之前触发
	 * @param event ProcessBeforeCompletedEvent对象
	 */
	void beforeProcessCompleted(ProcessBeforeCompletedEvent event);
	/**
	 * 规则流结束之后触发
	 * @param event ProcessAfterCompletedEvent对象
	 */
	void afterProcessCompleted(ProcessAfterCompletedEvent event);
	/**
	 * 流经规则流中每个节点前触发
	 * @param event ProcessBeforeNodeTriggeredEvent对象
	 */
	void beforeNodeTriggered(ProcessBeforeNodeTriggeredEvent event);
	/**
	 * 流经规则流中每个节点后触发
	 * @param event ProcessBeforeNodeTriggeredEvent对象
	 */
	void afterNodeTriggered(ProcessAfterNodeTriggeredEvent event);
}
