package com.ruleforge.executor.controller;

import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.cache.CacheUtils;
import com.ruleforge.runtime.cache.KnowledgeCache;
import com.ruleforge.runtime.response.FlowExecutionResponse;
import com.ruleforge.runtime.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final KnowledgeService knowledgeService;

    @RequestMapping("/do")
    public String doTest(@RequestParam("path") String path, @RequestParam("flow") String flow) throws Exception {
        KnowledgePackage knowledgePackage = knowledgeService.getKnowledge(path);
        KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackage);
        FlowExecutionResponse response = session.startProcess(flow);
        return response.getFlowExecutionResponses().toString();
    }

    @PostMapping("/knowledge")
    public void knowledge(@RequestBody Map<String, String> params) {
        String packageId = params.get("packageId");
        if (packageId != null) {
            KnowledgeCache knowledgeCache = CacheUtils.getKnowledgeCache();
            knowledgeCache.markKnowledgeDirty(packageId);
            log.info("Marked package [{}] as dirty.", packageId);
        }
    }
}
