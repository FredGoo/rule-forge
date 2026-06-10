//! `Agenda` — the rule-firing scheduler. V5.25 P4 ships the
//! salience-sorting MVP; activation_group / agenda_group / loop /
//! date filters come in the next P4 sub-phases.
//!
//! The Agenda is a thin layer over the activations the
//! `ReteRuleEngine` collects from the network. The engine walks
//! facts, the network emits `Activation` outcomes, the engine
//! pushes them into the Agenda, and pops them in salience order
//! to fire.
//!
//! ## Java parity
//!
//! Java's `Agenda` is a nested `RuleBox` tree:
//! - `ActivationRuleBox` — top-level, holds all activations.
//! - `ActivationGroupRuleBox` — one per `activation_group`,
//!   mutually exclusive (only one rule per group can fire per
//!   cycle).
//! - `AgendaGroupRuleBox` — one per `agenda_group`, only fires
//!   when focused (Drools' `ksession.getAgenda().getAgendaGroup(name).setFocus()`).
//!
//! P4 ships `ActivationRuleBox` only. `ActivationGroupRuleBox` and
//! `AgendaGroupRuleBox` are P4+ follow-ups; the activation's
//! `activation_group` / `agenda_group` fields are already
//! captured by `TerminalActivity` and the engine reads them to
//! route the activation.

use std::collections::BinaryHeap;

use crate::rete::activity::Activation;

/// `Agenda` — sorted activation queue for one fire cycle.
///
/// `BinaryHeap` is a max-heap; we use `Reverse` via the
/// `Ord` impl on `Activation` to get ascending salience (Java
/// `PriorityQueue` default is also ascending by `compareTo`).
pub struct Agenda {
    activations: BinaryHeap<Activation>,
}

impl Agenda {
    pub fn new() -> Self {
        Self {
            activations: BinaryHeap::new(),
        }
    }

    /// Add an activation. The activation's `Ord` impl sorts by
    /// salience (higher first).
    pub fn add(&mut self, activation: Activation) {
        self.activations.push(activation);
    }

    /// Pop the highest-salience activation. Returns `None` when
    /// the agenda is empty.
    pub fn pop(&mut self) -> Option<Activation> {
        self.activations.pop()
    }

    /// Number of pending activations.
    pub fn len(&self) -> usize {
        self.activations.len()
    }

    pub fn is_empty(&self) -> bool {
        self.activations.is_empty()
    }

    /// Drain all pending activations. Used by `ReteRuleEngine`'s
    /// reset between fire cycles.
    pub fn clear(&mut self) {
        self.activations.clear();
    }
}

impl Default for Agenda {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn pop_returns_highest_salience_first() {
        let mut a = Agenda::new();
        a.add(Activation::new("r1", "first", 1));
        a.add(Activation::new("r2", "high", 10));
        a.add(Activation::new("r3", "mid", 5));
        let first = a.pop().unwrap();
        assert_eq!(first.rule_id, "r2");
        assert_eq!(first.salience, 10);
        let second = a.pop().unwrap();
        assert_eq!(second.rule_id, "r3");
        let third = a.pop().unwrap();
        assert_eq!(third.rule_id, "r1");
        assert!(a.pop().is_none());
    }

    #[test]
    fn empty_agenda_returns_none() {
        let mut a = Agenda::new();
        assert!(a.is_empty());
        assert!(a.pop().is_none());
    }

    #[test]
    fn with_action_template_preserved() {
        let mut a = Agenda::new();
        a.add(
            Activation::new("r1", "n", 0)
                .with_action("approved", json!(true)),
        );
        let popped = a.pop().unwrap();
        let t = popped.action_template.unwrap();
        assert_eq!(t.target, "approved");
        assert_eq!(t.value, json!(true));
    }

    #[test]
    fn clear_empties_agenda() {
        let mut a = Agenda::new();
        a.add(Activation::new("r1", "n", 1));
        a.add(Activation::new("r2", "n", 2));
        a.clear();
        assert!(a.is_empty());
    }
}
