package com.ruleforge.ir.migration;

import com.ruleforge.ir.drl.DrlParseException;
import com.ruleforge.ir.dsl.DslEntry;
import com.ruleforge.ir.dsl.DslMappingSet;
import com.ruleforge.ir.dsl.DslScope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V5.42.6 — 一次性 .dsl + .dslrd → .drl 迁移工具。
 *
 * <p>.dslrd 文本里"自然语言"段(像 {@code Applicant is at least 21}) 跟
 * {@link DslMappingSet} 里某条 entry 的 key 匹配时,替换成对应 DRL template
 * (像 {@code Applicant(age >= 21)})。
 *
 * <p>实现:.dslrd 行内按 DRL 关键字(when / then / end / rule / extends)
 * 切成段,段之间的"natural language"段按当前 scope(WHEN / THEN / OUTSIDE)
 * 查 {@link DslMappingSet} 替换。
 *
 * <p>第一版限制:
 * <ul>
 *   <li>natural language 段必须**完整**匹配某 entry.key(无 fuzzy / partial)</li>
 *   <li>natural language 段出现在 when 后面 → 查 WHEN + ANY scope</li>
 *   <li>natural language 段出现在 then 后面 → 查 THEN + ANY scope</li>
 *   <li>未匹配 → DrlParseException(防止 .dsl 漏 mapping 静默丢)</li>
 *   <li>placeholder 替换:key 里 {@code {name}} → 行里 capture 的 token;
 *       template 里 {@code {name}} → 对应 capture(按 name 匹配第一个同名 placeholder,
 *       Drools 6 实际 .dsl 习惯 key 跟 template placeholder 名一致)</li>
 * </ul>
 *
 * @since 5.42
 */
public class DslToDrlConverter {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");

