package com.ruleforge.decision.flow.delegate;

import com.ruleforge.Utils;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.builder.ResourceBase;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.ExecutionResponse;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 规则执行 serviceTask 委托 — 把 BPMN 流程变量注入规则引擎,
 * 触发规则,并把结果写回流程变量。
 *
 * <p>原归属: {@code com.ruleforge.console.flow.delegate} (ruleforge-console-app)。
 * 移到这里 (ruleforge-decision, 共享 lib) 的原因: <strong>BPMN 流程由 executor-app
 * 启动并执行</strong>(因为 {@code /api/loan/evaluate} 走 executor-app),而
 * console-app ↛ executor-app 是强制的模块边界 — executor-app 的 classpath 上
 * 根本没有 console-app 的类。共享 lib 是唯一合规的位置。
 *
 * <p>关键依赖(全部来自 ruleforge-core,decision 已经依赖): {@code KnowledgeBuilder},
 * {@code KnowledgeService}, {@code KnowledgeSessionFactory}。
 */
@Component("ruleServiceTaskDelegate")
@Slf4j
@RequiredArgsConstructor
public class RuleServiceTaskDelegate implements JavaDelegate {

    private final KnowledgeBuilder knowledgeBuilder;

    @Override
    public void execute(DelegateExecution execution) {
        String file = execution.getCurrentFlowElement().getAttributeValue(
                "http://ruleforge.com/schema", "file");
        String project = execution.getCurrentFlowElement().getAttributeValue(
                "http://ruleforge.com/schema", "project");
        String version = execution.getCurrentFlowElement().getAttributeValue(
                "http://ruleforge.com/schema", "version");

        if (file == null || file.isEmpty()) {
            log.warn("No rule file specified for service task: {}", execution.getCurrentActivityId());
            return;
        }

        KnowledgePackage knowledgePackage;
        // Try loading as knowledge package first (projectName/packageId format)
        if (project != null && !project.isEmpty() && !file.startsWith("/")) {
            KnowledgeService service = (KnowledgeService) Utils.getApplicationContext().getBean(KnowledgeService.BEAN_ID);
            String resourceKey = project + "/" + file;
            try {
                knowledgePackage = service.getKnowledge(resourceKey);
            } catch (Exception e) {
                log.warn("Failed to load knowledge package: {}, building from file instead", resourceKey);
                knowledgePackage = buildFromFile(file, version);
            }
        } else {
            // Build directly from rule file
            knowledgePackage = buildFromFile(file, version);
        }
        if (knowledgePackage == null) {
            log.error("Knowledge package not found for file: {}", file);
            return;
        }

        KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackage);

        // Insert process variables as facts
        Map<String, Object> variables = execution.getVariables();
        Map<String, Object> parameters = insertFacts(session, variables);

        ExecutionResponse response;
        try {
            if (parameters != null) {
                response = session.fireRules(parameters);
            } else {
                response = session.fireRules();
            }
        } catch (com.ruleforge.exception.RuleException e) {
            // 非致命:本地 ctx.session 已经包了 OutputModel,Flowable 这条 session
            // 跑同一份 .rl 时 var-assign OutputModel.ruleResult 会找不到 entity —
            // 这种情况只让本地的 mutation 生效(同步回 POJO),Flowable 路径继续
            log.warn("[FLOWABLE-DELEGATE] 规则执行出现非致命异常(本地 session 已处理): {}", e.getMessage());
            com.ruleforge.runtime.response.ExecutionResponseImpl emptyResp =
                    new com.ruleforge.runtime.response.ExecutionResponseImpl();
            emptyResp.setFiredRules(new java.util.ArrayList<>());
            response = emptyResp;
        }

        // Write results back to process variables
        Map<String, Object> resultVariables = extractResults(session, variables);
        execution.setVariables(resultVariables);

        // Store execution info
        ExecutionResponseImpl res = (ExecutionResponseImpl) response;
        execution.setVariable("_firedRules", res.getFiredRules().size());
        execution.setVariable("_matchedRules", res.getMatchedRules().size());

