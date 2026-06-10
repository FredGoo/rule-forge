//! `LeftType` — LHS expression kind enum (10 values, port of Java `LeftType.java`).
//!
//! Java variants: `variable`, `parameter`, `method`, `function`, `eval`,
//! `all`, `exist`, `collect`, `commonfunction`, `NamedReference`.
//!
//! **Lowercase JSON keys** — Java `LeftType` serializes to lowercase for
//! most variants but PascalCase for `NamedReference`. We use
//! `#[serde(rename_all = "lowercase")]` then override the one PascalCase
//! variant. (Mirrors Jackson's per-enum-constant `@JsonProperty` if it
//! were applied — Java's default for enum names is the variant name as-is.)

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum LeftType {
    #[serde(rename = "variable")]
    Variable,
    #[serde(rename = "parameter")]
    Parameter,
    #[serde(rename = "method")]
    Method,
    #[serde(rename = "function")]
    Function,
    #[serde(rename = "eval")]
    Eval,
    #[serde(rename = "all")]
    All,
    #[serde(rename = "exist")]
    Exist,
    #[serde(rename = "collect")]
    Collect,
    #[serde(rename = "commonfunction")]
    CommonFunction,
    // Java's enum name is `NamedReference` (PascalCase, no @JsonProperty).
    #[serde(rename = "NamedReference")]
    NamedReference,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn serde_uses_lowercase_for_most() {
        let s = serde_json::to_string(&LeftType::Variable).unwrap();
        assert_eq!(s, "\"variable\"");
        let s = serde_json::to_string(&LeftType::CommonFunction).unwrap();
        assert_eq!(s, "\"commonfunction\"");
    }

    #[test]
    fn serde_named_reference_keeps_pascalcase() {
        let s = serde_json::to_string(&LeftType::NamedReference).unwrap();
        assert_eq!(s, "\"NamedReference\"");
    }
}
