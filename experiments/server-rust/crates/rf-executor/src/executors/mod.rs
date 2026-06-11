//! 9 concrete NodeExecutor implementations.
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
//! - `BoundaryEventExecutor` — error / timer boundary on an activity edge
//!   (V5.27 P0). Outgoing edges of the boundary are the handler path; the
//!   main flow continues via the activity's normal edge.
//! - `SubProcessExecutor` — call a sub-flow by id and rejoin (V5.27 P0).
//!   Recursive: it `traverse()`s the sub-flow with a fresh context and
//!   copies back the output vars when it completes.
//! - `StartEventExecutor` — V5.28 P7. Manual start = `Continue`; message
//!   start = `Suspend` with `message:<eventName>` wait_ref; timer start
//!   is `Unsupported` (the scheduler in `main.rs` runs timer flows
//!   directly without going through the dispatcher).

pub mod action;
pub mod boundary_event;
pub mod gateway;
pub mod intermediate_event;
pub mod rule;
pub mod script;
pub mod start_event;
pub mod sub_process;
pub mod user_task;

pub use action::{ActionExecutor, ActionFn, MockActionRegistry};
pub use boundary_event::{BoundaryEventError, BoundaryEventExecutor, BoundaryEventKind};
pub use gateway::GatewayExecutor;
pub use intermediate_event::{IntermediateEventError, IntermediateEventExecutor, IntermediateEventKind};
pub use rule::RuleExecutor;
pub use script::ScriptExecutor;
pub use start_event::{StartEventError, StartEventExecutor, StartTrigger};
pub use sub_process::{SubProcessError, SubProcessExecutor};
pub use user_task::UserTaskExecutor;
