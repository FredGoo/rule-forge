---
name: analyze-package
description: Comprehensive analysis of a rule package — execution trends, rule coverage, and anomaly detection combined
---

# Analyze Package

Perform a comprehensive analysis of a rule package by combining execution data analysis with rule content review.

## Steps

1. Run the following CLI commands to gather data:
   - `node cli/bin/ruleforge.js analysis flow-trend --start 7d --package {package_path}`
   - `node cli/bin/ruleforge.js analysis rule-coverage --start 30d --package {package_path}`
   - `node cli/bin/ruleforge.js analysis reject-top --start 7d --package {package_path}`
   - `node cli/bin/ruleforge.js analysis anomaly --package {package_path}`
   - `node cli/bin/ruleforge.js analysis packages` (to verify package exists)

2. If the package has decision flow content, also export it:
   - `node cli/bin/ruleforge.js export projects` — find the project
   - `node cli/bin/ruleforge.js export packages --project {project}` — find the package ID
   - `node cli/bin/ruleforge.js export package --project {project} --package-id {id}` — get rule content

3. Synthesize a report covering:
   - **Execution Overview**: Volume trends, success/reject rates over time
   - **Rule Coverage**: Which rules are hot (frequently triggered) vs cold (rarely triggered)
   - **Reject Analysis**: Top reject codes and their distribution
   - **Anomaly Detection**: Any statistical anomalies detected
   - **Rule-Data Correlation**: Cross-reference rule content with execution patterns (e.g., a rule with creditScore > 800 that never fires suggests the threshold is too high)

## Parameters

- `{package_path}`: The rule package path (e.g., "loan-rules")

## Output Format

Provide a structured analysis report in Chinese with actionable findings.
