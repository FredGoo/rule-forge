package com.ruleforge.ir.migration;

/**
 * V5.41.5 — 老 .xml → 新格式(.pmml / .dmn / .drl)一次性迁移 orchestrator。
 *
 * <p>用途:给 V5.41(以及后续 V5.42)切格式 PR 配套的"数据迁移"工具。一次性跑在
 * console-app 启动时(可配置 {@code ruleforge.legacy-xml.migrate=true}):
 * <ol>
 *   <li>扫 Git 仓库里所有老 .xml</li>
 *   <li>按根元素分派:
 *       <ul>
 *         <li>{@code <decision-table>} → XmlToDmnTableConverter(V5.40.5)</li>
 *         <li>{@code <scorecard>} → XmlToPmmlScorecardConverter(V5.41.5)</li>
 *         <li>{@code <decision-tree>} → XmlToPmmlTreeConverter(V5.41.5)</li>
 *         <li>{@code <rule>} / {@code <rule-set>} / {@code <ruleflow>} → V5.42 DRL(留 TODO)</li>
 *       </ul>
 *   </li>
 *   <li>写 .dmn / .pmml / .drl 到 Git 仓库同目录,新文件跟老 .xml 同 basename 不同后缀</li>
 *   <li>成功转换后删原 .xml(可选,看 caller 决定)</li>
 * </ol>
 *
 * <p><b>V5.41.5 scope</b>:本类只声明入口 + 分派骨架,具体"扫 Git 仓库 + 写文件"是 console-app
 * 启动钩子的事(需要 Spring app context + 仓库 API,本类在 ruleforge-core 里不能依赖)。本类
 * 暴露 {@link #migrate(String)} 单条 .xml 字符串入口,让 console-app / 运维脚本可以串行
 * 调用,不用直接 import 两个具体 converter。
 *
 * <p>失败时抛 {@link XmlMigrationException},由调用方决定 fallback 策略(保留原 .xml 不动)。
 *
 * @since 5.41
 */
public class LegacyXmlMigrator {

    private final XmlToDmnTableConverter tableConverter = new XmlToDmnTableConverter();
    private final XmlToPmmlScorecardConverter scorecardConverter = new XmlToPmmlScorecardConverter();
    private final XmlToPmmlTreeConverter treeConverter = new XmlToPmmlTreeConverter();

    /**
     * Given 老 .xml 字符串,When migrate,Then 产生新格式 XML 字符串(分派到对应 converter)。
     *
     * <p>返回 {@link MigrationResult} 包含输出字符串 + 输出格式("dmn" / "pmml"),让 caller
     * 知道写文件用哪个后缀。
     */
    public MigrationResult migrate(String xmlContent) {
        if (xmlContent == null || xmlContent.isEmpty()) {
            throw new XmlMigrationException("XML content must not be empty");
        }
        String rootName = peekRootElement(xmlContent);
        if (rootName == null) {
            throw new XmlMigrationException("XML has no root element");
        }
        switch (rootName) {
            case "decision-table":
                return new MigrationResult(tableConverter.convert(xmlContent), "dmn");
            case "scorecard":
                return new MigrationResult(scorecardConverter.convert(xmlContent), "pmml");
            case "decision-tree":
                return new MigrationResult(treeConverter.convert(xmlContent), "pmml");
            case "rule":
            case "rule-set":
            case "ruleflow":
                // V5.42 DRL 转换器尚未实现,留 TODO
                throw new XmlMigrationException(
                    "Migration of <" + rootName + "> to DRL is V5.42 scope; not yet implemented");
            default:
                throw new XmlMigrationException(
                    "Unrecognized legacy .xml root element: <" + rootName + ">");
        }
    }

    private static String peekRootElement(String xml) {
        // 简单 string scan,避免每次 migrate 都走完整 XML parse;命错时由具体 converter 重 parse
        // 找第一个 ">" 之前的 "<xxx" 拿 root name
        int firstLt = xml.indexOf('<');
        if (firstLt < 0) {
            return null;
        }
        int firstGt = xml.indexOf('>', firstLt);
        if (firstGt < 0) {
            return null;
        }
        String tagStart = xml.substring(firstLt + 1, firstGt).trim();
        // 跳过 <?xml ... ?> 处理指令
        if (tagStart.startsWith("?")) {
            int nextLt = xml.indexOf('<', firstGt);
            if (nextLt < 0) {
                return null;
            }
            int nextGt = xml.indexOf('>', nextLt);
            if (nextGt < 0) {
                return null;
            }
            tagStart = xml.substring(nextLt + 1, nextGt).trim();
            // 更新 firstLt/firstGt 到新位置,后续 <rule-config> 解析靠这些
            firstLt = nextLt;
            firstGt = nextGt;
        }
        // 去掉 attribute 段
        int sp = tagStart.indexOf(' ');
        if (sp > 0) {
            tagStart = tagStart.substring(0, sp);
        }
        int slash = tagStart.indexOf('/');
        if (slash > 0) {
            tagStart = tagStart.substring(0, slash);
        }
        // 已知包裹:老 .xml 几乎都是 <rule-config> 包一层,实际 model 在子元素。
        // 命中 <rule-config> 就递归找第一个子 element
        if ("rule-config".equals(tagStart)) {
            int scanFrom = firstGt + 1;
            int nextLt = xml.indexOf('<', scanFrom);
            while (nextLt >= 0) {
                // 跳过 <? ...?> 处理指令 + <!--  --> 注释
                if (nextLt + 1 < xml.length() && (xml.charAt(nextLt + 1) == '?'
                    || (xml.charAt(nextLt + 1) == '!' && nextLt + 3 < xml.length()
                        && xml.charAt(nextLt + 2) == '-' && xml.charAt(nextLt + 3) == '-'))) {
                    int end = xml.indexOf('>', nextLt);
                    if (end < 0) {
                        return null;
                    }
                    if (xml.charAt(nextLt + 1) == '!') {
                        int commentEnd = xml.indexOf("-->", end);
                        if (commentEnd < 0) {
                            return null;
                        }
                        nextLt = xml.indexOf('<', commentEnd + 3);
                    } else {
                        nextLt = xml.indexOf('<', end);
                    }
                    continue;
                }
                int nextGt = xml.indexOf('>', nextLt);
                if (nextGt < 0) {
                    return null;
                }
                String childTag = xml.substring(nextLt + 1, nextGt).trim();
                int spIdx = childTag.indexOf(' ');
                if (spIdx > 0) {
                    childTag = childTag.substring(0, spIdx);
                }
                int slIdx = childTag.indexOf('/');
                if (slIdx > 0) {
                    childTag = childTag.substring(0, slIdx);
                }
                return childTag;
            }
            return null;
        }
        return tagStart;
    }

    /**
     * V5.41.5 — 迁移结果 record:输出字符串 + 目标格式("dmn" / "pmml" / "drl")。
     */
    public static final class MigrationResult {
        private final String content;
        private final String targetFormat;

        public MigrationResult(String content, String targetFormat) {
            this.content = content;
            this.targetFormat = targetFormat;
        }

        public String getContent() {
            return content;
        }

        public String getTargetFormat() {
            return targetFormat;
        }
    }
}
