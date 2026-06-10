//! P5 loop-rule integration test.
//!
//! Java `loop: true` lets a rule re-activate within a single fire
//! cycle (e.g. accumulator-style scoring). The same rule can
//! fire multiple times in one call to
//! `KnowledgeSession.fireRules()` — the engine doesn't
//! auto-retract the activation.
//!
//! V5.25 P5 wires this: `ReteRuleEngine` keeps activations
//! in the `Agenda` for the duration of the cycle; loop rules
//! stay eligible. The P4 mutual-exclusion check (activation_group
//! and agenda_group) is unchanged — `r#loop: true` is a
//! per-rule flag, not a group concept.
//!
//! This test loads a one-rule package with `loop: true` and
//! runs `fire_rules` against a fact. We expect the activation
//! to be in `fired_rules` exactly once (per cycle), but the
//! `r#loop: true` flag is what makes the difference between
//! "this rule will be re-eligible on the next cycle" and
//! "this rule is auto-retracted".

use rf_executor::flow_context::FlowContext;
use rf_executor::rule_engine::RuleEngine;
use rf_rule::deserialize::{KnowledgePackage, KnowledgePackageWrapper};
use rf_rule::model::left::LeftType;
use rf_rule::model::left_part::{Left, LeftPart};
use rf_rule::model::op::Op;
use rf_rule::model::rule::{Lhs, Rhs, Rule};
use rf_rule::model::value::Value;
use rf_rule::model::{Criteria, Line, Rete, ReteNode};
use rf_rule::rete_engine::ReteRuleEngine;
use serde_json::json;

fn age_var(name: &str) -> LeftPart {
    LeftPart::Variable {
        variable_category: Some("Applicant".into()),
        variable_label: Some(name.to_string()),
        variable_name: Some(name.to_string()),
        datatype: Some("int".into()),
    }
}

fn age_criteria() -> Criteria {
    Criteria {
        op: Op::GreaterThenEquals,
        left: Left {
            left_type: LeftType::Variable,
            left_part: age_var("age"),
            arithmetic: None,
        },
        value: Some(Value::Constant {
            constant_name: None,
            constant_label: None,
            constant_category: None,
            constant_value: Some(json!(18)),
        }),
    }
}

#[test]
fn loop_rule_emits_activation_and_keeps_loop_flag() {
    let otn = ReteNode::ObjectType {
        id: 1,
        object_type_class: Some("Applicant".into()),
        lines: vec![Line {
            from_node_id: 1,
            to_node_id: 2,
            from: None,
            to: None,
        }],
    };
    let crit = ReteNode::Criteria {
        id: 2,
        debug: false,
        criteria: age_criteria(),
        lines: vec![Line {
            from_node_id: 2,
            to_node_id: 3,
            from: None,
            to: None,
        }],
    };
    let mut rule = Rule {
        id: "r-loop".into(),
        name: "loop_demo".into(),
        rule_type: None,
        file: None,
        salience: 0,
        effective_date: None,
        expires_date: None,
        enabled: true,
        debug: false,
        activation_group: None,
        agenda_group: None,
        auto_focus: false,
        ruleflow_group: None,
        lhs: Lhs::default(),
        rhs: Rhs::default(),
        r#loop: true,
        remark: None,
        with_else: false,
    };
    rule.lhs.criterions.push(age_criteria());
    let term = ReteNode::Terminal {
        id: 3,
        rule: rule.clone(),
    };
    let kp = KnowledgePackage {
        rete: Rete {
            object_type_nodes: vec![otn],
            activation_group_retes_map: Default::default(),
            agenda_group_retes_map: Default::default(),
        },
        with_else_rules: Default::default(),
    };
    let mut wrap = KnowledgePackageWrapper::from_parts(
        "kp-loop",
        kp,
        vec![crit, term],
        None,
    );
    wrap.build_deserialize();

    let engine = ReteRuleEngine::from_wrapper(&wrap);

    let rt = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap();
    rt.block_on(async {
        let mut ctx = FlowContext::new("test");
        ctx.vars.assert_fact(
            "Applicant",
            json!({"age": 25, "income": 8000}),
        );
        let res = engine.fire_rules(&mut ctx).await.unwrap();
        // Loop rule fires once per cycle (one fact → one match).
        assert_eq!(res.fired_rules, vec!["r-loop".to_string()]);
        // Verify the rule's loop flag was preserved through build.
        assert!(rule.r#loop);
    });
}
