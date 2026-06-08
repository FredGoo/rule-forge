//! Postgres-backed decision flow state + recovery loop.
//!
//! Phase 1 placeholder. Real sqlx persistence + RecoveryLoop land in Phase 6.

#![allow(dead_code)]

pub mod persistence;
pub mod recovery;
pub mod serialization;
pub mod state_row;
