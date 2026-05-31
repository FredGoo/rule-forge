---
name: simulate-impact
description: Simulate the impact of a rule change by analyzing historical decisions against modified rules
---

# Simulate Impact

Analyze how a proposed rule change would affect past decisions by comparing historical execution patterns with the modified rule conditions.

## Steps

1. Get the current rule package content:
   - `node cli/bin/ruleforge.js export package --project {project} --package-id {id}`

2. Get recent decision execution data to understand the current impact:
   - `node cli/bin/ruleforge.js analysis flow-trend --start 7d --package {package_path}`
   - `node cli/bin/ruleforge.js analysis reject-top --start 7d --package {package_path}`

3. Query historical decision data for simulation:
   - Query recent decisions: `SELECT input_params, output_params, execution_status, reject_code FROM nd_decision_flow_log fl JOIN nd_decision_flow_params fp ON fl.id = fp.flow_log_id WHERE fl.rule_package_path = '{package_path}' ORDER BY fl.created_at DESC LIMIT 100`

4. Analyze the proposed change:
   - What rules are being modified? (conditions, thresholds, actions)
   - What decisions would change? (apply new conditions to historical input data)
   - Impact metrics:
     - **Volume Impact**: How many more/fewer decisions would be affected?
     - **Reject Rate Change**: Would the reject rate increase or decrease?
     - **Risk Assessment**: Any new edge cases introduced?

5. If shadow/comparison data exists, check it:
   - Query `nd_decision_shadow_comparison` for any existing shadow execution results

## Parameters

- `{project}`: Project name
- `{id}`: Package ID
- `{package_path}`: Rule package path
- The user should describe the proposed change in their message

## Output

An impact assessment report showing:
- Current state metrics
- Projected state metrics after change
- Affected decision percentage
- Risk assessment and recommendations
