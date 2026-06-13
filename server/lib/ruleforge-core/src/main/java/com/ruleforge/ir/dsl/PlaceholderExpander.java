package com.ruleforge.ir.dsl;

import com.ruleforge.ir.drl.DrlParseException;

import java.util.regex.Matcher;

/**
 * V5.42.3a — {@code ${varName}} 占位符展开器。
 *
 * <p>Drools 6 .dslrd 文件用 {@code ${varName}} 标记 "运行时变量引用";
 * 展开器把 {@code ${X}} 替换成 {@code X}(去掉包装,保留变量名)。
 *
 * <p>为什么简单粗暴:展开器**不**做变量解析(从 caller 传入 map 查值),
 * 只做"语法包装"工作 — 真值替换在 DrlDeserializer / KnowledgeBuilder 阶段,
 * 跟老 RuleForge Rule model 解耦。
 *
 * <p>限制:
 * <ul>
 *   <li>占位符名语法: {@code [a-zA-Z_][a-zA-Z0-9_]*}(同 DslParser)</li>
 *   <li>{@code ${} } / {@code ${1a}} / 不闭合的 {@code ${} — DrlParseException</li>
 *   <li>字符串字面量内 {@code ${...}} 也会被替换 — V5.42.3a caller 自己保证
 *       不在字符串字面量内用 {@code ${}}(Drools 6 原生行为一致)</li>
 * </ul>
 *
 * @since 5.42
 */
public class PlaceholderExpander {

    private static final java.util.regex.Pattern PLACEHOLDER_NAME =
        java.util.regex.Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)}");

    /**
     * 把 {@code ${X}} 替换成 {@code X}(去掉包装)。其他文本原样保留。
     *
     * @throws DrlParseException 占位符语法不合法时
     */
    public String expand(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        // 先用 regex 抓所有合法占位符,再扫一次"剩余"段(找 ${ 不闭合或非合规)
        Matcher m = PLACEHOLDER_NAME.matcher(source);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            // 复制 [last, start) 到 out
            out.append(source, last, start);
            // 替换 ${X} → X
            out.append(m.group(1));
            last = end;
        }
        out.append(source, last, source.length());
        // 校验:剩余没有遗漏的 ${ — 任意 ${ 必须成功配 }
        validateNoUnclosed(source, out.toString());
        return out.toString();
    }

    /**
     * 检查原文本里所有 {@code ${} 都已处理。如果 output 里出现 {@code ${},
     * 说明源文本有未闭合占位符或非合规占位符。
     *
     * <p>这个简单 check 覆盖:不闭合 {@code ${} / 空 ${} / 数字开头 ${1a}。
     * 合法的 ${X} 已被 regex 替换,output 不会再有 ${。
     */
    private void validateNoUnclosed(String source, String output) {
        if (output.indexOf("${") >= 0) {
            // 找出第一个未处理的 ${ — 报行号
            int idx = source.indexOf("${");
            int line = 1;
            int col = 0;
            for (int i = 0; i < idx && i < source.length(); i++) {
                if (source.charAt(i) == '\n') {
                    line++;
                    col = 0;
                } else {
                    col++;
                }
            }
            throw new DrlParseException(
                "DSL ${} 占位符语法不合法,位置 " + idx + " (line " + line
                + " col " + col + ")。V5.42.3a 要求 ${name} 形式,name 满足 [a-zA-Z_][a-zA-Z0-9_]*",
                line, col);
        }
    }
}
