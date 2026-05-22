package com.ruleforge.model.flow;

import com.ruleforge.action.Action;
import com.ruleforge.action.VariableAssignAction;
import com.ruleforge.model.flow.ins.FlowContext;
import com.ruleforge.model.flow.ins.ProcessInstance;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgePackageWrapper;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.response.FlowExecutionResponse;
import com.ruleforge.runtime.response.NodeExecutionResponseImpl;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Jacky.gao
 * @author Fred
 * 2015年4月20日
 */
@Slf4j
public abstract class BindingNode extends FlowNode {
    private final Logger logger = LoggerFactory.getLogger(BindingNode.class);
    private KnowledgePackageWrapper knowledgePackageWrapper;

    public BindingNode() {
    }

    public BindingNode(String name) {
        super(name);
    }

    protected KnowledgeSession executeKnowledgePackage(FlowContext context, ProcessInstance instance) {
        log.debug("executeKnowledgePackage {} {}", getName(), context.getResponse());
        KnowledgeSession parentSession = (KnowledgeSession) context.getWorkingMemory();
        KnowledgePackage knowledgePackage = this.knowledgePackageWrapper.getKnowledgePackage();
        KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(this.knowledgePackageWrapper, context, parentSession);

        if (knowledgePackage.getFlowMap() != null && !knowledgePackage.getFlowMap().isEmpty()) {
            String processId = knowledgePackage.getFlowMap().values().iterator().next().getId();
            FlowExecutionResponse flowExecutionResponse = session.startProcess(processId, context.getVariables(), ((ExecutionResponseImpl) context.getResponse()).getSort());
            ((ExecutionResponseImpl) context.getResponse()).addFlowExecutionResponse(flowExecutionResponse);
            ((ExecutionResponseImpl) context.getResponse()).setSort(((ExecutionResponseImpl) flowExecutionResponse).getSort());
        } else {
            ExecutionResponseImpl executionResponse = (ExecutionResponseImpl) context.getResponse();

            RuleExecutionResponse ruleExecutionResponse = session.fireRules(context.getVariables());
            executionResponse.addRuleExecutionResponse(ruleExecutionResponse);

            if (this instanceof RuleNode) {
                try {
                    NodeExecutionResponseImpl nodeExecutionResponse = new NodeExecutionResponseImpl();
                    nodeExecutionResponse.setSort(executionResponse.getSort());
                    nodeExecutionResponse.setRuleNodeName(this.name);
                    if (ruleExecutionResponse.getMatchedRules() != null && !ruleExecutionResponse.getMatchedRules().isEmpty()) {
                        RuleInfo ruleInfo = ruleExecutionResponse.getMatchedRules().get(ruleExecutionResponse.getMatchedRules().size() - 1);
                        if (ruleInfo != null) {
                            Rule rule = (Rule) ruleInfo;
                            nodeExecutionResponse.setMatchedRuleKey(rule.getName());
                            nodeExecutionResponse.setMatchedRuleName(rule.getRemark());
                            Rhs rhs = rule.getRhs();
                            if (Objects.nonNull(rhs)) {
                                List<Action> actions = rhs.getActions();
                                if (!CollectionUtils.isEmpty(actions)) {
                                    for (Action action : actions) {
                                        if (action instanceof VariableAssignAction) {
                                            VariableAssignAction variableAssignAction = (VariableAssignAction) action;
                                            if (variableAssignAction.getValue() instanceof SimpleValue) {
                                                SimpleValue simpleValue = (SimpleValue) variableAssignAction.getValue();
                                                nodeExecutionResponse.setMatchedRuleAction(simpleValue.getContent());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    executionResponse.addNodeExecutionResponse(nodeExecutionResponse);
                    executionResponse.incSort();
                } catch (Exception e) {
                    logger.error("addNodeExecutionResponse error", e);
                }
            }
        }

        Map<String, Object> parameters = session.getParameters();
        Map<String, Object> variables = context.getVariables();

        for (String key : parameters.keySet()) {
            if (!key.equals("return_to__")) {
                variables.put(key, parameters.get(key));
            }
        }

        return session;
    }

    public KnowledgePackageWrapper getKnowledgePackageWrapper() {
        return this.knowledgePackageWrapper;
    }

    public void setKnowledgePackageWrapper(KnowledgePackageWrapper knowledgePackageWrapper) {
        this.knowledgePackageWrapper = knowledgePackageWrapper;
    }
}
