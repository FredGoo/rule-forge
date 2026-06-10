//! `ValueType` — RHS expression kind enum (9 values, port of Java `ValueType.java`).
//!
//! Java names are `Input` / `Variable` / `Constant` / `VariableCategory` /
//! `Method` / `Parameter` / `Paren` / `CommonFunction` / `NamedReference`.
//! Serde default is the variant name, which matches.

use serde::{Deserialize, Serialize};

/// Discriminator for [`super::value::Value`].
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum ValueType {
    Input,
    Variable,
    Constant,
    VariableCategory,
    Method,
    Parameter,
    Paren,
    CommonFunction,
    NamedReference,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn serde_uses_variant_name() {
        let s = serde_json::to_string(&ValueType::VariableCategory).unwrap();
        assert_eq!(s, "\"VariableCategory\"");
    }
}
