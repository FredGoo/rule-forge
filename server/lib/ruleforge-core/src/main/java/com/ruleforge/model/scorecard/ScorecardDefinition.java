package com.ruleforge.model.scorecard;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Library;

/**
 * @author Jacky.gao
 * @since 2016年9月20日
 */
public class ScorecardDefinition {
	private String name;
	private Integer salience;
	private Date effectiveDate;
	private Date expiresDate;
	private Boolean enabled;
	private Boolean debug;
	
	private String attributeColName;
	private String attributeColWidth;
	private String attributeColVariableCategory;
	
	private String conditionColName;
	private String conditionColWidth;
	
	private String scoreColName;
	private String scoreColWidth;
	
	private boolean weightSupport;
	private ScoringType scoringType;
	private String scoringBean;
	private AssignTargetType assignTargetType;
	
	private String remark;
	private String variableCategory;
	private String variableName;
	private String variableLabel;
	private Datatype datatype;
	
	private List<CardCell> cells;
	private List<CustomCol> customCols;
	private List<AttributeRow> rows;
	private List<Library> libraries;

	/**
	 * V5.41 — PMML 4.4 {@code <Scorecard useReasonCodes="...">} 映射。
	 * 老 .xml 决策表 → PMML 迁移时,V5.41.5 XmlToPmmlScorecardConverter
	 * 把 rule output 里的 reason 字段合并成 reason codes。{@code null} = V5.40
	 * 老用法不关心 PMML 特有语义,走默认 {@code false}。
	 *
	 * @since 5.41
	 */
	private Boolean useReasonCodes;

	/**
	 * V5.41 — PMML 4.4 {@code <Scorecard initialScore="...">} 映射。
	 * 给所有 characteristic baseline score 的兜底常量(不写则每个 characteristic
	 * 各自 baselineScore 字段兜底)。{@code null} = V5.40 老用法。
	 *
	 * @since 5.41
	 */
	private Double initialScore;

	/**
	 * V5.41 — PMML 4.4 {@code <Scorecard baselineMethod="...">} 映射。
	 * 多个 characteristic 之间聚合方式:{@code max / min / sum / none}(pmml4s 1.5.6
	 * enum 不认 {@code other / median})。{@code null} = V5.40 老用法走默认 {@code max}。
	 *
	 * @since 5.41
	 */
	private String baselineMethod;

