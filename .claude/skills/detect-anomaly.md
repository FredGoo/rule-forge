---
name: detect-anomaly
description: Detect anomalies in decision execution — reject rate spikes, latency degradation, statistical deviations
---

# Detect Anomalies

Run statistical anomaly detection against historical baseline data. Identifies reject rate spikes, latency degradation, and success rate drops.

## Steps

1. Run the anomaly detection command:
   - `node cli/bin/ruleforge.js analysis anomaly --baseline-days {days} --sigma {threshold} [--package {package_path}]`

2. If anomalies are detected, investigate further:
   - `node cli/bin/ruleforge.js analysis flow-trend --start 7d --package {package_path}` — check trend
   - `node cli/bin/ruleforge.js analysis reject-top --start 24h --package {package_path}` — check reject codes

3. For each anomaly found, analyze:
   - **Metric**: Which metric is anomalous (success_rate, reject_rate, avg_total_time)?
   - **Baseline vs Current**: What is the expected vs observed value?
   - **Sigma Delta**: How many standard deviations from the mean?
   - **Severity**: HIGH (>3σ), MEDIUM (>2σ), LOW
   - **Possible Cause**: Correlate with recent deployments, rule changes, or data changes

4. If rule changes are suspected, check deployment history:
   - Query `gr_deployment_history` table for recent deployments to the package

## Parameters

- `{days}`: Baseline period in days (default: 7)
- `{threshold}`: Sigma threshold (default: 2.0)
- `{package_path}`: Optional package filter

## Output

Anomaly report with severity, possible causes, and recommended actions.
