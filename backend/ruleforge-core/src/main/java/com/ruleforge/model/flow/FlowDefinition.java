package com.ruleforge.model.flow;

import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.builder.ResourceBase;
import com.ruleforge.dsl.DSLRuleSetBuilder;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.flow.ins.FlowContext;
import com.ruleforge.model.flow.ins.FlowInstance;
import com.ruleforge.model.flow.ins.ProcessInstance;
import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.RuleSet;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgePackageWrapper;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.event.impl.ProcessAfterCompletedEventImpl;
import com.ruleforge.runtime.event.impl.ProcessBeforeStartedEventImpl;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.service.KnowledgePackageService;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 规则流定义类。
 * <p>包含了规则流的所有节点（FlowNode）和它们之间的连接关系。
 * 它定义了一个业务流程的结构，可以被实例化并执行。</p>
 */
@Data
public class FlowDefinition implements ProcessDefinition {
    /**
     * 规则流定义的唯一标识符。
     */
    private String id;
    /**
     * 是否启用调试模式。如果为true，执行过程中会记录更详细的日志或信息。
     */
    private boolean debug;
    /**
     * 规则流所引用的库定义列表（如变量库、常量库、动作库等）。
     * 在序列化时通常会被忽略，可能由其他机制处理。
     */
    @JsonIgnore
    private List<Library> libraries;
    /**
     * 规则流包含的所有节点列表。
     * 使用自定义的JSON反序列化器 {@link FlowNodeJsonDeserializer} 进行处理。
     */
    @JsonDeserialize(using = FlowNodeJsonDeserializer.class)
    private List<FlowNode> nodes;

    /**
     * 默认构造函数。
     */
    public FlowDefinition() {
    }

    /**
     * 根据此流程定义创建一个新的流程实例并开始执行。
     *
     * @param context 流程执行的上下文，包含工作内存、参数、响应等信息。
     * @return 创建并启动的流程实例 {@link FlowInstance}。
     * @throws RuleException 如果流程定义中未找到 {@link StartNode}。
     */
    public ProcessInstance newInstance(FlowContext context) {
        ExecutionResponseImpl response = (ExecutionResponseImpl) context.getResponse();
        response.setFlowId(this.id);
        StartNode startNode = null;

        for (FlowNode node : this.nodes) {
            if (node instanceof StartNode) {
                startNode = (StartNode) node;
                break;
            }
        }

        if (startNode == null) {
            throw new RuleException("StartNode must be define.");
        } else {
            response.addNodeName(startNode.getName());
            FlowInstance instance = new FlowInstance(this, this.debug);
            KnowledgeSession session = (KnowledgeSession) context.getWorkingMemory();
            session.fireEvent(new ProcessBeforeStartedEventImpl(instance, session));
            startNode.enter(context, instance);
            session.fireEvent(new ProcessAfterCompletedEventImpl(instance, session));
            return instance;
        }
    }

    /**
     * 构建节点之间的连接关系。
     * <p>在反序列化后，连接对象（Connection）中只存储了目标节点的名称。
     * 此方法负责根据节点名称查找实际的 {@link FlowNode} 对象，并设置到连接中。</p>
     */
    public void buildConnectionToNode() {
        Iterator<FlowNode> var1 = this.nodes.iterator();

        while (true) {
            List connections;
            do {
                do {
                    if (!var1.hasNext()) {
                        return;
                    }

                    FlowNode node = var1.next();
                    connections = node.getConnections();
                } while (connections == null);
            } while (connections.isEmpty());

            for (Object connection : connections) {
                Connection conn = (Connection) connection;
                String nodeName = conn.getToName();
                conn.setTo(this.getFlowNode(nodeName));
            }
        }
    }

    /**
     * 根据节点名称查找流程定义中的 {@link FlowNode} 对象。
     *
     * @param nodeName 要查找的节点名称。
     * @return 找到的 {@link FlowNode} 对象。
     * @throws RuleException 如果具有指定名称的节点未找到。
     */
    private FlowNode getFlowNode(String nodeName) {
        Iterator<FlowNode> var2 = this.nodes.iterator();

        FlowNode node;
        do {
            if (!var2.hasNext()) {
                throw new RuleException("Flow node [" + nodeName + "] not found.");
            }

            node = var2.next();
        } while (!node.getName().equals(nodeName));

        return node;
    }

    /**
     * 创建一个新的、适用于序列化的流程定义副本。
     * <p>此方法会初始化某些节点（如规则节点、决策节点）所需的知识包（KnowledgePackage），
     * 并可能移除一些仅用于运行时或设计时的临时数据（如节点的坐标、宽度、高度，决策项的LHS等），
     * 以便生成更干净、更适合持久化或传输的流程定义版本。</p>
     *
     * @param knowledgeBuilder        用于构建知识库的构建器。
     * @param knowledgePackageService 用于获取预构建知识包的服务。
     * @param dslRuleSetBuilder       用于构建DSL规则集的构建器。
     * @return 一个新的、经过处理的 {@link FlowDefinition} 实例。
     * @throws IOException 如果在处理资源或构建知识包时发生IO错误。
     */
    public FlowDefinition newFlowDefinitionForSerialize(KnowledgeBuilder knowledgeBuilder, KnowledgePackageService knowledgePackageService,
                                                        DSLRuleSetBuilder dslRuleSetBuilder, String projectVersion) throws RuleException {
        this.initNodeKnowledgePackage(knowledgeBuilder, knowledgePackageService, dslRuleSetBuilder, projectVersion);
        FlowDefinition fd = new FlowDefinition();
        fd.setLibraries(this.libraries);
        fd.setId(this.id);
        fd.setDebug(this.debug);
        fd.setNodes(this.nodes);

        for (FlowNode node : this.nodes) {
            node.setX(null);
            node.setY(null);
            node.setWidth(null);
            node.setHeight(null);
            if (node instanceof DecisionNode) {
                DecisionNode decisionNode = (DecisionNode) node;

                for (DecisionItem item : decisionNode.getItems()) {
                    item.setLhs(null);
                    item.setLhsXml(null);
                    item.setScript(null);
                }
            } else if (node instanceof ScriptNode) {
                ScriptNode scriptNode = (ScriptNode) node;
                scriptNode.setScript(null);
                scriptNode.setActionType(null);
                scriptNode.setActionXml(null);
                scriptNode.setActionsData(null);
            }
        }

        return fd;
    }

