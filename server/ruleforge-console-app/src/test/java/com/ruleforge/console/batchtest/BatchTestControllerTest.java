package com.ruleforge.console.batchtest;

import com.ruleforge.console.app.entity.BatchTestRowEntity;
import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import com.ruleforge.console.app.mapper.BatchTestRowMapper;
import com.ruleforge.console.app.mapper.BatchTestSessionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Feature: BatchTest V5.8.0 多态化 REST API
 *
 * Controller 行为:
 *   - start: 路由到 BatchTestOrchestrator,返回 200 / 400 / 501
 *   - progress: 查进度,NOT_FOUND 时返 404
 *   - results: 分页查行结果
 *   - list: 列表查 session,支持 subjectType 过滤
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchTestController - 批量测试 REST API (V5.8.0)")
class BatchTestControllerTest {

    @Mock private BatchTestOrchestrator orchestrator;
    @Mock private BatchTestSessionMapper sessionMapper;
    @Mock private BatchTestRowMapper rowMapper;
    @InjectMocks private BatchTestController controller;

    @Nested
    @DisplayName("Scenario: 启动批量测试")
    class StartBatchTest {

        // Given 合法 FLOW+FILE 请求
        // When POST /start
        // Then 返 200 + sessionId
        @Test
        @DisplayName("FLOW+FILE 成功启动,返 sessionId")
        void shouldStartFlowFile() {
            when(orchestrator.startBatchTest(any())).thenReturn(123L);

            StartBatchTestRequest req = new StartBatchTestRequest(
                    BatchTestSessionEntity.SUBJECT_FLOW, 7L,
                    BatchTestSessionEntity.INPUT_FILE, null,
                    Map.of("flowParams", Map.of("flowId", "flow-x")),
                    "my-project", "pkg-1", "flow-x");

            ResponseEntity<Map<String, Object>> resp = controller.start(req);

            assertThat(resp.getStatusCode().value()).isEqualTo(200);
            assertThat(resp.getBody().get("sessionId")).isEqualTo(123L);
            assertThat(resp.getBody().get("status")).isEqualTo("RUNNING");
        }

        // Given 暂未实现的 mode(FLOW+DATASOURCE 或 DATASOURCE+*)
        // When POST /start
        // Then 返 501 + error message
        @Test
        @DisplayName("未实现的 mode 返 501")
        void shouldReturn501ForUnsupportedMode() {
            when(orchestrator.startBatchTest(any()))
                    .thenThrow(new UnsupportedOperationException(
                            "subjectType=DATASOURCE 暂未实现"));

            StartBatchTestRequest req = new StartBatchTestRequest(
                    BatchTestSessionEntity.SUBJECT_DATASOURCE, 99L,
                    BatchTestSessionEntity.INPUT_DATASOURCE, 99L,
                    Map.of(), null, null, null);

            ResponseEntity<Map<String, Object>> resp = controller.start(req);

            assertThat(resp.getStatusCode().value()).isEqualTo(501);
            assertThat(resp.getBody().get("error").toString()).contains("暂未实现");
        }

        // Given 非法参数(未知的 subjectType)
        // When POST /start
        // Then 返 400
        @Test
        @DisplayName("非法 subjectType 返 400")
        void shouldReturn400ForUnknownSubjectType() {
            when(orchestrator.startBatchTest(any()))
                    .thenThrow(new IllegalArgumentException(
                            "Unknown subjectType: BOGUS"));

            StartBatchTestRequest req = new StartBatchTestRequest(
                    "BOGUS", 1L,
                    BatchTestSessionEntity.INPUT_FILE, null,
                    Map.of(), null, null, null);

            ResponseEntity<Map<String, Object>> resp = controller.start(req);

            assertThat(resp.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("Scenario: 查询进度")
    class GetProgress {

        // Given session 存在
        // When GET /sessions/{id}/progress
        // Then 返 200 + 进度 map
        @Test
        @DisplayName("存在的 session 返 200 + 进度")
        void shouldReturnProgress() {
            when(orchestrator.getProgress(42L)).thenReturn(Map.of(
                    "sessionId", 42,
                    "status", "RUNNING",
                    "totalRows", 100,
                    "progress", 0.5,
                    "errorCount", 2,
                    "subjectType", "FLOW",
                    "inputSourceType", "FILE"));

            ResponseEntity<Map<String, Object>> resp = controller.progress(42L);

            assertThat(resp.getStatusCode().value()).isEqualTo(200);
            assertThat(resp.getBody().get("progress")).isEqualTo(0.5);
        }

        // Given session 不存在
        // When GET /sessions/{id}/progress
        // Then 返 404
        @Test
        @DisplayName("不存在的 session 返 404")
        void shouldReturn404ForMissingSession() {
            when(orchestrator.getProgress(999L)).thenReturn(Map.of("status", "NOT_FOUND"));

            ResponseEntity<Map<String, Object>> resp = controller.progress(999L);

            assertThat(resp.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("Scenario: 查行结果 + 列历史")
    class ResultsAndList {

        // Given session 有行结果
        // When GET /sessions/{id}/results?page=1&size=20
        // Then 返 rows + total
        @Test
        @DisplayName("查行结果返分页 + total")
        void shouldReturnPagedResults() {
            BatchTestRowEntity row = new BatchTestRowEntity();
            row.setId(1L);
            row.setRowIndex(0);
            row.setStatus("SUCCESS");
            when(orchestrator.getResults(eq(42L), eq(0), eq(20))).thenReturn(List.of(row));
            when(rowMapper.selectCount(any())).thenReturn(100L);

            ResponseEntity<Map<String, Object>> resp = controller.results(42L, 1, 20);

            assertThat(resp.getStatusCode().value()).isEqualTo(200);
            assertThat(resp.getBody().get("rows")).isEqualTo(List.of(row));
            assertThat(resp.getBody().get("total")).isEqualTo(100L);
            assertThat(resp.getBody().get("page")).isEqualTo(1);
        }

        // Given subjectType=FLOW
        // When GET /sessions?subjectType=FLOW
        // Then orchestrator 拿到过滤后的列表
        @Test
        @DisplayName("列历史支持 subjectType 过滤")
        void shouldListSessionsBySubjectType() {
            BatchTestSessionEntity s = new BatchTestSessionEntity();
            s.setId(1L);
            s.setSubjectType("FLOW");
            when(sessionMapper.selectList(any())).thenReturn(List.of(s));

            ResponseEntity<List<BatchTestSessionEntity>> resp = controller.list("FLOW", 20);

            assertThat(resp.getStatusCode().value()).isEqualTo(200);
            assertThat(resp.getBody()).hasSize(1);
            assertThat(resp.getBody().get(0).getSubjectType()).isEqualTo("FLOW");
        }
    }
}
