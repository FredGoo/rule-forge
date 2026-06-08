//! Placeholder. Real MockRuleEngine lands in Phase 4.

use rf_executor::flow_context::FlowContext;
use rf_executor::rule_engine::{RuleEngine, RuleEngineError, RuleResults};

pub struct MockRuleEngine;

#[async_trait::async_trait]
impl RuleEngine for MockRuleEngine {
    async fn fire_rules(&self, _ctx: &FlowContext) -> Result<RuleResults, RuleEngineError> {
        unimplemented!("Phase 4")
    }
}
