package com.ruleforge.model.flow;

import com.ruleforge.debug.MsgType;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.Node;
import com.ruleforge.model.flow.ins.FlowContext;
import com.ruleforge.model.flow.ins.FlowInstance;
import com.ruleforge.model.flow.ins.ProcessInstance;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.event.impl.ProcessAfterNodeTriggeredEventImpl;
import com.ruleforge.runtime.event.impl.ProcessBeforeNodeTriggeredEventImpl;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @author Jacky.gao
 * 2015年1月28日
 */
@Slf4j
public abstract class FlowNode implements Node {
    protected String name;
    protected String eventBean;
    protected String x;
    protected String y;
    protected String width;
    protected String height;
    protected String packageName;
    protected Integer index;

    protected List<Connection> connections;

    public FlowNode() {
    }

    public FlowNode(String name) {
        this.name = name;
    }

    public final void enter(FlowContext context, FlowInstance instance) {
        String msg = ">>> 进入决策流节点：" + name;
        context.logMsg(msg, MsgType.RuleFlow);
        ExecutionResponseImpl executionResponse = (ExecutionResponseImpl) context.getResponse();
        executionResponse.addNodeName(name);

        KnowledgeSession session = (KnowledgeSession) context.getWorkingMemory();
        session.fireEvent(new ProcessBeforeNodeTriggeredEventImpl(this, instance, session));
        enterNode(context, instance);
        session.fireEvent(new ProcessAfterNodeTriggeredEventImpl(this, instance, session));
    }

    public abstract void enterNode(FlowContext context, FlowInstance instance);

    protected void leave(String connectionName, FlowContext context, FlowInstance instance) {
        // passFlag判断
        KnowledgeSession session = (KnowledgeSession) context.getWorkingMemory();
        Object outputObj = session.getAllFactsMap().get("com.ruleforge.OutputModel");

        if (outputObj instanceof GeneralEntity) {
            GeneralEntity entity = (GeneralEntity) outputObj;
            if (entity.containsKey("ruleResult") && entity.get("ruleResult") != null &&
                    ((Integer) entity.get("ruleResult") > 0)) {
                String msg = "<<< 退出决策流节点：" + name;
                context.logMsg(msg, MsgType.RuleFlow);
                return;
            }
        } else if (outputObj != null) {
            try {
                Class<?> clazz = outputObj.getClass();
                Method method = clazz.getMethod("getRuleResult");
                Object result = method.invoke(outputObj);

                if (result != null &&
                        ((Integer) result > 0)) {
                    String msg = "<<< 退出决策流节点：" + name;
                    context.logMsg(msg, MsgType.RuleFlow);
                    return;
                }
            } catch (Exception e) {
                log.error("对象没有 getRuleResult 方法: {}", outputObj != null ? outputObj.getClass().getName() : null, e);
            }
        }

        for (Connection connection : connections) {
            if (connectionName != null) {
                String cName = connection.getName();
                cName = cName == null ? cName : cName.trim();
                if (connectionName.trim().equals(cName)) {
                    connection.execute(context, instance);
                    break;
                }
            } else if (connection.evaluate(context)) {
                connection.execute(context, instance);
                break;
            }
        }
    }

    protected void executeNodeEvent(EventType type, FlowContext context, ProcessInstance instance) {
        if (StringUtils.isEmpty(eventBean)) {
            return;
        }
        ApplicationContext applicationContext = context.getApplicationContext();
        NodeEvent event = (NodeEvent) applicationContext.getBean(eventBean);
        if (type.equals(EventType.enter)) {
            event.enter(this, instance, context);
        } else {
            event.leave(this, instance, context);
        }
    }

    public abstract FlowNodeType getType();

    public List<Connection> getConnections() {
        return connections;
    }

    public void setConnections(List<Connection> connections) {
        this.connections = connections;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEventBean() {
        return eventBean;
    }

    public void setEventBean(String eventBean) {
        this.eventBean = eventBean;
    }

    public String getX() {
        return x;
    }

    public void setX(String x) {
        this.x = x;
    }

    public String getY() {
        return y;
    }

    public void setY(String y) {
        this.y = y;
    }

    public String getWidth() {
        return width;
    }

    public void setWidth(String width) {
        this.width = width;
    }

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }
}

enum EventType {
    enter, leave;
}
