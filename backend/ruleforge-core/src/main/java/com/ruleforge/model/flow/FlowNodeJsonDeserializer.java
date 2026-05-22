package com.ruleforge.model.flow;

import com.ruleforge.runtime.KnowledgePackageWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Jacky.gao
 * 2015年4月21日
 */
public class FlowNodeJsonDeserializer extends JsonDeserializer<List<FlowNode>> {

    @Override
    public List<FlowNode> deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException, JsonProcessingException {
        ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
        JsonNode jsonNode = mapper.readTree(jsonParser);
        List<FlowNode> flowNodes = new ArrayList<>();
        Iterator<JsonNode> childrenNodesIter = jsonNode.elements();
        while (childrenNodesIter.hasNext()) {
            JsonNode childNode = childrenNodesIter.next();
            JsonNode type = childNode.get("type");
            if (type == null) {
                continue;
            }
            FlowNode node = null;
            FlowNodeType nodeType = FlowNodeType.valueOf(type.asText());
            switch (nodeType) {
                case Action:
                    ActionNode actionNode = new ActionNode();
                    JsonNode actionBeanNode = childNode.get("actionBean");
                    if (actionBeanNode != null) {
                        actionNode.setActionBean(actionBeanNode.asText());
                    }
                    node = actionNode;
                    break;
                case Script:
                    ScriptNode scriptNode = new ScriptNode();
                    JsonNode sn = childNode.get("script");
                    if (sn != null) {
                        scriptNode.setScript(sn.asText());
                    }
                    node = scriptNode;
                    break;
                case Decision:
                    DecisionNode decisionNode = new DecisionNode();
                    DecisionType decisionType = DecisionType.valueOf(childNode.get("decisionType").asText());
                    decisionNode.setDecisionType(decisionType);
                    JsonNode itemsNode = childNode.get("items");
                    Iterator<JsonNode> iter = itemsNode.elements();
                    List<DecisionItem> items = new ArrayList<>();
                    while (iter.hasNext()) {
                        JsonNode itemNode = iter.next();
                        DecisionItem item = new DecisionItem();
                        item.setTo(itemNode.get("to").asText());
                        if (!decisionType.equals(DecisionType.Criteria)) {
                            item.setPercent(itemNode.get("percent").asInt());
                        }
                        items.add(item);
                    }
                    decisionNode.setItems(items);
                    node = decisionNode;
                    break;
                case End:
                    node = new EndNode();
                    break;
                case Fork:
                    node = new ForkNode();
                    break;
                case Join:
                    node = new JoinNode();
                    break;
                case Rule:
                    RuleNode ruleNode = new RuleNode();
                    ruleNode.setFile(childNode.get("file").asText());
                    JsonNode versionNode = childNode.get("version");
                    if (versionNode != null) {
                        ruleNode.setVersion(versionNode.asText());
                    }
                    node = ruleNode;
                    break;
                case RulePackage:
                    RulePackageNode packageNode = new RulePackageNode();
                    packageNode.setPackageId(childNode.get("packageId").asText());
                    if (childNode.get("project") != null) {
                        packageNode.setPackageId(childNode.get("project").asText());
                    }
                    node = packageNode;
                    break;
                case Start:
                    node = new StartNode();
                    break;
            }
            String name = childNode.get("name").asText();
            node.setName(name);
            JsonNode eventNode = childNode.get("eventBean");
            if (eventNode != null) {
                node.setEventBean(eventNode.asText());
            }
            JsonNode connectionsNode = childNode.get("connections");
            if (connectionsNode != null) {
                List<Connection> connections = new ArrayList<>();
                Iterator<JsonNode> iter = connectionsNode.elements();
                while (iter.hasNext()) {
                    JsonNode connNode = iter.next();
                    Connection conn = mapper.treeToValue(connNode, Connection.class);
                    connections.add(conn);
                }
                for (Connection conn : connections) {
                    conn.buildDeserialize();
                }
                node.setConnections(connections);
            }
            JsonNode knowledgePackageWrapperNode = childNode.get("knowledgePackageWrapper");
            if (knowledgePackageWrapperNode != null) {
                KnowledgePackageWrapper wrapper = mapper.treeToValue(knowledgePackageWrapperNode, KnowledgePackageWrapper.class);
                wrapper.buildDeserialize();
                BindingNode bindingNode = (BindingNode) node;
                bindingNode.setKnowledgePackageWrapper(wrapper);
            }
            flowNodes.add(node);
        }
        return flowNodes;
    }
}