        try {
            session.writeLogFile();
        } catch (Exception e) {
            log.error("Failed to write log file", e);
        }
    }

    private KnowledgePackage buildFromFile(String file, String version) {
        try {
            ResourceBase resourceBase = knowledgeBuilder.newResourceBase();
            String ver = (version != null && !"LATEST".equals(version)) ? version : null;
            resourceBase.addResource(file, ver, true);
            KnowledgeBase knowledgeBase = knowledgeBuilder.buildKnowledgeBase(resourceBase);
            return knowledgeBase.getKnowledgePackage();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build knowledge package from file: " + file, e);
        }
    }

    private Map<String, Object> insertFacts(KnowledgeSession session, Map<String, Object> variables) {
        Map<String, Object> parameters = null;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof GeneralEntity) {
                // BPMN 里以 entity 形式传进来的 fact(老路径,兼容)
                session.insert(value);
            } else if (value instanceof Map) {
                // 关键:applicant / order 走 hybrid 路径时是 Map(因为 Flowable 序列化
                // 要求 Serializable,entity 持有 Spring bean 引用不可序列化)。
                // 这种 key 已知是 fact,转成 transient GeneralEntity 当 session fact。
                // 其它 key 的 Map 仍按原行为当 parameters(规则用 fireRules(parameters) 传)。
                if ("applicant".equals(key)) {
                    session.insert(asTransientEntity((Map<String, Object>) value,
                            "com.ruleforge.decision.model.ApplicantModel"));
                } else if ("order".equals(key)) {
                    session.insert(asTransientEntity((Map<String, Object>) value,
                            "com.ruleforge.decision.model.OrderModel"));
                } else {
                    parameters = (Map<String, Object>) value;
                }
            } else {
                session.insert(value);
            }
        }
        return parameters;
    }

    /**
     * 把 facts Map 包成 {@link GeneralEntity} — 给规则 DSL 写
     * {@code applicant.age} 用,字段从 map 读。{@link GeneralEntity} 继承自
     * {@code HashMap<String, Object>},所以把 facts putAll 进去就行,规则读
     * {@code entity.get("age")} 直接命中。
     *
     * <p><b>targetClass 必须是规则变量定义表 {@code nd_rule_variable_def.clazz}
     * 里登记的全限定类名</b>(如 {@code com.ruleforge.decision.model.ApplicantModel})。
     * 规则引擎用 {@code GeneralEntity.getTargetClass()} 当 allFactsMap 的 key
     * (见 {@code KnowledgeSessionImpl.getClassName()}),targetClass 错了规则
     * 找不到 fact,0 rules fired。
     *
     * <p>不带 lazy 机制(delegate 在 ruleforge-decision 共享 lib,不持有
     * DataSourceProvider);eager 字段走 map,字段不在 map → 返回 null,
     * 规则 DSL 用 {@code contains} / null-safe 表达式。
     */
    private GeneralEntity asTransientEntity(Map<String, Object> facts, String targetClass) {
        GeneralEntity entity = new GeneralEntity(targetClass);
        entity.putAll(facts);
        log.info("[DEBUG-ENTITY] created entity targetClass={}, facts={}", targetClass, facts);
        return entity;
    }

    private Map<String, Object> extractResults(KnowledgeSession session, Map<String, Object> originalVars) {
        Map<String, Object> results = new HashMap<>();
        for (Map.Entry<String, Object> entry : originalVars.entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;
            results.put(entry.getKey(), value);
            // Flatten entity properties for UEL condition access (e.g. user.passed → user_passed)
            if (value instanceof Map entityMap) {
                for (Map.Entry<String, Object> prop : ((Map<String, Object>) entityMap).entrySet()) {
                    if (prop.getValue() != null) {
                        results.put(entry.getKey() + "_" + prop.getKey(), prop.getValue());
                    }
                }
            }
        }
        Map<String, Object> params = session.getParameters();
        if (params != null) {
            results.putAll(params);
        }
        return results;
    }
}
