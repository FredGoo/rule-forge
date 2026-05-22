package com.ruleforge.console.servlet;

import com.ruleforge.Utils;
import org.apache.commons.lang.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;


/**
 * @author Fred Gu
 * 2025-04-21 13:28
 */
public abstract class RenderPageServletHandler extends WriteJsonServletHandler implements ApplicationContextAware {
    protected ApplicationContext applicationContext;

    protected String buildProjectNameFromFile(String file) {
        String project = null;
        if (StringUtils.isNotBlank(file)) {
            file = Utils.decodeURL(file);
            if (file.startsWith("/")) {
                file = file.substring(1);
                int pos = file.indexOf("/");
                project = file.substring(0, pos);
            }
        }
        return project;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

}
