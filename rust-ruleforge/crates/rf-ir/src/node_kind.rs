//! Placeholder. Real NodeKind + TaskType enums land in Phase 2.

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NodeKind {
    StartEvent,
    EndEvent,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TaskType {
    Rule,
}
