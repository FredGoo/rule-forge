//! `rf-http` binary entry point.
//!
//! Phase 6 surface (Phase 5 routes + pg-backed state):
//! - `POST /ruleforge/evaluate`        run a flow synchronously
//! - `POST /ruleforge/flow/decision`   resume a suspended flow
//! - `POST /ruleforge/flow/invalidate` drop a flow_id from the cache
//! - `GET  /ruleforge/flow/load`       proxy to the Java console
//! - `GET  /health`                    liveness probe
//!
//! Wiring:
//! - `FlowDefinitionRepo` caches parsed BPMN, with `HttpFlowLoader`
//!   hitting the Java console on miss.
//! - `ExecutorRegistry` wires `MockRuleEngine` for v0 (Phase 7+ could
//!   swap in a real engine).
//! - If `PG_URL` is set, builds a `PgInflightStore` and a recovery
//!   loop. Otherwise falls back to the in-memory `MemInflightStore`.

use std::net::SocketAddr;
use std::str::FromStr;
use std::sync::Arc;
use std::time::Duration;

use anyhow::{Context, Result};
use clap::Parser;
use rf_executor::dispatch::ExecutorRegistry;
use rf_http::flow_def_repo::{FlowDefinitionRepo, HttpFlowLoader};
use rf_http::inflight::{InflightStore, PgInflightStore};
use rf_http::routes::{decision, evaluate, health, invalidate, load};
use rf_http::state::AppState;
use rf_rule::mock::MockRuleEngine;
use rf_state::persistence::PgStateStore;
use rf_state::recovery::RecoveryLoop;
use rf_state::Recover;
use tracing::{info, warn};
use tracing_subscriber::EnvFilter;

#[derive(Debug, Parser)]
#[command(name = "rf-http", about = "RuleForge Rust flow executor — HTTP front")]
struct Cli {
    /// Listen port (default 8281, mirrors Java executor on 8280).
    #[arg(long, env = "HTTP_PORT", default_value = "8281")]
    http_port: u16,

    /// Console URL to fetch BPMN XML from.
    /// (e.g. `http://localhost:8180`).
    #[arg(long, env = "CONSOLE_URL", default_value = "http://localhost:8180")]
    console_url: String,

    /// Postgres URL for the Rust state store.
    /// (Phase 6: e.g. `postgres://ruleforge:ruleforge@localhost:5432/ruleforge_rust`).
    #[arg(long, env = "PG_URL", default_value = "")]
    pg_url: String,

    /// Worker ID for recovery loop logging.
    #[arg(long, env = "WORKER_ID", default_value = "rust-flow-1")]
    worker_id: String,

    /// Recovery sweep interval (seconds). Default 30s, mirrors the
    /// Java `@Scheduled(fixedRate = 30_000)`.
    #[arg(long, env = "RECOVERY_INTERVAL_SECS", default_value = "30")]
    recovery_interval_secs: u64,
}

#[tokio::main]
async fn main() -> Result<()> {
    // tracing — respect RUST_LOG, default to info for our crates
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::try_from_default_env().unwrap_or_else(|_| {
            EnvFilter::new("info,rf_http=debug,rf_executor=debug,rf_state=debug")
        }))
        .init();

    let cli = Cli::parse();
    info!(?cli, "rf-http starting");

    let loader = Arc::new(HttpFlowLoader {
        base_url: cli.console_url.clone(),
        client: reqwest::Client::builder()
            .build()
            .context("build reqwest client")?,
    });
    let repo = Arc::new(FlowDefinitionRepo::new(loader));
    let registry = Arc::new(ExecutorRegistry::with_rule_engine(Arc::new(MockRuleEngine)));

    // Pick the in-flight store: pg-backed if `pg_url` is set, else
    // in-memory (dev fallback).
    let inflight: Arc<dyn InflightStore> = if cli.pg_url.is_empty() {
        warn!("PG_URL is empty — using in-memory inflight store; state lost on restart");
        Arc::new(rf_http::inflight::MemInflightStore::new())
    } else {
        let state = Arc::new(
            PgStateStore::connect(&cli.pg_url)
                .await
                .context("connect pg")?,
        );
        rf_state::migrate(state.pool())
            .await
            .context("run migrations")?;
        info!(pg_url = %cli.pg_url, "pg-backed inflight store ready");

        // Recovery loop — re-drives WAITING_CALLBACK / PENDING_ASYNC
        // rows. The HTTP layer implements `Recover` so the loop
        // can call back into the same wiring.
        let recover = Arc::new(HttpRecover {
            repo: Arc::clone(&repo),
        });
        RecoveryLoop::new(
            Arc::clone(&state),
            recover as Arc<dyn Recover>,
            cli.worker_id.clone(),
            Duration::from_secs(cli.recovery_interval_secs),
        )
        .start();
        Arc::new(PgInflightStore::new(state, Arc::clone(&repo)))
    };

    let state = AppState::with_inflight(
        repo,
        registry,
        inflight,
        cli.worker_id.clone(),
        cli.console_url.clone(),
        cli.pg_url.clone(),
    );

    let app = axum::Router::new()
        .route("/health", axum::routing::get(health::health))
        .route(
            "/ruleforge/evaluate",
            axum::routing::post(evaluate::evaluate),
        )
        .route(
            "/ruleforge/flow/decision",
            axum::routing::post(decision::decide),
        )
        .route(
            "/ruleforge/flow/invalidate",
            axum::routing::post(invalidate::invalidate),
        )
        .route("/ruleforge/flow/load", axum::routing::get(load::load))
        .with_state(state);

    let addr = SocketAddr::from_str(&format!("0.0.0.0:{}", cli.http_port))
        .with_context(|| format!("invalid HTTP_PORT={}", cli.http_port))?;
    info!(%addr, "listening");

    let listener = tokio::net::TcpListener::bind(&addr)
        .await
        .with_context(|| format!("bind {}", addr))?;
    axum::serve(listener, app).await.context("axum::serve")?;

    Ok(())
}

/// `Recover` impl that re-fetches the def via the console on cold
/// start and then asks the executor to resume. The real
/// implementation re-uses the same wiring as `/ruleforge/evaluate`
/// minus the request body (the row carries everything we need).
#[allow(dead_code)] // repo is for Phase 7
struct HttpRecover {
    repo: Arc<FlowDefinitionRepo>,
}

#[async_trait::async_trait]
impl Recover for HttpRecover {
    async fn recover(
        &self,
        _flow_run_id: &str,
        _flow_xml_version: Option<&str>,
    ) -> anyhow::Result<bool> {
        // Phase 6 stub. Phase 7 will: (a) load the def by row's
        // flow_id (cache miss → HttpFlowLoader), (b) re-hydrate
        // ctx from row_vars, (c) re-build the FlowContext, (d)
        // call traverse(), (e) write back the outcome. For now
        // the row stays in WAITING_CALLBACK and a future sweep
        // will retry.
        tracing::warn!(
            flow_run_id = %_flow_run_id,
            "RecoveryLoop picked a row but the resume path is a Phase 7 concern"
        );
        Ok(false)
    }
}