    /**
     * 初始化流程中特定类型节点（如规则节点、决策节点、脚本节点、分支节点）的知识包（KnowledgePackage）。
     * <p>这些节点可能需要独立的规则执行环境，此方法负责根据节点的配置（引用的规则文件、规则包ID、内置脚本或条件）
     * 构建相应的 {@link KnowledgePackage} 并包装在 {@link KnowledgePackageWrapper} 中，设置回节点对象。</p>
     *
     * @param knowledgeBuilder        用于构建知识库的构建器。
     * @param knowledgePackageService 用于获取预构建知识包的服务。
     * @param dslRuleSetBuilder       用于构建DSL规则集的构建器。
     * @throws IOException 如果在处理资源或构建知识包时发生IO错误。
     */
    private void initNodeKnowledgePackage(KnowledgeBuilder knowledgeBuilder, KnowledgePackageService knowledgePackageService,
                                          DSLRuleSetBuilder dslRuleSetBuilder, String projectVersion) throws RuleException {
        for (FlowNode node : this.nodes) {
            KnowledgeBase knowledgeBase;
            if (node instanceof RuleNode) {
                ResourceBase resourceBase = knowledgeBuilder.newResourceBase();
                RuleNode ruleNode = (RuleNode) node;
                resourceBase.addResource(ruleNode.getFile(), ruleNode.getVersion(), projectVersion);
                knowledgeBase = knowledgeBuilder.buildKnowledgeBase(resourceBase);
                KnowledgePackage knowledgePackage = knowledgeBase.getKnowledgePackage();
                ruleNode.setKnowledgePackageWrapper(new KnowledgePackageWrapper(knowledgePackage));
            } else if (node instanceof RulePackageNode) {
                RulePackageNode rulePackageNode = (RulePackageNode) node;
                String packageId = rulePackageNode.getProject() + "/" + rulePackageNode.getPackageId();
                KnowledgePackage knowledgePackage = knowledgePackageService.buildKnowledgePackage(packageId);
                rulePackageNode.setKnowledgePackageWrapper(new KnowledgePackageWrapper(knowledgePackage));
            } else {
                RuleSet ruleSet;
                String script;
                if (node instanceof DecisionNode) {
                    DecisionNode decisionNode = (DecisionNode) node;
                    if (decisionNode.getDecisionType().equals(DecisionType.Criteria)) {
                        ruleSet = decisionNode.buildRuleSet(this.libraries, this.debug, this.id);
                        script = decisionNode.buildDSLScript(this.libraries, this.debug, this.id);
                        if (script != null) {
                            RuleSet rs = dslRuleSetBuilder.build(script);
                            ruleSet.getRules().addAll(rs.getRules());
                        }

                        knowledgeBase = knowledgeBuilder.buildKnowledgeBase(ruleSet);
                        decisionNode.setKnowledgePackageWrapper(new KnowledgePackageWrapper(knowledgeBase.getKnowledgePackage()));
                    }
                } else {
                    Iterator ruleSetIterator;
                    if (node instanceof ScriptNode) {
                        ScriptNode scriptNode = (ScriptNode) node;
                        if (scriptNode.getActionType().equals(ActionType.script)) {
                            script = scriptNode.buildDSLScript(this.libraries);
                            ruleSet = dslRuleSetBuilder.build(script);
                        } else {
                            ruleSet = scriptNode.buildRuleSet(this.libraries);
                        }

                        knowledgeBase = knowledgeBuilder.buildKnowledgeBase(ruleSet);
                        scriptNode.setKnowledgePackageWrapper(new KnowledgePackageWrapper(knowledgeBase.getKnowledgePackage()));
                    } else if (node instanceof ForkNode) {
                        List<Connection> connections = node.getConnections();
                        ruleSetIterator = connections.iterator();

                        while (ruleSetIterator.hasNext()) {
                            Connection conn = (Connection) ruleSetIterator.next();
                            script = conn.getScript();
                            if (script != null) {
                                script = conn.buildDSLScript(this.libraries);
                                ruleSet = dslRuleSetBuilder.build(script);
                                knowledgeBase = knowledgeBuilder.buildKnowledgeBase(ruleSet);
                                conn.setKnowledgePackageWrapper(new KnowledgePackageWrapper(knowledgeBase.getKnowledgePackage()));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 向流程定义中添加一个库引用。
     *
     * @param lib 要添加的库 {@link Library} 对象。
     */
    public void addLibrary(Library lib) {
        if (this.libraries == null) {
            this.libraries = new ArrayList<>();
        }

        this.libraries.add(lib);
    }

}
