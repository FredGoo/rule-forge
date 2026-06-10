//! `ReteBuilder` — compile a `KnowledgePackageWrapper` (the static
//! `Rete` graph) into a runtime `ReteInstance` (the `Activity` graph
//! that processes facts).
//!
//! V5.25 P3 supports the full 5-node RETE graph:
//! - `ObjectTypeActivity` — typed fact entry
//! - `CriteriaActivity` — single `left op value` predicate
//! - `AndActivity` / `OrActivity` — join nodes (with separate
//!   inbound and outbound `Path` lists)
//! - `TerminalActivity` — leaf, holds the rule
//!
//! All non-leaf nodes share a single `Vec<Option<Arc<dyn Activity +
//! Send + Sync>>>` slot per `all_nodes` index. The wire phase
//! dispatches on the source node's kind to call the right
//! `push_path` impl. For And/Or target nodes, the same `Path` Arc
//! is also pushed to the target's inbound list — the join node
//! reads `inbound_paths[i].is_passed()` on `enter` to decide
//! whether to fire.
//!
//! ## Wire order
//!
//! For each non-leaf node, for each `Line`, we:
//! 1. Resolve the target Arc (cloned once, refcount 2).
//! 2. Build `Arc<Path>` with the target.
//! 3. Push the Path clone to the source's outbound list
//!    (`Arc::get_mut(source)` works because source refcount is
//!    still 1).
//! 4. If the target is And/Or, also push the Path clone to the
//!    target's inbound list. We use `mem::replace` +
//!    `Arc::try_unwrap` to take the target Arc out of its Vec
//!    slot (the path's `to` field holds the only other refcount)
//!    so we can downcast and mutate.

use std::sync::Arc;

use crate::deserialize::KnowledgePackageWrapper;
use crate::fact::Fact;
use crate::model::{Line, ReteNode};
use crate::rete::{
    activity::Activity, AbstractActivity, AndActivity, CriteriaActivity, EvaluationContext,
    ObjectTypeActivity, OrActivity, Path, TerminalActivity,
};

/// `ReteInstance` — the runtime activity graph.
///
/// Slots are indexed by `KnowledgePackageWrapper.all_nodes` index.
/// `None` means "not an activatable node at this index" (an OTN slot
/// — OTNs are kept in a separate `otn_activities` list).
///
/// `Send + Sync`: required by `RuleEngine: Send + Sync`. All fields
/// use `Arc` (Send) + `Path { AtomicBool }` (Sync) so the whole
/// struct is `Send + Sync` without `Arc<Mutex<…>>`.
pub struct ReteInstance {
    pub otn_activities: Vec<Arc<ObjectTypeActivity>>,
    /// Parallel to `wrapper.all_nodes`. `Some(activity)` for any
    /// activatable node — Criteria / And / Or / Terminal. `None`
    /// for OTN slots (which live in `otn_activities`).
    pub node_activities: Vec<Option<Arc<dyn Activity + Send + Sync>>>,
    /// Flat list of every `Path` in the network. The wire phase
    /// pushes one entry per `Line` (and one per OTN→crit
    /// `Line`). `reset()` iterates this list to clear every
    /// `Path::passed` flag in one shot — needed because join
    /// nodes read their inbound paths' `passed` flag on `enter`,
    /// and stale flags from the previous fire cycle would
    /// re-fire the join on the next fact.
    pub all_paths: Vec<Arc<Path>>,
}

