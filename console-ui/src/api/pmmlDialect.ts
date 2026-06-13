/**
 * V5.41.6 — PMML IR 来源方言 enum(console-ui 层,跟后端 ScorecardDefinition / DecisionTree
 * 字段镜像)。跟 V5.40.6 {@link ./decisionTableDialect} 同款设计。
 *
 * <p>目的:让前端组件能根据 dialect 决定:
 * <ul>
 *   <li>显示的 schema 类型(老 RuleForge XML vs PMML 4.4)</li>
 *   <li>编辑器顶部的 "Source format" 指示器(只读,V5.41+ 单向:XML → PMML, 不回退)</li>
 *   <li>导出按钮的可用性(PMML 资源只能用 "Export as XML" 备份)</li>
 * </ul>
 *
 * <p>Scorecard + DecisionTree 共用此 enum(都走 PMML 4.4 IR)。值跟后端 Java
 * {@code Boolean useReasonCodes != null || String baselineMethod != null} 检测逻辑
 * 一致(详见 {@link com.ruleforge.ir.pmml.PmmlResourceDispatcher#dispatch})。
 *
 * <p><b>V5.41.6 限制</b>:console-ui 编辑器当前**只展示** dialect,不解析 PMML 内容做完整
 * 编辑。PMML 完整编辑支持(双向 round-trip UI)是 V5.50+ 单独 PR(本 task 不含)。
 */
export type PmmlDialect = 'RULEFORGE_NATIVE' | 'PMML';

/**
 * Default dialect for V5.40-and-earlier scorecard/decision-tree (no PMML fields set).
 * 老 .xml scorecard/tree 反序列化后 4 个 PMML 字段全 null,前端统一显示为 RULEFORGE_NATIVE。
 */
export const DEFAULT_DIALECT: PmmlDialect = 'RULEFORGE_NATIVE';

/**
 * V5.41.6 — Detect dialect from file extension (.pmml → PMML, .xml → RULEFORGE_NATIVE).
 * 这是 console-ui 一侧 "Source format" 指示器显示逻辑的源头(后端 KnowledgeBuilder
 * 在 dispatch 时会按真实文件内容覆盖,前端这个推断只在后端响应未就绪时用)。
 */
export function detectPmmlDialectFromFilePath(filePath: string): PmmlDialect {
    if (!filePath) return DEFAULT_DIALECT;
    const lower = filePath.toLowerCase();
    if (lower.endsWith('.pmml')) {
        return 'PMML';
    }
    return DEFAULT_DIALECT;
}

/**
 * V5.41.6 — Human-readable label for each dialect(给 console-ui 顶部 "Source format" 指示器用)。
 */
export function pmmlDialectLabel(dialect: PmmlDialect | null | undefined): string {
    if (!dialect) return 'RuleForge XML (legacy)';
    switch (dialect) {
        case 'RULEFORGE_NATIVE':
            return 'RuleForge XML (legacy)';
        case 'PMML':
            return 'PMML 4.4 (V5.41+)';
        default:
            // exhaustiveness check — should never hit
            return 'Unknown';
    }
}
