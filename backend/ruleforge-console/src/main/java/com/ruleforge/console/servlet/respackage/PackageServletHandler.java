package com.ruleforge.console.servlet.respackage;

import com.ruleforge.Configure;
import com.ruleforge.controller.KnowledgePackageReceiverServlet;
import com.ruleforge.Utils;
import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.console.model.ClientConfig;
import com.ruleforge.console.repository.RepositoryService;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgePackageWrapper;
import com.ruleforge.runtime.cache.CacheUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Jacky.gao
 * @author fred
 * 2016年6月3日
 */
public class PackageServletHandler  {
    private final Logger logger = LoggerFactory.getLogger(PackageServletHandler.class);

    public static final String KB_KEY = "_kb";
    public static final String VCS_KEY = "_vcs";
    public static final String IMPORT_EXCEL_DATA = "_import_excel_data";
    public static final String EXPORT_EXCEL_TEST_DATA = "_export_excel_test_data";

    private RepositoryService repositoryService;
    private KnowledgeBuilder knowledgeBuilder;
    private HttpSessionKnowledgeCache httpSessionKnowledgeCache;


    public void pushKnowledgePackageToClients(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String project = req.getParameter("project");
        project = Utils.decodeURL(project);
        String packageId = project + "/" + Utils.decodeURL(req.getParameter("packageId"));
        if (packageId.startsWith("/")) {
            packageId = packageId.substring(1);
        }
        KnowledgePackage knowledgePackage = CacheUtils.getKnowledgeCache().getKnowledge(packageId);

        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_NULL);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.setDateFormat(new SimpleDateFormat(Configure.getDateFormat()));
        StringWriter writer = new StringWriter();
        mapper.writeValue(writer, new KnowledgePackageWrapper(knowledgePackage));
        String content = writer.getBuffer().toString();
        writer.close();
        StringBuilder sb = new StringBuilder();
        List<ClientConfig> clients = new ArrayList<>();
        int i = 0;
        for (ClientConfig config : clients) {
            if (i > 0) {
                sb.append("<br>");
            }
            boolean result = pushKnowledgePackage(packageId, content, config.getClient());
            if (result) {
                sb.append("<span class=\"text-info\" style='line-height:30px'>推送到客户端：").append(config.getName()).append("：").append(config
                        .getClient()).append(" 成功</span>");
            } else {
                sb.append("<span class=\"text-danger\" style='line-height:30px'>推送到客户端：").append(config.getName()).append("：").append(config
                        .getClient()).append(" 失败</span>");
            }
            i++;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("info", sb.toString());
    }

    private boolean pushKnowledgePackage(String packageId, String content, String client) {
        try {
            if (client.endsWith("/")) {
                client = client.substring(0, client.length() - 1);
            }
            String clientUrl = client + "/urule" + KnowledgePackageReceiverServlet.URL;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("packageId", packageId);
            map.add("content", content);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
//            ResponseEntity<String> response = this.restTemplate.postForEntity(clientUrl, request, String.class);
//
//            String result = response.getBody();
//            return result.equals("ok");
            return true;
        } catch (Exception ex) {
            logger.error("pushKnowledgePackage error", ex);
            return false;
        }
    }

    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    public void setKnowledgeBuilder(KnowledgeBuilder knowledgeBuilder) {
        this.knowledgeBuilder = knowledgeBuilder;
    }

    public void setHttpSessionKnowledgeCache(
            HttpSessionKnowledgeCache httpSessionKnowledgeCache) {
        this.httpSessionKnowledgeCache = httpSessionKnowledgeCache;
    }

    public String url() {
        return "/packageeditor";
    }
}