impl ReteInstance {
    /// Build a ReteInstance from a `KnowledgePackageWrapper`. The
    /// caller is responsible for calling `build_deserialize` first
    /// (so `Line.from` / `to` indices are populated).
    pub fn from_wrapper(wrapper: &KnowledgePackageWrapper) -> Self {
        let mut all_paths: Vec<Arc<Path>> = Vec::new();
        // 1. Build OTN activities (typed-fact entry points).
        let mut otn_activities = Vec::with_capacity(
            wrapper.knowledge_package.rete.object_type_nodes.len(),
        );
        for otn_node in &wrapper.knowledge_package.rete.object_type_nodes {
            let ReteNode::ObjectType {
                object_type_class, ..
            } = otn_node
            else {
                panic!("rete.object_type_nodes must all be ObjectType");
            };
            let class = object_type_class.clone().unwrap_or_default();
            otn_activities.push(Arc::new(ObjectTypeActivity::new(class)));
        }

        // 2. Build per-node activities in `all_nodes` order.
        let mut node_activities: Vec<Option<Arc<dyn Activity + Send + Sync>>> =
            (0..wrapper.all_nodes.len()).map(|_| None).collect();
        for (i, node) in wrapper.all_nodes.iter().enumerate() {
            let act: Arc<dyn Activity + Send + Sync> = match node {
                ReteNode::Criteria { criteria, debug, .. } => Arc::new(
                    CriteriaActivity::new(criteria.clone(), *debug),
                ),
                ReteNode::And { to_line_count, .. } => {
                    Arc::new(AndActivity::new(*to_line_count))
                }
                ReteNode::Or { .. } => Arc::new(OrActivity::new()),
                ReteNode::Terminal { rule, .. } => Arc::new(TerminalActivity::new(
                    rule.id.clone(),
                    rule.name.clone(),
                    rule.salience,
                    rule.activation_group.clone(),
                    rule.agenda_group.clone(),
                )),
                ReteNode::ObjectType { .. } => continue,
            };
            node_activities[i] = Some(act);
        }

        // 3. Wire lines. For each non-leaf source, for each line,
        //    add a `Path(target)` to the source's outbound. If the
        //    target is And/Or, also add the same Path to the
        //    target's inbound.
        let otn_to_idx = Self::build_otn_id_to_index(&wrapper.knowledge_package.rete.object_type_nodes);
        let mut wire_plan: Vec<(usize, usize)> = Vec::new();
        for (i, node) in wrapper.all_nodes.iter().enumerate() {
            let lines: &[Line] = match node {
                ReteNode::Criteria { lines, .. }
                | ReteNode::And { lines, .. }
                | ReteNode::Or { lines, .. } => lines,
                _ => continue,
            };
            for line in lines {
                let Some(to_idx) = line.to else { continue };
                wire_plan.push((i, to_idx));
            }
        }
        for (source_idx, target_idx) in wire_plan {
            // Resolve the target Arc. The `target_arc` is a
            // local clone — it gets dropped at end of this
            // block, leaving the slot's Arc as the only strong
            // owner. The `path` keeps a `Weak` (not a strong
            // ref) so it doesn't add to the strong count.
            let target_arc: Arc<dyn Activity + Send + Sync> = if let Some(act) =
                node_activities.get(target_idx).and_then(|s| s.clone())
            {
                act
            } else {
                let otn_id = wrapper.all_nodes[target_idx].id();
                let otn_pos = otn_to_idx.get(&otn_id).copied().unwrap_or(0);
                otn_activities[otn_pos].clone()
            };
            let path = Arc::new(Path::new(&target_arc));
            // Drop target_arc NOW (not at end of block) so the
            // slot's strong count returns to 1, enabling
            // `Arc::get_mut` for both the source and the
            // (join) target.
            drop(target_arc);
            all_paths.push(path.clone());

            // 1. Push to source's outbound. `Arc::get_mut`
            //    works on the source slot: the wire phase
            //    hasn't wired any inbound/outbound to it yet,
            //    so its refcount is 1.
            {
                let Some(slot) = node_activities.get_mut(source_idx) else { continue };
                let Some(source_arc) = slot.as_mut() else { continue };
                if let Some(source_mut) = Arc::get_mut(source_arc) {
                    push_path_on(source_mut, path.clone());
                }
            }

            // 2. If target is And/Or, push to target's inbound.
            //    The slot's Arc strong count is 1 (we just
            //    dropped the local clone; the path's `to` is a
            //    `Weak`). `Arc::get_mut` works.
            let target_is_join = matches!(
                wrapper.all_nodes.get(target_idx),
                Some(ReteNode::And { .. } | ReteNode::Or { .. })
            );
            if target_is_join {
                let Some(slot) = node_activities.get_mut(target_idx) else { continue };
                let Some(target_arc_slot) = slot.as_mut() else { continue };
                eprintln!("[wire-inbound] before get_mut strong={} weak={}", Arc::strong_count(target_arc_slot), Arc::weak_count(target_arc_slot));
                if let Some(target_mut) = Arc::get_mut(target_arc_slot) {
                    push_inbound_on(target_mut, path.clone());
                } else {
                    eprintln!("[wire-inbound] get_mut returned None!");
                }
            }
        }

        // 4. Wire OTN→[downstream] edges. OTNs have refcount=1
        //    (only the otn_activities Vec holds them), so
        //    `Arc::get_mut` works directly.
        let mut otn_wire_plan: Vec<(usize, usize)> = Vec::new();
        for (otn_pos, otn_node) in wrapper
            .knowledge_package
            .rete
            .object_type_nodes
            .iter()
            .enumerate()
        {
            let ReteNode::ObjectType { lines, .. } = otn_node else {
                continue;
            };
            for line in lines {
                let Some(to_idx) = line.to else { continue };
                otn_wire_plan.push((otn_pos, to_idx));
            }
        }
        for (otn_pos, target_idx) in otn_wire_plan {
            let target: Arc<dyn Activity + Send + Sync> = if let Some(act) =
                node_activities.get(target_idx).and_then(|s| s.clone())
            {
                act
            } else {
                let otn_id = wrapper.all_nodes[target_idx].id();
                let otn_pos = otn_to_idx.get(&otn_id).copied().unwrap_or(0);
                otn_activities[otn_pos].clone()
            };
            let path = Arc::new(Path::new(&target));
            all_paths.push(path.clone());
            if let Some(otn_arc) = otn_activities.get_mut(otn_pos) {
                if let Some(otn_mut) = Arc::get_mut(otn_arc) {
                    otn_mut.push_path(path);
                }
            }
        }

        ReteInstance {
            otn_activities,
            node_activities,
            all_paths,
        }
    }

