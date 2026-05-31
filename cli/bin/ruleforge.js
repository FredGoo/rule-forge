#!/usr/bin/env node

const { Command } = require('commander');
const fs = require('fs');
const path = require('path');
const fetch = require('node-fetch');

// Config
const CONFIG_PATH = path.join(require('os').homedir(), '.ruleforge', 'config.json');

function loadConfig() {
    if (fs.existsSync(CONFIG_PATH)) {
        return JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8'));
    }
    return { server: 'http://localhost:8180/ruleforgeV2' };
}

function saveConfig(config) {
    const dir = path.dirname(CONFIG_PATH);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(CONFIG_PATH, JSON.stringify(config, null, 2));
}

function getServer() {
    const config = loadConfig();
    return config.server || 'http://localhost:8180/ruleforgeV2';
}

async function apiGet(path, params = {}) {
    const url = new URL(getServer() + path);
    Object.entries(params).forEach(([k, v]) => {
        if (v !== undefined && v !== null) url.searchParams.append(k, v);
    });
    const resp = await fetch(url.toString());
    if (!resp.ok) throw new Error(`HTTP ${resp.status}: ${resp.statusText}`);
    return resp.json();
}

function parseDate(str) {
    if (!str) return new Date().toISOString();
    // Support relative: 1h, 6h, 24h, 7d, 30d
    const match = str.match(/^(\d+)(h|d)$/);
    if (match) {
        const ms = parseInt(match[1]) * (match[2] === 'h' ? 3600000 : 86400000);
        return new Date(Date.now() - ms).toISOString();
    }
    return new Date(str).toISOString();
}

function output(data, format = 'json') {
    if (format === 'json') {
        console.log(JSON.stringify(data, null, 2));
    } else if (format === 'table') {
        if (Array.isArray(data)) {
            if (data.length === 0) { console.log('(empty)'); return; }
            const keys = Object.keys(data[0]);
            // Header
            console.log(keys.join('\t'));
            console.log(keys.map(() => '---').join('\t'));
            data.forEach(row => {
                console.log(keys.map(k => String(row[k] ?? '')).join('\t'));
            });
        } else {
            console.log(JSON.stringify(data, null, 2));
        }
    }
}

const program = new Command();
program
    .name('ruleforge')
    .description('RuleForge CLI - Agent-friendly command line interface')
    .version('1.0.0');

// === config ===
program.command('config')
    .description('Show or set configuration')
    .option('--server <url>', 'Set server URL')
    .action((opts) => {
        if (opts.server) {
            const config = loadConfig();
            config.server = opts.server;
            saveConfig(config);
            console.log(`Server set to: ${opts.server}`);
        } else {
            console.log(JSON.stringify(loadConfig(), null, 2));
        }
    });

// === analysis ===
const analysis = program.command('analysis').description('Decision log analysis');

analysis.command('flow-trend')
    .description('Decision flow time series trend')
    .option('--start <date>', 'Start time (ISO date or relative like 24h, 7d)', '24h')
    .option('--end <date>', 'End time (ISO date)', undefined)
    .option('--package <pkg>', 'Rule package path filter')
    .option('--granularity <g>', 'Time granularity: hourly or daily', 'hourly')
    .option('--format <fmt>', 'Output format: json or table', 'json')
    .action(async (opts) => {
        const params = {
            startTime: parseDate(opts.start),
            endTime: opts.end ? new Date(opts.end).toISOString() : new Date().toISOString(),
            granularity: opts.granularity
        };
        if (opts.package) params.rulePackagePath = opts.package;
        const data = await apiGet('/analysis/flow/timeseries', params);
        output(data, opts.format);
    });

analysis.command('reject-top')
    .description('Top reject codes distribution')
    .option('--start <date>', 'Start time', '24h')
    .option('--end <date>', 'End time')
    .option('--package <pkg>', 'Rule package filter')
    .option('--limit <n>', 'Top N', '20')
    .option('--format <fmt>', 'Output format', 'json')
    .action(async (opts) => {
        const params = {
            startTime: parseDate(opts.start),
            endTime: opts.end ? new Date(opts.end).toISOString() : new Date().toISOString(),
            limit: opts.limit
        };
        if (opts.package) params.rulePackagePath = opts.package;
        const data = await apiGet('/analysis/flow/reject-distribution', params);
        output(data, opts.format);
    });

