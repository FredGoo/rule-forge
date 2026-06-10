//! 6 concrete NodeExecutor implementations.
//!
//! - `RuleExecutor`   — delegates to `dyn RuleEngine` (mock for Phase 4)
//! - `ActionExecutor` — calls `fn(&mut Vars)` from a mock registry
//! - `ScriptExecutor` — stub: rhai supported as format="rhai", no-op for now
//! - `GatewayExecutor` — no-op (routing happens in `next_node`)
//! - `UserTaskExecutor` — the Phase 4 keystone: returns `NodeResult::Suspend`
//!   and writes `current_awaiting_field` for the next gateway's binary
//!   decision routing
//! - `IntermediateEventExecutor` — message / signal / timer catch events
//!   that suspend the flow until an external event arrives (V5.26 P0)

pub mod action;
pub mod gateway;
pub mod intermediate_event;
pub mod rule;
pub mod script;
pub mod user_task;

pub use action::{ActionExecutor, ActionFn, MockActionRegistry};
pub use gateway::GatewayExecutor;
pub use intermediate_event::{IntermediateEventError, IntermediateEventExecutor, IntermediateEventKind};
pub use rule::RuleExecutor;
pub use script::ScriptExecutor;
pub use user_task::UserTaskExecutor;
