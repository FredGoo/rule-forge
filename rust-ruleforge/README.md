# rust-ruleforge

Rust port of the RuleForge decision flow executor (V5.20+ self-built engine).
**Sibling** project to the Java executor — runs on its own port (8281), uses its own Postgres state store, talks the same HTTP protocol to the Java console.

## Status

🚧 Phase 1 — Skeleton workspace, no real flow logic yet.

See [`snug-wobbling-reddy.md`](../../.claude/plans/snug-wobbling-reddy.md) for the full plan.

## Crate layout

```
crates/
├── rf-ir/         # Immutable IR (FlowDefinition, FlowNode, NodeKind sum type)
├── rf-parse/      # BpmnXmlParser (roxmltree, zero-copy)
├── rf-executor/   # FlowContext, Traverser type-state, 5 NodeExecutors
├── rf-rule/       # trait RuleEngine + MockRuleEngine
├── rf-state/      # sqlx pg persistence + 30s RecoveryLoop
└── rf-http/       # axum service: /ruleforge/evaluate + /flow/decision + /flow/invalidate
```

## Quick start (later phases)

```bash
# Build
cargo build --workspace

# Test
cargo test --workspace

# Run
CONSOLE_URL=http://localhost:8180 \
PG_URL=postgres://ruleforge:ruleforge@localhost:5432/ruleforge_rust \
HTTP_PORT=8281 \
cargo run --bin rf-http

# Smoke
curl -X POST localhost:8281/ruleforge/evaluate \
  -H 'content-type: application/json' \
  -d '{"flow_id": "test", "vars": {}}'
```

## License

MIT
