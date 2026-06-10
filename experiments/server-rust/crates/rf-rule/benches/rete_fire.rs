//! P6 micro-benchmark: 1000 facts × 10 rules, fire-cycle throughput.
//!
//! Run with `cargo bench -p rf-rule` (Criterion harness). P6
//! treats this as optional — the goal is to catch any
//! pathological O(n²) / O(n³) regressions in the wire-up /
//! path-traversal code, not to ship a public benchmark.

use criterion::{black_box, criterion_group, criterion_main, Criterion};
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

fn age_crit(min: i64) -> Criteria {
    Criteria {
        op: Op::GreaterThenEquals,
        left: Left {
            left_type: LeftType::Variable,
            left_part: LeftPart::Variable {
                variable_category: Some("Applicant".into()),
                variable_label: Some("age".into()),
                variable_name: Some("age".into()),
                datatype: Some("int".into()),
            },
            arithmetic: None,
        },
        value: Some(Value::Constant {
            constant_name: None,
            constant_label: None,
            constant_category: None,
            constant_value: Some(json!(min)),
        }),
    }
}

fn build_engine() -> ReteRuleEngine {
    // 10 rules, each tied to its own criteria node, all attached
    // to the same OTN. Different salience.
    let mut nodes: Vec<ReteNode> = Vec::new();
    let mut otn_lines: Vec<Line> = Vec::new();
    for i in 0..10 {
        let crit_id = 2 + i as i32;
        let term_id = 12 + i as i32;
        otn_lines.push(Line {
            from_node_id: 1,
            to_node_id: crit_id,
            from: None,
            to: None,
        });
        nodes.push(ReteNode::Criteria {
            id: crit_id,
            debug: false,
            criteria: age_crit(18 + (i as i64)),
            lines: vec![Line {
                from_node_id: crit_id,
                to_node_id: term_id,
                from: None,
                to: None,
            }],
        });
        let rule = Rule {
            id: format!("r{}", i),
            name: format!("r{}", i),
            rule_type: None,
            file: None,
            salience: 10 - (i as i32),
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
            r#loop: false,
            remark: None,
            with_else: false,
        };
        nodes.push(ReteNode::Terminal {
            id: term_id,
            rule,
        });
    }
    let otn = ReteNode::ObjectType {
        id: 1,
        object_type_class: Some("Applicant".into()),
        lines: otn_lines,
    };
    let kp = KnowledgePackage {
        rete: Rete {
            object_type_nodes: vec![otn],
            activation_group_retes_map: Default::default(),
            agenda_group_retes_map: Default::default(),
        },
        with_else_rules: Default::default(),
    };
    let mut wrap = KnowledgePackageWrapper::from_parts("bench", kp, nodes, None);
    wrap.build_deserialize();
    ReteRuleEngine::from_wrapper(&wrap)
}

fn bench_fire(c: &mut Criterion) {
    let engine = build_engine();
    let rt = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap();
    c.bench_function("fire_10_rules_1_fact", |b| {
        b.iter(|| {
            rt.block_on(async {
                let mut ctx = FlowContext::new("t");
                ctx.vars.assert_fact(
                    "Applicant",
                    json!({"age": 30}),
                );
                let _ = black_box(engine.fire_rules(&mut ctx).await.unwrap());
            });
        });
    });
}

criterion_group!(benches, bench_fire);
criterion_main!(benches);
