package com.ruleforge.console.servlet.client;

import com.ruleforge.Utils;
import com.ruleforge.console.util.EnvironmentUtils;
import com.ruleforge.console.repository.RepositoryService;
import com.ruleforge.console.repository.BaseRepositoryService;
import com.ruleforge.console.servlet.RenderPageServletHandler;
import com.ruleforge.console.servlet.RequestContext;
import com.ruleforge.exception.RuleException;
import com.ruleforge.console.model.User;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Jacky.gao
 * 2016年8月11日
 */
public class ClientConfigServletHandler extends RenderPageServletHandler {
    private RepositoryService repositoryService;

    @Override
    public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String method = retriveMethod(req);
        if (method != null) {
            invokeMethod(method, req, resp);
        }
    }

    public void loadData(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String project = req.getParameter("project");
        project = Utils.decodeURL(project);
        writeObjectToJson(resp, repositoryService.loadClientConfigs(project));
    }

    public void save(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String project = req.getParameter("project");
        project = Utils.decodeURL(project);
        String file = project + "/" + BaseRepositoryService.CLIENT_CONFIG_FILE;
        String content = req.getParameter("content");
        content = Utils.decodeURL(content);
        User user = EnvironmentUtils.getLoginUser(new RequestContext(req, resp));
        try {
            repositoryService.saveFile(file, content, false, null, user);
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public String url() {
        return "/clientconfig";
    }
}
