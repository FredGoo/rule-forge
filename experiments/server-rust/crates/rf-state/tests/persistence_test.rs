//! BDD integration tests for the `PgStateStore`.
//!
//! All tests require a live Postgres. Set `PG_URL` to a running
//! `postgres://ruleforge:ruleforge@127.0.0.1:5433/ruleforge_rust` (or
//! any other test DB) and the tests will:
//!   1. `sqlx::migrate!()` against the URL
//!   2. truncate `rust_decision_flow_state` at the start of each test
//!   3. exercise the persistence contract below
//!
//! ## Scenarios
//!
//! ### Scenario: insert_start then mark_completed round-trip
//! - **Given** an empty `rust_decision_flow_state` table
//! - **When** `insert_start` a fresh `flow_run_id`
//! - **And** `mark_completed` the same run
//! - **Then** `select_by_flow_run_id` returns status=COMPLETED, vars round-tripped
//!
//! ### Scenario: mark_suspended records wait_type / wait_ref / payload
//! - **Given** an empty table
//! - **When** `insert_start` then `mark_suspended(USER_TASK, "approve", payload)`
//! - **Then** `select_by_flow_run_id` returns status=WAITING_CALLBACK + wait_type=USER_TASK
//! - **And** payload round-trips through jsonb
//!
//! ### Scenario: try_advisory_lock is exclusive — only one caller wins
//! - **Given** an empty table
//! - **When** 10 concurrent tasks call `try_advisory_lock(same_key)`
//! - **Then** exactly 1 task sees `acquired == true`, the other 9 see `false`
//!
//! ### Scenario: release_advisory_lock unblocks the next caller
//! - **Given** one caller holds the advisory lock
//! - **When** that caller calls `release_advisory_lock`
//! - **And** a second caller calls `try_advisory_lock`
//! - **Then** the second caller's `acquired == true`
//!
//! ### Scenario: select_recoverable_skip_locked only returns ready rows
//! - **Given** 2 WAITING_CALLBACK rows (1 ready now, 1 with `next_retry_at` in the future)
//! - **And** 1 COMPLETED row
//! - **When** `select_recoverable_skip_locked(10)`
//! - **Then** returns only the 1 ready WAITING_CALLBACK row
//!
//! ### Scenario: mark_failed records error_message
//! - **Given** a fresh insert
//! - **When** `mark_failed("boom")`
//! - **Then** select returns status=FAILED + error_message="boom"
//!
//! ### Scenario: put_suspended atomically inserts then suspends (V5.31 P1)
//! - **Given** an empty `rust_decision_flow_state` table
//! - **When** `put_suspended("p1", "p1-run", "ct", "UserTask", "v1", "approve", payload)`
//! - **Then** select returns status=WAITING_CALLBACK (NOT PENDING — the old
//!   2-query pattern would have left the row stuck at PENDING if the
//!   second query failed; this is the V5.31 P1 atomicity guarantee)
//! - **And** `output_model` round-trips the payload
//!
//! ### Scenario: put_suspended rolls back the whole tx on second-query failure (V5.31 P1)
//! - **Given** an empty `rust_decision_flow_state` table
//! - **When** `put_suspended` is called with a `flow_xml_version` that
//!   violates the column's `VARCHAR(200)` CHECK (over-length string)
//! - **Then** the call returns `Err`
//! - **And** `select_by_flow_run_id` returns `None` — the PENDING row
//!   from Phase 1 was rolled back along with the failed Phase 2
//!
//! ### Scenario: mark_terminal_with_vars commits status + row_vars atomically (V5.31 P1)
//! - **Given** a fresh `insert_start` row
//! - **When** `mark_terminal_with_vars(.., FlowStatus::Completed, "end", "EndEvent", vars, 123)`
//! - **Then** select returns status=COMPLETED + row_vars round-tripped +
//!   progress=1.0 + total_execution_ms=123 (all in one tx — the
//!   V5.31+ SAGA "pre-compensation vars snapshot on FAILED" lands
//!   here with a `target_status=Failed` call)

use std::sync::Arc;
use std::time::Duration;

