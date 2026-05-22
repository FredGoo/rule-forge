package com.ruleforge.console.util;

import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.repository.model.Type;

import static com.ruleforge.console.repository.BaseRepositoryService.RES_PACKGE_FILE;
import static com.ruleforge.console.storage.RuleForgeBaseRepositoryService.PACKAGE_CONFIG_FILE;

/**
 * 文件类型工具类
 */
public class FileTypeUtils {

    /**
     * 根据文件名映射到对应的文件类型
     *
     * @param name 文件名
     * @return 文件类型
     */
    public static Type mapFileNameToType(String name) {
        Type type = null;
        if (name.toLowerCase().endsWith(FileType.ActionLibrary.toString())) {
            type = Type.action;
        } else if (name.toLowerCase().endsWith(FileType.VariableLibrary.toString())) {
            type = Type.variable;
        } else if (name.toLowerCase().endsWith(FileType.ConstantLibrary.toString())) {
            type = Type.constant;
        } else if (name.toLowerCase().endsWith(FileType.Ruleset.toString())) {
            type = Type.rule;
        } else if (name.toLowerCase().endsWith(FileType.RulesetLib.toString())) {
            type = Type.rule;
        } else if (name.toLowerCase().endsWith(FileType.DecisionTable.toString())) {
            type = Type.decisionTable;
        } else if (name.toLowerCase().endsWith(FileType.Crosstab.toString())) {
            type = Type.crosstab;
        } else if (name.toLowerCase().endsWith(FileType.UL.toString())) {
            type = Type.ul;
        } else if (name.toLowerCase().endsWith(FileType.ParameterLibrary.toString())) {
            type = Type.parameter;
        } else if (name.toLowerCase().endsWith(FileType.RuleFlow.toString())) {
            type = Type.flow;
        } else if (name.toLowerCase().endsWith(FileType.ScriptDecisionTable.toString())) {
            type = Type.scriptDecisionTable;
        } else if (name.toLowerCase().endsWith(FileType.DecisionTree.toString())) {
            type = Type.decisionTree;
        } else if (name.toLowerCase().endsWith(FileType.Scorecard.toString())) {
            type = Type.scorecard;
        } else if (name.toLowerCase().endsWith(FileType.ComplexScorecard.toString())) {
            type = Type.complexscorecard;
        } else if (name.toLowerCase().endsWith(PACKAGE_CONFIG_FILE)) {
            type = Type.packageConfig;
        } else if (name.toLowerCase().endsWith(RES_PACKGE_FILE)) {
            type = Type.resourcePackage;
        }

        return type;
    }
}