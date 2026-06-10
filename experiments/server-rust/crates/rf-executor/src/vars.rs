//! Process variable bag — V5.25: 升格为 WorkingMemory(简化版)。
//!
//! ## V5.25 模型
//!
//! - **fact** = `(FactId, Value)` 对,带 `class` 索引
//! - **var** = 路径(`.` 分隔) → Value 的"赋值"语义(像 Java 的 VariableCategoryValue
//!   assign to a category variable)
//! - 一个 Vars 实例同时存 **facts**(RETE working memory)和 **vars**(rule 的
//!   input/output 共享通道)。这两者在 ReteRuleEngine 内部可以互相转换
//!   (assign 时 implicit assert_fact,等等)
//!
//! 现有 6 个测试文件用 `vars.insert("key", value)` 风格 — V5.25 P0 改
//! 成 `vars.assign("key", value)` 或 `vars.assert_fact("Class", value)`。
//! 行为兼容(都是写值),只是 API 名字明确。
//!
//! ## 设计决策
//!
//! - `BTreeMap<FactId, Value>` 做 fact 存储(BTreeMap 给 PartialEq + 确定性遍历)
//! - `BTreeMap<FactId, String>` 做 fact → class 反向索引
//! - `BTreeMap<String, BTreeSet<FactId>>` 做 class → facts 索引(RETE 用)
//! - `BTreeMap<String, Value>` 做 var 赋值(RETE 不依赖这个,但 HTTP
//!   handler 序列化 / 调试需要)
//! - `fire_epoch: u64` per-fire cycle 计数(RETE EvaluationContext
//!   按 epoch 清缓存)

use std::collections::{BTreeMap, BTreeSet};

use serde::Serialize;
use serde_json::Value;

/// 单调递增的 fact ID。Java 端用 Object identity(无 ID),Rust 这边
/// 给每个 fact 分配一个 id,主要是为了 (a) FactTracker 跟踪 join
/// 状态 (b) class_index 用 id set 表达。
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct FactId(pub u64);

/// 单调递增的 fire epoch,每 `reset_fire_epoch()` 一次 +1。
/// RETE 的 EvaluationContext 缓存(`criteria_value_map` /
/// `part_value_map`)按 fire_epoch 清空,跟 Java `EvaluationContextImpl.clean()`
/// 行为对齐。
pub type FireEpoch = u64;

/// 通配 class name,对应 Java 端 `"__*__"`。
pub const WILDCARD_CLASS: &str = "__*__";

#[derive(Debug, Clone, Default, PartialEq)]
pub struct Vars {
    /// facts — RETE working memory
    pub facts: BTreeMap<FactId, Value>,
    /// fact → class 反向索引
    pub fact_class: BTreeMap<FactId, String>,
    /// class → facts 索引
    class_index: BTreeMap<String, BTreeSet<FactId>>,
    /// var assignments(rule input/output 共享通道,path → value)
    pub var_assigns: BTreeMap<String, Value>,
    next_fact_id: u64,
    fire_epoch: u64,
}

impl Vars {
    pub fn new() -> Self {
        Self::default()
    }

    // ---- V5.25: WorkingMemory API ----

    /// `assert_fact` — insert 一个 typed fact 到 working memory。
    /// 对应 Java `WorkingMemory.assertFact(Object)`。
    pub fn assert_fact(&mut self, class_name: impl Into<String>, value: Value) -> FactId {
        let class = class_name.into();
        let id = self.alloc_fact_id();
        self.facts.insert(id, value);
        self.fact_class.insert(id, class.clone());
        self.class_index.entry(class).or_default().insert(id);
        id
    }

    /// `update` — 替换已有 fact(按 id)。返回 true 表示更新成功。
    pub fn update(&mut self, id: FactId, new_value: Value) -> bool {
        if !self.facts.contains_key(&id) {
            return false;
        }
        self.facts.insert(id, new_value);
        true
    }

