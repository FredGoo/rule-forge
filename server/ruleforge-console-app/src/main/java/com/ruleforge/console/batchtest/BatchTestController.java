package com.ruleforge.console.batchtest;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruleforge.console.app.entity.BatchTestRowEntity;
import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import com.ruleforge.console.app.mapper.BatchTestRowMapper;
import com.ruleforge.console.app.mapper.BatchTestSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BatchTest REST API(V5.8.0)
 *
 * 端点:
 *   POST   /ruleforge/batchtest/start                    — 启动 session
 *   GET    /ruleforge/batchtest/sessions/{id}/progress   — 轮询进度
 *   GET    /ruleforge/batchtest/sessions/{id}/results    — 拉行结果(分页)
 *   GET    /ruleforge/batchtest/sessions                 — 列历史 session
 *
 * V5.8.0 状态:
 *   - FLOW+FILE 模式完全可用
 *   - FLOW+DATASOURCE / DATASOURCE+* 模式 controller 会返回 501 Not Implemented
 *     (实际 fetchAndInsert 在 InputSource 里抛 UnsupportedOperationException)
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/batchtest")
@RequiredArgsConstructor
public class BatchTestController {

    private final BatchTestOrchestrator orchestrator;
    private final BatchTestSessionMapper sessionMapper;
    private final BatchTestRowMapper rowMapper;

    /**
     * 启动一次批量测试
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestBody StartBatchTestRequest req) {
        try {
            Long sessionId = orchestrator.startBatchTest(req);
            Map<String, Object> resp = new HashMap<>();
            resp.put("sessionId", sessionId);
            resp.put("status", BatchTestSessionEntity.STATUS_RUNNING);
            resp.put("subjectType", req.subjectType());
            resp.put("inputSourceType", req.inputSourceType());
            return ResponseEntity.ok(resp);
        } catch (UnsupportedOperationException e) {
            // V5.8.0 暂未实现的 mode
            log.warn("BatchTest start refused: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("BatchTest start invalid: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 轮询进度(Vue BatchTestDialog 每 1-2s 调一次)
     */
    @GetMapping("/sessions/{id}/progress")
    public ResponseEntity<Map<String, Object>> progress(@PathVariable("id") Long sessionId) {
        Map<String, Object> progress = orchestrator.getProgress(sessionId);
        if ("NOT_FOUND".equals(progress.get("status"))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(progress);
    }

    /**
     * 拉行结果(分页)
     */
    @GetMapping("/sessions/{id}/results")
    public ResponseEntity<Map<String, Object>> results(
            @PathVariable("id") Long sessionId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        // 简单分页(以后改成 keyset 优化大列表)
        int offset = Math.max(0, (page - 1) * size);
        List<BatchTestRowEntity> rows = orchestrator.getResults(sessionId, offset, size);
        Long total = rowMapper.selectCount(
                new QueryWrapper<BatchTestRowEntity>().eq("session_id", sessionId));
        Map<String, Object> resp = new HashMap<>();
        resp.put("rows", rows);
        resp.put("page", page);
        resp.put("size", size);
        resp.put("total", total);
        return ResponseEntity.ok(resp);
    }

    /**
     * 列历史 session(给 dashboard 用)
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<BatchTestSessionEntity>> list(
            @RequestParam(value = "subjectType", required = false) String subjectType,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        QueryWrapper<BatchTestSessionEntity> qw = new QueryWrapper<>();
        if (subjectType != null) {
            qw.eq("subject_type", subjectType);
        }
        qw.orderByDesc("create_time").last("LIMIT " + limit);
        List<BatchTestSessionEntity> sessions = sessionMapper.selectList(qw);
        return ResponseEntity.ok(sessions);
    }
}
