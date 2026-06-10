//! RETE engine runtime — the activity graph that the static `Rete`
//! model compiles to.
//!
//! V5.25 P3 ships six building blocks:
//! - `activity` — `Activity` trait + `AbstractActivity` helper +
//!   `Activation` / `ActivityOutcome`
//! - `path` — `Path` with interior-mutability `passed` flag
//! - `fact_tracker` — per-cycle fact→criteria match record
//! - `evaluation_context` — per-cycle cache + working-memory handle
//! - `and_activity` / `or_activity` — join nodes (P3)
//!
//! The concrete nodes (`ObjectTypeActivity`, `CriteriaActivity`,
//! `TerminalActivity`, `AndActivity`, `OrActivity`) are siblings in
//! this module.

pub mod activity;
pub mod and_activity;
pub mod criteria_activity;
pub mod evaluation_context;
pub mod fact_tracker;
pub mod object_type_activity;
pub mod or_activity;
pub mod path;
pub mod terminal_activity;

pub use activity::{AbstractActivity, ActionTemplate, Activation, Activity, ActivityOutcome};
pub use and_activity::AndActivity;
pub use criteria_activity::CriteriaActivity;
pub use evaluation_context::{EvaluateResponse, EvaluationContext};
pub use fact_tracker::{CriteriaId, FactTracker};
pub use object_type_activity::ObjectTypeActivity;
pub use or_activity::OrActivity;
pub use path::Path;
pub use terminal_activity::TerminalActivity;
