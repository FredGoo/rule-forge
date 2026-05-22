package com.ruleforge.builder;

import com.ruleforge.model.flow.FlowDefinition;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.rete.Rete;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgePackageImpl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Jacky.gao
 * @since 2014年12月22日
 */
public class KnowledgeBase {
    private ResourceLibrary resourceLibrary;
    private Map<String, FlowDefinition> flowMap;
    private Rete rete;
    private KnowledgePackageImpl knowledgePackage;

    public KnowledgeBase(Rete rete) {
        this(rete, null);
    }

    protected KnowledgeBase(Rete rete, Map<String, FlowDefinition> flowMap) {
        this.rete = rete;
        this.resourceLibrary = rete.getResourceLibrary();
        this.flowMap = flowMap;
    }

    public KnowledgePackage getKnowledgePackage() {
        if (this.knowledgePackage != null) {
            return this.knowledgePackage;
        } else {
            this.knowledgePackage = new KnowledgePackageImpl();
            this.knowledgePackage.setRete(this.rete);
            this.knowledgePackage.setFlowMap(this.flowMap);
            Map<String, String> variableCategoryMap = new HashMap<>();
            this.knowledgePackage.setVariableCategoryMap(variableCategoryMap);
            List<VariableCategory> variableCategories = this.resourceLibrary.getVariableCategories();
            Map<String, String> parameters = new HashMap<>();
            this.knowledgePackage.setParameters(parameters);
            Iterator<VariableCategory> var4 = variableCategories.iterator();

            while (true) {
                List variables;
                do {
                    do {
                        VariableCategory category;
                        String name;
                        do {
                            if (!var4.hasNext()) {
                                return this.knowledgePackage;
                            }

                            category = var4.next();
                            name = category.getName();
                            variableCategoryMap.put(name, category.getClazz());
                        } while (!name.equals("参数"));

                        variables = category.getVariables();
                    } while (variables == null);
                } while (variables.isEmpty());

                for (Object variable : variables) {
                    Variable var = (Variable) variable;
                    parameters.put(var.getName(), var.getType().name());
                }
            }
        }
    }

    public Rete getRete() {
        return this.rete;
    }

    public ResourceLibrary getResourceLibrary() {
        return this.resourceLibrary;
    }

    public Map<String, FlowDefinition> getFlowMap() {
        return this.flowMap;
    }
}
