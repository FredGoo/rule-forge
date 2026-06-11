//! Sum-type node kinds.
//!
//! `NodeKind` encodes both the BPMN element kind AND the extension data the
//! executor needs. The Java side keeps `NodeType` as a flat enum + an
//! `extensionAttrs: Map<String,String>` and re-parses those attrs on every
//! dispatch — Rust pushes the discriminator into the type so dispatch is
//! exhaustive and attribute lookups are confined to the parser.
//!
//! Compare Java `NodeExecutorRegistry` (string-keyed `Map`):
//! ```java
//! registry.get(node.getType() + ":" + ext.get("ruleforge:taskType"))
//! ```
//! Rust `match`:
//! ```ignore
//! match &node.kind {
//!     NodeKind::ServiceTask { task_type: TaskType::Rule, .. } => …,
//!     NodeKind::ServiceTask { task_type: TaskType::Action, .. } => …,
//!     …
//! }
//! ```
//! Adding a node kind is a compile-time error in the dispatch match.

use serde::{Deserialize, Serialize};

use crate::attrs::Attrs;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum NodeKind {
    StartEvent,
    EndEvent,
    ServiceTask {
        task_type: TaskType,
        attrs: Attrs,
    },
    ScriptTask {
        format: String,
        source: String,
        attrs: Attrs,
    },
    UserTask {
        decision_type: String,
        decision_field: String,
        attrs: Attrs,
    },
    ExclusiveGateway {
        attrs: Attrs,
    },
    ParallelGateway {
        attrs: Attrs,
    },
    IntermediateEvent {
        attrs: Attrs,
    },
    /// `boundaryEvent` — sits on the edge of an activity. Two flavors
    /// in V5.27: `errorBoundaryEvent` (catches a thrown error from
    /// the attached activity) and `timerBoundaryEvent` (fires after
    /// `timerDuration`; common for SLA timeouts). The
    /// `eventType` attr is the discriminator (mirrors
    /// `IntermediateEvent`'s `eventType`). Attached activity is
    /// identified by the BPMN `attachedToRef` attribute.
    ///
    /// V5.28 P1 — `attached_to` is the id of the activity this
    /// boundary is attached to (parsed from the BPMN
    /// `attachedToRef` attribute). When the activity throws an
    /// error matching this boundary's `ruleforge:errorRef`, the
    /// `traverse` driver routes the flow to this boundary's
    /// outgoing edges (the handler path) instead of the
    /// activity's normal outgoing. V5.27 treated the boundary as
    /// a sibling in the sequence flow (its outgoing was the
    /// handler path, but no actual error routing happened);
    /// V5.28 P1 is the "really attached" version. Backward
    /// compat: `attached_to == None` keeps the V5.27 sibling
    /// behaviour (executor suspends with `error:<ref>` or
    /// timer).
    BoundaryEvent {
        attached_to: Option<String>,
        attrs: Attrs,
    },
    /// Recognized but not executed by the v0 executor — parser still extracts
    /// the node so legacy BPMN files don't fail to load, but a flow that
    /// actually traverses into a subProcess will be rejected at runtime.
    SubProcess {
        attrs: Attrs,
    },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum TaskType {
    Rule,
    Action,
    Package,
    RulesPackage,
}

impl TaskType {
    /// Parse the `ruleforge:taskType` attribute value. Unknown values yield
    /// `None` so the parser can surface a clear error (a serviceTask without
    /// a recognised task type is malformed BPMN, not a silent skip).
    pub fn from_ruleforge(value: &str) -> Option<Self> {
        match value {
            "rule" | "rules" => Some(TaskType::Rule),
            "action" => Some(TaskType::Action),
            "package" => Some(TaskType::Package),
            "rulesPackage" => Some(TaskType::RulesPackage),
            _ => None,
        }
    }
}
