package com.ruleforge.builder.table;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.table.Column;
import com.ruleforge.model.table.ColumnType;
import com.ruleforge.model.table.Row;
import com.ruleforge.model.table.ScriptCell;
import com.ruleforge.model.table.ScriptDecisionTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V5.44.2 — ScriptDecisionTable → DRL 4 转换器,补回 V5.43.5 行为降级。
 *
 * <p>V5.43.5 删 {@code CellScriptDSLBuilder} / {@code ScriptDecisionTableRulesBuilder}
 * (老 .ul DSL 链路)后,ScriptDecisionTable 走 KnowledgeBuilder 占位注释,
 * 静默 0 rule。V5.44.2 实现 ScriptDecisionTable → DRL 4 字符串转换,再走 V5.42
 * 既有 DrlResourceBuilder 路径 → Rule 列表,行为补回。
 *
 * <p>转换模型(简化版,跟老 DSL 链的"每行 = 1 rule"语义对齐):
 * <pre>
 *   ScriptDecisionTable 1 张表 → 多个 DRL rule(每 row = 1 rule)
 *   rule "&lt;tableName&gt;_r&lt;rowNum&gt;"
 *       when
 *           eval(&lt;cell1_lhs_script&gt;) and
 *           eval(&lt;cell2_lhs_script&gt;) and ...
 *       then
 *           &lt;cell_n_rhs_script&gt;;
 *           &lt;cell_m_rhs_script&gt;;
 *       end
 * </pre>
 *
 * <p>Cell 的 column.type 决定它进 LHS 还是 RHS:
 * <ul>
 *   <li>{@code Criteria} → LHS,包成 {@code eval(script)} 段</li>
 *   <li>{@code Assignment} / {@code ConsolePrint} / {@code ExecuteMethod} → RHS,
 *       script 原样作 action</li>
 * </ul>
 *
 * <p><b>不在 V5.44.2 范围</b>(留 V5.44.3+ 单独 PR):
 * <ul>
 *   <li>Row 跨行合并(rowspan > 1)— V5.44.2 简化:每 row 独立成 rule,rowspan 字段读取但不进 emit</li>
 *   <li>Library 引用自动拼成 DRL 顶层 import 段 — V5.44.3 grammar 加 import 后再补</li>
 *   <li>Cell script 内嵌语法检查 — V5.44.2 只把 script 当字符串嵌入,DrlDeserializer 端会报 syntax 错</li>
 * </ul>
 *
 * @since 5.44
 */
public class ScriptDecisionTableToDrlConverter {

    /**
     * 顶层入口:ScriptDecisionTable → DRL 4 文本。KnowledgeBuilder 拿到 DRL 文本
     * 后走 DrlResourceBuilder → List&lt;Rule&gt;。
     */
    public String convert(ScriptDecisionTable table) {
        if (table == null) {
            throw new RuleException("ScriptDecisionTable 不能为 null");
        }
        StringBuilder drl = new StringBuilder();
        appendHeader(drl, table);
        appendRules(drl, table);
        return drl.toString();
    }

    private void appendHeader(StringBuilder drl, ScriptDecisionTable table) {
        // V5.44.2 — DRL 4 grammar 要求顶层必须有 package 段(grammar rule `compilationUnit`
        // 至少 1 个 entry,package / unit / import 都行;package 走空内容合法)。
        // ScriptDecisionTable 走 unit 形式即可,但 V5.44.2 简化:加个 placeholder 注释
        // 标记表名,DrlDeserializer 端不解析 tableName metadata(从 path 推断)。
        // V5.44.3 import 段进来后,library 信息挪到 import。
        drl.append("// V5.44.2 — auto-generated from ScriptDecisionTable\n");
    }

    private void appendRules(StringBuilder drl, ScriptDecisionTable table) {
        // 按 row 分组 cell
        Map<Integer, List<ScriptCell>> byRow = groupCellsByRow(table);
        // 按 row num 升序产出
        List<Integer> rowNums = new ArrayList<>(byRow.keySet());
        rowNums.sort(Integer::compareTo);

        for (Integer rowNum : rowNums) {
            List<ScriptCell> cells = byRow.get(rowNum);
            appendRule(drl, table, rowNum, cells);
        }
    }

    private void appendRule(StringBuilder drl, ScriptDecisionTable table, int rowNum, List<ScriptCell> cells) {
        if (cells.isEmpty()) {
            return;
        }
        String ruleName = buildRuleName(table, rowNum);
        drl.append("rule \"").append(ruleName).append("\" when\n");

        // LHS:收集 column.type == Criteria 的 cell
        List<String> lhsExprs = new ArrayList<>();
        // RHS:收集 column.type != Criteria 的 cell
        List<String> rhsStmts = new ArrayList<>();
        for (ScriptCell cell : cells) {
            Column col = findColumn(table, cell.getCol());
            String script = cell.getScript();
            if (script == null || script.trim().isEmpty()) {
                // 空 cell 跳过
                continue;
            }
            if (col != null && col.getType() == ColumnType.Criteria) {
                lhsExprs.add("    eval(" + script + ")");
            } else {
                rhsStmts.add("    " + script + ";");
            }
        }

        if (lhsExprs.isEmpty()) {
            // 无 LHS — DRL grammar 要求 when 段至少 1 个 expr,给个恒真
            drl.append("    eval(true)\n");
        } else {
            for (int i = 0; i < lhsExprs.size(); i++) {
                if (i == lhsExprs.size() - 1) {
                    drl.append(lhsExprs.get(i)).append("\n");
                } else {
                    drl.append(lhsExprs.get(i)).append(" and\n");
                }
            }
        }

        drl.append("then\n");
        if (rhsStmts.isEmpty()) {
            // 无 RHS — DRL grammar 要求 then 段至少 1 个 statement,给个空 update
            drl.append("    // no-op\n");
        } else {
            for (String stmt : rhsStmts) {
                drl.append(stmt).append("\n");
            }
        }
        drl.append("end\n\n");
    }

    private String buildRuleName(ScriptDecisionTable table, int rowNum) {
        // V5.44.2 简化:用 row 号当 rule name(DRL 4 顶层 rule name 唯一即可,
        // KnowledgeBuilder 端不做去重 — 老 ScriptDecisionTable 行为是"每行 1 rule")
        return "ScriptTable_r" + rowNum;
    }

    private Map<Integer, List<ScriptCell>> groupCellsByRow(ScriptDecisionTable table) {
        Map<Integer, List<ScriptCell>> byRow = new HashMap<>();
        if (table.getCellMap() == null) {
            return byRow;
        }
        for (ScriptCell cell : table.getCellMap().values()) {
            byRow.computeIfAbsent(cell.getRow(), k -> new ArrayList<>()).add(cell);
        }
        return byRow;
    }

    private Column findColumn(ScriptDecisionTable table, int colNum) {
        if (table.getColumns() == null) {
            return null;
        }
        for (Column c : table.getColumns()) {
            if (c.getNum() == colNum) {
                return c;
            }
        }
        return null;
    }
}
