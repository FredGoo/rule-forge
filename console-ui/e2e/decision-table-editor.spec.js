import { test, expect } from '@playwright/test';
import { login } from './helpers.js';

/**
 * BDD Tests for Decision Table Editor
 *
 * Given: User is logged in and opens decision table editor
 * When: User interacts with decision table
 * Then: Expected table operations should work
 */
test.describe('Decision Table Editor', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // ── BDD STUB: should load decision table editor page ──
    // Given: A logged-in user navigates to /html/decision-table-editor.html?file=/project/dt.xml
    // When:  The page finishes loading and the network is idle
    // Then:  The browser title should contain "决策表编辑器"
    // And:   The #container element should be visible
    test('should load decision table editor page', async ({ page }) => {
        await page.goto('/html/decision-table-editor.html?file=/project/dt.xml');
        await page.waitForLoadState('networkidle');

        // Then: Page title should be "决策表编辑器"
        await expect(page).toHaveTitle(/决策表编辑器/);

        // Then: Container should be rendered
        const container = page.locator('#container');
        await expect(container).toBeVisible();
    });

    // ── BDD STUB: should render container with content ──
    // Given: A logged-in user is on the decision table editor page at /html/decision-table-editor.html?file=/project/dt.xml
    // When:  The DecisionTable component has finished its initial render
    // Then:  The #container element should be visible
    // And:   The #container should contain at least one child element (rendered table UI)
    test('should render container with content', async ({ page }) => {
        await page.goto('/html/decision-table-editor.html?file=/project/dt.xml');
        await page.waitForLoadState('networkidle');

        // Then: Container should have child elements
        const container = page.locator('#container');
        await expect(container).toBeVisible({ timeout: 10000 });

        const children = container.locator('*');
        const childCount = await children.count();
        expect(childCount).toBeGreaterThan(0);
    });

    // ── BDD STUB: should initialize table editor ──
    // Given: A logged-in user is on the decision table editor page
    // When:  The DecisionTable JS module initializes
    // Then:  The #container element should be visible
    // And:   The #container should have non-empty innerHTML (DecisionTable rendered its UI)
    test('should initialize table editor', async ({ page }) => {
        await page.goto('/html/decision-table-editor.html?file=/project/dt.xml');
        await page.waitForLoadState('networkidle');

        // Then: Container should have content from DecisionTable initialization
        const container = page.locator('#container');
        await expect(container).toBeVisible({ timeout: 10000 });

        // Then: Container should have some rendered content
        const innerHTML = await container.evaluate(el => el.innerHTML);
        expect(innerHTML.length).toBeGreaterThan(0);
    });

    // ── BDD STUB: should handle right-click on container ──
    // Given: A logged-in user is on the decision table editor page
    // When:  The user right-clicks on the #container
    // Then:  No uncaught error should be thrown
    // And:   A context menu may or may not appear (table editor may or may not support right-click)
    test('should handle right-click on container', async ({ page }) => {
        await page.goto('/html/decision-table-editor.html?file=/project/dt.xml');
        await page.waitForLoadState('networkidle');

        // When: Right-click on container
        const container = page.locator('#container');
        await container.click({ button: 'right' });

        // Then: Wait briefly for any context menu
        await page.waitForTimeout(500);
    });

    // ── BDD STUB: should render dialog components ──
    // Given: A logged-in user is on the decision table editor page
    // When:  The React shell mounts the dialog provider
    // Then:  The #dialogContainer element should be attached to the DOM
    // And:   The #dialogContainer should contain at least one child (React-rendered dialog host)
    test('should render dialog components', async ({ page }) => {
        await page.goto('/html/decision-table-editor.html?file=/project/dt.xml');
        await page.waitForLoadState('networkidle');

        // Then: Dialog container should exist
        const dialogContainer = page.locator('#dialogContainer');
        await expect(dialogContainer).toBeAttached();

        // Then: Dialog container should have React-rendered components
        const dialogChildren = dialogContainer.locator('*');
        const childCount = await dialogChildren.count();
        expect(childCount).toBeGreaterThan(0);
    });
});