use rf_state::persistence::PgStateStore;
use rf_state::state_row::{FlowStatus, WaitType};
use serde_json::json;
use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;

/// Resolve the pg URL. Returns `None` if `PG_URL` is unset / unreachable;
/// the test should `return` in that case (per BDD, tests must not silently
/// skip — but a missing live DB is a build-environment issue, not a test
/// failure).
async fn try_pool() -> Option<PgPool> {
    let url = std::env::var("PG_URL")
        .unwrap_or_else(|_| "postgres://ruleforge:ruleforge@127.0.0.1:5433/ruleforge_rust".into());
    let pool = PgPoolOptions::new()
        .max_connections(8)
        .acquire_timeout(Duration::from_secs(2))
        .connect(&url)
        .await
        .ok()?;
    Some(pool)
}

async fn fresh_table(pool: &PgPool) {
    sqlx::query("TRUNCATE TABLE rust_decision_flow_state RESTART IDENTITY")
        .execute(pool)
        .await
        .expect("truncate");
}

async fn run_migrations(pool: &PgPool) {
    rf_state::migrate(pool)
        .await
        .expect("migrate rust_decision_flow_state");
}

#[tokio::test]
async fn given_empty_table_when_insert_start_then_mark_completed_then_status_completed() {
    let Some(pool) = try_pool().await else {
        eprintln!("PG_URL not reachable, skipping");
        return;
    };
    run_migrations(&pool).await;
    fresh_table(&pool).await;

    let store = PgStateStore::new(pool.clone());

    store
        .insert_start(
            "loan_flow",
            "run-1",
            Some("start"),
            Some("StartEvent"),
            Some("v1"),
        )
        .await
        .expect("insert_start");

    let row = store
        .select_by_flow_run_id("run-1")
        .await
        .expect("select")
        .expect("row exists");
    assert_eq!(row.flow_id, "loan_flow");
    assert_eq!(row.status, FlowStatus::Pending);
    assert_eq!(row.current_node_id.as_deref(), Some("start"));

    store
        .mark_completed(
            "run-1",
            Some("end"),
            json!({"approved": true, "score": 0.83}),
            42,
        )
        .await
        .expect("mark_completed");

    let row = store
        .select_by_flow_run_id("run-1")
        .await
        .expect("select")
        .expect("row exists");
    assert_eq!(row.status, FlowStatus::Completed);
    assert_eq!(row.current_node_id.as_deref(), Some("end"));
    assert_eq!(row.total_execution_ms, 42);
    assert_eq!(row.row_vars, Some(json!({"approved": true, "score": 0.83})));
}

#[tokio::test]
async fn given_inserted_run_when_mark_suspended_then_wait_type_recorded() {
    let Some(pool) = try_pool().await else {
        return;
    };
    run_migrations(&pool).await;
    fresh_table(&pool).await;

    let store = PgStateStore::new(pool.clone());
    store
        .insert_start("flow", "run-sus", Some("u1"), Some("UserTask"), Some("v1"))
        .await
        .expect("insert");

    let payload = json!({"decisionField": "approve", "label": "审批"});
    store
        .mark_suspended(
            "run-sus",
            Some("u1"),
            Some("UserTask"),
            WaitType::UserTask,
            "approve",
            None,
            payload.clone(),
        )
        .await
        .expect("mark_suspended");

    let row = store
        .select_by_flow_run_id("run-sus")
        .await
        .expect("select")
        .expect("row exists");
    assert_eq!(row.status, FlowStatus::WaitingCallback);
    assert_eq!(row.wait_type, Some(WaitType::UserTask));
    assert_eq!(row.wait_ref.as_deref(), Some("approve"));
    assert_eq!(row.output_model, Some(payload));
}

