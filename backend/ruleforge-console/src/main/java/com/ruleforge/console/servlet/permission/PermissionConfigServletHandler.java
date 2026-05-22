package com.ruleforge.console.servlet.permission;

import com.ruleforge.Utils;
import com.ruleforge.console.util.EnvironmentUtils;
import com.ruleforge.console.model.User;
import com.ruleforge.console.exception.NoPermissionException;
import com.ruleforge.console.repository.RepositoryService;
import com.ruleforge.console.repository.BaseRepositoryService;
import com.ruleforge.console.repository.model.RepositoryFile;
import com.ruleforge.console.repository.permission.PermissionService;
import com.ruleforge.console.repository.permission.PermissionStore;
import com.ruleforge.console.servlet.RenderPageServletHandler;
import com.ruleforge.console.servlet.RequestContext;
import com.ruleforge.exception.RuleException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Jacky.gao
 * 2016年8月30日
 */
public class PermissionConfigServletHandler extends RenderPageServletHandler {
    private RepositoryService repositoryService;
    private PermissionStore permissionStore;

    @Override
    public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!((PermissionService) permissionStore).isAdmin()) {
            throw new NoPermissionException();
        }
        String method = retriveMethod(req);
        if (method != null) {
            invokeMethod(method, req, resp);
        }
    }

    public void loadResourceSecurityConfigs(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String companyId = EnvironmentUtils.getLoginUser(new RequestContext(req, resp)).getCompanyId();
        try {
            List<UserPermission> permissions = repositoryService.loadResourceSecurityConfigs(companyId);
            List<User> users = EnvironmentUtils.getEnvironmentProvider().getUsers();
            if (users == null) users = new ArrayList<>();
            List<UserPermission> result = new ArrayList<>();
            for (User user : users) {
                if (user.isAdmin()) {
                    continue;
                }
                if (companyId != null) {
                    if (user.getCompanyId() == null) {
                        continue;
                    }
                    if (!user.getCompanyId().equals(companyId)) {
                        continue;
                    }
                }
                boolean exist = false;
                for (UserPermission p : permissions) {
                    if (p.getUsername().equals(user.getUsername())) {
                        exist = true;
                        break;
                    }
                }
                if (exist) {
                    continue;
                }
                UserPermission up = new UserPermission();
                up.setProjectConfigs(new ArrayList<ProjectConfig>());
                up.setUsername(user.getUsername());
                result.add(up);
            }
            result.addAll(permissions);
            List<RepositoryFile> projects = repositoryService.loadProjects(companyId);
            for (UserPermission p : result) {
                buildProjectConfigs(projects, p);
            }
            writeObjectToJson(resp, result);
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    private void buildProjectConfigs(List<RepositoryFile> projects, UserPermission p) {
        List<ProjectConfig> configs = p.getProjectConfigs();
        if (configs == null) {
            configs = new ArrayList<>();
            p.setProjectConfigs(configs);
        }
        for (RepositoryFile project : projects) {
            boolean exist = false;
            for (ProjectConfig c : p.getProjectConfigs()) {
                if (project.getName().equals(c.getProject())) {
                    exist = true;
                    break;
                }
            }
            if (exist) {
                continue;
            }
            ProjectConfig config = new ProjectConfig();
            config.setProject(project.getName());
            configs.add(config);
        }
    }

    public void saveResourceSecurityConfigs(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User user = EnvironmentUtils.getLoginUser(new RequestContext(req, resp));
        String companyId = user.getCompanyId();
        String content = req.getParameter("content");
        content = Utils.decodeURL(content);
        String path = BaseRepositoryService.RESOURCE_SECURITY_CONFIG_FILE + (companyId == null ? "" : companyId);
        try {
            repositoryService.saveFile(path, content, false, null, user);
            permissionStore.refreshPermissionStore();
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    public void setPermissionStore(PermissionStore permissionStore) {
        this.permissionStore = permissionStore;
    }

    @Override
    public String url() {
        return "/permission";
    }
}