analysis.command('package-summary')
    .description('Package/flow summary statistics')
    .option('--start <date>', 'Start time', '24h')
    .option('--end <date>', 'End time')
    .option('--format <fmt>', 'Output format', 'table')
    .action(async (opts) => {
        const params = {
            startTime: parseDate(opts.start),
            endTime: opts.end ? new Date(opts.end).toISOString() : new Date().toISOString()
        };
        const data = await apiGet('/analysis/flow/packages-summary', params);
        output(data, opts.format);
    });

analysis.command('rule-coverage')
    .description('Rule coverage analysis - hot/cold/dead rules')
    .option('--start <date>', 'Start time', '30d')
    .option('--end <date>', 'End time')
    .option('--package <pkg>', 'Rule package filter')
    .action(async (opts) => {
        const params = {
            startTime: parseDate(opts.start),
            endTime: opts.end ? new Date(opts.end).toISOString() : new Date().toISOString()
        };
        if (opts.package) params.rulePackagePath = opts.package;
        const data = await apiGet('/analysis/rule/coverage', params);
        output(data);
    });

analysis.command('rule-frequency')
    .description('Rule fire frequency ranking')
    .option('--start <date>', 'Start time', '24h')
    .option('--end <date>', 'End time')
    .option('--package <pkg>', 'Rule package filter')
    .option('--format <fmt>', 'Output format', 'table')
    .action(async (opts) => {
        const params = {
            startTime: parseDate(opts.start),
            endTime: opts.end ? new Date(opts.end).toISOString() : new Date().toISOString()
        };
        if (opts.package) params.rulePackagePath = opts.package;
        const data = await apiGet('/analysis/rule/fire-frequency', params);
        output(data, opts.format);
    });

analysis.command('anomaly')
    .description('Detect anomalies vs historical baseline')
    .option('--baseline-days <n>', 'Baseline period in days', '7')
    .option('--sigma <t>', 'Sigma threshold', '2.0')
    .option('--package <pkg>', 'Rule package filter')
    .action(async (opts) => {
        const params = {
            baselineDays: opts.baselineDays,
            sigmaThreshold: opts.sigma
        };
        if (opts.package) params.rulePackagePath = opts.package;
        const data = await apiGet('/analysis/anomaly/detect', params);
        output(data);
    });

analysis.command('packages')
    .description('List all rule package paths')
    .action(async () => {
        const data = await apiGet('/analysis/packages');
        output(data);
    });

// === export ===
const exp = program.command('export').description('Export rule content');

exp.command('projects')
    .description('List all projects')
    .action(async () => {
        const data = await apiGet('/export/projects');
        output(data);
    });

exp.command('packages')
    .description('List packages in a project')
    .requiredOption('--project <name>', 'Project name')
    .action(async (opts) => {
        const data = await apiGet(`/export/project/${encodeURIComponent(opts.project)}/packages`);
        output(data);
    });

exp.command('package')
    .description('Export full package content with all files')
    .requiredOption('--project <name>', 'Project name')
    .requiredOption('--package-id <id>', 'Package ID or name')
    .action(async (opts) => {
        const data = await apiGet(`/export/project/${encodeURIComponent(opts.project)}/package/${encodeURIComponent(opts.packageId)}`);
        output(data);
    });

exp.command('file')
    .description('Export a single file content')
    .requiredOption('--path <path>', 'File path')
    .option('--version <ver>', 'Version')
    .action(async (opts) => {
        const params = { path: opts.path };
        if (opts.version) params.version = opts.version;
        const data = await apiGet('/export/file', params);
        output(data);
    });

// === decision (query logs) ===
const decision = program.command('decision').description('Query decision logs');

decision.command('list')
    .description('List recent decision logs (placeholder - uses monitoring API)')
    .option('--limit <n>', 'Max results', '20')
    .action(async (opts) => {
        // This would need a dedicated endpoint; for now show package summary
        console.log('Note: Use ruleforge analysis flow-trend for execution data');
    });

program.parse();
