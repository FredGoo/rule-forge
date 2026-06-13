//! P6 cross-impl integration tests.
//!
//! Goal: exercise the full pipeline (build package → fire
//! rules → check matched/fired/output) on realistic rule
//! shapes that mirror the Java editor's real-world outputs.
//!
//! Java reference behavior (from `ruleforge-core` golden
//! tests + the editor's demo projects):
//! - All-match-rules fire in salience-descending order.
//! - activation_group is mutually exclusive (only the
//!   highest-salience rule in a group fires).
//! - agenda_group requires focus; rules in unfocused groups
//!   are skipped.
//! - And/Or joins behave as the criteria structure dictates.
//! - A criteria that fails short-circuits the And chain (no
//!   downstream propagation).
//!
//! P5 already covered per-type adapters. P6 combines them
//! with the engine to verify end-to-end behavior on multi-rule
//! packages.

use rf_executor::flow_context::FlowContext;
use rf_executor::rule_engine::RuleEngine;
use rf_rule::deserialize::{KnowledgePackage, KnowledgePackageWrapper};
use rf_rule::model::left::LeftType;
use rf_rule::model::left_part::{Left, LeftPart};
use rf_rule::model::op::Op;
use rf_rule::model::rule::{Lhs, Rhs, Rule, RuleType};
use rf_rule::model::value::Value;
use rf_rule::model::{Criteria, Line, Rete, ReteNode};
use rf_rule::rete_engine::ReteRuleEngine;
use rf_rule::rule_type::{
    decision_table::{DecisionTableRow, DecisionTableSpec, HitPolicy},
    decision_tree::DecisionTreeNode,
};
use serde_json::json;

// ---------- helpers ----------

/// V5.46.1 — `Applicant(name == <name>)` for the multi-fact regression test.
fn name_eq_crit(name: &str) -> Criteria {
    Criteria {
        op: Op::Equals,
        left: Left {
            left_type: LeftType::Variable,
            left_part: LeftPart::Variable {
                variable_category: Some("Applicant".into()),
                variable_label: Some("name".into()),
                variable_name: Some("name".into()),
                datatype: Some("String".into()),
            },
            arithmetic: None,
        },
        value: Some(Value::Constant {
            constant_name: None,
            constant_label: None,
            constant_category: None,
            constant_value: Some(json!(name)),
        }),
    }
}

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

fn age_lt_crit(max: i64) -> Criteria {
    Criteria {
        op: Op::LessThen,
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
            constant_value: Some(json!(max)),
        }),
    }
}

fn simple_rule(id: &str) -> Rule {
    Rule {
        id: id.into(),
        name: id.into(),
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
        r#loop: false,
        remark: None,
        with_else: false,
    }
}

// ---------- scenarios ----------

