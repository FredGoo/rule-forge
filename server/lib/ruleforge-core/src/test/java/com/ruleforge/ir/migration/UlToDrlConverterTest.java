package com.ruleforge.ir.migration;

import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleSet;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.model.rule.lhs.Lhs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.42.3b — {@code .ul → .drl} 一次性 emit 工具 BDD。
 *
 * <p>这是**保留并行**的迁移辅助:把 .ul 解析后的 {@link RuleSet} 重新
 * 序列化成 .drl 文本,让运维能"看到"对应 DRL 形式,手动搬运 / 批量迁移。
 *
 * <p>第一版范围(V5.42.3b):
 * <ul>
 *   <li>顶层 rule metadata 全 emit: name / salience / agenda-group / activation-group /
 *       ruleflow-group / auto-focus / no-loop / lock-on-active / enabled /
 *       date-effective / date-expires</li>
 *   <li>D2 化 else: {@code Rule.withElse && elseRule != null} → emit
 *       {@code rule "X_else" extends "X"}</li>
 *   <li>lhs / rhs 内部 emit **不**支持 — 留 V5.42.5+(跟 V5.42 D4 老 CellCondition
 *       决定一致:复杂 lhs 内容运维要手动 rewrite)</li>
 *   <li>输出 DRL 在 lint 角度上**不**通过 DrlParser(因为 lhs 是空)— caller 用
 *       标识位 / 注释明确说明 "V5.42.3b emit 第一版" + "需运维补 lhs"</li>
 * </ul>
 *
 * @since 5.42
 */
@DisplayName("V5.42.3b — .ul → .drl 一次性 emit 工具 BDD")
class UlToDrlConverterTest {

    private UlToDrlConverter converter;

    @BeforeEach
    void setUp() {
        converter = new UlToDrlConverter();
    }

    // ============================================================
    // === 最简 rule 顶层 metadata ===
    // ============================================================

    @Nested
    @DisplayName("Given 简单 RuleSet,When emit,Then DRL 顶层 metadata 正确")
    class SimpleRule {

        @Test
        @DisplayName("只 name:rule \"R1\" when then end(无 lhs)")
        void nameOnly() {
            RuleSet rs = new RuleSet();
            rs.setRules(new ArrayList<>());
            rs.getRules().add(newRule("R1", null, null, null));
            String drl = converter.emit(rs);
            // 第一版 lhs 留 TODO 注释(运维手动补)
            assertTrue(drl.contains("rule \"R1\""), "emit 应含 rule name,实际:" + drl);
            assertTrue(drl.contains("when"), "emit 应含 when 段,实际:" + drl);
            assertTrue(drl.contains("then"), "emit 应含 then 段,实际:" + drl);
            assertTrue(drl.contains("end"), "emit 应含 end 段,实际:" + drl);
        }

        @Test
        @DisplayName("salience → [salience N]")
        void salience() {
            RuleSet rs = new RuleSet();
            rs.setRules(new ArrayList<>());
            Rule r = newRule("R1", null, null, null);
            r.setSalience(99);
            rs.getRules().add(r);
            String drl = converter.emit(rs);
            assertTrue(drl.contains("[salience 99]"), "salience → [salience 99],实际:" + drl);
        }

        @Test
        @DisplayName("agenda-group / activation-group / ruleflow-group → [agenda-group \"X\"]")
        void groups() {
            RuleSet rs = new RuleSet();
            rs.setRules(new ArrayList<>());
            Rule r = newRule("R1", null, null, null);
            r.setAgendaGroup("g1");
            r.setActivationGroup("a1");
            r.setRuleflowGroup("rf1");
            rs.getRules().add(r);
            String drl = converter.emit(rs);
            // 注意:substring 不带 ] — 单 attribute 走 [...] 形式,多 attribute 走 [...] 中间用 , 分隔
            assertTrue(drl.contains("agenda-group \"g1\""), "agenda-group emit,实际:" + drl);
            assertTrue(drl.contains("activation-group \"a1\""), "activation-group emit,实际:" + drl);
            assertTrue(drl.contains("ruleflow-group \"rf1\""), "ruleflow-group emit,实际:" + drl);
        }

        @Test
        @DisplayName("boolean fields: auto-focus / no-loop / lock-on-active / enabled")
        void booleans() {
            RuleSet rs = new RuleSet();
            rs.setRules(new ArrayList<>());
            Rule r = newRule("R1", null, null, null);
            r.setAutoFocus(true);
            r.setLoop(false);  // loop = false → no-loop true(语义反转)
            r.setEnabled(false);
            rs.getRules().add(r);
            String drl = converter.emit(rs);
            assertTrue(drl.contains("auto-focus true"), "auto-focus emit,实际:" + drl);
            assertTrue(drl.contains("no-loop true"), "no-loop 语义反转(loop=false→no-loop=true),实际:" + drl);
            assertTrue(drl.contains("enabled false"), "enabled emit,实际:" + drl);
            // 注:V5.42.3b 简化,lock-on-active 不映射(Rule 没对应字段)— V5.42.5
        }

        @Test
        @DisplayName("date-effective / date-expires → [date-effective \"YYYY-MM-DD\"]")
        void dates() {
            RuleSet rs = new RuleSet();
            rs.setRules(new ArrayList<>());
            Rule r = newRule("R1", null, null, null);
            r.setEffectiveDate(parseDate("2026-01-01"));
            r.setExpiresDate(parseDate("2027-01-01"));
            rs.getRules().add(r);
            String drl = converter.emit(rs);
            assertTrue(drl.contains("date-effective \"2026-01-01\""), "date-effective emit,实际:" + drl);
            assertTrue(drl.contains("date-expires \"2027-01-01\""), "date-expires emit,实际:" + drl);
        }
    }

