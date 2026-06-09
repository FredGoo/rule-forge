package com.ruleforge.console.app.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruleforge.console.app.draft.DraftApplyService;
import com.ruleforge.console.app.draft.DraftEntity;
import com.ruleforge.console.app.draft.DraftService;
import com.ruleforge.console.app.service.IAnalysisService;
import com.ruleforge.console.service.impl.RuleForgeRepositoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * ToolExecutor V5.22 AI 工具测试
 *
 * 不走 Spring,Mock 所有 service。验证:
 * - draft_rule 校验 + 调 DraftService.createDraft
 * - list_drafts 按 project/status 过滤
 * - get_draft 返 DTO 或 404
 * - submit/approve/reject/apply 状态机
 * - generate_test_cases + run_test 走 LLM agent 流程
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ToolExecutor - V5.22 AI Rule Authoring tools")
class ToolExecutorV522Test {

    @Mock
    private IAnalysisService analysisService;
    @Mock
    private RuleForgeRepositoryServiceImpl repoService;
    @Mock
    private DraftService draftService;
    @Mock
    private DraftApplyService draftApplyService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ToolExecutor(analysisService, repoService, objectMapper, draftService, draftApplyService);
    }

    // ========== draft_rule ==========

    @Nested
    @DisplayName("Scenario: draft_rule 工具")
    class DraftRule {

        @Test
        @DisplayName("Given 合法 content When 调 draft_rule Then 调 DraftService.createDraft 并返 draftId")
        void shouldCreateDraft() throws Exception {
            // Given
            String content = "{\"type\":\"decision_table\",\"rows\":[],\"columns\":[],\"cellMap\":{}}";
            DraftEntity d = newDraft("drf_abc", DraftEntity.STATUS_DRAFT, content);
            when(draftService.createDraft(eq("decision_table"), eq("demo"), eq(content),
                    eq("user1"), any(), any(), any(), any()))
                    .thenReturn(d);
            lenient().when(draftService.toDto(any(DraftEntity.class))).thenAnswer(inv -> {
                DraftEntity e = inv.getArgument(0);
                ObjectNode n = objectMapper.createObjectNode();
                n.put("draftId", e.getDraftId());
                n.put("status", e.getStatus());
                n.put("ruleType", e.getRuleType());
                n.put("project", e.getProject());
                try {
                    n.set("content", objectMapper.readTree(e.getContent()));
                } catch (Exception ex) {
                    n.put("content", e.getContent());
                }
                return n;
            });

            // When
            String args = "{\"ruleType\":\"decision_table\",\"project\":\"demo\",\"content\":" +
                    objectMapper.writeValueAsString(content) + ",\"createdBy\":\"user1\"}";
            String result = executor.execute(ToolRegistry.DRAFT_RULE, args);

            // Then
            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("draftId").asText()).isEqualTo("drf_abc");
            assertThat(r.get("status").asText()).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("Given content 不合法 When 调 draft_rule Then 返 error + 不写 DB")
        void shouldRejectInvalidContent() throws Exception {
            // Given — content 缺 cellMap,validateContent 抛
            doThrow(new IllegalArgumentException("content.cellMap 必填"))
                    .when(draftService).validateContent(eq("decision_table"), anyString());

            // When
            String args = "{\"ruleType\":\"decision_table\",\"project\":\"demo\",\"content\":\"{\\\"type\\\":\\\"decision_table\\\",\\\"rows\\\":[],\\\"columns\\\":[]}\",\"createdBy\":\"u\"}";
            String result = executor.execute(ToolRegistry.DRAFT_RULE, args);

            // Then
            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("error").asText()).isEqualTo("content_validation_failed");
            assertThat(r.get("message").asText()).contains("cellMap");
        }

        @Test
        @DisplayName("Given 缺必填参数 When 调 draft_rule Then 返 error")
        void shouldRequireMandatoryArgs() {
            String result = executor.execute(ToolRegistry.DRAFT_RULE, "{\"ruleType\":\"decision_table\"}");
            assertThat(result).contains("error").contains("必填");
        }
    }

    // ========== list_drafts ==========

    @Nested
    @DisplayName("Scenario: list_drafts 工具")
    class ListDrafts {

        @Test
        @DisplayName("按 project 列草稿")
        void shouldListByProject() throws Exception {
            when(draftService.listByProject("demo", 50)).thenReturn(java.util.List.of(
                    newDraft("d1", "DRAFT", "x"), newDraft("d2", "PENDING_REVIEW", "x")
            ));
            lenient().when(draftService.toDto(any(DraftEntity.class))).thenAnswer(inv -> {
                DraftEntity e = inv.getArgument(0);
                ObjectNode n = objectMapper.createObjectNode();
                n.put("draftId", e.getDraftId());
                n.put("status", e.getStatus());
                n.put("ruleType", e.getRuleType());
                n.put("project", e.getProject());
                try {
                    n.set("content", objectMapper.readTree(e.getContent()));
                } catch (Exception ex) {
                    n.put("content", e.getContent());
                }
                return n;
            });

            String result = executor.execute(ToolRegistry.LIST_DRAFTS,
                    "{\"project\":\"demo\",\"limit\":50}");

            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("count").asInt()).isEqualTo(2);
            assertThat(r.get("drafts").isArray()).isTrue();
        }
    }

    // ========== get_draft ==========

    @Nested
    @DisplayName("Scenario: get_draft 工具")
    class GetDraft {

        @Test
        @DisplayName("给定存在的 draftId 返 DTO")
        void shouldReturnDto() throws Exception {
            DraftEntity d = newDraft("drf_abc", "DRAFT",
                    "{\"type\":\"decision_table\",\"rows\":[],\"columns\":[],\"cellMap\":{}}");
            when(draftService.get("drf_abc")).thenReturn(Optional.of(d));
            lenient().when(draftService.toDto(any(DraftEntity.class))).thenAnswer(inv -> {
                DraftEntity e = inv.getArgument(0);
                ObjectNode n = objectMapper.createObjectNode();
                n.put("draftId", e.getDraftId());
                n.put("status", e.getStatus());
                n.put("ruleType", e.getRuleType());
                n.put("project", e.getProject());
                try {
                    n.set("content", objectMapper.readTree(e.getContent()));
                } catch (Exception ex) {
                    n.put("content", e.getContent());
                }
                return n;
            });

            String result = executor.execute(ToolRegistry.GET_DRAFT, "{\"draftId\":\"drf_abc\"}");

            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("draftId").asText()).isEqualTo("drf_abc");
        }

        @Test
        @DisplayName("给定不存在的 draftId 返 error")
        void shouldReturnNotFound() {
            when(draftService.get("none")).thenReturn(Optional.empty());
            String result = executor.execute(ToolRegistry.GET_DRAFT, "{\"draftId\":\"none\"}");
            assertThat(result).contains("draft_not_found");
        }
    }

    // ========== 状态机 tools ==========

    @Nested
    @DisplayName("Scenario: 审批状态机")
    class StateMachineTools {

        @Test
        @DisplayName("submit_draft 走 DraftService.submitForReview")
        void shouldSubmit() throws Exception {
            DraftEntity d = newDraft("d1", "PENDING_REVIEW", "x");
            when(draftService.submitForReview("d1", "u")).thenReturn(d);
            lenient().when(draftService.toDto(any(DraftEntity.class))).thenAnswer(inv -> {
                DraftEntity e = inv.getArgument(0);
                ObjectNode n = objectMapper.createObjectNode();
                n.put("draftId", e.getDraftId());
                n.put("status", e.getStatus());
                n.put("ruleType", e.getRuleType());
                n.put("project", e.getProject());
                try {
                    n.set("content", objectMapper.readTree(e.getContent()));
                } catch (Exception ex) {
                    n.put("content", e.getContent());
                }
                return n;
            });

            String result = executor.execute(ToolRegistry.SUBMIT_DRAFT,
                    "{\"draftId\":\"d1\",\"submittedBy\":\"u\"}");
            assertThat(objectMapper.readTree(result).get("status").asText()).isEqualTo("PENDING_REVIEW");
        }

        @Test
        @DisplayName("approve_draft 走 DraftService.approve")
        void shouldApprove() throws Exception {
            DraftEntity d = newDraft("d1", "APPROVED", "x");
            when(draftService.approve("d1", "r", "ok")).thenReturn(d);
            lenient().when(draftService.toDto(any(DraftEntity.class))).thenAnswer(inv -> {
                DraftEntity e = inv.getArgument(0);
                ObjectNode n = objectMapper.createObjectNode();
                n.put("draftId", e.getDraftId());
                n.put("status", e.getStatus());
                n.put("ruleType", e.getRuleType());
                n.put("project", e.getProject());
                try {
                    n.set("content", objectMapper.readTree(e.getContent()));
                } catch (Exception ex) {
                    n.put("content", e.getContent());
                }
                return n;
            });

            String result = executor.execute(ToolRegistry.APPROVE_DRAFT,
                    "{\"draftId\":\"d1\",\"reviewer\":\"r\",\"comment\":\"ok\"}");
            assertThat(objectMapper.readTree(result).get("status").asText()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("reject_draft 走 DraftService.reject")
        void shouldReject() throws Exception {
            DraftEntity d = newDraft("d1", "REJECTED", "x");
            when(draftService.reject("d1", "r", "no")).thenReturn(d);
            lenient().when(draftService.toDto(any(DraftEntity.class))).thenAnswer(inv -> {
                DraftEntity e = inv.getArgument(0);
                ObjectNode n = objectMapper.createObjectNode();
                n.put("draftId", e.getDraftId());
                n.put("status", e.getStatus());
                n.put("ruleType", e.getRuleType());
                n.put("project", e.getProject());
                try {
                    n.set("content", objectMapper.readTree(e.getContent()));
                } catch (Exception ex) {
                    n.put("content", e.getContent());
                }
                return n;
            });

            String result = executor.execute(ToolRegistry.REJECT_DRAFT,
                    "{\"draftId\":\"d1\",\"reviewer\":\"r\",\"reason\":\"no\"}");
            assertThat(objectMapper.readTree(result).get("status").asText()).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("apply_draft 走 DraftApplyService.applyToPackage")
        void shouldApply() throws Exception {
            ObjectNode out = objectMapper.createObjectNode();
            out.put("draftId", "d1");
            out.put("newVersion", "v1.0.5");
            when(draftApplyService.applyToPackage(eq("d1"), eq("/pkg"), any(), any(), any())).thenReturn(out);

            String result = executor.execute(ToolRegistry.APPLY_DRAFT,
                    "{\"draftId\":\"d1\",\"packagePath\":\"/pkg\",\"reviewer\":\"r\"}");
            assertThat(objectMapper.readTree(result).get("newVersion").asText()).isEqualTo("v1.0.5");
        }
    }

    // ========== generate_test_cases ==========

    @Nested
    @DisplayName("Scenario: generate_test_cases 工具")
    class GenerateTestCases {

        @Test
        @DisplayName("从 decision_table 的 cellMap 反推测试用例")
        void shouldGenerateFromCellMap() throws Exception {
            String content = """
                    {
                      "type": "decision_table",
                      "rows": [
                        {"rowId": "r1", "remark": "age<18 reject"},
                        {"rowId": "r2", "remark": "income<3000 reject"}
                      ],
                      "columns": [
                        {"colId": "c1", "type": "condition", "variable": "customer.age", "operator": "lt", "datatype": "number"},
                        {"colId": "c2", "type": "condition", "variable": "customer.monthlyIncome", "operator": "lt", "datatype": "number"},
                        {"colId": "c3", "type": "action", "variable": "decision.status", "operator": "assign", "datatype": "string"}
                      ],
                      "cellMap": {
                        "r1,c1": "18",
                        "r1,c3": "'REJECTED'",
                        "r2,c2": "3000",
                        "r2,c3": "'REJECTED'"
                      }
                    }
                    """;
            DraftEntity d = newDraft("d1", "DRAFT", content);
            when(draftService.get("d1")).thenReturn(Optional.of(d));

            String result = executor.execute(ToolRegistry.GENERATE_TEST_CASES,
                    "{\"draftId\":\"d1\",\"count\":5}");

            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("count").asInt()).isEqualTo(2); // 2 个 row
            JsonNode tests = r.get("testCases");
            // 第一个测试用例
            JsonNode t1 = tests.get(0);
            assertThat(t1.get("rowId").asText()).isEqualTo("r1");
            assertThat(t1.get("inputs").get("customer.age").asInt()).isEqualTo(18);
            // cellMap value 保留单引号(schema 设计:字符串要带引号)
            assertThat(t1.get("expectedAction").get("decision.status").asText()).isEqualTo("'REJECTED'");
        }

        @Test
        @DisplayName("草稿不存在返 error")
        void shouldReturnErrorForMissingDraft() {
            when(draftService.get("none")).thenReturn(Optional.empty());
            String result = executor.execute(ToolRegistry.GENERATE_TEST_CASES, "{\"draftId\":\"none\"}");
            assertThat(result).contains("draft_not_found");
        }
    }

    // ========== run_test ==========

    @Nested
    @DisplayName("Scenario: run_test 工具")
    class RunTest {

        @Test
        @DisplayName("测试用例全 PASS — 命中 row r1")
        void shouldMatchRow() throws Exception {
            String content = """
                    {
                      "type": "decision_table",
                      "rows": [{"rowId": "r1", "remark": "age<18 reject"}],
                      "columns": [
                        {"colId": "c1", "type": "condition", "variable": "customer.age", "operator": "lt", "datatype": "number"},
                        {"colId": "c2", "type": "action", "variable": "decision.status", "operator": "assign", "datatype": "string"}
                      ],
                      "cellMap": {
                        "r1,c1": "18",
                        "r1,c2": "'REJECTED'"
                      }
                    }
                    """;
            DraftEntity d = newDraft("d1", "DRAFT", content);
            when(draftService.get("d1")).thenReturn(Optional.of(d));

            // 直接 JSON 内嵌,不再二次编码
            String args = """
                    {
                      "draftId": "d1",
                      "testCases": [
                        {"name": "under18", "rowId": "r1", "inputs": {"customer.age": 17}, "expectedAction": {"decision.status": "REJECTED"}}
                      ]
                    }
                    """;
            String result = executor.execute(ToolRegistry.RUN_TEST, args);

            JsonNode r = objectMapper.readTree(result);
            assertThat(r.get("passed").asInt()).isEqualTo(1);
            assertThat(r.get("failed").asInt()).isEqualTo(0);
            assertThat(r.get("results").get(0).get("matchedRowId").asText()).isEqualTo("r1");
            assertThat(r.get("results").get(0).get("status").asText()).isEqualTo("PASS");
        }
    }

    // ========== helper ==========

    private DraftEntity newDraft(String id, String status, String content) {
        DraftEntity d = new DraftEntity();
        d.setDraftId(id);
        d.setRuleType("decision_table");
        d.setProject("demo");
        d.setContent(content);
        d.setStatus(status);
        d.setCreatedBy("user1");
        d.setSource("LLM");
        return d;
    }
}