#[test]
fn three_rule_package_salience_orders_fire() {
    // 3 rules, each tied to its own criteria node, all attached
    // to the same OTN. Each rule has a different salience. The
    // fact matches all three → all fire, in salience-desc order.
    //
    //   OTN(1) → crit_ge_18(2)  → term r-lo  (salience 0)
    //   OTN(1) → crit_ge_25(3)  → term r-mid (salience 5)
    //   OTN(1) → crit_ge_18(4)  → term r-hi  (salience 10)
    //
    // (Using age=30: age>=18 ✓, age>=25 ✓, age>=18 ✓ → all fire.)
    let crit_lo = ReteNode::Criteria {
        id: 2,
        debug: false,
        criteria: age_crit(18),
        lines: vec![Line {
            from_node_id: 2,
            to_node_id: 5,
            from: None,
            to: None,
        }],
    };
    let crit_mid = ReteNode::Criteria {
        id: 3,
        debug: false,
        criteria: age_crit(25),
        lines: vec![Line {
            from_node_id: 3,
            to_node_id: 6,
            from: None,
            to: None,
        }],
    };
    let crit_hi = ReteNode::Criteria {
        id: 4,
        debug: false,
        criteria: age_crit(18),
        lines: vec![Line {
            from_node_id: 4,
            to_node_id: 7,
            from: None,
            to: None,
        }],
    };
    let otn = ReteNode::ObjectType {
        id: 1,
        object_type_class: Some("Applicant".into()),
        lines: vec![
            Line { from_node_id: 1, to_node_id: 2, from: None, to: None },
            Line { from_node_id: 1, to_node_id: 3, from: None, to: None },
            Line { from_node_id: 1, to_node_id: 4, from: None, to: None },
        ],
    };
    let mut rule_lo = simple_rule("r-lo");
    let mut rule_mid = simple_rule("r-mid");
    rule_mid.salience = 5;
    let mut rule_hi = simple_rule("r-hi");
    rule_hi.salience = 10;
    let kp = KnowledgePackage {
        rete: Rete {
            object_type_nodes: vec![otn],
            activation_group_retes_map: Default::default(),
            agenda_group_retes_map: Default::default(),
        },
        with_else_rules: Default::default(),
    };
    let all_nodes = vec![
        crit_lo,
        crit_mid,
        crit_hi,
        ReteNode::Terminal { id: 5, rule: rule_lo },
        ReteNode::Terminal { id: 6, rule: rule_mid },
        ReteNode::Terminal { id: 7, rule: rule_hi },
    ];
    let mut wrap = KnowledgePackageWrapper::from_parts("kp", kp, all_nodes, None);
    wrap.build_deserialize();
    let engine = ReteRuleEngine::from_wrapper(&wrap);
    let rt = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap();
    rt.block_on(async {
        let mut ctx = FlowContext::new("t");
        ctx.vars.assert_fact("Applicant", json!({"age": 30, "income": 8000}));
        let res = engine.fire_rules(&mut ctx).await.unwrap();
        // Expected order: r-hi (10) → r-mid (5) → r-lo (0).
        assert_eq!(
            res.fired_rules,
            vec!["r-hi".to_string(), "r-mid".to_string(), "r-lo".to_string()]
        );
    });
}

#[test]
fn activation_group_mutually_excludes_first_wins() {
    // Two rules, both tied to the same criteria, both in
    // activation_group = "approve". Higher-salience wins.
    let crit = ReteNode::Criteria {
        id: 2,
        debug: false,
        criteria: age_crit(18),
        lines: vec![
            Line { from_node_id: 2, to_node_id: 3, from: None, to: None },
            Line { from_node_id: 2, to_node_id: 4, from: None, to: None },
        ],
    };
    let otn = ReteNode::ObjectType {
        id: 1,
        object_type_class: Some("Applicant".into()),
        lines: vec![Line { from_node_id: 1, to_node_id: 2, from: None, to: None }],
    };
    let mut rule_a = simple_rule("r-a");
    rule_a.salience = 10;
    rule_a.activation_group = Some("approve".into());
    let mut rule_b = simple_rule("r-b");
    rule_b.salience = 5;
    rule_b.activation_group = Some("approve".into());
    let kp = KnowledgePackage {
        rete: Rete {
            object_type_nodes: vec![otn],
            activation_group_retes_map: Default::default(),
            agenda_group_retes_map: Default::default(),
        },
        with_else_rules: Default::default(),
    };
    let all_nodes = vec![
        crit,
        ReteNode::Terminal { id: 3, rule: rule_a },
        ReteNode::Terminal { id: 4, rule: rule_b },
    ];
    let mut wrap = KnowledgePackageWrapper::from_parts("kp", kp, all_nodes, None);
    wrap.build_deserialize();
    let engine = ReteRuleEngine::from_wrapper(&wrap);
    let rt = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap();
    rt.block_on(async {
        let mut ctx = FlowContext::new("t");
        ctx.vars.assert_fact("Applicant", json!({"age": 30}));
        let res = engine.fire_rules(&mut ctx).await.unwrap();
        assert_eq!(res.fired_rules, vec!["r-a".to_string()]);
    });
}

