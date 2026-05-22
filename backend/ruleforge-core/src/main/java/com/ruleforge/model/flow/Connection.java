package com.ruleforge.model.flow;

import com.ruleforge.model.flow.ins.FlowContext;
import com.ruleforge.model.flow.ins.FlowInstance;
import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.LibraryType;
import com.ruleforge.runtime.KnowledgePackageWrapper;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Iterator;
import java.util.List;

/**
 * @author Jacky.gao
 * 2015年2月28日
 */
public class Connection {
    public static final String RETURN_VALUE_KEY = "return_value__";
    private String name;
    private String toName;
    private String script;
    private String g;
    private KnowledgePackageWrapper knowledgePackageWrapper;
    @JsonIgnore
    private FlowNode to;

    public Connection() {
    }

    public boolean evaluate(FlowContext context) {
        if (this.knowledgePackageWrapper == null) {
            return true;
        } else {
            KnowledgeSession parentSession = (KnowledgeSession) context.getWorkingMemory();
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(this.knowledgePackageWrapper, context, parentSession);
            session.fireRules(context.getVariables());
            Object result = session.getParameter("return_value__");
            return result != null && Boolean.parseBoolean(result.toString());
        }
    }

    public void buildDeserialize() {
        if (this.knowledgePackageWrapper != null) {
            this.knowledgePackageWrapper.buildDeserialize();
        }

    }

    public void execute(FlowContext context, FlowInstance instance) {
        this.to.enter(context, instance);
    }

    public String buildDSLScript(List<Library> libraries) {
        StringBuilder sb = new StringBuilder();
        if (libraries != null) {

            for (Library lib : libraries) {
                String path = lib.getPath();
                if (lib.getVersion() != null) {
                    path = path + ":" + lib.getVersion();
                }

                LibraryType type = lib.getType();
                switch (type) {
                    case Action:
                        sb.append("importActionLibrary \"").append(path).append("\"");
                        sb.append("\r\n");
                        break;
                    case Constant:
                        sb.append("importConstantLibrary \"").append(path).append("\"");
                        sb.append("\r\n");
                        break;
                    case Parameter:
                        sb.append("importParameterLibrary \"").append(path).append("\"");
                        sb.append("\r\n");
                        break;
                    case Variable:
                        sb.append("importVariableLibrary \"").append(path).append("\"");
                        sb.append("\r\n");
                }
            }
        }

        sb.append("rule \"conn\"");
        sb.append("\r\n");
        sb.append("if");
        sb.append("\r\n");
        sb.append(this.script);
        sb.append("\r\n");
        sb.append("then");
        sb.append("\r\n");
        sb.append("parameter.return_value__=true");
        sb.append("\r\n");
        sb.append("end");
        return sb.toString();
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getToName() {
        return this.toName;
    }

    public void setToName(String toName) {
        this.toName = toName;
    }

    public FlowNode getTo() {
        return this.to;
    }

    public void setTo(FlowNode to) {
        this.to = to;
    }

    public String getScript() {
        return this.script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public KnowledgePackageWrapper getKnowledgePackageWrapper() {
        return this.knowledgePackageWrapper;
    }

    public void setKnowledgePackageWrapper(KnowledgePackageWrapper knowledgePackageWrapper) {
        this.knowledgePackageWrapper = knowledgePackageWrapper;
    }

    public String getG() {
        return this.g;
    }

    public void setG(String g) {
        this.g = g;
    }
}
