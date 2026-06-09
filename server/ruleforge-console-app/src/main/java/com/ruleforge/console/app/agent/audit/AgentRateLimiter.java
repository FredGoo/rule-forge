package com.ruleforge.console.app.agent.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 工具调用限流器 (V5.22.2)
 *
 * <p>每个 user + session 组合,过去 1 小时最多 100 次工具调用(滑动窗口)。
 * <p>user 单独也限(防止同一 user 走多 session 绕开)。
 * <p>超出抛 {@link RateLimitExceededException}。
 *
 * <p>内存存储 — 重启会清空(对短期滥用足够;长期滥用交给 nd_agent_audit 离线分析)。
 */
@Slf4j
@Component
public class AgentRateLimiter {

    /** 默认 100 calls / hour(用户原话) */
    @Value("${ruleforge.agent.rate-limit.max-per-hour:100}")
    private int maxPerHour;

    /** key = userId|sessionId(只 user 也算,只 session 也算) */
    private final ConcurrentHashMap<String, Deque<Instant>> userWindow = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> sessionWindow = new ConcurrentHashMap<>();

    /**
     * 检查并记录一次调用。
     *
     * @throws RateLimitExceededException 超出上限时
     */
    public void check(String userId, String sessionId) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(1, ChronoUnit.HOURS);

        if (userId != null && !userId.isEmpty()) {
            if (!tryAcquire(userWindow.computeIfAbsent(userId, k -> new ArrayDeque<>()), now, cutoff)) {
                throw new RateLimitExceededException(
                        "用户 " + userId + " 超过每小时 " + maxPerHour + " 次调用上限");
            }
        }
        if (sessionId != null && !sessionId.isEmpty()) {
            if (!tryAcquire(sessionWindow.computeIfAbsent(sessionId, k -> new ArrayDeque<>()), now, cutoff)) {
                throw new RateLimitExceededException(
                        "会话 " + sessionId + " 超过每小时 " + maxPerHour + " 次调用上限");
            }
        }
    }

    /** 获取当前用户最近一小时调用数(只读) */
    public int currentHourCount(String userId) {
        Deque<Instant> ts = userWindow.get(userId);
        if (ts == null) return 0;
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        synchronized (ts) {
            return (int) ts.stream().filter(t -> t.isAfter(cutoff)).count();
        }
    }

    private boolean tryAcquire(Deque<Instant> window, Instant now, Instant cutoff) {
        synchronized (window) {
            // 滑动窗口:丢掉超过 1 小时的旧时间戳
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            if (window.size() >= maxPerHour) {
                log.warn("[RateLimit] 上限命中 size={} max={}", window.size(), maxPerHour);
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    /** 测试 / 管理用:清空所有窗口 */
    public void reset() {
        userWindow.clear();
        sessionWindow.clear();
    }

    public int getMaxPerHour() {
        return maxPerHour;
    }

    /** 限流异常 */
    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String msg) { super(msg); }
    }
}
