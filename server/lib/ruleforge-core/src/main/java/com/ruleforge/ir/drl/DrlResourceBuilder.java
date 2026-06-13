package com.ruleforge.ir.drl;

import com.ruleforge.model.rule.Rule;

import java.util.List;

/**
 * V5.42.4 — DrlResourceBuilder:DrlResource → List<Rule>。
 *
 * <p>API 形态跟老 {@link com.ruleforge.builder.resource.ResourceBuilder ResourceBuilder<T>}
 * 不一样 — 老的是 Element-based(DOM XML 入口),本类直接接 .drl 文本。
 * V5.42.5 KnowledgeBuilder 入口负责把老 Resource 链接入。
 *
 * <p>{@link #support(DrlResource)} 用 path 后缀判断:
 * <ul>
 *   <li>{@code .drl} / {@code .DRL} → true</li>
 *   <li>其他 → false(让其他 builder 接管)</li>
 * </ul>
 *
 * <p>{@link #build(DrlResource)} 走 {@link DrlDeserializer#parseDrl(String, DatatypeResolver)}。
 *
 * @since 5.42
 */
public class DrlResourceBuilder {

    private final DatatypeResolver resolver;

    public DrlResourceBuilder(DatatypeResolver resolver) {
        this.resolver = resolver;
    }

    public List<Rule> build(DrlResource resource) {
        return DrlDeserializer.parseDrl(resource.getContent(), resolver);
    }

    /**
     * 是否支持这个 resource — V5.42.4 决定:path 后缀是 .drl / .DRL 就支持。
     * 不知道 resource 的话(没 path)也支持 — caller 自己控制。
     */
    public boolean support(DrlResource resource) {
        String path = resource.getPath();
        if (path == null) {
            return true;
        }
        String lower = path.toLowerCase();
        return lower.endsWith(".drl");
    }
}