    /// `retract` — 删 fact(按 id)。返回 true 表示删除成功。
    pub fn retract(&mut self, id: FactId) -> bool {
        let Some(class) = self.fact_class.remove(&id) else {
            return false;
        };
        self.facts.remove(&id);
        if let Some(set) = self.class_index.get_mut(&class) {
            set.remove(&id);
            if set.is_empty() {
                self.class_index.remove(&class);
            }
        }
        true
    }

    /// `facts_of_class` — 按 class 查所有同 class 的 facts(确定性顺序)。
    pub fn facts_of_class(&self, class_name: &str) -> Vec<&Value> {
        match self.class_index.get(class_name) {
            None => Vec::new(),
            Some(ids) => ids.iter().filter_map(|id| self.facts.get(id)).collect(),
        }
    }

    /// `class_of` — 查 fact 的 class name。
    pub fn class_of(&self, id: FactId) -> Option<&str> {
        self.fact_class.get(&id).map(String::as_str)
    }

    /// `get_fact` — 按 id 查 fact value。
    pub fn get_fact(&self, id: FactId) -> Option<&Value> {
        self.facts.get(&id)
    }

    /// `current_fire_epoch` — 现在是第几轮 fire。
    pub fn current_fire_epoch(&self) -> FireEpoch {
        self.fire_epoch
    }

    /// `reset_fire_epoch` — fire 周期开始时调用(RETE engine 内部用)。
    pub fn reset_fire_epoch(&mut self) -> FireEpoch {
        self.fire_epoch += 1;
        self.fire_epoch
    }

    // ---- var assign 通道(rule input/output) ----

    /// `assign` — 给 var 路径赋值。对应 Java `WorkingMemory.setSessionValue` /
    /// rule action `VariableAssignAction`。
    pub fn assign(&mut self, key: impl Into<String>, val: Value) -> Option<Value> {
        self.var_assigns.insert(key.into(), val)
    }

    /// `insert` — V5.25 之前的旧 API,等价于 `assign`。
    /// 保留作为 alias,主要给 action closure body 用(短名字顺口)。
    /// **新代码请用 `assign`**。
    #[inline]
    pub fn insert(&mut self, key: impl Into<String>, val: Value) -> Option<Value> {
        self.assign(key, val)
    }

    /// `assign_serialized` — assign 一个 Serialize 值。
    pub fn assign_serialized<T: Serialize>(
        &mut self,
        key: impl Into<String>,
        val: T,
    ) -> Result<(), serde_json::Error> {
        let v = serde_json::to_value(val)?;
        self.assign(key, v);
        Ok(())
    }

    /// `get` — 读 var 路径(直接查 `var_assigns`,不走 fact tree)。
    /// 跟 Java `KnowledgeSession.getSessionValue` 对齐。
    pub fn get(&self, key: &str) -> Option<&Value> {
        self.var_assigns.get(key)
    }

    pub fn get_str(&self, key: &str) -> Option<&str> {
        self.get(key).and_then(Value::as_str)
    }

    pub fn get_i64(&self, key: &str) -> Option<i64> {
        self.get(key).and_then(Value::as_i64)
    }

    /// `get_bool` — 避免 lifetime 问题,返回 owned `bool`。
    pub fn get_bool(&self, key: &str) -> Option<bool> {
        self.get(key).and_then(Value::as_bool)
    }

    /// `resolve_path` — 在 var_assigns 里 walk `.` 路径,支持嵌套 Object。
    /// 跟 Java `ConditionEvaluator.resolveVariable()` 对齐。
    pub fn resolve_path(&self, path: &str) -> Option<&Value> {
        let mut iter = path.split('.');
        let first = iter.next()?;
        let mut cur = self.var_assigns.get(first)?;
        for segment in iter {
            cur = cur.as_object()?.get(segment)?;
        }
        Some(cur)
    }

    /// `as_object` — 暴露 var_assigns 给 JSON 序列化器 / 调试用。
    /// 注意:历史 API 返回 `&BTreeMap<String, Value>`,V5.25 沿用。
    pub fn as_object(&self) -> &BTreeMap<String, Value> {
        &self.var_assigns
    }

    /// `into_inner` — 拿走 var_assigns 的所有权(给 persistence layer 用)。
    pub fn into_inner(self) -> BTreeMap<String, Value> {
        self.var_assigns
    }

