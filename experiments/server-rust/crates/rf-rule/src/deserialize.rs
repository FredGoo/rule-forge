//! `KnowledgePackageWrapper` — top-level JSON loader.
//!
//! Mirrors Java `KnowledgePackageWrapper.java`:
//! ```text
//! {
//!   "knowledgePackage": { ... rule meta ... },
//!   "allNodes": [ {id, nodeType, ...} ... ],
//!   "id": "...",
//!   "version": "..."
//! }
//! ```
//!
//! The wire format stores the RETE graph as a flat list of nodes
//! (`allNodes`) plus per-`ObjectTypeNode` `lines` whose `fromNodeId` /
//! `toNodeId` are integer references into that flat list. After serde
//! deserializes, [`rebuild_lines`] walks the graph and resolves those
//! integer references to indices into the node vector.

use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use crate::model::{Line, Rete, ReteNode};

/// `KnowledgePackage` — meta about a compiled rule set. P0 keeps it
/// as a thin marker; the actual `Rete` lives on the wrapper for v0.
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
pub struct KnowledgePackage {
    /// Root of the RETE network.
    #[serde(default)]
    pub rete: Rete,
    /// If `with_else` rules are present, the `build_with_else_rules`
    /// step attaches the paired else-Rule here.
    #[serde(default, skip_serializing_if = "HashMap::is_empty")]
    pub with_else_rules: HashMap<String, crate::model::Rule>,
}

/// Top-level JSON wrapper — exactly the shape `KnowledgePackageWrapper`
/// produces in the console-app save path.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct KnowledgePackageWrapper {
    pub knowledge_package: KnowledgePackage,
    pub all_nodes: Vec<ReteNode>,
    pub id: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub version: Option<String>,
}

impl KnowledgePackageWrapper {
    /// Build a wrapper from a [`KnowledgePackage`] and a pre-built
    /// flat node list. The caller is responsible for walking the
    /// rete graph (via [`collect_all_nodes`] or by parsing the JSON
    /// `allNodes` field) — the wrapper itself does not attempt to
    /// recurse, because line targets need a global id→node map.
    pub fn from_parts(
        id: impl Into<String>,
        kp: KnowledgePackage,
        all_nodes: Vec<ReteNode>,
        version: Option<String>,
    ) -> Self {
        Self {
            knowledge_package: kp,
            all_nodes,
            id: id.into(),
            version,
        }
    }

    /// Resolve `Line.from_node_id` / `to_node_id` into node indices.
    /// After this call, each `Line` carries a usable `from` / `to`
    /// index into `self.all_nodes`.
    pub fn build_deserialize(&mut self) {
        // Build a id→index map for fast lookup. Include the OTNs at
        // positions after `all_nodes` so an OTN's own `from` resolves
        // (Java seeds it the same way: `line.setFrom(typeNode)` first).
        let mut id_index: HashMap<i32, usize> = self
            .all_nodes
            .iter()
            .enumerate()
            .map(|(i, n)| (n.id(), i))
            .collect();
        let otn_base = self.all_nodes.len();
        for (i, otn) in self
            .knowledge_package
            .rete
            .object_type_nodes
            .iter()
            .enumerate()
        {
            id_index.insert(otn.id(), otn_base + i);
        }

        for otn in &mut self.knowledge_package.rete.object_type_nodes {
            if let ReteNode::ObjectType { lines, .. } = otn {
                resolve_lines(lines, &id_index);
            }
        }

        // Recursively resolve child-node lines too (And/Or/Criteria).
        for (_i, node) in self.all_nodes.iter_mut().enumerate() {
            match node {
                ReteNode::And { lines, .. }
                | ReteNode::Or { lines, .. }
                | ReteNode::Criteria { lines, .. } => {
                    resolve_lines(lines, &id_index);
                }
                ReteNode::ObjectType { .. } | ReteNode::Terminal { .. } => {}
            }
        }
    }

    /// Build the `with_else_rules` map. Java calls this after
    /// `buildDeserialize`. For v0 we just collect `with_else: true`
    /// rules into a `HashMap<rule.id, rule>`. The cross-link
    /// (else rule pairing) is done by ID match in P5.
    pub fn build_with_else_rules(&mut self) {
        let mut map = HashMap::new();
        for node in &self.all_nodes {
            if let ReteNode::Terminal { rule, .. } = node {
                if rule.with_else {
                    map.insert(rule.id.clone(), rule.clone());
                }
            }
        }
        self.knowledge_package.with_else_rules = map;
    }
}

/// Helper: return the inner `lines: Vec<Line>` of a node if any.
fn node_lines(node: &ReteNode) -> Option<&Vec<Line>> {
    match node {
        ReteNode::ObjectType { lines, .. }
        | ReteNode::And { lines, .. }
        | ReteNode::Or { lines, .. }
        | ReteNode::Criteria { lines, .. } => Some(lines),
        ReteNode::Terminal { .. } => None,
    }
}