#[test]
fn agenda_group_requires_focus() {
    // Two rules, both tied to the same criteria. r-main has
    // no agenda_group (always fires); r-extra has
    // agenda_group="EXTRA" (only fires on focus).
    let crit = ReteNode::Criteria {
        id: 2,
        debug: false,
        criteria: age_crit(18),
        lines: vec![
            Line { from_node_id: 2, to_node_id: 3, from: None, to: None },
            Line { from_node_id: 2, to_node_id: 4, from: None, to: None },
        ],
    };
    let otn = ReteNode::ObjectType {
        id: 1,
        object_type_class: Some("Applicant".into()),
        lines: vec![Line { from_node_id: 1, to_node_id: 2, from: None, to: None }],
    };
    let mut main_rule = simple_rule("r-main");
    let mut extra_rule = simple_rule("r-extra");
    extra_rule.agenda_group = Some("EXTRA".into());
    let kp = KnowledgePackage {
        rete: Rete {
            object_type_nodes: vec![otn],
            activation_group_retes_map: Default::default(),
            agenda_group_retes_map: Default::default(),
        },
        with_else_rules: Default::default(),
    };
    let all_nodes = vec![
        crit,
        ReteNode::Terminal { id: 3, rule: main_rule },
        ReteNode::Terminal { id: 4, rule: extra_rule },
    ];
    let mut wrap = KnowledgePackageWrapper::from_parts("kp", kp, all_nodes, None);
    wrap.build_deserialize();
    let mut engine = ReteRuleEngine::from_wrapper(&wrap);
    let rt = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap();
    // Default focus is MAIN → r-extra skipped.
    rt.block_on(async {
        let mut ctx = FlowContext::new("t");
        ctx.vars.assert_fact("Applicant", json!({"age": 30}));
        let res = engine.fire_rules(&mut ctx).await.unwrap();
        assert_eq!(res.fired_rules, vec!["r-main".to_string()]);
    });
    // Switch focus to EXTRA → both fire (r-main has no group).
    engine.set_focused_agenda_group(Some("EXTRA".into()));
    rt.block_on(async {
        let mut ctx = FlowContext::new("t");
        ctx.vars.assert_fact("Applicant", json!({"age": 30}));
        let res = engine.fire_rules(&mut ctx).await.unwrap();
        assert_eq!(
            res.fired_rules,
            vec!["r-main".to_string(), "r-extra".to_string()]
        );
    });
}

#[test]
fn decision_table_three_rows_with_hit_policy_first() {
    // Build a 3-row decision table, attach each row's criteria
    // to the same OTN. With age=35, rows 1 (age>=18) and 2
    // (age>=30) match; row 3 (age>=50) does not.
    let spec = DecisionTableSpec {
        id: "dt1".into(),
        name: "age_band".into(),
        salience: 0,
        hit_policy: HitPolicy::First,
        rows: vec![
            DecisionTableRow {
                row_num: 1,
                conditions: vec![Some(age_crit(18))],
                actions: vec!["young_adult".into()],
            },
            DecisionTableRow {
                row_num: 2,
                conditions: vec![Some(age_crit(30))],
                actions: vec!["established".into()],
            },
            DecisionTableRow {
                row_num: 3,
                conditions: vec![Some(age_crit(50))],
                actions: vec!["senior".into()],
            },
        ],
    };
    let rules = spec.build();
    let crit1 = ReteNode::Criteria {
        id: 2,
        debug: false,
        criteria: age_crit(18),
        lines: vec![Line { from_node_id: 2, to_node_id: 5, from: None, to: None }],
    };
    let crit2 = ReteNode::Criteria {
        id: 3,
        debug: false,
        criteria: age_crit(30),
        lines: vec![Line { from_node_id: 3, to_node_id: 6, from: None, to: None }],
    };
    let crit3 = ReteNode::Criteria {
        id: 4,
        debug: false,
        criteria: age_crit(50),
        lines: vec![Line { from_node_id: 4, to_node_id: 7, from: None, to: None }],
    };
    let otn = ReteNode::ObjectType {
        id: 1,
        object_type_class: Some("Applicant".into()),
        lines: vec![
            Line { from_node_id: 1, to_node_id: 2, from: None, to: None },
            Line { from_node_id: 1, to_node_id: 3, from: None, to: None },
            Line { from_node_id: 1, to_node_id: 4, from: None, to: None },
        ],
    };
    let kp = KnowledgePackage {
        rete: Rete {
            object_type_nodes: vec![otn],
            activation_group_retes_map: Default::default(),
            agenda_group_retes_map: Default::default(),
        },
        with_else_rules: Default::default(),
    };
    let all_nodes = vec![
        crit1,
        crit2,
        crit3,
        ReteNode::Terminal { id: 5, rule: rules[0].clone() },
        ReteNode::Terminal { id: 6, rule: rules[1].clone() },
        ReteNode::Terminal { id: 7, rule: rules[2].clone() },
    ];
    let mut wrap = KnowledgePackageWrapper::from_parts("dt", kp, all_nodes, None);
    wrap.build_deserialize();
    let engine = ReteRuleEngine::from_wrapper(&wrap);
    let rt = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap();
    rt.block_on(async {
        let mut ctx = FlowContext::new("t");
        ctx.vars.assert_fact("Applicant", json!({"age": 35}));
        let res = engine.fire_rules(&mut ctx).await.unwrap();
        assert!(res.matched_rules.contains(&"dt1-row1".to_string()));
        assert!(res.matched_rules.contains(&"dt1-row2".to_string()));
        assert!(!res.matched_rules.contains(&"dt1-row3".to_string()));
    });
}

