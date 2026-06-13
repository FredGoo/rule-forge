package com.ruleforge.ir.drl;

import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.FromLeftPart;
import com.ruleforge.model.rule.lhs.Lhs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V5.52 — DRL deserializer → FromLeftPart 链路 BDD。
 *
 * <p>覆盖:
 * <ul>
 *   <li>V5.52.1:from $stream 解析产出 FromLeftPart(fromSource="stream"),挂 Lhs.criterion 链</li>
 *   <li>V5.52.2:from collect(InnerPattern) 解析产出 FromLeftPart(fromSource="collect",
 *       multiCondition 装 inner pattern)</li>
 *   <li>V5.52.3:from accumulate(...) 5 内置(count/sum/avg/min/max)解析产出
 *       FromLeftPart(fromSource="accumulate", statisticType=对应)</li>
 *   <li>每 sub-task 各加 outer type 校验失败 / 拒收路径</li>
 * </ul>
 *
 * <p>每 sub-task (V5.52.1/2/3) 加新 {@code @Nested} 扩本类 — 集中所有 from-clause 端
 * deserializer 断言。
 */
@DisplayName("V5.52 — DRL deserializer → FromLeftPart 链路")
class DrlLhsWiringTest {

    private DatatypeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DatatypeResolver();
        resolver.register("Applicant",
            DatatypeResolver.TypeInfo.fact("Applicant", Arrays.asList("age", "income", "score")));
        resolver.register("Loan",
            DatatypeResolver.TypeInfo.fact("Loan", Arrays.asList("amount")));
        // V5.52.2 / V5.52.3 也会用到 Number/Integer/ArrayList(内置 5 统计的 binding type)
        resolver.register("Number",
            DatatypeResolver.TypeInfo.fact("Number", java.util.Collections.emptyList()));
        resolver.register("Integer",
            DatatypeResolver.TypeInfo.fact("Integer", java.util.Collections.emptyList()));
        resolver.register("ArrayList",
            DatatypeResolver.TypeInfo.fact("ArrayList", java.util.Collections.emptyList()));
    }

    // ============================================================
    // === from $stream ===
    // ============================================================

    @Nested
    @DisplayName("Given DRL '$a : Type(...) from $stream',When deserialize,Then FromLeftPart(fromSource='stream') 挂 Lhs.criterion 链")
    class FromStream {

        @Test
        @DisplayName("from $stream → Lhs.criterion 是 And,内含 1 个 Criteria(Left.leftPart=FromLeftPart)")
        void fromStreamYieldsFromLeftPart() {
            List<Rule> rules = DrlDeserializer.parseDrl(
                "rule \"R1\" when $a : Applicant(age > 18) from $stream then end", resolver);
            assertThat(rules).hasSize(1);
            Lhs lhs = rules.get(0).getLhs();
            assertThat(lhs).isNotNull();
            assertThat(lhs.getCriterion()).isInstanceOf(And.class);
            And and = (And) lhs.getCriterion();
            assertThat(and.getCriterions()).hasSize(1);
            Criteria c = (Criteria) and.getCriterions().get(0);
            assertThat(c.getLeft().getLeftPart()).isInstanceOf(FromLeftPart.class);

            FromLeftPart fp = (FromLeftPart) c.getLeft().getLeftPart();
            assertThat(fp.getFromSource()).isEqualTo("stream");
            // outer type "Applicant" 走 isKnown 校验过
            assertThat(fp.getVariableCategory()).isEqualTo("Applicant");
            // variableName 走 source expr 的字面 text(given alt 2 'drlPattern FROM expr',
            //   source expr 是 methodChain '$stream' — 整段 text 都塞进 variableName)
            assertThat(fp.getVariableName()).isEqualTo("$stream");
        }

        @Test
        @DisplayName("from $stream + outer type 未注册 → DrlParseException")
        void fromStreamUnknownOuterTypeFails() {
            // 临时卸掉 Applicant 模拟 "未注册"
            DatatypeResolver fresh = new DatatypeResolver();
            // Applicant 不注册
            assertThatThrownBy(() -> DrlDeserializer.parseDrl(
                "rule \"R1\" when $a : Applicant(age > 18) from $stream then end", fresh))
                .isInstanceOf(DrlParseException.class);
        }
    }

    // ============================================================
    // === from collect(...) ===
    // ============================================================

    @Nested
    @DisplayName("Given DRL '$xs : List() from collect(InnerPattern)',When deserialize,Then FromLeftPart(fromSource='collect', multiCondition)")
    class FromCollect {

        @Test
        @DisplayName("from collect(Applicant(age > 18)) → FromLeftPart 1 个 multiCondition 1 个 PropertyCriteria(age > 18)")
        void fromCollectYieldsFromLeftPartWithMultiCondition() {
            List<Rule> rules = DrlDeserializer.parseDrl(
                "rule \"R1\" when $xs : ArrayList() from collect(Applicant(age > 18)) then end",
                resolver);
            assertThat(rules).hasSize(1);
            Lhs lhs = rules.get(0).getLhs();
            And and = (And) lhs.getCriterion();
            // V5.52.2:从外层 LHS 看只有 1 个 criterion(FromLeftPart wrapping collect)—
            //   内层 'age > 18' 被装进 FromLeftPart.multiCondition,不作为顶层 criterion
            assertThat(and.getCriterions()).hasSize(1);

            Criteria c = (Criteria) and.getCriterions().get(0);
            assertThat(c.getLeft().getLeftPart()).isInstanceOf(FromLeftPart.class);
            FromLeftPart fp = (FromLeftPart) c.getLeft().getLeftPart();
            assertThat(fp.getFromSource()).isEqualTo("collect");
            assertThat(fp.getVariableCategory()).isEqualTo("ArrayList");
            assertThat(fp.getMultiCondition()).isNotNull();
            assertThat(fp.getMultiCondition().getConditions()).hasSize(1);
            assertThat(fp.getMultiCondition().getConditions().get(0).getProperty()).isEqualTo("age");
            assertThat(fp.getMultiCondition().getConditions().get(0).getOp())
                .isEqualTo(com.ruleforge.model.rule.Op.GreaterThen);
            assertThat(fp.getMultiCondition().getType())
                .isEqualTo(com.ruleforge.model.rule.lhs.JunctionType.and);
        }

        @Test
        @DisplayName("from collect 外层 type 未注册 → DrlParseException")
        void fromCollectUnknownOuterTypeFails() {
            DatatypeResolver fresh = new DatatypeResolver();
            // ArrayList 不注册
            fresh.register("Applicant",
                DatatypeResolver.TypeInfo.fact("Applicant", Arrays.asList("age")));
            assertThatThrownBy(() -> DrlDeserializer.parseDrl(
                "rule \"R1\" when $xs : ArrayList() from collect(Applicant(age > 18)) then end",
                fresh))
                .isInstanceOf(DrlParseException.class);
        }
    }

    // ============================================================
    // === from accumulate(...) — V5.52.1 显式拒收 ===
    // ============================================================

    @Nested
    @DisplayName("Given DRL '$n : Number() from accumulate(...)',When deserialize in V5.52.1,Then DrlParseException")
    class FromAccumulateDeferred {

        @Test
        @DisplayName("from accumulate(count) 在 V5.52.1 抛 DrlParseException(V5.52.3 才接)")
        void fromAccumulateCountThrowsInV5521() {
            assertThatThrownBy(() -> DrlDeserializer.parseDrl(
                "rule \"R1\" " +
                    "when $n : Number() from accumulate(Applicant(age > 18), " +
                    "init(count := 0), " +
                    "action($n.setValue(count + 1)), " +
                    "result(count)) " +
                    "then end",
                resolver))
                .isInstanceOf(DrlParseException.class)
                .hasMessageContaining("V5.52.3");
        }
    }
}
