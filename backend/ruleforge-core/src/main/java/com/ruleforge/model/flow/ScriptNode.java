package com.ruleforge.model.flow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.ruleforge.action.Action;
import com.ruleforge.model.flow.ins.FlowContext;
import com.ruleforge.model.flow.ins.FlowInstance;
import com.ruleforge.model.rule.*;

/**
 * @author Jacky.gao
 * 2015年4月22日
 */
public class ScriptNode extends BindingNode {
    private String script;
    private String actionXml;
    private ActionType actionType;
    private List<Action> actionsData;
    private FlowNodeType type;

    public ScriptNode() {
        this.actionType = ActionType.script;
        this.actionsData = new ArrayList();
        this.type = FlowNodeType.Script;
    }

    public void enterNode(FlowContext context, FlowInstance instance) {
        instance.setCurrentNode(this);
        this.executeNodeEvent(EventType.enter, context, instance);
        this.executeKnowledgePackage(context, instance);
        this.executeNodeEvent(EventType.leave, context, instance);
        this.leave((String) null, context, instance);
    }

    public FlowNodeType getType() {
        return this.type;
    }

    public RuleSet buildRuleSet(List<Library> libraries) {
        RuleSet rs = new RuleSet();
        Rule rule = new Rule();
        rule.setName("sr");
        Rhs rhs = new Rhs();
        rule.setRhs(rhs);
        rhs.setActions(this.actionsData);
        if (libraries != null) {
            Iterator var5 = libraries.iterator();

            while (var5.hasNext()) {
                Library lib = (Library) var5.next();
                rs.addLibrary(lib);
            }
        }

        List<Rule> rules = new ArrayList();
        rules.add(rule);
        rs.setRules(rules);
        return rs;
    }

    public String buildDSLScript(List<Library> libraries) {
        StringBuffer sb = new StringBuffer();
        if (libraries != null) {
            Iterator var3 = libraries.iterator();

            while (var3.hasNext()) {
                Library lib = (Library) var3.next();
                String path = lib.getPath();
                if (lib.getVersion() != null) {
                    path = path + ":" + lib.getVersion();
                }

                LibraryType type = lib.getType();
                switch (type) {
                    case Action:
                        sb.append("importActionLibrary \"" + path + "\"");
                        sb.append("\r\n");
                        break;
                    case Constant:
                        sb.append("importConstantLibrary \"" + path + "\"");
                        sb.append("\r\n");
                        break;
                    case Parameter:
                        sb.append("importParameterLibrary \"" + path + "\"");
                        sb.append("\r\n");
                        break;
                    case Variable:
                        sb.append("importVariableLibrary \"" + path + "\"");
                        sb.append("\r\n");
                }
            }
        }

        sb.append("rule \"sr\" ");
        sb.append(" ");
        sb.append("if");
        sb.append(" ");
        sb.append("then");
        sb.append(" ");
        if (this.script != null) {
            sb.append(this.script);
        }

        sb.append(" ");
        sb.append("end");
        sb.append(" ");
        return sb.toString();
    }

    public String getScript() {
        return this.script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public ActionType getActionType() {
        return this.actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public String getActionXml() {
        return this.actionXml;
    }

    public void setActionXml(String actionXml) {
        this.actionXml = actionXml;
    }

    public List<Action> getActionsData() {
        return this.actionsData;
    }

    public void setActionsData(List<Action> actions) {
        this.actionsData = actions;
    }
}