#[tokio::test]
async fn given_advisory_lock_contention_when_holder_active_then_contenders_see_false() {
    let Some(pool) = try_pool().await else {
        return;
    };
    run_migrations(&pool).await;
    fresh_table(&pool).await;

    let store = Arc::new(PgStateStore::new(pool.clone()));
    // Hold a lock on the main task for 200ms; meanwhile spawn 5
    // contenders; every contender must see `false`. After the holder
    // releases, a fresh call must succeed.
    let key = "contended-run";
    let main_lock = store.try_advisory_lock(key).await.expect("holder try");
    assert!(main_lock, "main lock should be acquired");

    let contender_store = Arc::clone(&store);
    let contender = tokio::spawn(async move {
        let mut conn = contender_store
            .acquire_conn()
            .await
            .expect("contender conn");
        sqlx::query_scalar::<_, bool>("SELECT pg_try_advisory_lock(hashtext($1))")
            .bind(key)
            .fetch_one(&mut *conn)
            .await
            .expect("contender try")
    });
    let contender_got = contender.await.unwrap();
    assert!(
        !contender_got,
        "contender must NOT acquire while holder holds"
    );

    // Holder releases; the next caller succeeds.
    let released = store.release_advisory_lock(key).await.expect("release");
    assert!(released, "release should return true");
    let fresh = store.try_advisory_lock(key).await.expect("fresh try");
    assert!(fresh, "after release, fresh caller acquires");
    let _ = store.release_advisory_lock(key).await;
}

#[tokio::test]
async fn given_released_lock_when_second_caller_acquires_then_true() {
    let Some(pool) = try_pool().await else {
        return;
    };
    run_migrations(&pool).await;
    fresh_table(&pool).await;

    let store = PgStateStore::new(pool.clone());
    let first = store.try_advisory_lock("seq").await.expect("first try");
    let second = store.try_advisory_lock("seq").await.expect("second try");
    assert!(first, "first call should acquire");
    assert!(!second, "second concurrent call should NOT acquire");
    let released = store.release_advisory_lock("seq").await.expect("release");
    assert!(released, "release should return true after successful lock");
    let third = store.try_advisory_lock("seq").await.expect("third try");
    assert!(third, "after release, third call should acquire");
}

#[tokio::test]
async fn given_recoverable_table_when_select_recoverable_then_only_ready_rows_returned() {
    let Some(pool) = try_pool().await else {
        return;
    };
    run_migrations(&pool).await;
    fresh_table(&pool).await;

    let store = PgStateStore::new(pool.clone());
    // Ready WAITING_CALLBACK row (next_retry_at NULL)
    store
        .insert_start("f", "ready-1", Some("n1"), Some("UserTask"), Some("v1"))
        .await
        .unwrap();
    store
        .mark_suspended(
            "ready-1",
            Some("n1"),
            Some("UserTask"),
            WaitType::UserTask,
            "approve",
            None,
            json!({}),
        )
        .await
        .unwrap();
    // Not-yet-ready WAITING_CALLBACK row (next_retry_at = +1h)
    store
        .insert_start("f", "future-1", Some("n1"), Some("UserTask"), Some("v1"))
        .await
        .unwrap();
    store
        .mark_suspended(
            "future-1",
            Some("n1"),
            Some("UserTask"),
            WaitType::UserTask,
            "approve",
            Some(chrono::Utc::now() + chrono::Duration::hours(1)),
            json!({}),
        )
        .await
        .unwrap();
    // COMPLETED row — should be ignored
    store
        .insert_start("f", "done-1", Some("n1"), Some("UserTask"), Some("v1"))
        .await
        .unwrap();
    store
        .mark_completed("done-1", Some("end"), json!({}), 1)
        .await
        .unwrap();

    let recoverable = store
        .select_recoverable_skip_locked(10)
        .await
        .expect("recoverable");
    let ids: Vec<&str> = recoverable.iter().map(|r| r.flow_run_id.as_str()).collect();
    assert!(ids.contains(&"ready-1"), "should include ready-1: {ids:?}");
    assert!(
        !ids.contains(&"future-1"),
        "should NOT include future-1: {ids:?}"
    );
    assert!(
        !ids.contains(&"done-1"),
        "should NOT include done-1: {ids:?}"
    );
}

