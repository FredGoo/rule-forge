package com.ruleforge.ir.dsl;

import com.ruleforge.ir.drl.DrlParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V5.42.3a — Drools 6 .dsl mapping 文件解析器。
 *
 * <p>规则:
 * <ul>
 *   <li>每行: {@code [scope]key=template}</li>
 *   <li>{@code [scope]} 合法值: {@code [when]} / {@code [then]} / {@code [*]} / {@code [any]}
 *       (大小写不敏感)— 缺失 / 空 / 非法 → DrlParseException</li>
 *   <li>{@code key} / {@code template} 都可含 {@code {placeholder}},placeholder 名
 *       必须是 {@code [a-zA-Z_][a-zA-Z0-9_]*}(V5.42.3a 限定 — Drools 6 实际更宽,
 *       严格语法够用,留给 V5.42.5 follow-up 放宽)</li>
 *   <li>空行 / 纯空白 / 以 {@code #} 开头行跳过</li>
 *   <li>缺 {@code =} → DrlParseException 带行号</li>
 * </ul>
 *
 * <p>不用 ANTLR — 格式简单(行级),手写解析更清楚、更易 debug。V5.42.5 .dsl 跨行
 * 表达式再考虑升 grammar。
 *
 * @since 5.42
 */
public class DslParser {

    /** placeholder 名字:字母/下划线开头,后续字母/数字/下划线 */
    private static final Pattern PLACEHOLDER_NAME = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)}");

    /** 行级 entry: [scope] key = template(空白容忍)— 拆 scope 用 */
    private static final Pattern SCOPE_PREFIX = Pattern.compile("^\\s*\\[([^\\]]*)]\\s*(.*)$");

    public List<DslEntry> parse(String source) {
        List<DslEntry> result = new ArrayList<>();
        DslMappingSet set = parseAsSet(source);
        result.addAll(set.getAllEntries());
        return result;
    }

    public DslMappingSet parseAsSet(String source) {
        DslMappingSet set = new DslMappingSet();
        if (source == null || source.isEmpty()) {
            return set;
        }
        String[] lines = source.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String raw = lines[i];
            String line = raw == null ? "" : raw;
            // 跳过空行 / 纯注释
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            // 拆 scope prefix
            Matcher m = SCOPE_PREFIX.matcher(line);
            if (!m.matches()) {
                throw new DrlParseException(
                    "DSL 行缺少 [scope] 前缀: '" + line + "'。"
                    + "V5.42.3a 要求每行以 [when] / [then] / [*] / [any] 之一开头。",
                    lineNo, 0);
            }
            String scopeLabel = m.group(1);
            String rest = m.group(2);
            DslScope scope;
            try {
                scope = DslScope.fromLabel(scopeLabel);
            } catch (IllegalArgumentException e) {
                throw new DrlParseException(
                    "DSL scope 标签非法 '" + scopeLabel + "':"
                    + " 合法值 [when] / [then] / [*] / [any]。", lineNo, 0);
            }
            // 拆 key=template — split on **first** '=' only,key 本身可含 '='(Drools 6 兼容)
            int eqIdx = rest.indexOf('=');
            if (eqIdx < 0) {
                throw new DrlParseException(
                    "DSL 缺少 '=' 分隔符(key=template):'" + line + "'", lineNo, 0);
            }
            String key = rest.substring(0, eqIdx).trim();
            String template = rest.substring(eqIdx + 1).trim();
            // 校验 key 内 placeholder 名合规
            validatePlaceholders(key, lineNo);
            // template 内 placeholder 也校验(防止 V5.42.3b .dslrd 误用)
            validatePlaceholders(template, lineNo);
            if (key.isEmpty()) {
                throw new DrlParseException("DSL key 不能为空:'" + line + "'", lineNo, 0);
            }
            set.add(new DslEntry(scope, key, template));
        }
        return set;
    }

    /**
     * 校验字符串内所有 {@code {placeholder}} 名字合规。
     * <p>语法: {@code [a-zA-Z_][a-zA-Z0-9_]*}。
     * <p>非合规:{@code {}} / {@code {1a}} / {@code {a-b}} / {@code {a b}} 等。
     */
    private void validatePlaceholders(String text, int lineNo) {
        // 找出所有 {X} 形式(简单括号匹配)— 比 regex 严格
        int idx = 0;
        while (idx < text.length()) {
            int open = text.indexOf('{', idx);
            if (open < 0) break;
            int close = text.indexOf('}', open + 1);
            if (close < 0) {
                throw new DrlParseException(
                    "DSL 占位符不闭合:'{' 出现但找不到 '}' 位置 " + open + ": " + text,
                    lineNo, open);
            }
            String name = text.substring(open + 1, close);
            if (!PLACEHOLDER_NAME.matcher("{" + name + "}").matches()) {
                throw new DrlParseException(
                    "DSL 占位符名非法 '" + name + "':V5.42.3a 要求 [a-zA-Z_][a-zA-Z0-9_]*,"
                    + "位置 " + open + ": " + text, lineNo, open);
            }
            idx = close + 1;
        }
    }
}
