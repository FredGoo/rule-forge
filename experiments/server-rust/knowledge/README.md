# Knowledge package directory

Mount this directory (or any directory of `*.json` files) into
the `rf-http` container at `/knowledge` to wire the production
`ReteRuleEngine`. The binary's `KNOWLEDGE_DIR` env var points
to this mount; if the directory is empty (no `*.json` files),
the binary logs a warning and falls back to `MockRuleEngine`.

## File format

Each `*.json` file is a `KnowledgePackageWrapper` — the exact
shape the Java console-app's save path produces:

```json
{
  "id": "loan_rules_v1",
  "version": "1.0.0",
  "knowledgePackage": { "rete": {...}, "with_else_rules": {...} },
  "allNodes": [ {"id": 1, "nodeType": "objectType", ...}, ... ]
}
```

The loader (`rf-rule/src/loader.rs::load_dir`) reads every
`*.json` in this directory, sorts by filename for deterministic
order, and feeds them to `ReteRuleEngine::from_wrappers`. The
engine then evaluates the rules in the wrapper's RETE graph
against the working memory that the HTTP `/evaluate` handler
seeds (`vars.applicant`, etc.).

## How to populate

The Java console-app exports knowledge packages to its
`RF_REPO_DIR` (default `/app/data/riskruleforge`). To feed the
Rust binary:

1. Run the Java console and edit a flow / rule in the UI.
2. Save the rule — this writes a `*.json` to the console's
   `RF_REPO_DIR`.
3. Mount that directory into the Rust container at
   `/knowledge`.

For the local dev path, point `KNOWLEDGE_DIR` at any directory
of `KnowledgePackageWrapper` JSON files you have.

## Empty / missing

The `rf-http` binary does **not** fail to start when
`KNOWLEDGE_DIR` is empty or has no `*.json` files — it falls
back to `MockRuleEngine` (the Phase 4 hand-coded
`age>=18 && income>=5000` fixture) and logs a `warn!` so the
operator sees in logs that real rules aren't being used:

```
WARN KNOWLEDGE_DIR is empty — falling back to MockRuleEngine; pass
     --knowledge-dir=<path> to use real rule packages
```

This is intentional: it lets the Docker Compose stack come up
clean even if no rules are exported yet, and operators can
add rules at runtime by re-mounting the volume.
