//! Placeholder. Real RuleEngine trait lands in Phase 4.
//!
//! `RuleResults` (the result type) and `RuleEngineError` are derived here too
//! so `rf-rule::MockRuleEngine` has a stable interface to implement against.

use crate::flow_context::FlowContext;

#[async_trait::async_trait]
pub trait RuleEngine: Send + Sync {
    async fn fire_rules(&self, ctx: &FlowContext) -> Result<RuleResults, RuleEngineError>;
}

#[derive(Debug, Default, Clone)]
pub struct RuleResults {
    pub fired_rules: Vec<String>,
    pub matched_rules: Vec<String>,
}

#[derive(Debug, thiserror::Error)]
pub enum RuleEngineError {
    #[error("placeholder")]
    Placeholder,
}