    /// `into_parts` — 拆成 (facts, var_assigns),给 row_vars 持久化用。
    pub fn into_parts(
        self,
    ) -> (
        BTreeMap<FactId, Value>,
        BTreeMap<FactId, String>,
        BTreeMap<String, Value>,
    ) {
        (self.facts, self.fact_class, self.var_assigns)
    }

    fn alloc_fact_id(&mut self) -> FactId {
        let id = FactId(self.next_fact_id);
        self.next_fact_id += 1;
        id
    }
}

/// Walk a `Value::Object` root following a dot-separated path. Used by
/// `ConditionEvaluator` to resolve a token against a freshly built root.
pub fn resolve_path<'a>(root: &'a Value, path: &str) -> Option<&'a Value> {
    let mut cur = root;
    for segment in path.split('.') {
        cur = cur.as_object()?.get(segment)?;
    }
    Some(cur)
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn assign_and_get() {
        let mut v = Vars::new();
        v.assign("age", json!(20));
        assert_eq!(v.get("age"), Some(&json!(20)));
    }

    #[test]
    fn assert_fact_assigns_id_and_indexes_class() {
        let mut v = Vars::new();
        let id = v.assert_fact("User", json!({"name": "alice", "age": 30}));
        assert_eq!(v.class_of(id), Some("User"));
        let users = v.facts_of_class("User");
        assert_eq!(users.len(), 1);
        assert_eq!(users[0], &json!({"name": "alice", "age": 30}));
    }

    #[test]
    fn update_replaces_value() {
        let mut v = Vars::new();
        let id = v.assert_fact("User", json!({"age": 30}));
        assert!(v.update(id, json!({"age": 31})));
        assert_eq!(v.facts_of_class("User")[0], &json!({"age": 31}));
    }

    #[test]
    fn update_unknown_id_returns_false() {
        let mut v = Vars::new();
        assert!(!v.update(FactId(9999), json!({})));
    }

    #[test]
    fn retract_removes_fact_and_cleans_class_index() {
        let mut v = Vars::new();
        let id = v.assert_fact("User", json!({"age": 30}));
        assert!(v.retract(id));
        assert_eq!(v.facts_of_class("User").len(), 0);
        assert!(v.class_index.get("User").is_none());
        assert!(!v.retract(id)); // 已删
    }

    #[test]
    fn fire_epoch_increments() {
        let mut v = Vars::new();
        assert_eq!(v.current_fire_epoch(), 0);
        let e1 = v.reset_fire_epoch();
        assert_eq!(e1, 1);
        let e2 = v.reset_fire_epoch();
        assert_eq!(e2, 2);
    }

    #[test]
    fn multiple_facts_same_class() {
        let mut v = Vars::new();
        v.assert_fact("User", json!({"id": 1}));
        v.assert_fact("User", json!({"id": 2}));
        v.assert_fact("Order", json!({"total": 100}));
        assert_eq!(v.facts_of_class("User").len(), 2);
        assert_eq!(v.facts_of_class("Order").len(), 1);
        assert_eq!(v.facts_of_class("Product").len(), 0);
    }

    #[test]
    fn resolve_path_walks_nested_object() {
        let mut v = Vars::new();
        v.assign("applicant", json!({"age": 25, "income": 8000}));
        assert_eq!(v.resolve_path("applicant.age"), Some(&json!(25)));
        assert_eq!(v.resolve_path("applicant.income"), Some(&json!(8000)));
        assert_eq!(v.resolve_path("missing.path"), None);
    }

    #[test]
    fn fact_and_var_are_separate() {
        let mut v = Vars::new();
        v.assert_fact("User", json!({"name": "alice"}));
        v.assign("applicant", json!({"age": 25}));
        // facts 走 facts_of_class
        assert_eq!(v.facts_of_class("User").len(), 1);
        // var 走 get (flat) / resolve_path (walks nested object)
        assert_eq!(v.get("applicant"), Some(&json!({"age": 25})));
        assert_eq!(v.resolve_path("applicant.age"), Some(&json!(25)));
    }
}
