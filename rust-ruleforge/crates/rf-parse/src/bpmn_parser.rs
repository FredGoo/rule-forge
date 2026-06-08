//! Placeholder. Real BpmnXmlParser lands in Phase 2 (roxmltree).

use rf_ir::flow_definition::FlowDefinition;

pub struct BpmnXmlParser;

impl BpmnXmlParser {
    pub fn parse(&self, _xml: &str) -> Result<FlowDefinition, rf_parse::ParseErrorStub> {
        unimplemented!("Phase 2")
    }
}

// Shim so the placeholder compiles; real error type in Phase 2.
pub mod rf_parse {
    #[derive(Debug, thiserror::Error)]
    #[error("placeholder parse error")]
    pub struct ParseErrorStub;
}
