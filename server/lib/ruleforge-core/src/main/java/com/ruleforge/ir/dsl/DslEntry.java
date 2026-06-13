package com.ruleforge.ir.dsl;

/**
 * V5.42.3a — Drools 6 .dsl mapping 单条 entry。
 *
 * <p>形态: {@code [scope]key template=template}。
 *
 * <p>key 是用户写的 "自然语言"形式,带 {@code {placeholder}};template 是
 * 展开后的 DRL 片段,带相同 placeholder 实际引用。
 *
 * <p>V5.42.3a 不约束 placeholder 集(key 跟 template 必须共享同一 placeholder
 * 名集合 — Drools 6 强制)— 在 DslMappingSet.lookup 时 caller 自己保证。
 *
 * @since 5.42
 */
public class DslEntry {

    private final DslScope scope;
    private final String key;
    private final String template;

    public DslEntry(DslScope scope, String key, String template) {
        this.scope = scope;
        this.key = key;
        this.template = template;
    }

    public DslScope getScope() { return scope; }
    public String getKey() { return key; }
    public String getTemplate() { return template; }

    @Override
    public String toString() {
        return "DslEntry[" + scope + "|" + key + "=" + template + "]";
    }
}
