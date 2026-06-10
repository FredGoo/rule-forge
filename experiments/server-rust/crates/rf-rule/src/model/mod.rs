//! Rule model — the static data shapes that get loaded from a Java
//! `KnowledgePackageWrapper` JSON and walked by the RETE engine.
//!
//! The Java side splits these into `com.ruleforge.model.rule.*` and
//! `com.ruleforge.model.rete.*`; we mirror the shape here in one module
//! for v0. P3 may split into `model::rule` / `model::rete` submodules
//! when the count grows.

pub mod criteria;
pub mod left;
pub mod left_part;
pub mod op;
pub mod rete_node;
pub mod rule;
pub mod value;
pub mod value_type;

pub use criteria::Criteria;
pub use left::LeftType;
pub use left_part::{Left, LeftPart};
pub use op::Op;
pub use rete_node::{Line, NodeType, Rete, ReteNode};
pub use rule::{Lhs, Rhs, Rule, RuleType};
pub use value::Value;
pub use value_type::ValueType;
