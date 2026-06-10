//! `ReteNode` — the 5-node RETE graph, port of `com.ruleforge.model.rete.*`.
//!
//! The wire format mirrors Java's `ReteNodeJsonDeserializer` shape:
//! each node has an integer `id`, a `nodeType` discriminant, and
//! type-specific fields. `Line` carries `fromNodeId` / `toNodeId`
//! references that get resolved to `ReteNode` after deserialization.

use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;

use super::criteria::Criteria;
use super::rule::Rule;

/// `NodeType` — discriminator. 1:1 with Java `NodeType.java`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum NodeType {
    and,
    or,
    criteria,
    namedCriteria,
    objectType,
    terminal,
}

/// `ReteNode` — tagged enum, mirrors the Java polymorphic class hierarchy.
///
/// The wire format uses `nodeType` as the tag. After deserialization, the
/// `Line.from` / `Line.to` integer references are resolved to `ReteNode`
/// via `KnowledgePackageWrapper::build_deserialize` (Java) / our
/// `rebuild_lines` (Rust).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(tag = "nodeType", rename_all = "camelCase")]
pub enum ReteNode {
    /// `ObjectTypeNode` — typed fact entry point. One per `VariableCategory`.
    #[serde(rename = "objectType")]
    ObjectType {
        id: i32,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        object_type_class: Option<String>,
        #[serde(default)]
        lines: Vec<Line>,
    },
    /// `AndNode` — joins multiple incoming paths. `to_line_count` is the
    /// expected inbound count (used by Java for cycle detection).
    #[serde(rename = "and")]
    And {
        id: i32,
        #[serde(default)]
        to_line_count: i32,
        #[serde(default)]
        lines: Vec<Line>,
    },
    /// `OrNode` — fan-in, fires when any incoming path passes.
    #[serde(rename = "or")]
    Or {
        id: i32,
        #[serde(default)]
        lines: Vec<Line>,
    },
    /// `CriteriaNode` — single `left op value` predicate.
    #[serde(rename = "criteria")]
    Criteria {
        id: i32,
        #[serde(default)]
        debug: bool,
        criteria: Criteria,
        #[serde(default)]
        lines: Vec<Line>,
    },
    /// `TerminalNode` — leaf, holds the `Rule` to fire when activated.
    #[serde(rename = "terminal")]
    Terminal {
        id: i32,
        rule: Rule,
    },
}

impl ReteNode {
    pub fn id(&self) -> i32 {
        match self {
            Self::ObjectType { id, .. }
            | Self::And { id, .. }
            | Self::Or { id, .. }
            | Self::Criteria { id, .. }
            | Self::Terminal { id, .. } => *id,
        }
    }

    pub fn node_type(&self) -> NodeType {
        match self {
            Self::ObjectType { .. } => NodeType::objectType,
            Self::And { .. } => NodeType::and,
            Self::Or { .. } => NodeType::or,
            Self::Criteria { .. } => NodeType::criteria,
            Self::Terminal { .. } => NodeType::terminal,
        }
    }
}

/// `Line` — directed edge in the RETE graph.
///
/// JSON form: `{"fromNodeId": 1, "toNodeId": 2}`. The resolved `from` /
/// `to` pointers are `#[serde(skip)]` because they're filled in by
/// `rebuild_lines` after the full node list is parsed.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Line {
    pub from_node_id: i32,
    pub to_node_id: i32,
    #[serde(skip)]
    pub from: Option<usize>,
    #[serde(skip)]
    pub to: Option<usize>,
}

/// `Rete` — the top-level network. Holds a list of `ObjectTypeNode`s and
/// optional per-group sub-retes (activation / agenda group buckets).
///
/// Java's `Rete` also carries a `ResourceLibrary` (constants table);
/// we punt that to P2 when `Constant` lookup is needed.
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
pub struct Rete {
    #[serde(default)]
    pub object_type_nodes: Vec<ReteNode>,
    /// Map<groupName, List<ReteUnit>>. `ReteUnit` is a sub-rete plus
    /// rule-name / date metadata. Flattened to a `Vec<(group_name, sub_rete)>`
    /// for v0; the full `ReteUnit` model comes in P4 (activation groups).
    #[serde(default, skip_serializing_if = "BTreeMap::is_empty")]
    pub activation_group_retes_map: BTreeMap<String, Vec<Rete>>,
    #[serde(default, skip_serializing_if = "BTreeMap::is_empty")]
    pub agenda_group_retes_map: BTreeMap<String, Vec<Rete>>,
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::left::LeftType;
    use crate::model::left_part::LeftPart;
    use crate::model::op::Op;
    use crate::model::value::Value;

    #[test]
    fn object_type_node_serde() {
        let n = ReteNode::ObjectType {
            id: 1,
            object_type_class: Some("com.example.Applicant".into()),
            lines: vec![Line {
                from_node_id: 1,
                to_node_id: 2,
                from: None,
                to: None,
            }],
        };
        let s = serde_json::to_string(&n).unwrap();
        assert!(s.contains("\"nodeType\":\"objectType\""));
        let back: ReteNode = serde_json::from_str(&s).unwrap();
        assert_eq!(back, n);
    }

    #[test]
    fn criteria_node_serde() {
        let n = ReteNode::Criteria {
            id: 3,
            debug: true,
            criteria: Criteria {
                op: Op::GreaterThen,
                left: crate::model::left_part::Left {
                    left_type: LeftType::Variable,
                    left_part: LeftPart::Variable {
                        variable_category: Some("A".into()),
                        variable_label: Some("a".into()),
                        variable_name: Some("a".into()),
                        datatype: Some("int".into()),
                    },
                    arithmetic: None,
                },
                value: Some(Value::Constant {
                    constant_name: None,
                    constant_label: None,
                    constant_category: None,
                    constant_value: Some(serde_json::json!(18)),
                }),
            },
            lines: vec![],
        };
        let s = serde_json::to_string(&n).unwrap();
        let back: ReteNode = serde_json::from_str(&s).unwrap();
        assert_eq!(back, n);
    }

    #[test]
    fn and_node_serde() {
        let n = ReteNode::And {
            id: 4,
            to_line_count: 2,
            lines: vec![Line {
                from_node_id: 1,
                to_node_id: 4,
                from: None,
                to: None,
            }],
        };
        let s = serde_json::to_string(&n).unwrap();
        let back: ReteNode = serde_json::from_str(&s).unwrap();
        assert_eq!(back, n);
    }
}
