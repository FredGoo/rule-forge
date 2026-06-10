//! V5.27 E2E: `ReteRuleEngine` wires through the HTTP layer.
//!
//! Closes the loop from PR #61/#62 (IE feature) and the V5.25 RETE
//! port: instead of a hand-coded `MockRuleEngine` (`age>=18 &&
//! income>=5000`), the binary now loads real knowledge package
//! JSON files at startup and dispatches rule execution through
//! the production `ReteRuleEngine`. This file proves the wiring
//! works end-to-end: HTTP request → BPMN traversal →
//! `RuleExecutor::execute` → `ReteRuleEngine::fire_rules` →
//! `fired_rules` → `vars._fireable_rules` → HTTP response.
//!
//! ## Scenarios
//!
//! ### Scenario: real ReteRuleEngine fires a rule from a loaded package
//! - **Given** a knowledge package on disk with one rule
//!   `r-loan-approve` (criteria: `Applicant.age >= 18`)
//! - **And** an axum app with `ReteRuleEngine` built from that package
//! - **When** POST /ruleforge/evaluate with `applicant.age = 30`
//! - **Then** response 200 with `vars._fireable_rules = ["r-loan-approve"]`
//!
//! ### Scenario: rule does NOT fire when criteria fail
//! - **Given** same package
//! - **When** POST /ruleforge/evaluate with `applicant.age = 10`
//! - **Then** `vars._fireable_rules` is absent (or empty)
//!
//! ### Scenario: knowledge_dir loader rejects bad JSON with file path
//! - **Given** a directory with a malformed `broken.json`
//! - **When** `load_dir` is called
//! - **Then** returns `Err(LoadError::Json { path, .. })` with the
//!   file path so the operator can fix it

use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;

use axum::body::{to_bytes, Body};
use axum::http::{Request, StatusCode};
use axum::routing::post;
use axum::Router;
use rf_executor::dispatch::ExecutorRegistry;
use rf_executor::rule_engine::RuleEngine;
use rf_http::flow_def_repo::{FlowDefinitionRepo, StubFlowLoader};
use rf_http::routes::evaluate::evaluate;
use rf_http::state::AppState;
use rf_rule::deserialize::{KnowledgePackage, KnowledgePackageWrapper};
use rf_rule::loader::{load_dir, LoadError};
use rf_rule::model::left::LeftType;
use rf_rule::model::left_part::LeftPart;
use rf_rule::model::op::Op;
use rf_rule::model::rule::{Lhs, Rhs, Rule};
use rf_rule::model::value::Value;
use rf_rule::model::{Criteria, Left, Line, Rete, ReteNode};
use rf_rule::rete_engine::ReteRuleEngine;
use serde_json::{json, Value as JsonValue};
use tower::ServiceExt;

static COUNTER: AtomicU64 = AtomicU64::new(0);

fn tempdir() -> std::path::PathBuf {
    let n = COUNTER.fetch_add(1, Ordering::SeqCst);
    let p = std::env::temp_dir().join(format!(
        "rf-rule-e2e-{}-{}",
        std::process::id(),
        n
    ));
    std::fs::create_dir_all(&p).unwrap();
    p
}

const LOAN_BPMN: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:ruleforge="http://ruleforge.com/schema"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="loan_flow">
    <bpmn:startEvent id="s"><bpmn:outgoing>e1</bpmn:outgoing></bpmn:startEvent>
    <bpmn:serviceTask id="r1" ruleforge:taskType="rule">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="r1"/>
    <bpmn:sequenceFlow id="e2" sourceRef="r1" targetRef="end"/>
    <bpmn:endEvent id="end"/>
  </bpmn:process>
</bpmn:definitions>"#;

/// Build a knowledge package with one rule
/// `r-loan-approve` that fires when `applicant.age >= 18`. The
/// OTN class is `"applicant"` (lowercase) to match the HTTP
/// handler's `assert_fact` call (the handler uses the request's
/// top-level var name as the fact class).
fn build_loan_package() -> KnowledgePackageWrapper {
    let crit = ReteNode::Criteria {
        id: 2,
        debug: false,
        criteria: Criteria {
            op: Op::GreaterThenEquals,
            left: Left {
                left_type: LeftType::Variable,
                left_part: LeftPart::Variable {
                    variable_category: Some("applicant".into()),
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
                constant_value: Some(json!(18)),
            }),
        },
        lines: vec![Line {
            from_node_id: 2,
            to_node_id: 3,
            from: None,
            to: None,
        }],
    };
    let otn = ReteNode::ObjectType {
        id: 1,
        object_type_class: Some("applicant".into()),
        lines: vec![Line {
            from_node_id: 1,
            to_node_id: 2,
            from: None,
            to: None,
        }],
    };
    let rule = Rule {
        id: "r-loan-approve".into(),
        name: "Loan Approve".into(),
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
        "loan_rules_v1",
        kp,
        vec![crit, ReteNode::Terminal { id: 3, rule }],
        Some("1.0.0".into()),
    );
    wrap.build_deserialize();
    wrap
}

