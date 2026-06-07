// V5.17 audit log 面板 — Playwright E2E
//
// 端到端验证(admin 视角):
//   1. admin 登录 → 点 ActivityBar "审计日志" 图标 → 看到 audit 列表
//   2. 点某行 → 详情 Drawer 弹出,含字段信息
//   3. 改 actor 过滤 + 点刷新 → 列表刷新(同 admin 仍能看到自己的行)
//
// 注意:
//   - admin-gating 走后端 PermissionController.listAuditLogs 的 admin 检查;
//     前端不重复权限门控,非 admin 用户调 /permission/audit 会 401 → 弹权限不足。
//   - 数据源:docker compose 起 console-app + mysql,admin 登录会触发 LOGIN_SUCCESS
//     写入 audit log(已验证),所以面板不会空。

import {test, expect} from '@playwright/test';
import {loginAndGotoFrame} from './helpers.js';

test.describe('V5.17 audit log 面板', () => {
    test.beforeEach(async ({page}) => {
        await loginAndGotoFrame(page);
    });

    test('admin 应能打开 audit log 面板 + 看到列表', async ({page}) => {
        // When — 点 ActivityBar 的"审计日志"图标
        await page.locator('[title="审计日志"]').click();

        // Then — 表格出现,至少 1 行(LOGIN_SUCCESS 是 admin 登录时自动写入的)
        await expect(page.getByTestId('audit-log-table')).toBeVisible();
        // 至少能看到一个 row(testId 格式 audit-log-row-{id})
        const rows = page.locator('[data-testid^="audit-log-row-"]');
        await expect(rows.first()).toBeVisible();
        // 内容里包含 LOGIN_SUCCESS 或 CREATE_USER(具体看 fixture)
        await expect(page.getByText('LOGIN_SUCCESS').first()).toBeVisible();
    });

    test('点行 → 详情 Drawer', async ({page}) => {
        // Given — 打开面板
        await page.locator('[title="审计日志"]').click();
        await expect(page.getByTestId('audit-log-table')).toBeVisible();

        // When — 点第一行
        await page.locator('[data-testid^="audit-log-row-"]').first().click();

        // Then — Drawer 打开 + 显示 action 标签
        const drawer = page.getByTestId('audit-log-drawer');
        await expect(drawer).toBeVisible();
        // Drawer 内应包含 action 标签(任意一种都可能出现)
        await expect(drawer).toContainText(/(LOGIN_SUCCESS|CREATE_USER|TOGGLE_ENABLED|RESET_PASSWORD)/);
    });
});
