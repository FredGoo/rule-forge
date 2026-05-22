package com.ruleforge.console.servlet.diagram;

import com.ruleforge.Utils;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.builder.ResourceBase;
import com.ruleforge.console.servlet.RenderPageServletHandler;
import com.ruleforge.console.servlet.respackage.HttpSessionKnowledgeCache;
import com.ruleforge.model.Node;
import com.ruleforge.model.rete.*;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

public class ReteDiagramServletHandler extends RenderPageServletHandler {
    private KnowledgeBuilder knowledgeBuilder;
    private final ReteNodeLayout nodeLayout = new ReteNodeLayout();
    private final int nodeWidth = 30;
    private final int nodeHeight = 30;
    private HttpSessionKnowledgeCache httpSessionKnowledgeCache;

    public ReteDiagramServletHandler() {
    }

    public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String method = this.retriveMethod(req);
        if (method != null) {
            this.invokeMethod(method, req, resp);
        }
    }

    public void loadReteDiagramData(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String files = req.getParameter("files");
        files = Utils.decodeURL(files);
        KnowledgeBase knowledgeBase = (KnowledgeBase) this.httpSessionKnowledgeCache.get(req, "_kb");
        if (knowledgeBase == null) {
            ResourceBase resourceBase = this.knowledgeBuilder.newResourceBase();
            String[] paths = files.split(";");

            for (String s : paths) {
                String path = s;
                String[] subpaths = path.split(",");
                path = subpaths[0];
                String version = subpaths[1];
                path = Utils.toUTF8(path);
                resourceBase.addResource(path, version);
            }

            knowledgeBase = this.knowledgeBuilder.buildKnowledgeBase(resourceBase);
            this.httpSessionKnowledgeCache.put(req, "_kb", knowledgeBase);
        }

        Rete rete = knowledgeBase.getRete();
        Diagram diagram = this.buildReteDiagram(rete);
        this.writeObjectToJson(resp, diagram);
    }

    private Diagram buildReteDiagram(Rete rete) {
        Map<Node, NodeInfo> nodeMap = new HashMap();
        List<Edge> edges = new ArrayList();
        NodeInfo root = new NodeInfo();
        DiagramContext context = new DiagramContext(edges, nodeMap);
        root.setId(context.nextId());
        root.setLabel("Enter");
        root.setColor("#98AFC7");
        root.setWidth(30);
        root.setHeight(30);
        root.setRoundCorner(10);
        List<ObjectTypeNode> typeNodes = rete.getObjectTypeNodes();
        int level = 1;
        Iterator<ObjectTypeNode> var8 = typeNodes.iterator();

        while (true) {
            NodeInfo node;
            List lines;
            do {
                if (!var8.hasNext()) {
                    Box box = this.nodeLayout.layout(root);
                    Diagram diagram = new Diagram(edges, root);
                    if (box != null) {
                        diagram.setWidth(box.getWidth() + 500);
                        diagram.setHeight(box.getHeight() + 300);
                    }

                    return diagram;
                }

                ObjectTypeNode typeNode = var8.next();
                node = new NodeInfo();
                node.setId(context.nextId());
                node.setLabel("T");
                node.setTitle(typeNode.getObjectTypeClass());
                node.setColor("#97CBFF");
                node.setLevel(level);
                node.setWidth(30);
                node.setHeight(30);
                node.setRoundCorner(5);
                root.addChild(node);
                lines = typeNode.getLines();
            } while (lines == null);

            int nextLevel = level + 1;

            for (Object o : lines) {
                Line line = (Line) o;
                Edge edge = new Edge(root.getId(), node.getId());
                edges.add(edge);
                this.buildLine(line, context, node, nextLevel);
            }
        }
    }

    private void buildLine(Line line, DiagramContext context, NodeInfo parentNode, int level) {
        Node toNode = line.getTo();
        if (toNode != null) {
            Map<Node, NodeInfo> nodeMap = context.getNodeMap();
            NodeInfo newNodeInfo = null;
            if (nodeMap.containsKey(toNode)) {
                newNodeInfo = (NodeInfo) nodeMap.get(toNode);
                context.addEdge(new Edge(parentNode.getId(), newNodeInfo.getId()));
            } else {
                List<Line> lines = null;
                newNodeInfo = new NodeInfo();
                newNodeInfo.setLevel(level);
                newNodeInfo.setId(context.nextId());
                newNodeInfo.setWidth(30);
                newNodeInfo.setHeight(30);
                if (toNode instanceof CriteriaNode) {
                    CriteriaNode cnode = (CriteriaNode) toNode;
                    newNodeInfo.setColor("#B3D9D9");
                    newNodeInfo.setLabel("C");
                    newNodeInfo.setTitle(cnode.getCriteriaInfo());
                    newNodeInfo.setRoundCorner(30);
                    lines = cnode.getLines();
                } else if (toNode instanceof AndNode) {
                    AndNode andNode = (AndNode) toNode;
                    lines = andNode.getLines();
                    newNodeInfo.setColor("#DAB1D5");
                    newNodeInfo.setLabel("AND");
                    newNodeInfo.setRoundCorner(15);
                } else if (toNode instanceof OrNode) {
                    OrNode orNode = (OrNode) toNode;
                    lines = orNode.getLines();
                    newNodeInfo.setColor("#82D900");
                    newNodeInfo.setLabel("OR");
                    newNodeInfo.setRoundCorner(15);
                } else if (toNode instanceof TerminalNode) {
                    TerminalNode terminalNode = (TerminalNode) toNode;
                    newNodeInfo.setColor("orange");
                    newNodeInfo.setLabel(terminalNode.getRule().getName());
                    newNodeInfo.setTitle(terminalNode.getRule().getName());
                    newNodeInfo.setRoundCorner(0);
                }

                nodeMap.put(toNode, newNodeInfo);
                parentNode.addChild(newNodeInfo);
                context.addEdge(new Edge(parentNode.getId(), newNodeInfo.getId()));
                if (lines != null) {
                    int nextLevel = level + 1;
                    Iterator var10 = lines.iterator();

                    while (var10.hasNext()) {
                        Line nextLine = (Line) var10.next();
                        this.buildLine(nextLine, context, newNodeInfo, nextLevel);
                    }

                }
            }
        }
    }

    public void setKnowledgeBuilder(KnowledgeBuilder knowledgeBuilder) {
        this.knowledgeBuilder = knowledgeBuilder;
    }

    public void setHttpSessionKnowledgeCache(HttpSessionKnowledgeCache httpSessionKnowledgeCache) {
        this.httpSessionKnowledgeCache = httpSessionKnowledgeCache;
    }

    public String url() {
        return "/retediagram";
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

    }
}