#[tokio::test]
async fn given_running_run_when_mark_failed_then_error_message_persisted() {
    let Some(pool) = try_pool().await else {
        return;
    };
    run_migrations(&pool).await;
    fresh_table(&pool).await;

    let store = PgStateStore::new(pool.clone());
    store
        .insert_start("f", "fail-1", Some("n1"), Some("ServiceTask"), Some("v1"))
        .await
        .unwrap();
    store
        .mark_failed("fail-1", Some("n1"), "rule engine exploded")
        .await
        .unwrap();

    let row = store
        .select_by_flow_run_id("fail-1")
        .await
        .unwrap()
        .expect("row");
    assert_eq!(row.status, FlowStatus::Failed);
    assert_eq!(row.error_message.as_deref(), Some("rule engine exploded"));
}

// ----- 7: put_suspended atomically inserts then suspends (V5.31 P1) -----

/// V5.31 P1 — `put_suspended` folds `insert_start` (PENDING) +
/// `mark_suspended` (WAITING_CALLBACK) into a single transaction.
/// The row's terminal state is always either PENDING-then-rolled-back
/// (nothing on disk) or WAITING_CALLBACK (suspend committed), never
/// "PENDING forever because the second query failed". The old
/// 2-query `PgInflightStore::put` pattern broke this guarantee.
#[tokio::test]
async fn given_empty_table_when_put_suspended_then_status_is_waiting_callback_atomically() {
    let Some(pool) = try_pool().await else {
        return;
    };
    run_migrations(&pool).await;
    fresh_table(&pool).await;

    let store = PgStateStore::new(pool.clone());
    let payload = json!({
        "waitType": "USER_TASK",
        "waitRef": "approver",
        "payload": {"role": "manager"},
    });
    store
        .put_suspended(
            "p1",
            "p1-run",
            Some("userTask-1"),
            Some("UserTask"),
            Some("v1"),
            WaitType::UserTask,
            "approver",
            None,
            payload.clone(),
        )
        .await
        .expect("put_suspended");

    // Phase 1 (PENDING) + Phase 2 (WAITING_CALLBACK) committed
    // together — the row is fully visible as WAITING_CALLBACK. The
    // old 2-query pattern would also have shown this on success; the
    // difference shows up in test 8 (rollback on failure).
    let row = store
        .select_by_flow_run_id("p1-run")
        .await
        .expect("select")
        .expect("row exists after put_suspended");
    assert_eq!(row.status, FlowStatus::WaitingCallback);
    assert_eq!(row.current_node_id.as_deref(), Some("userTask-1"));
    assert_eq!(row.current_node_type.as_deref(), Some("UserTask"));
    assert_eq!(row.wait_type, Some(WaitType::UserTask));
    assert_eq!(row.wait_ref.as_deref(), Some("approver"));
    assert_eq!(row.output_model, Some(payload));
}

// ----- 8: put_suspended rolls back the whole tx on second-query failure (V5.31 P1) -----

