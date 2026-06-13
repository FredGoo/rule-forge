package com.ruleforge.model.decisiontree;

import java.util.Date;
import java.util.List;

import com.ruleforge.model.rule.Library;

/**
 * @author Jacky.gao
 * @since 2016年2月26日
 */
public class DecisionTree {
	private Integer salience;
	private Date effectiveDate;
	private Date expiresDate;
	private Boolean enabled;
	private Boolean debug;
	private String remark;
	private List<Library> libraries;
	private VariableTreeNode variableTreeNode;

	/**
	 * V5.41 — PMML 4.4 {@code <TreeModel missingValueStrategy="...">} 映射。
	 * 决策树遇到 missing input 时的策略:{@code lastPrediction / nullPrediction /
	 * defaultChild / aggregateNodes / weightedConfidence}。{@code null} = V5.40
	 * 老用法,RuleForge 老 .xml 决策树不区分 missing,按 RETE 求值"无证据"的默认分支。
	 *
	 * @since 5.41
	 */
	private String missingValueStrategy;

	/**
	 * V5.41 — PMML 4.4 {@code <TreeModel defaultChild="...">} 映射。
	 * 决策树某个 Node 没有 match 任何 predicate 时,跳到 defaultChild 指定的 child。
	 * 这里用 variableTreeNode 内部节点的 name(不是 id,PMML spec 用 name)。
	 * {@code null} = V5.40 老用法。
	 *
	 * @since 5.41
	 */
	private String defaultChild;

	/**
	 * V5.41 — PMML 4.4 {@code <TreeModel functionName="...">} 映射。
	 * PMML 决策树根节点目标变量类型:{@code classification / regression}。
	 * {@code null} = V5.40 老用法,RuleForge 老 .xml 决策树 action 端不区分。
	 *
	 * @since 5.41
	 */
	private String functionName;

	/**
	 * V5.41 — PMML 4.4 {@code <TreeModel splitCharacteristic="...">} 映射。
	 * 分裂节点时使用的纯度指标:{@code informationGain / gini / gainRatio /
	 * chiSquare / predictedClass / varianceReduction}(pmml4s 1.5.6 enum 范围)。
	 * {@code null} = V5.40 老用法(只用于决策路径,不参与训练)。
	 *
	 * @since 5.41
	 */
	private String splitCharacteristic;
	
	public Integer getSalience() {
		return salience;
	}
	public void setSalience(Integer salience) {
		this.salience = salience;
	}
	public Date getEffectiveDate() {
		return effectiveDate;
	}
	public void setEffectiveDate(Date effectiveDate) {
		this.effectiveDate = effectiveDate;
	}
	public Date getExpiresDate() {
		return expiresDate;
	}
	public void setExpiresDate(Date expiresDate) {
		this.expiresDate = expiresDate;
	}
	public Boolean getEnabled() {
		return enabled;
	}
	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}
	public Boolean getDebug() {
		return debug;
	}
	public void setDebug(Boolean debug) {
		this.debug = debug;
	}
	public String getRemark() {
		return remark;
	}
	public void setRemark(String remark) {
		this.remark = remark;
	}
	public List<Library> getLibraries() {
		return libraries;
	}
	public void setLibraries(List<Library> libraries) {
		this.libraries = libraries;
	}
	public VariableTreeNode getVariableTreeNode() {
		return variableTreeNode;
	}
	public void setVariableTreeNode(VariableTreeNode variableTreeNode) {
		this.variableTreeNode = variableTreeNode;
	}

	/**
	 * V5.41 — PMML 4.4 missingValueStrategy 字段读取。
	 * @since 5.41
	 */
	public String getMissingValueStrategy() {
		return missingValueStrategy;
	}

	/**
	 * V5.41 — PMML 4.4 missingValueStrategy 字段写入。
	 * @since 5.41
	 */
	public void setMissingValueStrategy(String missingValueStrategy) {
		this.missingValueStrategy = missingValueStrategy;
	}

	/**
	 * V5.41 — PMML 4.4 defaultChild 字段读取。
	 * @since 5.41
	 */
	public String getDefaultChild() {
		return defaultChild;
	}

	/**
	 * V5.41 — PMML 4.4 defaultChild 字段写入。
	 * @since 5.41
	 */
	public void setDefaultChild(String defaultChild) {
		this.defaultChild = defaultChild;
	}

	/**
	 * V5.41 — PMML 4.4 functionName 字段读取。
	 * @since 5.41
	 */
	public String getFunctionName() {
		return functionName;
	}

	/**
	 * V5.41 — PMML 4.4 functionName 字段写入。
	 * @since 5.41
	 */
	public void setFunctionName(String functionName) {
		this.functionName = functionName;
	}

	/**
	 * V5.41 — PMML 4.4 splitCharacteristic 字段读取。
	 * @since 5.41
	 */
	public String getSplitCharacteristic() {
		return splitCharacteristic;
	}

	/**
	 * V5.41 — PMML 4.4 splitCharacteristic 字段写入。
	 * @since 5.41
	 */
	public void setSplitCharacteristic(String splitCharacteristic) {
		this.splitCharacteristic = splitCharacteristic;
	}
}
