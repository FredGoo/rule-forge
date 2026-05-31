---
name: analyze-decision
description: Analyze a single decision execution — trace, attribution, and comparison with rule content
---

# Analyze Decision

Perform deep analysis on a single decision execution by examining the decision log details and correlating with rule definitions.

## Steps

1. Get the decision flow log details from the decision log tables. Query the database:
   - `SELECT * FROM nd_decision_flow_log WHERE id = {flow_log_id}`
   - `SELECT * FROM nd_decision_flow_params WHERE flow_log_id = {flow_log_id}`
   - `SELECT * FROM nd_decision_rule_log WHERE flow_log_id = {flow_log_id} ORDER BY rule_index`
   - `SELECT * FROM nd_decision_node_log WHERE flow_log_id = {flow_log_id} ORDER BY sort`

2. Get the rule package content for comparison:
   - `node cli/bin/ruleforge.js export package --project {project} --package-id {package_id}`

3. Analyze:
   - **Input**: What were the input parameters?
   - **Execution Path**: Which rules matched? Which fired? In what order?
   - **Decision Attribution**: Which rule(s) determined the final result (reject code, output fields)?
   - **Latency Breakdown**: How was total time distributed (load knowledge vs execution vs flow)?
   - **Rule vs Data**: Do the rule conditions align with the input data? Any edge cases?

## Parameters

- `{flow_log_id}`: The decision flow log ID
- `{project}`: Project name (for rule content export)
- `{package_id}`: Package ID (for rule content export)

## Output

Provide a detailed decision trace report showing the full execution path and attribution.
