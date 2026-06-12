package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.ConditionEvaluator;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.engine.FlowNodeRunner;
import com.ruleforge.decision.flow.engine.SuspendRegistry;
import com.ruleforge.decision.flow.engine.Token;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.SequenceFlow;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V5.34 A3 — 共享 compensation handler runner。
 *
 * <p>Mirror Rust V5.31 P0 {@code compensation.rs::run_handlers} 契约。
 *
 * <p>职责:从一个 {@code CompensationThrow} 节点触发后,
 * 1. pop innermost scope(栈顶)
 * 2. 遍历 {@code def.attachedCompensations} 倒序(activity + handler 倒序),跳过已 dedup 的 pair
 * 3. 对每个 pair,找到 handler node 的 outgoing 第一个 target 节点
 * 4. 跑 mini-traverse(handler sub-flow);vars union-merge 回 outer ctx
 * 5. handler failure → 累积到 {@link CompensationTrace}.failures,继续下一个
 * 6. handler suspend → 透传 AsyncNodeSuspendException(外层 traverse catch 写 WAITING_CALLBACK)
 * 7. 全部跑完 → 返回 trace
 *
 * <p>v0 简化(跟 Rust 端一致):保守地跑所有声明的 handler(不区分 activity 是否 completed)。
 */
@Slf4j
public final class CompensationRunner {

    private CompensationRunner() {}

    /**
     * 跑 innermost scope 的 compensation handlers。
     *
     * @param def     流程定义
     * @param ctx     outer 流程上下文
     * @param reg     节点执行器注册中心
     * @return handler 跑完的 trace(failures 列表)
     * @throws FlowExecutionException stack 空时报 "CompensationNoScope" 错
     * @throws AsyncNodeSuspendException handler sub-flow 抛 Suspend 时透传
     */
    public static CompensationTrace runHandlers(FlowDefinition def, FlowContext ctx,
                                                NodeExecutorRegistry reg) {
        CompensationTrace trace = new CompensationTrace();

        // V5.31 P0 v0 — pop 栈顶 scope(throw 是 BPMN "current scope" 默认)
        List<String> stack = ctx.currentToken().getCompensationStack();
        if (stack.isEmpty()) {
            throw new FlowExecutionException(
                "CompensationThrow with no open scope (empty stack) at flowRunId=" + ctx.identity().flowRunId());
        }
        String poppedScope = stack.remove(stack.size() - 1);
        log.debug("[COMP-POP] flowRunId={} popped scope={}, remaining={}",
            ctx.identity().flowRunId(), poppedScope, stack.size());

        // 收集候选 handler pairs(活动 + handler ids,倒序,跳过已 dedup 的)
        List<HandlerPair> handlers = collectHandlerPairs(def, ctx);
        log.debug("[COMP-CAND] flowRunId={} candidates={}", ctx.identity().flowRunId(), handlers.size());

        for (HandlerPair pair : handlers) {
            // 先记 dedup,即使后续 handler 失败也不重跑(resume 透传 Suspend 时也跳过)
            String key = pair.activityId + "::" + pair.handlerNodeId;
            ctx.currentToken().getCompensatedHandlers().add(key);

            String startNodeId = resolveSubFlowStart(def, pair.handlerNodeId);
            if (startNodeId == null) {
                log.warn("[COMP-SKIP] handler {} has no outgoing target, skipping", pair.handlerNodeId);
                continue;
            }
            log.debug("[COMP-RUN] activityId={} handlerNodeId={} startNodeId={}",
                pair.activityId, pair.handlerNodeId, startNodeId);

            try {
                runHandlerSubFlow(def, startNodeId, ctx, reg, pair);
            } catch (AsyncNodeSuspendException ex) {
                // V5.31 P0 — handler sub-flow suspend 透传,outer traverse catch 写 WAITING_CALLBACK
                log.info("[COMP-SUSPEND] handler {} suspended, propagating", pair.handlerNodeId);
                throw ex;
            } catch (Exception ex) {
                log.warn("[COMP-FAIL] handler {} failed: {}, continuing with next",
                    pair.handlerNodeId, ex.getMessage());
                trace.failures.add(pair.handlerNodeId + ": " + ex.getMessage());
            }
        }
        return trace;
    }

    /** 倒序遍历 attachedCompensations(activity 倒序 + handler 倒序),跳过 dedup 已记录的 pair。 */
    private static List<HandlerPair> collectHandlerPairs(FlowDefinition def, FlowContext ctx) {
        List<HandlerPair> out = new ArrayList<>();
        Map<String, List<String>> attached = def.getAttachedCompensations();
        if (attached == null || attached.isEmpty()) return out;
        // activity 倒序(LinkedHashMap 倒序遍历用 new ArrayList 反转)
        List<String> activityIds = new ArrayList<>(attached.keySet());
        Collections.reverse(activityIds);
        for (String activityId : activityIds) {
            List<String> handlerIds = attached.get(activityId);
            List<String> reversed = new ArrayList<>(handlerIds);
            Collections.reverse(reversed);
            for (String handlerId : reversed) {
                String key = activityId + "::" + handlerId;
                if (ctx.currentToken().getCompensatedHandlers().contains(key)) continue;
                out.add(new HandlerPair(activityId, handlerId));
            }
        }
        return out;
    }

