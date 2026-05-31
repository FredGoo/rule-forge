---
name: review-rules
description: Review rule quality — detect conflicts, redundancy, unreachable conditions, and suggest improvements
---

# Review Rules

Review rule definitions for quality issues by analyzing the rule content (conditions, actions, priorities) and cross-referencing with execution data.

## Steps

1. Export the rule package content:
   - `node cli/bin/ruleforge.js export projects` — find the project
   - `node cli/bin/ruleforge.js export packages --project {project}` — list packages
   - `node cli/bin/ruleforge.js export package --project {project} --package-id {id}` — get full content

2. Get execution data for context:
   - `node cli/bin/ruleforge.js analysis rule-coverage --start 30d --package {package_path}`
   - `node cli/bin/ruleforge.js analysis reject-top --start 7d --package {package_path}`

3. Review the rule content for:
   - **Conflicting Rules**: Multiple rules with same activation group but contradictory conditions
   - **Redundant Rules**: Rules that are subsumed by other rules (same conditions, same or subset actions)
   - **Unreachable Conditions**: Conditions that can never be true (e.g., `score > 100 AND score < 0`)
   - **Overly Broad Rules**: Rules with too few conditions that fire on everything
   - **Missing Edge Cases**: Common input values not covered by any rule
   - **Threshold Issues**: Thresholds that are unrealistic given the data distribution (cross-reference with execution data)

4. Generate a review report with:
   - Issue type (conflict/redundancy/unreachable/broad/missing/threshold)
   - Affected rules
   - Severity (high/medium/low)
   - Suggested fix

## Parameters

- `{project}`: Project name
- `{id}`: Package ID to review
- `{package_path}`: Package path for execution data filter

## Output

A structured rule quality review report with categorized findings and actionable suggestions.
