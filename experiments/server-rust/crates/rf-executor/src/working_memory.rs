//! `WorkingMemory` trait — 1:1 with Java `com.ruleforge.runtime.WorkingMemory`.
//!
//! V5.25 P0 已把 `Vars` 升格为事实袋 + var 通道(就是简化版
//! WorkingMemory)。这个 trait 把"用得着的方法"显式列出来,让 RETE
//! engine 可以 trait-object 拿一个 `&dyn WorkingMemory` 调,而不必硬
//! 编码到 `Vars` 上去 — 后续 P2 / P4 加 `Agenda` 时,fact / var
//! 通道解耦,RETE engine 可以换 mock / 持久化实现。
//!
//! ## 跟 `Vars` 的关系
//!
//! `Vars` **结构上**已经实现了 `WorkingMemory` 的全部方法 — 但 trait
//! 方法是借用检查友好的显式边界,而 `Vars` 的内联方法可以继续直接
//! 调用(zero-cost)。这里 `impl WorkingMemory for Vars {}` 是空体,
//! 全靠默认方法(转调同名 inherent method)。
//!
//! ## V5.18 BeanUtils 教训
//!
//! Java `WorkingMemory` 用 `Object` + 反射,BeanUtils.populate 转换
//! 时把 String "true" → Boolean 失败导致 V5.18 全局静默默认值 bug。
//! Rust 这边 trait **不做** implicit 类型转换 — 调 `assert_fact("X",
//! value)` 时 value 已经是 `serde_json::Value`,类型由调用方负责。

use crate::vars::{FactId, Vars};
use serde_json::Value as JsonValue;

/// `WorkingMemory` — the fact / var bag the RETE engine reads and
/// writes. `Vars` is the default in-process implementation.
pub trait WorkingMemory {
    // ---- facts (RETE working memory) ----

    /// Insert a typed fact; returns the new fact id.
    fn assert_fact(&mut self, class_name: &str, value: JsonValue) -> FactId;

    /// Replace an existing fact by id; returns true on success.
    fn update(&mut self, id: FactId, new_value: JsonValue) -> bool;

    /// Remove a fact by id; returns true on success.
    fn retract(&mut self, id: FactId) -> bool;

    /// All facts of a given class. Order is deterministic (BTreeSet
    /// iteration order).
    fn facts_of_class(&self, class_name: &str) -> Vec<JsonValue>;

    /// Look up a fact by id.
    fn get_fact(&self, id: FactId) -> Option<JsonValue>;

    // ---- var assignments (rule input / output channel) ----

    /// Assign a value to a var path.
    fn assign(&mut self, key: &str, value: JsonValue) -> Option<JsonValue>;

    /// Read a var by exact key.
    fn get(&self, key: &str) -> Option<JsonValue>;

    // ---- fire-cycle bookkeeping ----

    /// Increment and return the new fire epoch. RETE engine calls
    /// this at the start of each `fireRules()` so the
    /// `EvaluationContext` can clear its per-cycle caches.
    fn reset_fire_epoch(&mut self) -> u64;

    /// Current fire epoch (0 until the first `reset_fire_epoch`).
    fn current_fire_epoch(&self) -> u64;
}

impl WorkingMemory for Vars {
    fn assert_fact(&mut self, class_name: &str, value: JsonValue) -> FactId {
        Vars::assert_fact(self, class_name, value)
    }
    fn update(&mut self, id: FactId, new_value: JsonValue) -> bool {
        Vars::update(self, id, new_value)
    }
    fn retract(&mut self, id: FactId) -> bool {
        Vars::retract(self, id)
    }
    fn facts_of_class(&self, class_name: &str) -> Vec<JsonValue> {
        Vars::facts_of_class(self, class_name)
            .into_iter()
            .cloned()
            .collect()
    }
    fn get_fact(&self, id: FactId) -> Option<JsonValue> {
        Vars::get_fact(self, id).cloned()
    }
    fn assign(&mut self, key: &str, value: JsonValue) -> Option<JsonValue> {
        Vars::assign(self, key, value)
    }
    fn get(&self, key: &str) -> Option<JsonValue> {
        Vars::get(self, key).cloned()
    }
    fn reset_fire_epoch(&mut self) -> u64 {
        Vars::reset_fire_epoch(self)
    }
    fn current_fire_epoch(&self) -> u64 {
        Vars::current_fire_epoch(self)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn vars_satisfies_working_memory() {
        let mut wm: Box<dyn WorkingMemory> = Box::new(Vars::new());
        let id = wm.assert_fact("User", json!({"age": 30}));
        assert_eq!(wm.facts_of_class("User"), vec![json!({"age": 30})]);
        assert_eq!(wm.get_fact(id), Some(json!({"age": 30})));
        wm.assign("approved", json!(true));
        assert_eq!(wm.get("approved"), Some(json!(true)));
        assert_eq!(wm.reset_fire_epoch(), 1);
        assert_eq!(wm.current_fire_epoch(), 1);
    }
}