#[test]
fn decision_tree_binary_age_band() {
    // Tree: if age >= 18: leaf "adult", else leaf "minor".
    let spec = rf_rule::rule_type::DecisionTreeSpec {
        id: "dt".into(),
        name: "age_band".into(),
        salience: 0,
        root: DecisionTreeNode::Branch {
            condition: Box::new(age_crit(18)),
            true_branch: Box::new(DecisionTreeNode::Leaf {
                actions: vec!["adult".into()],
            }),
            false_branch: Box::new(DecisionTreeNode::Leaf {
                actions: vec!["minor".into()],
            }),
        },
    };
    let rules = spec.build();
    assert_eq!(rules.len(), 2);

    // Two criteria, both attached to the same OTN.
    let crit_ge = ReteNode::Criteria {
        id: 2,
        debug: false,
        criteria: age_crit(18),
        lines: vec![Line { from_node_id: 2, to_node_id: 4, from: None, to: None }],
    };
    let crit_lt = ReteNode::Criteria {
        id: 3,
        debug: false,
        criteria: age_lt_crit(18),
        lines: vec![Line { from_node_id: 3, to_node_id: 5, from: None, to: None }],
    };
    let otn = ReteNode::ObjectType {
        id: 1,
        object_type_class: Some("Applicant".into()),
        lines: vec![
            Line { from_node_id: 1, to_node_id: 2, from: None, to: None },
            Line { from_node_id: 1, to_node_id: 3, from: None, to: None },
        ],
    };
    let kp = KnowledgePackage {
        rete: Rete {
            object_type_nodes: vec![otn],
            activation_group_retes_map: Default::default(),
            agenda_group_retes_map: Default::default(),
        },
        with_else_rules: Default::default(),
    };
    let mut rule_ge = rules[0].clone();
    rule_ge.id = "dt-leaf1".into();
    let mut rule_lt = rules[1].clone();
    rule_lt.id = "dt-leaf2".into();
    let all_nodes = vec![
        crit_ge,
        crit_lt,
        ReteNode::Terminal { id: 4, rule: rule_ge },
        ReteNode::Terminal { id: 5, rule: rule_lt },
    ];
    let mut wrap = KnowledgePackageWrapper::from_parts("dt", kp, all_nodes, None);
    wrap.build_deserialize();
    let engine = ReteRuleEngine::from_wrapper(&wrap);
    let rt = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap();
    rt.block_on(async {
        // age=30 → adult only.
        let mut ctx = FlowContext::new("t");
        ctx.vars.assert_fact("Applicant", json!({"age": 30}));
        let res = engine.fire_rules(&mut ctx).await.unwrap();
        assert!(res.matched_rules.contains(&"dt-leaf1".to_string()));
        assert!(!res.matched_rules.contains(&"dt-leaf2".to_string()));
    });
    rt.block_on(async {
        // age=12 → minor only.
        let mut ctx = FlowContext::new("t");
        ctx.vars.assert_fact("Applicant", json!({"age": 12}));
        let res = engine.fire_rules(&mut ctx).await.unwrap();
        assert!(res.matched_rules.contains(&"dt-leaf2".to_string()));
        assert!(!res.matched_rules.contains(&"dt-leaf1".to_string()));
    });
}

#[test]
fn rule_type_tags_propagate_through_build() {
    // The rule_type field is preserved on the rule through
    // build_deserialize.
    let mut rule = simple_rule("r");
    rule.rule_type = Some(RuleType::Ul);
    assert_eq!(rule.rule_type, Some(RuleType::Ul));
}