    // ============================================================
    // === D2 else extends 化 ===
    // ============================================================

    @Nested
    @DisplayName("Given D2 else 关系,When emit,Then DRL extends 子句正确")
    class ElseExtends {

        @Test
        @DisplayName("Rule.withElse + elseRule → extends \"<elseRule.name>\"")
        void elseRuleEmitted() {
            RuleSet rs = new RuleSet();
            rs.setRules(new ArrayList<>());
            Rule main = newRule("X", null, null, null);
            Rule elseR = newRule("X_else", null, null, null);
            main.setWithElse(true);
            main.setElseRule(elseR);
            rs.getRules().add(main);
            rs.getRules().add(elseR);
            String drl = converter.emit(rs);
            // X → extends "X_else"
            assertTrue(drl.contains("rule \"X\" extends \"X_else\""),
                "X 应 emit extends 子句指 X_else,实际:" + drl);
        }

        @Test
        @DisplayName("无 elseRule:emit 普通 rule(不 emit extends)")
        void noElseNoExtends() {
            RuleSet rs = new RuleSet();
            rs.setRules(new ArrayList<>());
            rs.getRules().add(newRule("R1", null, null, null));
            String drl = converter.emit(rs);
            assertTrue(drl.contains("rule \"R1\""), "R1 应 emit,实际:" + drl);
            assertTrue(!drl.contains("extends"), "无 else → 不 emit extends,实际:" + drl);
        }
    }

    // ============================================================
    // === 顶层 dialect + 注释 ===
    // ============================================================

    @Nested
    @DisplayName("Given emit 输出,When 检查,Then 顶层 dialect \"mvel\" 走 V5.42 D4 决定")
    class TopLevelDialect {

        @Test
        @DisplayName("输出含顶层 dialect \"mvel\"(V5.42 D4 决定:可省略但不强制)")
        void optionalDialect() {
            RuleSet rs = new RuleSet();
            rs.setRules(new ArrayList<>());
            rs.getRules().add(newRule("R1", null, null, null));
            String drl = converter.emit(rs);
            // V5.42.3b 决定:emit 时**不**写顶层 dialect(D4 决定:可省略),
            // 让 caller 看到"这是 DRL 化第一版"语义
            assertTrue(!drl.contains("dialect \"mvel\""),
                "V5.42.3b 简化:不 emit 顶层 dialect,实际:" + drl);
        }

        @Test
        @DisplayName("输出含迁移标识注释:V5.42.3b 标识 + 需运维补 lhs")
        void migrationMarker() {
            RuleSet rs = new RuleSet();
            rs.setRules(new ArrayList<>());
            rs.getRules().add(newRule("R1", null, null, null));
            String drl = converter.emit(rs);
            assertTrue(drl.contains("// V5.42.3b"),
                "输出应标记 .ul→.drl 一次性 emit 工具版本,实际:" + drl);
            assertTrue(drl.contains("TODO") || drl.contains("需运维补"),
                "输出应说明 lhs 需手动补,实际:" + drl);
        }
    }

    // ============================================================
    // === 多 rule + 空 RuleSet 边界 ===
    // ============================================================

    @Nested
    @DisplayName("Given 边界 case,When emit,Then 行为稳定")
    class EdgeCases {

        @Test
        @DisplayName("多 rule 顺序保留")
        void multipleRules() {
            RuleSet rs = new RuleSet();
            rs.setRules(new ArrayList<>());
            rs.getRules().add(newRule("R1", null, null, null));
            rs.getRules().add(newRule("R2", null, null, null));
            rs.getRules().add(newRule("R3", null, null, null));
            String drl = converter.emit(rs);
            int i1 = drl.indexOf("rule \"R1\"");
            int i2 = drl.indexOf("rule \"R2\"");
            int i3 = drl.indexOf("rule \"R3\"");
            assertTrue(i1 > 0 && i2 > i1 && i3 > i2,
                "rule 顺序 R1 < R2 < R3,实际位置: " + i1 + "," + i2 + "," + i3);
        }

        @Test
        @DisplayName("空 RuleSet(无 rule)→ 不抛错,只含 header 注释")
        void emptyRuleSet() {
            RuleSet rs = new RuleSet();
            rs.setRules(new ArrayList<>());
            String drl = converter.emit(rs);
            assertNotNull(drl);
            // 至少含 V5.42.3b 标识
            assertTrue(drl.contains("// V5.42.3b"));
        }

        @Test
        @DisplayName("null RuleSet → 抛 IllegalArgumentException")
        void nullRuleSet() {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> converter.emit(null));
        }
    }

    // ============================================================
    // === helpers ===
    // ============================================================

    private static Rule newRule(String name, Lhs lhs, Rhs rhs, Rule elseRule) {
        Rule r = new Rule();
        r.setName(name);
        r.setLhs(lhs != null ? lhs : new Lhs());
        r.setRhs(rhs != null ? rhs : new Rhs());
        if (elseRule != null) {
            r.setWithElse(true);
            r.setElseRule(elseRule);
        }
        return r;
    }

    private static Date parseDate(String s) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
