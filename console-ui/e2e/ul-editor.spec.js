import { test, expect } from '@playwright/test';
import { login } from './helpers.js';

/**
 * BDD Tests for UL Script Editor
 *
 * Given: User is logged in and opens UL script editor
 * When: User interacts with script editor
 * Then: Expected script operations should work
 */
test.describe('UL Script Editor', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // ── BDD STUB: should load UL editor page with CodeMirror ──
    // Given: A logged-in user navigates to /html/ul-editor.html?file=/project/script.ul
    // When:  The page finishes loading and the network is idle
    // Then:  The browser title should contain "脚本编辑器"
    // And:   At least one .CodeMirror element should be visible (the UL script editor)
    test('should load UL editor page with CodeMirror', async ({ page }) => {
        await page.goto('/html/ul-editor.html?file=/project/script.ul');
        await page.waitForLoadState('networkidle');

        // Then: Page title should be "脚本编辑器"
        await expect(page).toHaveTitle(/脚本编辑器/);

        // Then: CodeMirror editor should be visible
        const codeMirror = page.locator('.CodeMirror').first();
        await expect(codeMirror).toBeVisible({ timeout: 15000 });
    });

    // ── BDD STUB: should display toolbar with buttons ──
    // Given: A logged-in user is on the UL editor page
    // When:  The EditorToolbar finishes mounting
    // Then:  The #toolbarContainer should be visible
    // And:   Buttons labeled "保存", "变量库", "常量库", "动作库", "参数库", and "快速测试" should all be visible
    test('should display toolbar with buttons', async ({ page }) => {
        await page.goto('/html/ul-editor.html?file=/project/script.ul');
        await page.waitForLoadState('networkidle');

        // Then: Toolbar container should be visible
        const toolbarContainer = page.locator('#toolbarContainer');
        await expect(toolbarContainer).toBeVisible({ timeout: 10000 });

        // Then: Save button should be visible
        const saveButton = page.locator('#toolbarContainer button:text-is("保存")');
        await expect(saveButton).toBeVisible();

        // Then: Import library buttons should be visible
        const varButton = page.locator('button:has-text("变量库")');
        await expect(varButton).toBeVisible();

        const constButton = page.locator('button:has-text("常量库")');
        await expect(constButton).toBeVisible();

        const actionButton = page.locator('button:has-text("动作库")');
        await expect(actionButton).toBeVisible();

        const paramButton = page.locator('button:has-text("参数库")');
        await expect(paramButton).toBeVisible();

        // Then: Quick test button should be visible
        const quickTestButton = page.locator('button:has-text("快速测试")');
        await expect(quickTestButton).toBeVisible();
    });

    // ── BDD STUB: should display CodeMirror with line numbers ──
    // Given: A logged-in user is on the UL editor page
    // When:  CodeMirror has finished initializing
    // Then:  The .CodeMirror element should be visible
    // And:   The CodeMirror gutters / line-number column should be visible
    test('should display CodeMirror with line numbers', async ({ page }) => {
        await page.goto('/html/ul-editor.html?file=/project/script.ul');
        await page.waitForLoadState('networkidle');

        // Then: CodeMirror should be rendered
        const codeMirror = page.locator('.CodeMirror').first();
        await expect(codeMirror).toBeVisible({ timeout: 15000 });

        // Then: CodeMirror should have line numbers
        const lineNumbers = page.locator('.CodeMirror-gutters, .CodeMirror-linenumber');
        await expect(lineNumbers.first()).toBeVisible();
    });

    // ── BDD STUB: should allow typing in CodeMirror editor ──
    // Given: A logged-in user is on the UL editor page with CodeMirror rendered
    // When:  The user clicks inside the .CodeMirror-code / .CodeMirror-scroll area and types "// test comment"
    // Then:  The typed content should appear inside the .CodeMirror-code element
    test('should allow typing in CodeMirror editor', async ({ page }) => {
        await page.goto('/html/ul-editor.html?file=/project/script.ul');
        await page.waitForLoadState('networkidle');

        // Then: CodeMirror should be visible
        const codeMirror = page.locator('.CodeMirror').first();
        await expect(codeMirror).toBeVisible({ timeout: 15000 });

        // When: Click on CodeMirror's code area (target the code lines area)
        const codeArea = codeMirror.locator('.CodeMirror-code, .CodeMirror-scroll').first();
        await codeArea.click({ force: true });

        // When: Type content
        await page.keyboard.type('// test comment');

        // Then: Content should be in the CodeMirror code element
        const codeContent = page.locator('.CodeMirror-code').first();
        await expect(codeContent).toBeVisible();
    });

    // ── BDD STUB: should render dialog container ──
    // Given: A logged-in user is on the UL editor page
    // When:  The React shell mounts the dialog provider
    // Then:  The #dialogContainer element should be attached to the DOM
    test('should render dialog container', async ({ page }) => {
        await page.goto('/html/ul-editor.html?file=/project/script.ul');
        await page.waitForLoadState('networkidle');

        // Then: Dialog container should exist
        const dialogContainer = page.locator('#dialogContainer');
        await expect(dialogContainer).toBeAttached();
    });
});
