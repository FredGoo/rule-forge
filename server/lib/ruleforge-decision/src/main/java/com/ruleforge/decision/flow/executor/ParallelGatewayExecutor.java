package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.NodeType;
import com.ruleforge.decision.flow.ir.SequenceFlow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V5.33 A0 — Parallel Gateway 节点执行器 + findJoinTarget 启发式。
 *
 * <p><b>执行器自身</b>:executor.execute() 仍 noop — 分裂/合流由 FlowNodeRunner.traverse 主循环识别。
 * <p><b>findJoinTarget 启发式</b>:在 FlowDefinition 上走 in-degree map,定位 fork 后的真 join 节点。
 * <ul>
 *   <li>in-degree > 1 的 PARALLEL_GATEWAY 且 outgoing 非空 → 候选</li>
 *   <li>1 个候选 → 返回该候选</li>
 *   <li>0 或 2+ 候选 → 返回 null(P0 fallback:各 branch 跑到底,不 join)</li>
 * </ul>
 */
@Slf4j
@Component
public class ParallelGatewayExecutor implements NodeExecutor {

    @Override
    public String supportedType() {
        return "PARALLEL_GATEWAY";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) {
        // V5.33 A0:fork/join 逻辑在 FlowNodeRunner.traverse 主循环里识别,这里 noop
        log.debug("[PARALLEL-GATEWAY] {} (noop — fork/join handled by FlowNodeRunner.traverse)",
            node.getName());
    }

    /**
     * 启发式找 fork 后的 join 节点。返回 join 节点 id,或 null(0 候选 / 2+ 候选 / P0 fallback)。
     */
    public static String findJoinTarget(FlowDefinition def) {
        // 1. 算所有节点的 in-degree(指向该节点的 sequenceFlow 数)
        Map<String, Integer> inDegree = new HashMap<>();
        for (SequenceFlow e : def.getEdges()) {
            inDegree.merge(e.getTargetId(), 1, Integer::sum);
        }
        // 2. 找 in-degree > 1 且 outgoing > 0 的 PARALLEL_GATEWAY
        List<String> candidates = new ArrayList<>();
        for (FlowNode n : def.getNodes().values()) {
            if (n.getType() == NodeType.PARALLEL_GATEWAY
                && inDegree.getOrDefault(n.getNodeId(), 0) > 1
                && !n.getOutgoingIds().isEmpty()) {
                candidates.add(n.getNodeId());
            }
        }
        if (candidates.size() == 1) return candidates.get(0);
        // 0 候选或 2+ 候选 → null(P0 fallback:各 branch 跑到底)
        if (candidates.size() >= 2) {
            log.debug("[PARALLEL-GATEWAY] findJoinTarget ambiguous: {} candidates, falling back to P0",
                candidates);
        }
        return null;
    }
}
