//! `PgStateStore` — sqlx-backed CRUD for `rust_decision_flow_state`.
//!
//! Public surface (all `async fn`, all `Result<_, StateError>`):
//! - `insert_start`            — upsert a fresh run (status=PENDING)
//! - `mark_running`            — flip PENDING → RUNNING when traversal begins
//! - `mark_suspended`          — PENDING/RUNNING → WAITING_CALLBACK (+ wait_type, wait_ref, payload)
//! - `mark_completed`          — * → COMPLETED + write final vars
//! - `mark_failed`             — * → FAILED + error_message
//! - `select_by_flow_run_id`   — read one row (used by /flow/decision resume)
//! - `try_advisory_lock`       — `pg_try_advisory_lock(hashtext($1))` — single-key CAS
//! - `release_advisory_lock`   — symmetric unlock
//! - `select_recoverable_skip_locked` — recovery sweep query
//!
//! ## Lock model
//!
//! The authoritative cross-process CAS is the **advisory lock** keyed on
//! `hashtext(flow_run_id)` (i64). One row → one advisory-lock slot.
//! Multiple processes / threads may call `try_advisory_lock` for the
//! same `flow_run_id`; exactly one wins; the rest see `false`.
//!
//! The `locked_by / locked_at / locked_until` columns are kept for
//! observability and Java parity but are not consulted for the CAS.
//! Holding a row's advisory lock for a long time doesn't block
//! `FOR UPDATE SKIP LOCKED` on the recovery sweep (those are different
//! lock domains).
//!
//! ## Why no `sqlx::query!` macro
//!
//! `sqlx::query!` requires a compile-time DB connection (or a `.sqlx/`
//! cache) — neither is friendly to a sibling Rust workspace that should
//! `cargo build` without external services. We use `sqlx::query()` /
//! `sqlx::query_as()` (runtime string parsing) and pay the cost of
//! runtime type checks; the only types we bind are the rust enum
//! column types which we encode manually in `state_row.rs`.

use std::time::Duration;

use chrono::{DateTime, Utc};
use serde_json::Value;
use sqlx::postgres::PgPoolOptions;
use sqlx::{PgPool, Postgres, Transaction};
use thiserror::Error;

use crate::state_row::{DecisionFlowState, FlowStatus, WaitType};

#[derive(Debug, Error)]
pub enum StateError {
    #[error("pg: {0}")]
    Sqlx(#[from] sqlx::Error),
    #[error("row not found: {0}")]
    NotFound(String),
}

/// Postgres-backed state store. Cheap to clone — `PgPool` is itself an
/// `Arc` internally.
#[derive(Debug, Clone)]
pub struct PgStateStore {
    pool: PgPool,
}

impl PgStateStore {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    /// Convenience: build a `PgPool` from a `postgres://...` URL with
    /// sensible production defaults.
    pub async fn connect(url: &str) -> Result<Self, StateError> {
        let pool = PgPoolOptions::new()
            .max_connections(8)
            .acquire_timeout(Duration::from_secs(5))
            .connect(url)
            .await?;
        Ok(Self::new(pool))
    }

    /// Borrow the underlying pool (for callers that need to run their
    /// own queries — e.g. the `PgInflightStore` adapter in rf-http).
    pub fn pool(&self) -> &PgPool {
        &self.pool
    }

    /// Acquire a single connection. Used by tests that want to hold an
    /// advisory lock for the lifetime of a single session (advisory
    /// locks are connection-scoped, not pool-scoped).
    pub async fn acquire_conn(&self) -> Result<sqlx::pool::PoolConnection<Postgres>, StateError> {
        Ok(self.pool.acquire().await?)
    }

