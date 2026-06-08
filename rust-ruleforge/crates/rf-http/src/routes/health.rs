//! `GET /health` — liveness probe.
//!
//! Phase 1: trivial 200 OK. Phase 7: extend to also report DB / cache / recovery loop status.

use axum::http::StatusCode;
use axum::response::IntoResponse;
use serde_json::json;

/// Always returns 200 OK with a small JSON body. Cheap, no DB touch.
pub async fn health() -> impl IntoResponse {
    (
        StatusCode::OK,
        axum::Json(json!({
            "status": "ok",
            "service": "rust-ruleforge",
            "phase": 1,
        })),
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn health_returns_200_with_phase_marker() {
        let resp = health().await.into_response();
        assert_eq!(resp.status(), StatusCode::OK);
    }
}