    /** 找 handler 节点 outgoing 第一个 target 节点 id;handler 自己没 outgoing 时返回 null。 */
    private static String resolveSubFlowStart(FlowDefinition def, String handlerNodeId) {
        FlowNode handler = def.getNode(handlerNodeId);
        if (handler == null || handler.getOutgoingIds().isEmpty()) return null;
        SequenceFlow first = def.getEdge(handler.getOutgoingIds().get(0));
        return first == null ? null : first.getTargetId();
    }

    /**
     * 跑 handler sub-flow(独立 ctx, vars 从 outer 克隆,compensationStack 留空,
     * sub-token 推到 worklist)。vars union-merge 回 outer ctx。
     */
    private static void runHandlerSubFlow(FlowDefinition def, String startNodeId,
                                          FlowContext outerCtx, NodeExecutorRegistry reg,
                                          HandlerPair pair) {
        // V5.39 A1 — handler sub-flow 在 subCtx 上跑,**BusinessVars 共享** outer
        // (handler 写"compensated"等累积 state 直接落到 outer,跨 handler 可见)。
        // 其它 handle(identity / rete / suspend)从 outer 委派,新 SuspendRegistry
        // (handler 内部不应污染 outer 的 bus 订阅)。
        //
        // subToken 用来推进 currentNodeId,handler 跑完 traverse 不会动 outer 的
        // rootToken。
        FlowContext subCtx = new FlowContext(
            outerCtx.identity(),
            outerCtx.vars(),       // 共享 BusinessVars(handler 写直接落到 outer)
            outerCtx.rete(),
            new SuspendRegistry());
        Token subToken = new Token("tok-comp-" + UUID.randomUUID());
        subToken.setCurrentNodeId(startNodeId);
        subCtx.activeTokens().add(subToken);
        subCtx.setCurrentToken(subToken);

        // stateMapper=null 走 stub(state 不持久化;handler sub-flow 失败就只 log,不影响 outer 状态)
        FlowNodeRunner runner = new FlowNodeRunner(reg, new ConditionEvaluator(), null);
        runner.traverse(def, subCtx, startNodeId);
        // vars 已经通过共享引用落到 outer.vars(),不用 merge。
    }

    /** handler(activity, handler_node) pair。 */
    private record HandlerPair(String activityId, String handlerNodeId) {}

    /**
     * V5.36 A6 — 跑指定 activity 的 compensation handlers(<b>不</b>从 stack pop scope)。
     *
     * <p>用于 {@link EndEventKind.Compensation} 变体:EndEvent 上声明
     * {@code ruleforge:attachedTo=actA} 时,只跑 actA 的 handlers,不动 stack。
     *
     * <p>语义跟 {@link #runHandlers} 类似,差别:
     * <ul>
     *   <li><b>不</b>从 compensationStack pop(EndEvent 已经是收尾节点,scope 由 ce 显式收)</li>
     *   <li>只跑 attachedCompensations[attachedTo] 列表里的 handlers(若没声明 → 警告 + 返回空 trace)</li>
     *   <li>dedup、failure 累积、suspend 透传 跟 runHandlers 一样</li>
     * </ul>
     */
    public static CompensationTrace runHandlersForActivity(FlowDefinition def, FlowContext ctx,
                                                           NodeExecutorRegistry reg,
                                                           String attachedTo) {
        CompensationTrace trace = new CompensationTrace();
        if (attachedTo == null || attachedTo.isBlank()) {
            log.warn("[COMP-A6] runHandlersForActivity called with blank attachedTo at flowRunId={}",
                ctx.identity().flowRunId());
            return trace;
        }
        Map<String, List<String>> attached = def.getAttachedCompensations();
        List<String> handlerIds = attached == null ? null : attached.get(attachedTo);
        if (handlerIds == null || handlerIds.isEmpty()) {
            log.warn("[COMP-A6] no handlers declared for activityId={} at flowRunId={}",
                attachedTo, ctx.identity().flowRunId());
            return trace;
        }
        // 倒序遍历(跟 runHandlers 行为一致)
        List<String> reversed = new ArrayList<>(handlerIds);
        Collections.reverse(reversed);
        for (String handlerId : reversed) {
            String key = attachedTo + "::" + handlerId;
            if (ctx.currentToken().getCompensatedHandlers().contains(key)) continue;
            ctx.currentToken().getCompensatedHandlers().add(key);

            String startNodeId = resolveSubFlowStart(def, handlerId);
            if (startNodeId == null) {
                log.warn("[COMP-A6-SKIP] handler {} has no outgoing target, skipping", handlerId);
                continue;
            }
            HandlerPair pair = new HandlerPair(attachedTo, handlerId);
            try {
                runHandlerSubFlow(def, startNodeId, ctx, reg, pair);
            } catch (AsyncNodeSuspendException ex) {
                log.info("[COMP-A6-SUSPEND] handler {} suspended, propagating", handlerId);
                throw ex;
            } catch (Exception ex) {
                log.warn("[COMP-A6-FAIL] handler {} failed: {}, continuing with next",
                    handlerId, ex.getMessage());
                trace.failures.add(handlerId + ": " + ex.getMessage());
            }
        }
        return trace;
    }

    /** 跑完返回的 trace(failures 列表)。 */
    public static final class CompensationTrace {
        public final List<String> failures = new ArrayList<>();
    }
}