    /// Upsert a fresh run. `flow_run_id` is the unique key — repeated
    /// calls with the same `flow_run_id` reset status to PENDING and
    /// overwrite the start metadata. Mirrors the Java V5.19
    /// `DecisionFlowStateService.insertStart()` upsert.
    pub async fn insert_start(
        &self,
        flow_id: &str,
        flow_run_id: &str,
        current_node_id: Option<&str>,
        current_node_type: Option<&str>,
        flow_xml_version: Option<&str>,
    ) -> Result<(), StateError> {
        sqlx::query(
            r#"
            INSERT INTO rust_decision_flow_state
                (flow_id, flow_run_id, status, current_node_id, current_node_type, flow_xml_version)
            VALUES ($1, $2, 'PENDING', $3, $4, $5)
            ON CONFLICT (flow_run_id) DO UPDATE SET
                status = 'PENDING',
                current_node_id = EXCLUDED.current_node_id,
                current_node_type = EXCLUDED.current_node_type,
                flow_xml_version = EXCLUDED.flow_xml_version,
                update_time = NOW()
            "#,
        )
        .bind(flow_id)
        .bind(flow_run_id)
        .bind(current_node_id)
        .bind(current_node_type)
        .bind(flow_xml_version)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    /// PENDING → RUNNING. Idempotent — repeated calls leave status=RUNNING.
    pub async fn mark_running(
        &self,
        flow_run_id: &str,
        current_node_id: Option<&str>,
    ) -> Result<(), StateError> {
        sqlx::query(
            r#"
            UPDATE rust_decision_flow_state
            SET status = 'RUNNING',
                current_node_id = $2,
                retry_count = retry_count + 1
            WHERE flow_run_id = $1
            "#,
        )
        .bind(flow_run_id)
        .bind(current_node_id)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    /// * → WAITING_CALLBACK + record wait_type / wait_ref / payload. The
    ///   payload is the `SuspendInfo.payload` blob, stored in the
    ///   `output_model` jsonb column so the resume route can re-hydrate
    ///   it 1:1.
    #[allow(clippy::too_many_arguments)]
    pub async fn mark_suspended(
        &self,
        flow_run_id: &str,
        current_node_id: Option<&str>,
        current_node_type: Option<&str>,
        wait_type: WaitType,
        wait_ref: &str,
        next_retry_at: Option<DateTime<Utc>>,
        payload: Value,
    ) -> Result<(), StateError> {
        sqlx::query(
            r#"
            UPDATE rust_decision_flow_state
            SET status = 'WAITING_CALLBACK',
                current_node_id = $2,
                current_node_type = $3,
                wait_type = $4,
                wait_ref = $5,
                next_retry_at = $6,
                output_model = $7
            WHERE flow_run_id = $1
            "#,
        )
        .bind(flow_run_id)
        .bind(current_node_id)
        .bind(current_node_type)
        .bind(wait_type)
        .bind(wait_ref)
        .bind(next_retry_at)
        .bind(payload)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    /// * → COMPLETED + write final vars snapshot. `total_execution_ms`
    ///   is the cumulative execution time (sum of all step durations);
    ///   callers pass the delta for this segment.
    #[allow(clippy::too_many_arguments)]
    pub async fn mark_completed(
        &self,
        flow_run_id: &str,
        current_node_id: Option<&str>,
        vars: Value,
        total_execution_ms: i64,
    ) -> Result<(), StateError> {
        sqlx::query(
            r#"
            UPDATE rust_decision_flow_state
            SET status = 'COMPLETED',
                current_node_id = $2,
                current_node_type = 'EndEvent',
                row_vars = $3,
                progress = 1.0,
                total_execution_ms = $4
            WHERE flow_run_id = $1
            "#,
        )
        .bind(flow_run_id)
        .bind(current_node_id)
        .bind(vars)
        .bind(total_execution_ms)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    /// * → FAILED + record the error message.
    pub async fn mark_failed(
        &self,
        flow_run_id: &str,
        current_node_id: Option<&str>,
        error_message: &str,
    ) -> Result<(), StateError> {
        sqlx::query(
            r#"
            UPDATE rust_decision_flow_state
            SET status = 'FAILED',
                current_node_id = $2,
                error_message = $3
            WHERE flow_run_id = $1
            "#,
        )
        .bind(flow_run_id)
        .bind(current_node_id)
        .bind(error_message)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    /// Read one row by `flow_run_id`. Returns `None` if not present.
    pub async fn select_by_flow_run_id(
        &self,
        flow_run_id: &str,
    ) -> Result<Option<DecisionFlowState>, StateError> {
        let row = sqlx::query_as::<_, DecisionFlowState>(
            r#"
            SELECT id, flow_id, flow_run_id, user_id, order_no, status,
                   current_node_id, current_node_type, next_retry_at,
                   wait_ref, wait_type, flow_xml_version, row_vars,
                   row_entity_snapshot, output_model, progress,
                   error_message, locked_by, locked_at, locked_until,
                   retry_count, total_execution_ms, fireable_rules,
                   matched_rules, create_time, update_time
            FROM rust_decision_flow_state
            WHERE flow_run_id = $1
            "#,
        )
        .bind(flow_run_id)
        .fetch_optional(&self.pool)
        .await?;
        Ok(row)
    }

    /// `pg_try_advisory_lock(hashtext(flow_run_id))` — single-key CAS.
    /// The lock is **connection-scoped** — it lives until the
    /// connection is returned to the pool OR the caller invokes
    /// `release_advisory_lock`. We deliberately don't hold a long
    /// connection for the duration of a flow; the lock is used
    /// per-step.
    pub async fn try_advisory_lock(&self, key: &str) -> Result<bool, StateError> {
        let mut conn = self.pool.acquire().await?;
        let got = sqlx::query_scalar::<_, bool>("SELECT pg_try_advisory_lock(hashtext($1))")
            .bind(key)
            .fetch_one(&mut *conn)
            .await?;
        Ok(got)
    }

    /// `pg_advisory_unlock(hashtext(flow_run_id))`. Returns `true` if
    /// a lock was actually held and released, `false` otherwise (e.g.
    /// the caller's session didn't own the lock).
    pub async fn release_advisory_lock(&self, key: &str) -> Result<bool, StateError> {
        let mut conn = self.pool.acquire().await?;
        let released = sqlx::query_scalar::<_, bool>("SELECT pg_advisory_unlock(hashtext($1))")
            .bind(key)
            .fetch_one(&mut *conn)
            .await?;
        Ok(released)
    }

    /// Recovery sweep — `WAITING_CALLBACK` and `PENDING_ASYNC` rows whose
    /// `next_retry_at` has elapsed. Uses `FOR UPDATE SKIP LOCKED` so
    /// multiple workers can sweep concurrently without contention; the
    /// picked rows are then individually locked with
    /// `try_advisory_lock(row.id)`.
    ///
    /// Excludes `wait_type = ASYNC_DATA` — message / signal catch
    /// waits are driven by external event delivery (`/flow/event`),
    /// not by the recovery loop. A `next_retry_at IS NULL` AsyncData
    /// row would otherwise be picked up forever and re-suspended in a
    /// busy loop.
    ///
    /// Returns up to `limit` rows. The caller is responsible for the
    /// subsequent `try_advisory_lock` + `resume`.
    pub async fn select_recoverable_skip_locked(
        &self,
        limit: i64,
    ) -> Result<Vec<DecisionFlowState>, StateError> {
        let mut tx: Transaction<'_, Postgres> = self.pool.begin().await?;
        let rows = sqlx::query_as::<_, DecisionFlowState>(
            r#"
            SELECT id, flow_id, flow_run_id, user_id, order_no, status,
                   current_node_id, current_node_type, next_retry_at,
                   wait_ref, wait_type, flow_xml_version, row_vars,
                   row_entity_snapshot, output_model, progress,
                   error_message, locked_by, locked_at, locked_until,
                   retry_count, total_execution_ms, fireable_rules,
                   matched_rules, create_time, update_time
            FROM rust_decision_flow_state
            WHERE status IN ('PENDING_ASYNC', 'WAITING_CALLBACK')
              AND (next_retry_at IS NULL OR next_retry_at <= NOW())
              AND (wait_type IS NULL OR wait_type != 'ASYNC_DATA')
            ORDER BY id
            LIMIT $1
            FOR UPDATE SKIP LOCKED
            "#,
        )
        .bind(limit)
        .fetch_all(&mut *tx)
        .await?;
        // The FOR UPDATE lock is released as soon as the tx commits;
        // advisory lock is what protects the row across workers after
        // the sweep.
        tx.commit().await?;
        Ok(rows)
    }

    /// Convenience: count rows in a given status (for /health + tests).
    pub async fn count_by_status(&self, status: FlowStatus) -> Result<i64, StateError> {
        let n = sqlx::query_scalar::<_, i64>(
            "SELECT COUNT(*) FROM rust_decision_flow_state WHERE status = $1",
        )
        .bind(status)
        .fetch_one(&self.pool)
        .await?;
        Ok(n)
    }

    // ── V5.31 P1 — compound atomic writes ─────────────────────────────────
    //
    // The single-query `mark_*` methods above are already atomic (a
    // single Postgres UPDATE is its own transaction). The methods
    // below are for **composite** writes that span multiple statements
    // and would otherwise leave half-written state if the second
    // statement failed after the first one succeeded.
    //
    // V5.31 P1 SAGA — the
    // `mark_terminal_with_vars(Failed, pre_compensation_vars)` path
    // is the landing zone for V5.31+ "persist compensation trace on
    // FAILED" — the API surface is designed so adding a
    // `compensation_trace: Option<Value>` field is a one-line SQL
    // change, no method-shape refactor.

    /// V5.31 P1 — acquire a Postgres transaction tied to this
    /// store. Caller is responsible for `commit()` / `rollback()`
    /// (or letting `Transaction` drop, which sqlx auto-rolls back).
    /// Same `Transaction<'_, Postgres>` shape as
    /// `select_recoverable_skip_locked` already uses.
    pub async fn begin_tx(&self) -> Result<Transaction<'_, Postgres>, StateError> {
        Ok(self.pool.begin().await?)
    }

    /// V5.31 P1 — atomically `insert_start` (PENDING) +
    /// `mark_suspended` (WAITING_CALLBACK) in a single transaction.
    ///
    /// Replaces the old `PgInflightStore::put` two-step pattern:
    /// that pattern called `insert_start` then `mark_suspended` as
    /// independent queries; if the second failed, the row stayed
    /// PENDING forever (broke `count_by_status(WaitingCallback)` and
    /// made `/flow/decision` 404). With `put_suspended` both writes
    /// commit together or roll back together.
    ///
    /// `ON CONFLICT (flow_run_id) DO UPDATE` keeps the original
    /// `insert_start` upsert semantics: a `put_suspended` for an
    /// existing `flow_run_id` resets the row to PENDING and then
    /// flips it to WAITING_CALLBACK (the resume path's expected
    /// behaviour — `/flow/decision` resume re-suspends with new
    /// `current_node_id` and `next_retry_at`).
    #[allow(clippy::too_many_arguments)]
    pub async fn put_suspended(
        &self,
        flow_id: &str,
        flow_run_id: &str,
        current_node_id: Option<&str>,
        current_node_type: Option<&str>,
        flow_xml_version: Option<&str>,
        wait_type: WaitType,
        wait_ref: &str,
        next_retry_at: Option<DateTime<Utc>>,
        payload: Value,
    ) -> Result<(), StateError> {
        let mut tx: Transaction<'_, Postgres> = self.pool.begin().await?;
        // Phase 1 — INSERT (PENDING). On conflict, reset to PENDING
        // and refresh the start metadata. The trigger
        // `trg_rust_decision_flow_state_touch` (migration line 68-71)
        // auto-bumps `update_time`.
        sqlx::query(
            r#"
            INSERT INTO rust_decision_flow_state
                (flow_id, flow_run_id, status, current_node_id, current_node_type, flow_xml_version)
            VALUES ($1, $2, 'PENDING', $3, $4, $5)
            ON CONFLICT (flow_run_id) DO UPDATE SET
                status = 'PENDING',
                current_node_id = EXCLUDED.current_node_id,
                current_node_type = EXCLUDED.current_node_type,
                flow_xml_version = EXCLUDED.flow_xml_version,
                update_time = NOW()
            "#,
        )
        .bind(flow_id)
        .bind(flow_run_id)
        .bind(current_node_id)
        .bind(current_node_type)
        .bind(flow_xml_version)
        .execute(&mut *tx)
        .await?;
        // Phase 2 — UPDATE → WAITING_CALLBACK. We pass the same
        // `current_node_id` / `current_node_type` as the INSERT so
        // the row's "where did we suspend" pointer survives the
        // tx. The wait_type / wait_ref / next_retry_at / payload
        // (output_model jsonb) are the actual suspend payload.
        sqlx::query(
            r#"
            UPDATE rust_decision_flow_state
            SET status = 'WAITING_CALLBACK',
                current_node_id = $2,
                current_node_type = $3,
                wait_type = $4,
                wait_ref = $5,
                next_retry_at = $6,
                output_model = $7
            WHERE flow_run_id = $1
            "#,
        )
        .bind(flow_run_id)
        .bind(current_node_id)
        .bind(current_node_type)
        .bind(wait_type)
        .bind(wait_ref)
        .bind(next_retry_at)
        .bind(payload)
        .execute(&mut *tx)
        .await?;
        tx.commit().await?;
        Ok(())
    }

    /// V5.31 P1 — atomically write a status flip (COMPLETED / FAILED)
    /// + `row_vars` snapshot + `total_execution_ms` in a single
    /// transaction. The single-query `mark_completed` is already
    /// atomic, but it doesn't bind `row_vars` (vars is a separate
    /// column from the `mark_completed` call's caller-supplied
    /// snapshot in the in-memory path). This method folds both
    /// writes so the post-terminal-state row's `row_vars` is
    /// guaranteed to match `status`.
    ///
    /// V5.31+ SAGA — when the compensation path lands
    /// (compensation handler ran, outer flow becomes FAILED), this
    /// is the API: `mark_terminal_with_vars(.., Failed, ..,
    /// pre_compensation_vars_snapshot, ..)`. The snapshot is what
    /// a future "undo compensation" recovery would reload. V5.31 P1
    /// only stores the snapshot; the undo path itself is V5.31+ scope.
    #[allow(clippy::too_many_arguments)]
    pub async fn mark_terminal_with_vars(
        &self,
        flow_run_id: &str,
        target_status: FlowStatus,
        current_node_id: Option<&str>,
        current_node_type: Option<&str>,
        row_vars: Value,
        total_execution_ms: i64,
    ) -> Result<(), StateError> {
        let mut tx: Transaction<'_, Postgres> = self.pool.begin().await?;
        sqlx::query(
            r#"
            UPDATE rust_decision_flow_state
            SET status = $2,
                current_node_id = $3,
                current_node_type = $4,
                row_vars = $5,
                progress = CASE WHEN $2 = 'COMPLETED' THEN 1.0 ELSE progress END,
                total_execution_ms = $6
            WHERE flow_run_id = $1
            "#,
        )
        .bind(flow_run_id)
        .bind(target_status)
        .bind(current_node_id)
        .bind(current_node_type)
        .bind(row_vars)
        .bind(total_execution_ms)
        .execute(&mut *tx)
        .await?;
        tx.commit().await?;
        Ok(())
    }
}
