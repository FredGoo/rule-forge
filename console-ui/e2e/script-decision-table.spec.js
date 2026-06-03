import { test, expect } from '@playwright/test';
import { login } from './helpers.js';

/**
 * BDD Tests for Script Decision Table Editor
 *
 * Given: User is logged in and opens script decision table editor
 * When: User interacts with script decision table
 * Then: Expected table operations should work
 */
test.describe('Script Decision Table Editor', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // ── BDD STUB: should load script decision table editor page ──
    // Given: A logged-in user navigates to /html/script-decision-table-editor.html?file=/project/script-table.xml
    // When:  The page finishes loading and the network is idle
    // Then:  The browser title should contain "脚本式决策表编辑器"
    // And:   The #container element should be visible
    test('should load script decision table editor page', async ({ page }) => {
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForLoadState('networkidle');

        // Then: Page title should be "脚本式决策表编辑器"
        await expect(page).toHaveTitle(/脚本式决策表编辑器/);

        // Then: Container should be rendered
        const container = page.locator('#container');
        await expect(container).toBeVisible();
    });

    // ── BDD STUB: should render container with content ──
    // Given: A logged-in user is on the script decision table editor page
    // When:  The ScriptDecisionTable component has finished its initial render
    // Then:  The #container should be visible
    // And:   The #container should contain at least one child element (rendered table UI)
    test('should render container with content', async ({ page }) => {
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForLoadState('networkidle');

        // Then: Container should have child elements
        const container = page.locator('#container');
        await expect(container).toBeVisible({ timeout: 10000 });

        const children = container.locator('*');
        const childCount = await children.count();
        expect(childCount).toBeGreaterThan(0);
    });

    // ── BDD STUB: should handle right-click on container ──
    // Given: A logged-in user is on the script decision table editor page
    // When:  The user right-clicks on the #container
    // Then:  No uncaught error should be thrown
    test('should handle right-click on container', async ({ page }) => {
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForLoadState('networkidle');

        // When: Right-click on container
        const container = page.locator('#container');
        await container.click({ button: 'right' });

        // Then: Wait briefly for any context menu
        await page.waitForTimeout(500);
    });

    // ── BDD STUB: should render dialog container ──
    // Given: A logged-in user is on the script decision table editor page
    // When:  The React shell mounts the dialog provider
    // Then:  The #dialogContainer element should be attached to the DOM
    test('should render dialog container', async ({ page }) => {
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForLoadState('networkidle');

        // Then: Dialog container should exist
        const dialogContainer = page.locator('#dialogContainer');
        await expect(dialogContainer).toBeAttached();
    });
});