	/**
	 * V5.41 — PMML 4.4 {@code <Scorecard reasonCodeAlgorithm="...">} 映射。
	 * reason code 生成算法:{@code pointsAbove / pointsBelow / none}。
	 * {@code null} = 不关心,pmml4s 1.5.6 默认 {@code pointsAbove}。
	 *
	 * @since 5.41
	 */
	private String reasonCodeAlgorithm;
	
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
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
	public String getAttributeColName() {
		return attributeColName;
	}
	public void setAttributeColName(String attributeColName) {
		this.attributeColName = attributeColName;
	}
	public String getAttributeColWidth() {
		return attributeColWidth;
	}
	public void setAttributeColWidth(String attributeColWidth) {
		this.attributeColWidth = attributeColWidth;
	}
	public String getAttributeColVariableCategory() {
		return attributeColVariableCategory;
	}
	public void setAttributeColVariableCategory(String attributeColVariableCategory) {
		this.attributeColVariableCategory = attributeColVariableCategory;
	}
	public String getConditionColName() {
		return conditionColName;
	}
	public void setConditionColName(String conditionColName) {
		this.conditionColName = conditionColName;
	}
	public String getConditionColWidth() {
		return conditionColWidth;
	}
	public void setConditionColWidth(String conditionColWidth) {
		this.conditionColWidth = conditionColWidth;
	}
	public String getScoreColName() {
		return scoreColName;
	}
	public void setScoreColName(String scoreColName) {
		this.scoreColName = scoreColName;
	}
	public String getScoreColWidth() {
		return scoreColWidth;
	}
	public void setScoreColWidth(String scoreColWidth) {
		this.scoreColWidth = scoreColWidth;
	}
	public boolean isWeightSupport() {
		return weightSupport;
	}
	public void setWeightSupport(boolean weightSupport) {
		this.weightSupport = weightSupport;
	}
	public ScoringType getScoringType() {
		return scoringType;
	}
	public void setScoringType(ScoringType scoringType) {
		this.scoringType = scoringType;
	}
	public String getScoringBean() {
		return scoringBean;
	}
	public void setScoringBean(String scoringBean) {
		this.scoringBean = scoringBean;
	}
	public AssignTargetType getAssignTargetType() {
		return assignTargetType;
	}
	public void setAssignTargetType(AssignTargetType assignTargetType) {
		this.assignTargetType = assignTargetType;
	}
	public String getVariableCategory() {
		return variableCategory;
	}
	public void setVariableCategory(String variableCategory) {
		this.variableCategory = variableCategory;
	}
	public String getVariableName() {
		return variableName;
	}
	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}
	public String getVariableLabel() {
		return variableLabel;
	}
	public void setVariableLabel(String variableLabel) {
		this.variableLabel = variableLabel;
	}
	public String getRemark() {
		return remark;
	}
	public void setRemark(String remark) {
		this.remark = remark;
	}
	public Datatype getDatatype() {
		return datatype;
	}
	public void setDatatype(Datatype datatype) {
		this.datatype = datatype;
	}
	public List<CustomCol> getCustomCols() {
		return customCols;
	}
	public void setCustomCols(List<CustomCol> customCols) {
		this.customCols = customCols;
	}
	public List<AttributeRow> getRows() {
		return rows;
	}
	public void setRows(List<AttributeRow> rows) {
		this.rows = rows;
	}
	public List<Library> getLibraries() {
		return libraries;
	}
	public void setLibraries(List<Library> libraries) {
		this.libraries = libraries;
	}
	public void addLibrary(Library library){
		if(libraries==null){
			libraries=new ArrayList<Library>();
		}
		libraries.add(library);
	}
	public List<CardCell> getCells() {
		return cells;
	}
	public void setCells(List<CardCell> cells) {
		this.cells = cells;
	}

	/**
	 * V5.41 — PMML 4.4 useReasonCodes 字段读取。
	 * @since 5.41
	 */
	public Boolean getUseReasonCodes() {
		return useReasonCodes;
	}

	/**
	 * V5.41 — PMML 4.4 useReasonCodes 字段写入。
	 * @since 5.41
	 */
	public void setUseReasonCodes(Boolean useReasonCodes) {
		this.useReasonCodes = useReasonCodes;
	}

	/**
	 * V5.41 — PMML 4.4 initialScore 字段读取。
	 * @since 5.41
	 */
	public Double getInitialScore() {
		return initialScore;
	}

	/**
	 * V5.41 — PMML 4.4 initialScore 字段写入。
	 * @since 5.41
	 */
	public void setInitialScore(Double initialScore) {
		this.initialScore = initialScore;
	}

	/**
	 * V5.41 — PMML 4.4 baselineMethod 字段读取。
	 * @since 5.41
	 */
	public String getBaselineMethod() {
		return baselineMethod;
	}

	/**
	 * V5.41 — PMML 4.4 baselineMethod 字段写入。
	 * @since 5.41
	 */
	public void setBaselineMethod(String baselineMethod) {
		this.baselineMethod = baselineMethod;
	}

	/**
	 * V5.41 — PMML 4.4 reasonCodeAlgorithm 字段读取。
	 * @since 5.41
	 */
	public String getReasonCodeAlgorithm() {
		return reasonCodeAlgorithm;
	}

	/**
	 * V5.41 — PMML 4.4 reasonCodeAlgorithm 字段写入。
	 * @since 5.41
	 */
	public void setReasonCodeAlgorithm(String reasonCodeAlgorithm) {
		this.reasonCodeAlgorithm = reasonCodeAlgorithm;
	}
}
