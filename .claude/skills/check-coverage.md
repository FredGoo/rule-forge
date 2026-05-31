---
name: check-coverage
description: Rule coverage report — which rules fire, which are dead, frequency distribution
---

# Check Rule Coverage

Analyze rule coverage across all packages or a specific package. Identify dead rules, cold rules, and frequency distribution.

## Steps

1. Run CLI commands:
   - `node cli/bin/ruleforge.js analysis rule-coverage --start 30d [--package {package_path}]`
   - `node cli/bin/ruleforge.js analysis rule-frequency --start 30d [--package {package_path}] --format table`

2. Analyze the results:
   - **Hot Rules**: Frequently triggered rules (fire count > 1000)
   - **Warm Rules**: Moderately triggered (100-1000)
   - **Cold Rules**: Rarely triggered (< 100) or not triggered in the time window
   - **Dead Rules**: Rules in the package definition that never appear in execution logs
   - **Frequency Distribution**: Bucket distribution [0-10, 10-100, 100-1000, 1000+]

3. For cold/dead rules, optionally export the rule content to understand why:
   - `node cli/bin/ruleforge.js export package --project {project} --package-id {id}`
   - Look at rule conditions — are thresholds unrealistic? Are conditions contradictory?

## Parameters

- `{package_path}`: Optional. If omitted, analyzes all packages.

## Output

A structured coverage report with recommendations for cold/dead rules.