fn resolve_lines(lines: &mut [Line], id_index: &HashMap<i32, usize>) {
    for line in lines {
        if line.from.is_none() {
            line.from = id_index.get(&line.from_node_id).copied();
        }
        if line.to.is_none() {
            line.to = id_index.get(&line.to_node_id).copied();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::{Criteria, Left, LeftPart, LeftType, Op, Rule, RuleType, Value};

    fn simple_rule(id: &str) -> Rule {
        Rule {
            id: id.to_string(),
            name: id.to_string(),
            rule_type: Some(RuleType::Rl),
            file: None,
            salience: 0,
            effective_date: None,
            expires_date: None,
            enabled: true,
            debug: true,
            activation_group: None,
            agenda_group: None,
            auto_focus: false,
            ruleflow_group: None,
            lhs: crate::model::Lhs {
                criterions: vec![Criteria {
                    op: Op::GreaterThenEquals,
                    left: Left {
                        left_type: LeftType::Variable,
                        left_part: LeftPart::Variable {
                            variable_category: Some("A".into()),
                            variable_label: Some("age".into()),
                            variable_name: Some("age".into()),
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
                }],
            },
            rhs: crate::model::Rhs { actions: vec![] },
            r#loop: false,
            remark: None,
            with_else: false,
        }
    }

    #[test]
    fn wrapper_serde_roundtrip() {
        let otn = ReteNode::ObjectType {
            id: 1,
            object_type_class: Some("com.example.Applicant".into()),
            lines: vec![Line {
                from_node_id: 1,
                to_node_id: 2,
                from: None,
                to: None,
            }],
        };
        let crit = ReteNode::Criteria {
            id: 2,
            debug: true,
            criteria: Criteria {
                op: Op::Equals,
                left: Left {
                    left_type: LeftType::Variable,
                    left_part: LeftPart::Variable {
                        variable_category: Some("A".into()),
                        variable_label: Some("name".into()),
                        variable_name: Some("name".into()),
                        datatype: Some("String".into()),
                    },
                    arithmetic: None,
                },
                value: Some(Value::Constant {
                    constant_name: None,
                    constant_label: None,
                    constant_category: None,
                    constant_value: Some(serde_json::json!("alice")),
                }),
            },
            lines: vec![Line {
                from_node_id: 2,
                to_node_id: 3,
                from: None,
                to: None,
            }],
        };
        let term = ReteNode::Terminal {
            id: 3,
            rule: simple_rule("r1"),
        };
        let kp = KnowledgePackage {
            rete: Rete {
                object_type_nodes: vec![otn],
                activation_group_retes_map: Default::default(),
                agenda_group_retes_map: Default::default(),
            },
            with_else_rules: Default::default(),
        };
        // all_nodes: criteria + terminal (OTN is separate on the rete).
        let mut wrap = KnowledgePackageWrapper::from_parts(
            "kp1",
            kp,
            vec![crit, term],
            Some("1.0".into()),
        );
        wrap.build_deserialize();
        let otn = &wrap.knowledge_package.rete.object_type_nodes[0];
        if let ReteNode::ObjectType { lines, .. } = otn {
            // from is the OTN itself (id=1), resolved to index 2 (after all_nodes).
            assert_eq!(lines[0].from, Some(2));
            // to is the criteria node (id=2), resolved to index 0 in all_nodes.
            assert_eq!(lines[0].to, Some(0));
        } else {
            panic!("expected ObjectType");
        }
    }

    #[test]
    fn build_with_else_rules_collects_with_else_true() {
        let mut r = simple_rule("r-with-else");
        r.with_else = true;
        let term = ReteNode::Terminal {
            id: 1,
            rule: r.clone(),
        };
        let kp = KnowledgePackage {
            rete: Rete {
                object_type_nodes: vec![],
                activation_group_retes_map: Default::default(),
                agenda_group_retes_map: Default::default(),
            },
            with_else_rules: Default::default(),
        };
        let mut wrap =
            KnowledgePackageWrapper::from_parts("kp", kp, vec![term], None);
        wrap.build_with_else_rules();
        assert!(wrap
            .knowledge_package
            .with_else_rules
            .contains_key("r-with-else"));
    }

    #[test]
    fn json_roundtrip_preserves_all_nodes() {
        let otn = ReteNode::ObjectType {
            id: 10,
            object_type_class: Some("X".into()),
            lines: vec![],
        };
        let term = ReteNode::Terminal {
            id: 11,
            rule: simple_rule("rt"),
        };
        let kp = KnowledgePackage {
            rete: Rete {
                object_type_nodes: vec![otn],
                activation_group_retes_map: Default::default(),
                agenda_group_retes_map: Default::default(),
            },
            with_else_rules: Default::default(),
        };
        let wrap = KnowledgePackageWrapper::from_parts(
            "kp-json",
            kp,
            vec![term.clone()],
            Some("1.0".into()),
        );
        let s = serde_json::to_string(&wrap).unwrap();
        let back: KnowledgePackageWrapper = serde_json::from_str(&s).unwrap();
        assert_eq!(back.id, "kp-json");
        assert_eq!(back.all_nodes.len(), 1);
        assert_eq!(back.all_nodes[0].id(), 11);
    }
}
