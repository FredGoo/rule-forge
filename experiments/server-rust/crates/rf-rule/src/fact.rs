//! `Fact` trait + `GeneralEntity` — the in-Rust "object" representation.
//!
//! Java facts are arbitrary `Object` and the RETE engine uses reflection
//! (`BeanUtils.getProperty`) to read fields. In Rust we don't have a
//! runtime class system, so a "fact" is one of:
//!
//! - `GeneralEntity` — a JSON object with a class name. Used for HTTP-
//!   fed input and for deserialized working-memory objects. Field reads
//!   walk the `BTreeMap` by key.
//! - `MapFact` — alias of `GeneralEntity`, retained as a type name for
//!   symmetry with the Java `MapFact` test fixture.
//!
//! V5.25 P0 ships only these two. P2 adds a `FactEnum` for typed enums
//! (loan decision = approved / rejected / referred).

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};
use serde_json::Value as JsonValue;

use rf_executor::vars::WILDCARD_CLASS;

/// A fact — anything that can be inserted into working memory and whose
/// fields can be read by name.
///
/// V5.25 only needs `class_name()` and `get_property(name)`. P2 will add
/// `set_property` for update-in-place.
pub trait Fact {
    /// The Java-style class name (e.g. `"com.example.Applicant"`). Used
    /// to match against `ObjectTypeNode.object_type_class`.
    fn class_name(&self) -> &str;

    /// Read a property by name. Java uses reflection; we use a
    /// `BTreeMap` lookup. Returns `None` for missing fields.
    fn get_property(&self, name: &str) -> Option<&JsonValue>;

    /// True if this fact matches a given `ObjectTypeNode.object_type_class`.
    /// `WILDCARD_CLASS` (`"__*__"`) matches everything; exact match otherwise.
    fn matches_class(&self, class: &str) -> bool {
        class == WILDCARD_CLASS || class == self.class_name()
    }
}

/// `GeneralEntity` — the canonical fact type. A JSON object with a class
/// name. Field reads are O(1) `BTreeMap` lookups.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct GeneralEntity {
    /// Java-style class name (e.g. `"com.example.Applicant"`).
    pub target_class: String,
    /// Field map. Keys are property names; values are JSON.
    pub fields: BTreeMap<String, JsonValue>,
}

impl GeneralEntity {
    pub fn new(class_name: impl Into<String>) -> Self {
        Self {
            target_class: class_name.into(),
            fields: BTreeMap::new(),
        }
    }

    pub fn with_field(mut self, name: impl Into<String>, value: JsonValue) -> Self {
        self.fields.insert(name.into(), value);
        self
    }
}

impl Fact for GeneralEntity {
    fn class_name(&self) -> &str {
        &self.target_class
    }

    fn get_property(&self, name: &str) -> Option<&JsonValue> {
        self.fields.get(name)
    }
}

/// `MapFact` — alias for `GeneralEntity`, kept for naming symmetry with
/// Java's `MapFact` test fixture. The two types are interchangeable; the
/// rename is purely a documentation aid.
pub type MapFact = GeneralEntity;

/// Construct a fact from a JSON `Value::Object` (e.g. one extracted from
/// a `Vars` assertion). Used by the HTTP-evaluate route and P1's
/// `ObjectTypeActivity`.
pub fn fact_from_value(
    class_name: &str,
    value: &JsonValue,
) -> Option<GeneralEntity> {
    let obj = value.as_object()?;
    let mut fields = BTreeMap::new();
    for (k, v) in obj {
        fields.insert(k.clone(), v.clone());
    }
    Some(GeneralEntity {
        target_class: class_name.to_string(),
        fields,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn general_entity_reads_fields() {
        let e = GeneralEntity::new("com.example.Applicant")
            .with_field("age", json!(30))
            .with_field("name", json!("alice"));
        assert_eq!(e.class_name(), "com.example.Applicant");
        assert_eq!(e.get_property("age"), Some(&json!(30)));
        assert_eq!(e.get_property("missing"), None);
    }

    #[test]
    fn matches_class_wildcard() {
        let e = GeneralEntity::new("com.example.Applicant");
        assert!(e.matches_class(WILDCARD_CLASS));
        assert!(e.matches_class("com.example.Applicant"));
        assert!(!e.matches_class("com.example.Order"));
    }

    #[test]
    fn fact_from_value_extracts_object() {
        let v = json!({"age": 30, "name": "alice"});
        let e = fact_from_value("Applicant", &v).unwrap();
        assert_eq!(e.class_name(), "Applicant");
        assert_eq!(e.fields.len(), 2);
    }

    #[test]
    fn fact_from_value_rejects_non_object() {
        let v = json!(42);
        assert!(fact_from_value("X", &v).is_none());
    }

    #[test]
    fn map_fact_is_alias() {
        fn assert_fact(_: &dyn Fact) {}
        let m: MapFact = GeneralEntity::new("X");
        assert_fact(&m);
    }
}
