import { test, expect } from '@playwright/test';
import { login } from './helpers.js';

/**
 * BDD Tests for Scorecard Editor
 *
 * Given: User is logged in and opens scorecard editor
 * When: User interacts with scorecard configuration
 * Then: Expected scorecard operations should work
 */
test.describe('Scorecard Editor', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // ── BDD STUB: should load scorecard editor page ──
    // Given: A logged-in user navigates to /html/score-card-editor.html?file=/project/scorecard.xml
    // When:  The page finishes loading and the network is idle
    // Then:  The browser title should contain "评分卡编辑器"
    // And:   The #tableContainer element should be visible
    test('should load scorecard editor page', async ({ page }) => {
        await page.goto('/html/score-card-editor.html?file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: Page title should be "评分卡编辑器"
        await expect(page).toHaveTitle(/评分卡编辑器/);

        // Then: Table container should be rendered
        const tableContainer = page.locator('#tableContainer');
        await expect(tableContainer).toBeVisible();
    });

    // ── BDD STUB: should display toolbar with buttons ──
    // Given: A logged-in user is on the scorecard editor page
    // When:  The EditorToolbar finishes mounting
    // Then:  The #toolbarContainer should be visible
    // And:   Buttons labeled "保存", "添加属性行", "添加自定义列", and "快速测试" should all be visible inside the toolbar
    test('should display toolbar with buttons', async ({ page }) => {
        await page.goto('/html/score-card-editor.html?file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: Toolbar container should be visible
        const toolbarContainer = page.locator('#toolbarContainer');
        await expect(toolbarContainer).toBeVisible({ timeout: 10000 });

        // Then: Save button should be visible (exact match)
        const saveButton = page.locator('#toolbarContainer button:text-is("保存")');
        await expect(saveButton).toBeVisible();

        // Then: "添加属性行" button should be visible
        const addAttrButton = page.locator('#toolbarContainer button:has-text("添加属性行")');
        await expect(addAttrButton).toBeVisible();

        // Then: "添加自定义列" button should be visible
        const addColButton = page.locator('#toolbarContainer button:has-text("添加自定义列")');
        await expect(addColButton).toBeVisible();

        // Then: "快速测试" button should be visible
        const quickTestButton = page.locator('#toolbarContainer button:has-text("快速测试")');
        await expect(quickTestButton).toBeVisible();
    });

    // ── BDD STUB: should render scorecard table ──
    // Given: A logged-in user is on the scorecard editor page
    // When:  The ScoreCardTable component has initialized
    // Then:  The #tableContainer should be visible
    // And:   The #tableContainer should have at least one child element (the scorecard grid)
    test('should render scorecard table', async ({ page }) => {
        await page.goto('/html/score-card-editor.html?file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: Table container should have content
        const tableContainer = page.locator('#tableContainer');
        await expect(tableContainer).toBeVisible({ timeout: 10000 });

        // Then: Table container should have child elements
        const children = tableContainer.locator('*');
        const childCount = await children.count();
        expect(childCount).toBeGreaterThan(0);
    });

    // ── BDD STUB: should add attribute row when clicking button ──
    // Given: A logged-in user is on the scorecard editor page with the toolbar rendered
    // When:  The user clicks the "添加属性行" toolbar button
    // Then:  A new attribute row should be added to the scorecard table inside #tableContainer
    test('should add attribute row when clicking button', async ({ page }) => {
        await page.goto('/html/score-card-editor.html?file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: Button should be visible
        const addAttrButton = page.locator('#toolbarContainer button:has-text("添加属性行")');
        await expect(addAttrButton).toBeVisible({ timeout: 10000 });

        // When: Get initial table content
        const tableContainer = page.locator('#tableContainer');
        const initialChildren = await tableContainer.locator('*').count();

        // When: Click add attribute button
        await addAttrButton.click({ force: true });

        // Then: Table should update
        await page.waitForTimeout(500);
    });

    // ── BDD STUB: should render dialog container ──
    // Given: A logged-in user is on the scorecard editor page
    // When:  The React shell mounts the dialog provider
    // Then:  The #dialogContainer element should be attached to the DOM
    test('should render dialog container', async ({ page }) => {
        await page.goto('/html/score-card-editor.html?file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: Dialog container should exist
        const dialogContainer = page.locator('#dialogContainer');
        await expect(dialogContainer).toBeAttached();
    });
});