fn build_app_with_engine(
    bpmn: &str,
    engine: Arc<dyn RuleEngine>,
) -> (AppState, Router) {
    let loader = Arc::new(StubFlowLoader::with_flow("loan_flow", bpmn));
    let repo = Arc::new(FlowDefinitionRepo::new(loader));
    let registry = Arc::new(ExecutorRegistry::with_rule_engine(engine));
    let state = AppState::new(repo, registry, "e2e-worker", "http://localhost:8180", "");
    let app = Router::new()
        .route("/ruleforge/evaluate", post(evaluate))
        .with_state(state.clone());
    (state, app)
}

async fn body_json(resp: axum::response::Response) -> JsonValue {
    let body = to_bytes(resp.into_body(), 1024 * 1024).await.unwrap();
    serde_json::from_slice(&body).unwrap()
}

#[tokio::test]
async fn given_loaded_package_when_evaluate_with_matching_fact_then_rule_fires() {
    let wrap = build_loan_package();
    // Round-trip through the loader to prove JSON path works
    // (the binary reads packages from disk, not in-memory).
    let dir = tempdir();
    let body = serde_json::to_string(&wrap).unwrap();
    std::fs::write(dir.join("loan_rules_v1.json"), body).unwrap();

    let loaded = load_dir(&dir).expect("load_dir");
    assert_eq!(loaded.len(), 1);
    let refs: Vec<&_> = loaded.iter().collect();
    let engine: Arc<dyn RuleEngine> =
        Arc::new(ReteRuleEngine::from_wrappers(&refs));

    let (_state, app) = build_app_with_engine(LOAN_BPMN, engine);

    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/evaluate")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flow_id": "loan_flow",
                        "applicant": { "age": 30, "income": 8000 }
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_json(resp).await;
    assert_eq!(body["result"], "COMPLETED");
    let fired = body["vars"]["_fireable_rules"]
        .as_array()
        .expect(&format!("_fireable_rules is array; body={body}"));
    assert!(
        fired.iter().any(|v| v.as_str() == Some("r-loan-approve")),
        "expected r-loan-approve in fired_rules, got {fired:?}"
    );
}

#[tokio::test]
async fn given_loaded_package_when_evaluate_with_non_matching_fact_then_rule_does_not_fire() {
    let wrap = build_loan_package();
    let dir = tempdir();
    std::fs::write(
        dir.join("loan_rules_v1.json"),
        serde_json::to_string(&wrap).unwrap(),
    )
    .unwrap();
    let loaded = load_dir(&dir).expect("load_dir");
    let refs: Vec<&_> = loaded.iter().collect();
    let engine: Arc<dyn RuleEngine> =
        Arc::new(ReteRuleEngine::from_wrappers(&refs));

    let (_state, app) = build_app_with_engine(LOAN_BPMN, engine);
    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/evaluate")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flow_id": "loan_flow",
                        "applicant": { "age": 10, "income": 0 }
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_json(resp).await;
    assert_eq!(body["result"], "COMPLETED");
    // No rule fired → _fireable_rules is not assigned by the executor
    // (RuleExecutor only assigns when results.fired_rules is non-empty).
    assert!(
        body["vars"].get("_fireable_rules").is_none(),
        "expected no _fireable_rules for non-matching fact, got {}",
        body["vars"]
    );
}

#[tokio::test]
async fn load_dir_reports_parse_error_with_file_path() {
    let dir = tempdir();
    std::fs::write(dir.join("broken.json"), "{ this is not json").unwrap();
    let err = load_dir(&dir).unwrap_err();
    match err {
        LoadError::Json { path, .. } => {
            assert!(
                path.ends_with("broken.json"),
                "expected path to point to broken.json, got {path:?}"
            );
        }
        other => panic!("expected Json error, got {other:?}"),
    }
}

#[tokio::test]
async fn load_dir_rejects_non_directory_path() {
    let dir = tempdir();
    let f = dir.join("not_a_dir.json");
    std::fs::write(&f, "{}").unwrap();
    let err = load_dir(&f).unwrap_err();
    assert!(matches!(err, LoadError::NotADirectory(_)));
}
