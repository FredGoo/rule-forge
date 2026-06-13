package com.ruleforge.ir.dsl;

/**
 * V5.42.3a — Drools 6 .dsl mapping scope 标签。
 *
 * <p>对应 .dsl 文件 {@code [when]} / {@code [then]} / {@code [*]} / {@code [any]} 之一。
 *
 * <p>{@link #WHEN} / {@link #THEN} 是基本段,跟 DRL when/then 段对应;
 * {@link #ANY} 是 "通用"段,任何 scope 查询都返回。
 *
 * @since 5.42
 */
public enum DslScope {
    /** lhs 段 / when 段 */
    WHEN,
    /** rhs 段 / then 段 */
    THEN,
    /** 通用 — 任何 scope 查询都命中 */
    ANY;

    /**
     * 大小写不敏感解析 — 接受 when / When / WHEN / then / Then / THEN / * / any / Any / ANY。
     *
     * @throws IllegalArgumentException 不在合法列表时
     */
    public static DslScope fromLabel(String label) {
        if (label == null) {
            throw new IllegalArgumentException("DSL scope label is null");
        }
        String trimmed = label.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("DSL scope label is empty");
        }
        switch (trimmed) {
            case "when": case "WHEN": case "When":
                return WHEN;
            case "then": case "THEN": case "Then":
                return THEN;
            case "*":
            case "any": case "ANY": case "Any":
                return ANY;
            default:
                throw new IllegalArgumentException(
                    "Unknown DSL scope label '" + label + "'. 合法值:[when] / [then] / [*] / [any]");
        }
    }
}
