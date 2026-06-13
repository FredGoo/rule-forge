// V5.50.7 — Tour nightly config (JavaScript, 跟现有 playwright.config.js 一致)。
//
// 13 tour spec (文件名以 _ 开头 + -tour 后缀,被现有 23 真 spec 排除):
// - _business-flow-100-tour
// - _dialog-100-tour / _dialog-tour
// - _editor-100-tour / _editor-tour
// - _frame-interaction-tour / _frame-tree-tour
// - _interactive-100-tour
// - _login-tour
// - _micro-tour
// - _responsive-tour
// - _state-tour
// - _visual-tour
//
// 单 browser chromium,60s timeout。
// 失败 **不**挡 PR merge,只 nightly 看。

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
        '**/_*-tour.spec.ts',
    ],
    projects: [
        {
            name: 'chromium',
            use: { browserName: 'chromium' },
        },
    ],
});