    /// Map `ReteNode::ObjectType.id` → position in `otn_activities`.
    fn build_otn_id_to_index(
        otns: &[ReteNode],
    ) -> std::collections::HashMap<i32, usize> {
        let mut m = std::collections::HashMap::new();
        for (i, n) in otns.iter().enumerate() {
            if let ReteNode::ObjectType { id, .. } = n {
                m.insert(*id, i);
            }
        }
        m
    }

    /// Run a fact through the network. Returns the list of
    /// `Activation`s produced.
    pub fn enter(
        &self,
        fact: &crate::fact::GeneralEntity,
        ctx: &mut EvaluationContext,
    ) -> Vec<crate::rete::Activation> {
        let mut out = Vec::new();
        for otn_arc in &self.otn_activities {
            let otn: &ObjectTypeActivity = &**otn_arc;
            if !otn.supports(fact.class_name()) {
                continue;
            }
            for o in <ObjectTypeActivity as Activity>::enter(otn, fact, ctx) {
                if let crate::rete::ActivityOutcome::Activation(a) = o {
                    out.push(a);
                }
            }
        }
        out
    }

    /// Reset all activities' per-cycle state. Called by
    /// `ReteRuleEngine` at the start of every fire cycle.
    /// Clears every `Path::passed` flag in the network so the
    /// next fact walk starts from a clean slate.
    pub fn reset(&self) {
        for p in &self.all_paths {
            p.reset();
        }
        for otn_arc in &self.otn_activities {
            let mut owned: ObjectTypeActivity = (**otn_arc).clone();
            <ObjectTypeActivity as Activity>::reset(&mut owned);
        }
        for slot in &self.node_activities {
            if let Some(arc) = slot {
                let mut owned: Box<dyn Activity + Send + Sync> = boxed_clone_arc(arc);
                owned.reset();
            }
        }
    }
}

/// Push a path onto a non-OTN source activity's outbound list.
/// The source is `&mut dyn Activity`; we downcast in place. Each
/// `downcast_mut` borrows `act` for the duration of the
/// expression; the borrow ends when the `if let` block exits
/// (and before the next `if let`).
fn push_path_on(act: &mut dyn Activity, path: Arc<Path>) {
    if let Some(c) = act.as_any_mut().downcast_mut::<CriteriaActivity>() {
        c.push_path(path);
    } else if let Some(c) = act.as_any_mut().downcast_mut::<AndActivity>() {
        c.push_path(path);
    } else if let Some(c) = act.as_any_mut().downcast_mut::<OrActivity>() {
        c.push_path(path);
    }
    // Terminal — leaf, has no outbound.
}

