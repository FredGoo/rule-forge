// V5.50.5 — PR smoke subset config (JavaScript, 跟现有 playwright.config.js 一致)。
//
// 7 spec 子集(覆盖 nav / auth / 5 类 editor / release):
// - app
// - login
// - frame-navigation
// - datasource-panel
// - decision-table-editor
// - rule-editor
// - package-editor
//
// 单 browser(chromium),60s timeout,7 spec 期望 2-3min 跑完。
// 失败 info-only,不挡 merge — V5.50.5 设计。

import { defineConfig } from '@playwright/test';

export default defineConfig({
    testDir: './e2e',
    timeout: 60_000,
    retries: 0,
    use: {
        baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000',
        headless: true,
        screenshot: 'only-on-failure',
        storageState: undefined,
    },
    testMatch: [
        '**/app.spec.ts',
        '**/login.spec.ts',
        '**/frame-navigation.spec.ts',
        '**/datasource-panel.spec.ts',
        '**/decision-table-editor.spec.ts',
        '**/rule-editor.spec.ts',
        '**/package-editor.spec.ts',
    ],
    projects: [
        {
            name: 'chromium',
            use: { browserName: 'chromium' },
        },
    ],
});
