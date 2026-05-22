package com.ruleforge.executor.service.impl;

import com.ruleforge.builder.resource.Resource;
import com.ruleforge.builder.resource.ResourceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecResourceProvider implements ResourceProvider {

    private final RestTemplate consoleRestTemplate;

    @Override
    public Resource provide(String path, String version, String projectVersion, boolean containSnapshot) {
        log.info("ExecResourceProvider path: {} version: {} projectVersion: {}", path, version, projectVersion);
        if (StringUtils.hasText(version)) {
            return new Resource(sendRequest(path + ":" + version, projectVersion), path, projectVersion);
        } else {
            return new Resource(sendRequest(path, projectVersion), path, projectVersion);
        }
    }

    @Override
    public boolean support(String path) {
        return path.startsWith("/");
    }

    private String sendRequest(String path, String projectVersion) {
        String url = "/ruleforgeV2/frame/fileSource";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("path", path);
        map.add("env", "test");
        map.add("projectVersion", projectVersion);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
        ParameterizedTypeReference<Map<String, String>> responseType =
                new ParameterizedTypeReference<>() {};
        ResponseEntity<Map<String, String>> response = this.consoleRestTemplate.exchange(
                url, HttpMethod.POST, request, responseType);
        return response.getBody().get("content");
    }
}
