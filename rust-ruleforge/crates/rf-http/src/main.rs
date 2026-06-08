//! `rf-http` binary entry point.
//!
//! Phase 1: axum server on `HTTP_PORT` (default 8281) with only `/health`.
//! Phase 5+: add `/ruleforge/evaluate`, `/flow/decision`, `/flow/invalidate`.

use std::net::SocketAddr;
use std::str::FromStr;

use anyhow::Context;
use clap::Parser;
use rf_http::routes::health;
use tracing::info;
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
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // tracing — respect RUST_LOG, default to info for our crates
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::try_from_default_env().unwrap_or_else(|_| {
            EnvFilter::new("info,rf_http=debug,rf_executor=debug,rf_state=debug")
        }))
        .init();

    let cli = Cli::parse();
    info!(?cli, "rf-http starting");

    let app = axum::Router::new().route("/health", axum::routing::get(health::health));

    let addr = SocketAddr::from_str(&format!("0.0.0.0:{}", cli.http_port))
        .with_context(|| format!("invalid HTTP_PORT={}", cli.http_port))?;
    info!(%addr, "listening");

    let listener = tokio::net::TcpListener::bind(&addr)
        .await
        .with_context(|| format!("bind {}", addr))?;
    axum::serve(listener, app).await.context("axum::serve")?;

    Ok(())
}
