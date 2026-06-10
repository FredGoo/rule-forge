//! RETE engine runtime — the activity graph that the static `Rete`
//! model compiles to.
//!
//! V5.25 P1 ships the four P1 building blocks:
//! - `activity` — `Activity` trait + `AbstractActivity` helper +
//!   `Activation` / `ActivityOutcome`
//! - `path` — `Path` with interior-mutability `passed` flag
//! - `fact_tracker` — per-cycle fact→criteria match record
//! - `evaluation_context` — per-cycle cache + working-memory handle
//!
//! The concrete nodes (`ObjectTypeActivity`, `CriteriaActivity`,
//! `TerminalActivity`) are siblings in this module — see their
//! respective files. P3 will add `and_activity` / `or_activity` for
//! joins.

pub mod activity;
pub mod criteria_activity;
pub mod evaluation_context;
pub mod fact_tracker;
pub mod object_type_activity;
pub mod path;
pub mod terminal_activity;

pub use activity::{AbstractActivity, ActionTemplate, Activation, Activity, ActivityOutcome};
pub use criteria_activity::CriteriaActivity;
pub use evaluation_context::{EvaluateResponse, EvaluationContext};
pub use fact_tracker::{CriteriaId, FactTracker};
pub use object_type_activity::ObjectTypeActivity;
pub use path::Path;
pub use terminal_activity::TerminalActivity;
