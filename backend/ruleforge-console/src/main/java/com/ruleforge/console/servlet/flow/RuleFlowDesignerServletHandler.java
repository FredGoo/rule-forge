package com.ruleforge.console.servlet.flow;

import com.ruleforge.console.repository.RepositoryService;
import com.ruleforge.console.repository.model.ResourcePackage;
import com.ruleforge.console.servlet.RenderPageServletHandler;
import com.ruleforge.exception.RuleException;
import org.apache.commons.lang.StringUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Jacky.gao
 * 2016年6月3日
 */
public class RuleFlowDesignerServletHandler extends RenderPageServletHandler {
    private RepositoryService repositoryService;

    @Override
    public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String method = retriveMethod(req);
        if (method != null) {
            invokeMethod(method, req, resp);
        }
    }

    public void loadPackages(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String project = req.getParameter("project");
        try {
            if (StringUtils.isEmpty(project)) {
                List<ResourcePackage> resourcePackageList = new ArrayList<>();
                List<String> projectNames = repositoryService.loadProjectNames();
                for (String projectName : projectNames) {
                    resourcePackageList.addAll(repositoryService.loadProjectResourcePackages(projectName));
                }
                writeObjectToJson(resp, resourcePackageList);
            } else {
                List<ResourcePackage> packages = repositoryService.loadProjectResourcePackages(project);
                writeObjectToJson(resp, packages);
            }
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public String url() {
        return "/ruleflowdesigner";
    }
}