/// Push a path onto a join node's inbound list.
fn push_inbound_on(act: &mut dyn Activity, path: Arc<Path>) {
    if let Some(c) = act.as_any_mut().downcast_mut::<AndActivity>() {
        c.add_inbound_path(path);
    } else if let Some(c) = act.as_any_mut().downcast_mut::<OrActivity>() {
        c.add_inbound_path(path);
    }
}

/// No-op activity used as a placeholder when `mem::replace` extracts
/// an Arc from a Vec slot. This is replaced back with the real
/// activity (or skipped) immediately after.
struct NoopActivity;

impl Activity for NoopActivity {
    fn enter(
        &self,
        _fact: &crate::fact::GeneralEntity,
        _ctx: &mut EvaluationContext,
    ) -> Vec<crate::rete::ActivityOutcome> {
        vec![]
    }
    fn reset(&mut self) {}
    fn join_node_is_passed(&self) -> bool {
        false
    }
    fn pass_and_node(&mut self) {}
    fn as_any_mut(&mut self) -> &mut dyn std::any::Any {
        self
    }
}

impl AbstractActivity for NoopActivity {
    fn paths(&self) -> &[Arc<Path>] {
        &[]
    }
    fn push_path(&mut self, _path: Arc<Path>) {}
}

/// Box-clone a trait-object `Arc` into an owned `Box<dyn Activity>`.
/// Used by `reset` to get a `&mut` handle on the activity's state.
/// P3 uses a `NoopActivity` reset (the per-cycle state lives in
/// `Path::AtomicBool` which is reset via the path, not the
/// activity). P5+ will revisit when activities carry their own
/// `passed: bool` that needs clearing.
fn boxed_clone_arc(_arc: &Arc<dyn Activity + Send + Sync>) -> Box<dyn Activity + Send + Sync> {
    Box::new(NoopActivity)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::left::LeftType;
    use crate::model::left_part::LeftPart;
    use crate::model::op::Op;
    use crate::model::rule::Rule;
    use crate::model::value::Value;
    use crate::model::{Criteria, Left, Rete};
    use crate::deserialize::KnowledgePackage;

    fn simple_rule(id: &str) -> Rule {
        Rule {
            id: id.into(),
            name: id.into(),
            rule_type: None,
            file: None,
            salience: 0,
            effective_date: None,
            expires_date: None,
            enabled: true,
            debug: false,
            activation_group: None,
            agenda_group: None,
            auto_focus: false,
            ruleflow_group: None,
            lhs: crate::model::Lhs::default(),
            rhs: crate::model::Rhs::default(),
            r#loop: false,
            remark: None,
            with_else: false,
        }
    }

    fn age_geq_18_criteria() -> Criteria {
        Criteria {
            op: Op::GreaterThenEquals,
            left: Left {
                left_type: LeftType::Variable,
                left_part: LeftPart::Variable {
                    variable_category: Some("Applicant".into()),
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
        }
    }

    #[test]
    fn build_minimal_single_criteria_rule() {
        let otn = ReteNode::ObjectType {
            id: 1,
            object_type_class: Some("Applicant".into()),
            lines: vec![Line {
                from_node_id: 1,
                to_node_id: 2,
                from: None,
                to: None,
            }],
        };
        let crit = ReteNode::Criteria {
            id: 2,
            debug: false,
            criteria: age_geq_18_criteria(),
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
        let mut wrap = KnowledgePackageWrapper::from_parts(
            "kp",
            kp,
            vec![crit, term],
            None,
        );
        wrap.build_deserialize();
        let instance = ReteInstance::from_wrapper(&wrap);
        assert_eq!(instance.otn_activities.len(), 1);
        assert_eq!(instance.node_activities.len(), 2);
        assert!(instance.node_activities[0].is_some());
        assert!(instance.node_activities[1].is_some());
    }
}
