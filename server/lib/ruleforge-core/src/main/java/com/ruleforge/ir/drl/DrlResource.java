package com.ruleforge.ir.drl;

/**
 * V5.42.4 — DrlResource:DrlResourceBuilder 的输入形态。
 *
 * <p>跟老 {@link com.ruleforge.builder.resource.RuleResource} 同形态(content + path),
 * 独立类是为 V5.42.4 不依赖老 XML Resource 链。V5.42.5 KnowledgeBuilder 入口会
 * 统一转换。
 *
 * <p>字段:
 * <ul>
 *   <li>{@code content} — .drl 文本</li>
 *   <li>{@code path} — 资源路径(供日志 / error reporting)</li>
 * </ul>
 *
 * @since 5.42
 */
public class DrlResource {

    private final String content;
    private final String path;

    public DrlResource(String content, String path) {
        this.content = content;
        this.path = path;
    }

    public String getContent() { return content; }
    public String getPath() { return path; }
}