    /**
     * 主入口 — 输入 .dslrd 文本 + DslMappingSet,产出 V5.42.6 标识 + DRL 文本。
     *
     * @throws DrlParseException null / 空 / 未匹配 mapping / 解析失败时
     */
    public String convert(String dslrd, DslMappingSet mapping) {
        if (dslrd == null || dslrd.trim().isEmpty()) {
            throw new DrlParseException(".dslrd 文本不能为 null / 空");
        }
        if (mapping == null) {
            throw new DrlParseException("DslMappingSet 不能为 null");
        }
        StringBuilder out = new StringBuilder();
        out.append("// V5.42.6 — 一次性 .dsl + .dslrd → .drl 迁移工具\n");
        out.append("// 注:natural language 段按 DslMappingSet 替换成 DRL template。\n");
        out.append("\n");
        // 行内 token 切分:DRL 关键字(rule / extends / when / then / end)作分隔符
        // 行内处理:找 when / then / end 关键字,中间内容作 natural language 段
        String[] lines = dslrd.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                out.append(line).append("\n");
                continue;
            }
            out.append(processLine(line, mapping)).append("\n");
        }
        return out.toString();
    }

    /**
     * 处理单行:.dslrd 真实情况是单行多段(`rule "X" when A then B end`)和
     * 多行每段都有 natural language 都支持。
     * 策略:行内按 `when` / `then` / `end` 切分 → 切出来的 natural language
     * 段用 {@code replaceInLine} 替换。
     */
    private static String processLine(String line, DslMappingSet mapping) {
        // 找 DRL 关键字位置(when / then / end)— 保留这些 token,中间内容作 NL 段
        // 简化为:用 regex 找 4 个段位置,不在 OUTSIDE / DRL 关键字之间的内容都作 NL
        // 第一版:行内 substring 找 when/then/end,切出段
        List<int[]> matches = new ArrayList<>(); // [start, end, type]
        findKeyword(line, "when", matches, 1);
        findKeyword(line, "then", matches, 2);
        findKeyword(line, "end", matches, 3);
        if (matches.isEmpty()) {
            // 整行是 natural language(在 rule 行 / extends 行之外的预 when 内容)
            return line;
        }
        matches.sort(Comparator.comparingInt((int[] a) -> a[0]));
        StringBuilder sb = new StringBuilder();
        int cursor = 0;
        for (int k = 0; k < matches.size(); k++) {
            int[] m = matches.get(k);
            int kwType = m[2];
            // 上一段到当前关键字之间的内容是 NL
            if (m[0] > cursor) {
                String nl = line.substring(cursor, m[0]);
                DslScope scope = scopeForContext(matches, k);
                sb.append(replaceInLine(nl, mapping, scope));
            }
            // 关键字原样保留
            sb.append(line, m[0], m[1]);
            cursor = m[1];
        }
        // 末尾段
        if (cursor < line.length()) {
            String nl = line.substring(cursor);
            DslScope scope = scopeForLast(matches);
            sb.append(replaceInLine(nl, mapping, scope));
        }
        return sb.toString();
    }

    private static void findKeyword(String line, String kw, List<int[]> out, int type) {
        // 整词匹配(以 \b 为界)— 但 DRL 关键字后面空格分隔
        Pattern p = Pattern.compile("(^|\\s)" + Pattern.quote(kw) + "($|\\s)");
        Matcher m = p.matcher(line);
        while (m.find()) {
            int start = m.start() + (m.group(1) == null || m.group(1).isEmpty() ? 0 : m.group(1).length());
            int end = start + kw.length();
            out.add(new int[]{start, end, type});
        }
    }

    /** 给定位置 k 的关键字,判断它前面 NL 段属于什么 scope */
    private static DslScope scopeForContext(List<int[]> matches, int k) {
        // 找上一个 when / then — 当前 NL 段在该关键字**之后**归哪个 scope
        int kwType = matches.get(k)[2];
        if (kwType == 2) {
            // 当前关键字是 then → 前面 NL 段是 WHEN
            return DslScope.WHEN;
        }
        if (kwType == 3) {
            // 当前关键字是 end → 前面 NL 段是 THEN
            return DslScope.THEN;
        }
        if (kwType == 1) {
            // 当前关键字是 when → 前面 NL 段是 rule 头部(name 之类),不查 mapping
            return null;
        }
        return null;
    }

    /** 行末段 scope(最后一个关键字之后) */
    private static DslScope scopeForLast(List<int[]> matches) {
        if (matches.isEmpty()) return null;
        int lastType = matches.get(matches.size() - 1)[2];
        if (lastType == 2) return DslScope.THEN; // 末 then 后到 end 前
        if (lastType == 1) return DslScope.WHEN; // 末 when 后到 then 前
        return null;
    }

    /**
     * 替换 NL 段里的 natural language → DRL template。
     */
    private static String replaceInLine(String segment, DslMappingSet mapping, DslScope scope) {
        if (scope == null) {
            return segment; // rule name / extends / end 之类,不动
        }
        String trimmed = segment.trim();
        if (trimmed.isEmpty()) return segment;
        List<DslEntry> sorted = new ArrayList<>(mapping.getAllEntries());
        sorted.sort(Comparator.comparingInt((DslEntry e) -> e.getKey().length()).reversed());
        // 循环替换 — 一行可能有多个 mapping(像 "X is 1 and Y is 2" 配两个 entry)
        String current = trimmed;
        boolean anyMatched = false;
        while (true) {
            boolean matchedThisRound = false;
            for (DslEntry e : sorted) {
                DslEntry found = mapping.lookup(scope, e.getKey());
                if (found == null) {
                    found = mapping.lookup(DslScope.ANY, e.getKey());
                }
                if (found == null) continue;
                String keyRegex = buildKeyRegex(found.getKey());
                Matcher m = Pattern.compile(keyRegex).matcher(current);
                if (m.find()) {
                    Map<String, Integer> placeholderIdx = new LinkedHashMap<>();
                    Matcher ph = PLACEHOLDER.matcher(found.getKey());
                    int idx = 1;
                    while (ph.find()) {
                        placeholderIdx.putIfAbsent(ph.group(1), idx++);
                    }
                    String replaced = fillTemplate(found.getTemplate(), m, placeholderIdx);
                    current = m.replaceAll(Matcher.quoteReplacement(replaced));
                    matchedThisRound = true;
                    anyMatched = true;
                    break; // 重头开始(避免长 key 跟短 key 嵌套误匹配)
                }
            }
            if (!matchedThisRound) break;
        }
        if (!anyMatched) {
            throw new DrlParseException(
                ".dslrd 里有未匹配 mapping 的 natural language 段:'" + trimmed +
                "'(scope=" + scope + ")。请在 .dsl mapping 里加 [when] / [then] / [*] 段。");
        }
        return current;
    }

    /** key 字符串里 {name} → (\S+) capture group,其他字符 quote 化 */
    private static String buildKeyRegex(String key) {
        Matcher m = PLACEHOLDER.matcher(key);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(Pattern.quote(key.substring(last, m.start())));
            sb.append("(\\S+)");
            last = m.end();
        }
        sb.append(Pattern.quote(key.substring(last)));
        return sb.toString();
    }

    /** template 字符串里 {name} → 对应 capture,其他字符原样 */
    private static String fillTemplate(String template, Matcher m, Map<String, Integer> placeholderIdx) {
        Matcher ph = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (ph.find()) {
            sb.append(template, last, ph.start());
            String name = ph.group(1);
            Integer idx = placeholderIdx.get(name);
            if (idx == null) {
                sb.append(ph.group(0));
            } else {
                String captured = m.group(idx);
                sb.append(captured == null ? ph.group(0) : captured);
            }
            last = ph.end();
        }
        sb.append(template.substring(last));
        return sb.toString();
    }
}
