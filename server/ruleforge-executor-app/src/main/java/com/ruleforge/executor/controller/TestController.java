package com.ruleforge.executor.controller;

import com.ruleforge.decision.lazy.DatasourceRoutingProvider;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.cache.CacheUtils;
import com.ruleforge.runtime.cache.KnowledgeCache;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import com.ruleforge.runtime.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final KnowledgeService knowledgeService;
    private final DatasourceRoutingProvider datasourceRoutingProvider;

    @RequestMapping("/do")
    public String doTest(@RequestParam("path") String path,
                         @RequestParam(value = "flow", required = false) String flow) throws Exception {
        KnowledgePackage knowledgePackage = knowledgeService.getKnowledge(path);
        KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackage);

        if (flow != null && !flow.isEmpty()) {
            // Flow execution is now handled by Flowable BPMN engine in console app.
            // Delegate to console's REST endpoint.
            return "Flow execution requires Flowable engine. Use console /flow/deploy and Flowable RuntimeService.";
        }

        RuleExecutionResponse response = session.fireRules();
        return response.toString();
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

    /**
     * V5.8.0: 数据源批量拉取(给 console-app BatchTest V5.8.0 FLOW+DATASOURCE 模式用)
     *
     * POST /test/datasource/fetch
     * body: {
     *   datasourceId: Long,
     *   clazz: String,
     *   entityIds: [String, ...],
     *   fieldNames: [String, ...]
     * }
     * returns: { rows: { entityId: { fieldName: value, ... }, ... }, count: int }
     *
     * 不走 ${ruleforge.root.path} 前缀 — 这个端点是 executor 内部给 console 调的,
     * 对外不暴露给最终用户。
     */
    @PostMapping("/datasource/fetch")
    public Map<String, Object> fetchDatasource(@RequestBody Map<String, Object> req) {
        Long datasourceId = ((Number) req.get("datasourceId")).longValue();
        String clazz = (String) req.get("clazz");
        @SuppressWarnings("unchecked")
        List<String> entityIds = (List<String>) req.get("entityIds");
        @SuppressWarnings("unchecked")
        List<String> fieldNames = (List<String>) req.get("fieldNames");

        Map<String, Map<String, Object>> result = datasourceRoutingProvider.fetchFieldsForEntities(
                datasourceId, clazz, entityIds, fieldNames);

        return Map.of("rows", result, "count", result.size());
    }
}
