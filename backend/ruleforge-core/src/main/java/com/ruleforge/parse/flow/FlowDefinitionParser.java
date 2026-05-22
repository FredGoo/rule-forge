package com.ruleforge.parse.flow;

import com.ruleforge.model.flow.FlowDefinition;
import com.ruleforge.model.flow.FlowNode;
import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.LibraryType;
import com.ruleforge.parse.Parser;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Jacky.gao
 * 2014年12月23日
 */
@SuppressWarnings("rawtypes")
public class FlowDefinitionParser implements Parser<FlowDefinition>, ApplicationContextAware {
    private Collection<FlowNodeParser> nodeParsers;

    public FlowDefinition parse(Element element) {
        FlowDefinition flow = new FlowDefinition();
        flow.setId(element.attributeValue("id"));
        String debug = element.attributeValue("debug");
        if (StringUtils.isNotBlank(debug)) {
            flow.setDebug(Boolean.parseBoolean(debug));
        }
        List<FlowNode> nodes = new ArrayList<>();
        for (Object obj : element.elements()) {
            if (obj == null || !(obj instanceof Element)) {
                continue;
            }
            Element ele = (Element) obj;
            String name = ele.getName();
            switch (name) {
                case "import-variable-library":
                    flow.addLibrary(buildLibrary(ele, LibraryType.Variable));
                    break;
                case "import-constant-library":
                    flow.addLibrary(buildLibrary(ele, LibraryType.Constant));
                    break;
                case "import-action-library":
                    flow.addLibrary(buildLibrary(ele, LibraryType.Action));
                    break;
                case "import-parameter-library":
                    flow.addLibrary(buildLibrary(ele, LibraryType.Parameter));
                    break;
                default:
                    for (FlowNodeParser parser : nodeParsers) {
                        if (parser.support(ele.getName())) {
                            nodes.add((FlowNode) parser.parse(ele));
                            break;
                        }
                    }
                    break;
            }
        }
        flow.setNodes(nodes);
        flow.buildConnectionToNode();
        return flow;
    }

    private Library buildLibrary(Element ele, LibraryType type) {
        String path = ele.attributeValue("path");
        if (path.endsWith(".xml")) {
            return new Library(path, null, type);
        } else {
            int versionPos = path.lastIndexOf(":");
            String version = path.substring(versionPos + 1, path.length());
            if (version.equals("LATEST")) version = null;
            path = path.substring(0, versionPos);
            return new Library(path, version, type);
        }
    }

    public boolean support(String name) {
        return name.equals("rule-flow");
    }

    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
        nodeParsers = applicationContext.getBeansOfType(FlowNodeParser.class).values();
    }
}
