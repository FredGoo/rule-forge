package com.ruleforge.executor.service.impl;

import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.builder.ResourceBase;
import com.ruleforge.exception.RuleException;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.service.KnowledgePackageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service("ruleforgeKnowledgePackageService")
public class KnowledgePackageServiceImpl implements KnowledgePackageService {
    private final KnowledgeBuilder knowledgeBuilder;
    private final RestTemplate consoleRestTemplate;

    @Override
    public KnowledgePackage buildKnowledgePackage(String packageInfo) throws RuleException {
        return buildKnowledgePackage(packageInfo, false);
    }

    @Override
    public KnowledgePackage buildKnowledgePackage(String packageInfo, Boolean latest) throws RuleException {
        String[] info = packageInfo.split("/");
        if (info.length != 2) {
            throw new RuleException("PackageInfo [" + packageInfo + "] is invalid. Correct such as \"projectName/packageId\".");
        }
        String project = info[0];
        String packageId = info[1];
        String projectVersion = "";

        List<Map<String, Object>> packageList = sendRequest(project);
        ResourceBase resourceBase = this.knowledgeBuilder.newResourceBase();
        for (Map<String, Object> item : packageList) {
            if (packageId.equals(item.get("id"))) {
                if (item.get("testVersion") != null) {
                    projectVersion = item.get("testVersion").toString();
                } else {
                    projectVersion = "LATEST";
                }
                for (Map<String, String> resourceItem : (List<Map<String, String>>) item.get("resourceItems")) {
                    resourceBase.addResource(resourceItem.get("path"), resourceItem.get("version"), projectVersion);
                }
                break;
            }
        }
        KnowledgeBase knowledgeBase = this.knowledgeBuilder.buildKnowledgeBase(resourceBase);
        KnowledgePackage knowledgePackage = knowledgeBase.getKnowledgePackage();
        knowledgePackage.setVersion(projectVersion);
        log.info("buildKnowledgePackage {}", knowledgePackage.getVersion());
        return knowledgePackage;
    }

    @Override
    public boolean isKnowledgePackageNeedUpdate(String packageInfo) {
        return true;
    }

    private List<Map<String, Object>> sendRequest(String project) {
        String url = "/ruleforgeV2/packageeditor/loadPackages";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("project", project);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        ParameterizedTypeReference<List<Map<String, Object>>> responseType =
                new ParameterizedTypeReference<>() {};
        ResponseEntity<List<Map<String, Object>>> response = this.consoleRestTemplate.exchange(
                url, HttpMethod.POST, request, responseType);
        return response.getBody();
    }
}
