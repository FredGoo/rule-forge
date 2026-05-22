package com.ruleforge.console.util;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.console.model.ApplicationAllVariableCategoryMap;
import com.ruleforge.console.model.TestDataImportErrorMsgDto;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Fred
 * @since 2025/8/24 21:55
 */
@Slf4j
public class TestDataAnalysisEventListener extends AnalysisEventListener<Map<Integer, String>> {
    private final Map<String, VariableCategory> variableCategoryMap;
    @Getter
    private final Map<Integer, ApplicationAllVariableCategoryMap> dataMap = new HashMap<>();
    @Getter
    private final List<TestDataImportErrorMsgDto> errorMsgDtoList = new ArrayList<>();

    private VariableCategory vc;
    private Map<Integer, String> headMap;
    private final Map<String, Variable> vcFieldMap = new HashMap<>();

    public TestDataAnalysisEventListener(Map<String, VariableCategory> variableCategoryMap) {
        this.variableCategoryMap = variableCategoryMap;
    }

    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        VariableCategory vc = variableCategoryMap.get(context.readSheetHolder().getSheetName());
        if (vc == null) {
            log.error("Variable category [{}] not exist.", context.readSheetHolder().getSheetName());
        } else {
            this.vc = vc;

            for (Variable variable : vc.getVariables()) {
                this.vcFieldMap.put(variable.getLabel(), variable);
            }
            this.headMap = headMap;
        }
    }

    @Override
    public void invoke(Map<Integer, String> data, AnalysisContext context) {
        if (this.vc == null) {
            return;
        }

        Map<String, Object> entity;
        if (vc.getName().equals(VariableCategory.PARAM_CATEGORY)) {
            entity = new HashMap<>();
        } else {
            entity = new GeneralEntity(vc.getClazz());
        }
        Map<String, Object> testRowData = dataMap
                .computeIfAbsent(context.readRowHolder().getRowIndex(), k -> new ApplicationAllVariableCategoryMap());
        for (Map.Entry<Integer, String> entry : data.entrySet()) {
            if (entry.getValue() != null && vcFieldMap.containsKey(headMap.get(entry.getKey()))) {
                String fieldName = headMap.get(entry.getKey());
                Variable variable = vcFieldMap.get(fieldName);

                try {
                    entity.put(variable.getName(), variable.getType().convert(entry.getValue()));
                } catch (Exception e) {
                    entity.put(variable.getName(), entry.getValue());
                    TestDataImportErrorMsgDto errorMsgDto = new TestDataImportErrorMsgDto(context.readSheetHolder().getSheetName(),
                            context.readRowHolder().getRowIndex(),
                            fieldName,
                            e.getMessage());
                    errorMsgDtoList.add(errorMsgDto);
                    log.error("invoke {}", errorMsgDto);
                }
            }
        }
        testRowData.put(context.readSheetHolder().getSheetName(), entity);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
    }

}
