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

impl ValueType {
    /// Short Chinese-bracket label used in `Value.id()` keys. Mirrors
    /// Java's `[变量]` / `[常量]` style for cache key readability in
    /// trace logs.
    pub fn as_label(&self) -> &'static str {
        match self {
            Self::Input => "[输入]",
            Self::Variable => "[变量]",
            Self::Constant => "[常量]",
            Self::VariableCategory => "[变量对象]",
            Self::Method => "[方法]",
            Self::Parameter => "[参数]",
            Self::Paren => "[括号]",
            Self::CommonFunction => "[公共函数]",
            Self::NamedReference => "[具名引用]",
        }
    }
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
