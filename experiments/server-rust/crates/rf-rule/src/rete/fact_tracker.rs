//! `FactTracker` — tracks which criteria a fact has matched, per
//! RETE fire. Mirrors Java `com.ruleforge.runtime.rete.FactTracker`.
//!
//! ## Shape
//!
//! Java: `Map<Object, List<BaseCriteria>>` keyed by the actual fact
//! object (uses object identity / `equals` for lookup).
//!
//! Rust port: `HashMap<FactId, Vec<CriteriaId>>`. We key by `FactId`
//! (the `u64` we mint in `Vars::assert_fact`) instead of by raw
//! `Rc<GeneralEntity>`, so:
//! 1. Equality is trivially correct (no `Hash` / `Eq` impls needed
//!    on the fact type).
//! 2. The fact can be dropped while its tracker is still alive (no
//!    dangling refs).
//!
//! ## Why Vec not HashSet
//!
//! Java uses `List<BaseCriteria>` and de-dupes inside `addObjectCriteria`
//! (only adds if the criteria isn't already in the list). We mirror
//! the linear scan for parity; for V5.25 P1 the list is at most a
//! handful of entries (single-criteria end-to-end first).
//!
//! ## `new_sub_tracker`
//!
//! Forks the current tracker for a child And/Or branch. P1 doesn't
//! need this (single-condition rule has no join), but we ship the
//! scaffolding so P3 can wire it without API churn.

use std::collections::HashMap;

use rf_executor::vars::FactId;

/// `CriteriaId` — opaque key for a `Criteria` in the tracker. We use
/// the `String` id Java computes via `Criteria.getId()` (e.g.
/// `"[变量]Applicant.age【大于等于】[常量]18"`).
pub type CriteriaId = String;

/// `FactTracker` — `fact → criteria it has matched` map.
#[derive(Debug, Default, Clone)]
pub struct FactTracker {
    by_fact: HashMap<FactId, Vec<CriteriaId>>,
}

impl FactTracker {
    pub fn new() -> Self {
        Self::default()
    }

    /// Record that `fact` matched `criteria` (de-duped per Java).
    pub fn add(&mut self, fact: FactId, criteria: CriteriaId) {
        let entry = self.by_fact.entry(fact).or_default();
        if !entry.contains(&criteria) {
            entry.push(criteria);
        }
    }

    /// Look up the criteria matched by `fact`.
    pub fn criteria_of(&self, fact: FactId) -> &[CriteriaId] {
        self.by_fact.get(&fact).map(Vec::as_slice).unwrap_or(&[])
    }

    /// All facts that have matched at least one criteria.
    pub fn facts(&self) -> impl Iterator<Item = FactId> + '_ {
        self.by_fact.keys().copied()
    }

    /// Fork for a child branch (P3). The child sees the parent's
    /// matches so far plus any new ones added in the child path.
    pub fn new_sub_tracker(&self) -> Self {
        Self {
            by_fact: self.by_fact.clone(),
        }
    }

    /// Merge another tracker into this one (used when an And / Or
    /// join consolidates branches).
    pub fn merge_from(&mut self, other: &FactTracker) {
        for (fact, criteria_list) in &other.by_fact {
            let entry = self.by_fact.entry(*fact).or_default();
            for c in criteria_list {
                if !entry.contains(c) {
                    entry.push(c.clone());
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn add_and_lookup() {
        let mut t = FactTracker::new();
        t.add(FactId(1), "c1".to_string());
        t.add(FactId(1), "c1".to_string()); // dedup
        t.add(FactId(1), "c2".to_string());
        assert_eq!(t.criteria_of(FactId(1)), &["c1".to_string(), "c2".to_string()]);
    }

    #[test]
    fn unknown_fact_returns_empty() {
        let t = FactTracker::new();
        assert!(t.criteria_of(FactId(99)).is_empty());
    }

    #[test]
    fn sub_tracker_inherits_parent() {
        let mut t = FactTracker::new();
        t.add(FactId(1), "c1".to_string());
        let sub = t.new_sub_tracker();
        assert_eq!(sub.criteria_of(FactId(1)), &["c1".to_string()]);
    }

    #[test]
    fn merge_collects_unique_criteria() {
        let mut a = FactTracker::new();
        a.add(FactId(1), "c1".to_string());
        let mut b = FactTracker::new();
        b.add(FactId(1), "c1".to_string()); // dup
        b.add(FactId(1), "c2".to_string());
        a.merge_from(&b);
        assert_eq!(
            a.criteria_of(FactId(1)),
            &["c1".to_string(), "c2".to_string()]
        );
    }
}
