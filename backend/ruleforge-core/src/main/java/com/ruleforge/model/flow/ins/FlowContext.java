package com.ruleforge.model.flow.ins;

import com.ruleforge.runtime.response.FlowExecutionResponse;
import com.ruleforge.runtime.rete.Context;

import java.util.List;
import java.util.Map;

/**
 * @author Jacky.gao
 * 2015年2月28日
 */
public interface FlowContext extends Context {
    Object getVariable(String key);

    Map<String, Object> getVariables();

    void addVariable(String key, Object object);

    void removeVariable(String key);

    List<FlowInstance> getFlowInstances();

    void addFlowInstance(FlowInstance instance);

    void setSessionValue(String key, Object value);

    Object getSessionValue(String key);

    FlowExecutionResponse getResponse();
}
