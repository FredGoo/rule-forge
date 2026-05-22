package com.ruleforge.console.app.service.impl;

import com.ruleforge.console.ExternalProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Fred
 * 2019-12-27 3:50 PM
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalProcessServiceImpl implements ExternalProcessService {

    private final RestTemplate execRestTemplate;

    @Override
    public void syncExec(String fullPackageId, String env, String username, Integer proportion, Date start, Date end) {
        log.info("syncExec {} {} {}", fullPackageId, env, proportion);
        String url = "/test/knowledge";
        Map<String, String> params = new HashMap<>();
        params.put("packageId", fullPackageId);
//        this.execRestTemplate.postForObject(url, params, Void.class);
    }

    @Override
    public String start(String project,
                        String title,
                        String nowVersion,
                        String version,
                        String explain,
                        String remark,
                        String fileName,
                        String filePath,
                        String passRateEffect,
                        Double passRateRange,
                        String badDebtRateEffect,
                        Double badDebtRateRange) throws Exception {
        log.info("{} ,{}, {}, {}, {}", project, version, explain, fileName, filePath);
        if (StringUtils.isEmpty("")) {
        }
        return "autoProcess";
    }

    @Override
    public String testStart(String title, String project, String fileName, Date startTime, Date endTime, String version, Integer testRate, String remark, String explain) throws Exception {
        return "autoProcess";
    }
}