/// V5.31 P1 atomicity test — the defining property of a tx. We
/// prove rollback with a `begin_tx` + manual mid-tx failure: the
/// INSERT (Phase 1) commits inside the tx, then a deliberately
/// invalid UPDATE (oversize `current_node_id` > VARCHAR(200))
/// fails. We drop the tx without committing — sqlx auto-rolls
/// back. The PENDING row inserted in Phase 1 must be gone.
///
/// Why not drive `put_suspended` directly? Its Phase 1 INSERT and
/// Phase 2 UPDATE bind the same `current_node_id` / `current_node_type`
/// columns, so an oversize value would fail Phase 1 itself rather
/// than "Phase 1 OK, Phase 2 fail". The hand-rolled tx below is
/// the cleanest way to exercise the rollback boundary.
#[tokio::test]
async fn given_mid_tx_failure_when_tx_dropped_then_phase1_row_rolled_back() {
    let Some(pool) = try_pool().await else {
        return;
    };
    run_migrations(&pool).await;
    fresh_table(&pool).await;

    let store = PgStateStore::new(pool.clone());
    // Hand-rolled tx: Phase 1 INSERT (will succeed), then Phase 2
    // UPDATE (will fail on oversize VARCHAR(200)). Drop without
    // commit — sqlx must auto-rollback Phase 1.
    {
        let mut tx = store.begin_tx().await.expect("begin_tx");
        sqlx::query(
            r#"
            INSERT INTO rust_decision_flow_state
                (flow_id, flow_run_id, status, current_node_id, current_node_type, flow_xml_version)
            VALUES ($1, $2, 'PENDING', $3, $4, $5)
            ON CONFLICT (flow_run_id) DO UPDATE SET
                status = 'PENDING',
                current_node_id = EXCLUDED.current_node_id,
                update_time = NOW()
            "#,
        )
        .bind("p1")
        .bind("p1-rollback")
        .bind(Some("start"))
        .bind(Some("StartEvent"))
        .bind(Some("v1"))
        .execute(&mut *tx)
        .await
        .expect("Phase 1 INSERT should succeed");
        // Phase 2 — oversize current_node_id (> VARCHAR(200))
        let oversize: String = "x".repeat(201);
        let res = sqlx::query(
            r#"
            UPDATE rust_decision_flow_state
            SET status = 'WAITING_CALLBACK',
                current_node_id = $2
            WHERE flow_run_id = $1
            "#,
        )
        .bind("p1-rollback")
        .bind(Some(&oversize))
        .execute(&mut *tx)
        .await;
        assert!(res.is_err(), "Phase 2 UPDATE should fail on oversize VARCHAR(200)");
        // `tx` drops here without commit → sqlx auto-rolls back.
    }

    // Phase 1's PENDING row must NOT be on disk — the tx rolled
    // back. The old 2-query pattern (separate `insert_start` +
    // `mark_suspended` calls) would have left the PENDING row
    // behind because Phase 1 had already committed in its own
    // implicit tx.
    let row = store.select_by_flow_run_id("p1-rollback").await.expect("select");
    assert!(
        row.is_none(),
        "PENDING row should have been rolled back; got: {row:?}"
    );
}

// ----- 9: mark_terminal_with_vars commits status + row_vars atomically (V5.31 P1) -----

/// V5.31 P1 — `mark_terminal_with_vars` writes `status` + `row_vars`
/// + `current_node_id` + `current_node_type` + `total_execution_ms`
/// in a single transaction. The single-query `mark_completed` only
/// binds `row_vars` (it doesn't accept `current_node_type` /
/// `total_execution_ms` as separate args — those are hard-coded).
/// `mark_terminal_with_vars` is the V5.31+ SAGA landing zone: a
/// `target_status=Failed` call will be the "pre-compensation vars
/// snapshot" write.
#[tokio::test]
async fn given_fresh_run_when_mark_terminal_with_vars_then_status_and_vars_commit_atomically() {
    let Some(pool) = try_pool().await else {
        return;
    };
    run_migrations(&pool).await;
    fresh_table(&pool).await;

    let store = PgStateStore::new(pool.clone());
    store
        .insert_start("p1", "p1-run", Some("start"), Some("StartEvent"), Some("v1"))
        .await
        .expect("insert_start");
    let final_vars = json!({
        "approved": true,
        "score": 0.92,
        "history": [{"step": "rule1", "result": "pass"}],
    });
    store
        .mark_terminal_with_vars(
            "p1-run",
            FlowStatus::Completed,
            Some("end"),
            Some("EndEvent"),
            final_vars.clone(),
            456,
        )
        .await
        .expect("mark_terminal_with_vars");

    let row = store
        .select_by_flow_run_id("p1-run")
        .await
        .expect("select")
        .expect("row exists");
    assert_eq!(row.status, FlowStatus::Completed);
    assert_eq!(row.current_node_id.as_deref(), Some("end"));
    assert_eq!(row.current_node_type.as_deref(), Some("EndEvent"));
    assert_eq!(row.row_vars, Some(final_vars));
    // The CASE expression in mark_terminal_with_vars' SQL flips
    // progress to 1.0 when target_status = COMPLETED.
    assert_eq!(row.progress, Some(1.0));
    assert_eq!(row.total_execution_ms, 456);
}
