package com.ruleforge.console.servlet.knowledge;

import com.ruleforge.Utils;
import com.ruleforge.console.ExternalProcessService;
import com.ruleforge.console.repository.RepositoryService;
import com.ruleforge.console.servlet.RenderPageServletHandler;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgePackageWrapper;
import com.ruleforge.runtime.service.KnowledgePackageService;
import com.ruleforge.console.model.PackageConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * @author Jacky.gao
 * 2016年8月17日
 */
@Slf4j
public class LoadKnowledgeServletHandler extends RenderPageServletHandler {
    private RepositoryService repositoryService;
    private KnowledgePackageService knowledgePackageService;

    @Override
    public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String method = retriveMethod(req);
        if (method != null) {
            invokeMethod(method, req, resp);
        }
    }

    public void loadProduct(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String packageId = req.getParameter("packageId");
        if (StringUtils.isEmpty(packageId)) {
            resp.setContentType("text/html");
            PrintWriter pw = resp.getWriter();
            pw.write("<h1>packageId can not be null<h1>");
            pw.flush();
            pw.close();
            return;
        }

        packageId = Utils.decodeURL(packageId);
        String[] packageIds = packageId.split("/");
        String project = packageIds[0];
        project = project.contains(":") ? project.split(":")[0] : project;
        String packageIdStr = packageIds[1];

        // 加载知识包版本配置
        try {
            PackageConfig packageConfig = this.repositoryService.loadPackageConfigs(project);

            String version = req.getParameter("version");
            if (Objects.equals(packageConfig.getVersion(), version)) {
                writeStringToJson(resp, "");
                return;
            }

            ExternalProcessService externalProcessService = applicationContext.getBean(ExternalProcessService.class);
//            writeStringToJson(resp, externalProcessService.getPackageContent(project, packageIdStr, packageConfig.getVersion()));
        } catch (Exception e) {
            log.error("LoadKnowledgeServletHandler error", e);
        }
    }

    public void loadTest(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String packageId = req.getParameter("packageId");
        if (StringUtils.isEmpty(packageId)) {
            resp.setContentType("text/html");
            PrintWriter pw = resp.getWriter();
            pw.write("<h1>packageId can not be null<h1>");
            pw.flush();
            pw.close();
            return;
        }

        packageId = Utils.decodeURL(packageId);
        KnowledgePackage knowledgePackage = knowledgePackageService.buildKnowledgePackage(packageId, true);

        writeObjectToJson(resp, new KnowledgePackageWrapper(knowledgePackage));
    }

    @Override
    public String url() {
        return "/loadknowledge";
    }

    @Autowired
    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    public void setKnowledgePackageService(KnowledgePackageService knowledgePackageService) {
        this.knowledgePackageService = knowledgePackageService;
    }
}
