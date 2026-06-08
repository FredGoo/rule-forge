//! Placeholder. Real FlowError + thiserror variants land in Phase 3.

#[derive(Debug, thiserror::Error)]
pub enum FlowError {
    #[error("placeholder")]
    Placeholder,
}
