package com.ruleforge.parse.flow;

import com.ruleforge.model.flow.Connection;
import com.ruleforge.parse.CriterionParser;
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
 * @since 2014年12月23日
 */
public abstract class FlowNodeParser<T> implements ApplicationContextAware, Parser<T> {
    protected Collection<CriterionParser> criterionParsers;

    protected List<Connection> parseConnections(Element element) {
        List<Connection> connections = new ArrayList<>();
        for (Object obj : element.elements()) {
            if (obj == null || !(obj instanceof Element)) {
                continue;
            }
            Element ele = (Element) obj;
            if (!ele.getName().equals("connection")) {
                continue;
            }
            connections.add(buildConnection(ele));
        }
        return connections;
    }

    private Connection buildConnection(Element element) {
        Connection conn = new Connection();
        conn.setName(element.attributeValue("name"));
        conn.setToName(element.attributeValue("to"));
        conn.setG(element.attributeValue("g"));
        String script = element.getStringValue();
        if (StringUtils.isNotEmpty(script)) {
            conn.setScript(script);
        }
        return conn;
    }

    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
        criterionParsers = applicationContext.getBeansOfType(CriterionParser.class).values();
    }
}
