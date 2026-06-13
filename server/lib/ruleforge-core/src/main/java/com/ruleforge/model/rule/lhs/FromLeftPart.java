package com.ruleforge.model.rule.lhs;

import com.ruleforge.runtime.rete.EvaluationContext;

import java.util.List;

/**
 * V5.50.1 — DRL <code>from</code> 子句的 LHS LeftPart。
 *
 * <p>DRL 形式:
 * <pre>
 *   $a : Applicant(...) from $stream
 *   $a : Applicant(...) from collect(...)
 *   $a : Applicant(...) from accumulate(...)
 * </pre>
 *
 * <p>跟 {@link CollectLeftPart} 形态对齐:本类只产 DTO shape(extends
 * {@link AbstractLeftPart}),<code>evaluate</code> 实现留 V5.50.3+ 把
 * <code>collect</code> / <code>accumulate</code> 调用方切到本类时补。
 * V5.50.1 阶段 grammar 接受新 DRL,deserializer 把 from 子句识别成本类挂
 * Rule.lhs.criterion 链即可。
 *
 * @since 5.50.1
 */
public class FromLeftPart extends AbstractLeftPart {
    private String property;
    /** "stream" / "collect" / "accumulate" — V5.50.1 不强制语义,留字符串辨识 */
    private String fromSource;

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public String getFromSource() {
        return fromSource;
    }

    public void setFromSource(String fromSource) {
        this.fromSource = fromSource;
    }

    /**
     * V5.50.1:evaluate 暂返回 0 / null。V5.50.3 收尾时按 fromSource 走
     * 不同 evaluator(stream → direct, collect → CollectLeftPart 委托,
     * accumulate → CommonFunctionLeftPart 委托)。
     *
     * <p>跟 {@link CollectLeftPart#evaluate(EvaluationContext, Object, List)}
     * 同形态(不挂 LeftPart 接口 — LeftPart 只约定 getId(),evaluate 是约定俗成的
     * duck-typed 方法,各 LeftPart 子类按需 override)。
     */
    public Object evaluate(EvaluationContext context, Object obj, List<Object> allMatchedObjects) {
        return 0;
    }

    @Override
    public String getId() {
        if (id == null) {
            id = "from(" + variableCategory + "." + variableLabel + ","
                + (fromSource == null ? "?" : fromSource) + ")";
        }
        return id;
    }
}