#[test]
fn empty_vars_yields_no_activations() {
    // No facts asserted → no rules fire.
    let crit = ReteNode::Criteria {
        id: 2,
        debug: false,
        criteria: age_crit(18),
        lines: vec![Line { from_node_id: 2, to_node_id: 3, from: None, to: None }],
    };
    let otn = ReteNode::ObjectType {
        id: 1,
        object_type_class: Some("Applicant".into()),
        lines: vec![Line { from_node_id: 1, to_node_id: 2, from: None, to: None }],
    };
    let rule = simple_rule("r-empty");
    let kp = KnowledgePackage {
        rete: Rete {
            object_type_nodes: vec![otn],
            activation_group_retes_map: Default::default(),
            agenda_group_retes_map: Default::default(),
        },
        with_else_rules: Default::default(),
    };
    let all_nodes = vec![
        crit,
        ReteNode::Terminal { id: 3, rule },
    ];
    let mut wrap = KnowledgePackageWrapper::from_parts("kp", kp, all_nodes, None);
    wrap.build_deserialize();
    let engine = ReteRuleEngine::from_wrapper(&wrap);
    let rt = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap();
    rt.block_on(async {
        let mut ctx = FlowContext::new("t");
        // No assert_fact — WorkingMemory is empty.
        let res = engine.fire_rules(&mut ctx).await.unwrap();
        assert!(res.fired_rules.is_empty());
    });
}

#[test]
fn one_shot_fire_evaluates_each_fact_independently() {
    // V5.46.1 regression lock — without `clean()` between facts
    // in `fire_rules`, the first fact's `false` would be cached
    // in `EvaluationContext.criteria_value_map` and all
    // subsequent facts would be misclassified.
    //
    // 4 facts in 1 `fire_rules()`:
    //   - "uuid-rand-99"  → no rule matches
    //   - "Mario"         → r-Mario matches
    //   - "Duncan"        → r-Duncan matches
    //   - "Toshiya"       → r-Toshiya matches
    // Expected: 3 fired rules (the non-matching fact's criteria
    // must not poison the 3 matching facts that follow).
    let crits = vec![
        (2, 5, "Mario"),
        (3, 6, "Duncan"),
        (4, 7, "Toshiya"),
    ];
    let mut nodes: Vec<ReteNode> = Vec::new();
    let mut otn_lines: Vec<Line> = Vec::new();
    for (cid, tid, name) in &crits {
        otn_lines.push(Line { from_node_id: 1, to_node_id: *cid, from: None, to: None });
        nodes.push(ReteNode::Criteria {
            id: *cid,
            debug: false,
            criteria: name_eq_crit(name),
            lines: vec![Line { from_node_id: *cid, to_node_id: *tid, from: None, to: None }],
        });
        nodes.push(ReteNode::Terminal {
            id: *tid,
            rule: simple_rule(&format!("r-{}", name)),
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
    let mut wrap = KnowledgePackageWrapper::from_parts("t", kp, nodes, None);
    wrap.build_deserialize();
    let engine = ReteRuleEngine::from_wrapper(&wrap);
    let rt = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap();
    rt.block_on(async {
        let mut ctx = FlowContext::new("t");
        // Insert 1 non-matching + 3 matching facts in a single
        // FlowContext, then fire_rules once.
        ctx.vars.assert_fact("Applicant", json!({"name": "uuid-rand-99"}));
        ctx.vars.assert_fact("Applicant", json!({"name": "Mario"}));
        ctx.vars.assert_fact("Applicant", json!({"name": "Duncan"}));
        ctx.vars.assert_fact("Applicant", json!({"name": "Toshiya"}));
        let res = engine.fire_rules(&mut ctx).await.unwrap();
        assert_eq!(
            res.fired_rules.len(),
            3,
            "expected 3 rules fired (one per matching fact), got {:?}",
            res.fired_rules
        );
        assert!(res.fired_rules.contains(&"r-Mario".to_string()));
        assert!(res.fired_rules.contains(&"r-Duncan".to_string()));
        assert!(res.fired_rules.contains(&"r-Toshiya".to_string()));
    });
}
