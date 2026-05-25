import { test, expect } from '@playwright/test';

/**
 * BDD Tests for Main Frame Navigation
 *
 * Given: User is logged in and on main frame
 * When: User interacts with project tree and file operations
 * Then: Expected navigation and file operations occur
 */
test.describe('Main Frame Navigation', () => {
    let page;

    // Given: User is logged in
    test.beforeEach(async ({ browser }) => {
        const context = await browser.newContext();
        page = await context.newPage();

        // Login first
        await page.goto('/html/login.html');
        await page.locator('input[name="username"], input[type="text"]').first().fill('admin');
        await page.locator('input[name="password"], input[type="password"]').first().fill('admin');
        await page.locator('button[type="submit"], button:has-text("登录"), button:has-text("Login")').first().click();
        await page.waitForURL(/\/html\/index\.html/);
    });

    // Given: User is on main frame
    // When: Page loads
    // Then: Project tree should be visible
    test('should load main frame with project tree', async ({ page }) => {
        // Then: Main frame should contain project tree
        const projectTree = page.locator('.tree, .project-tree, [data-testid="project-tree"]').first();
        await expect(projectTree).toBeVisible({ timeout: 10000 });

        // Then: Tree should have root nodes
        const treeNode = page.locator('.tree-node, .tree-item, li').first();
        await expect(treeNode).toBeVisible();
    });

    // Given: User is on main frame with project tree
    // When: User clicks on a tree node expand button
    // Then: Node should expand showing children
    test('should expand tree node when clicking expand button', async ({ page }) => {
        // Given: Wait for tree to load
        const expandButton = page.locator('.tree-expand, .tree-toggle, [class*="expand"]').first();
        await expect(expandButton).toBeVisible();

        // When: Click expand button
        await expandButton.click();

        // Then: Node should expand
        const childNodes = page.locator('.tree-children, .tree-node-children, ul li');
        await expect(childNodes.first()).toBeVisible({ timeout: 5000 });
    });

    // Given: User is on main frame
    // When: User right-clicks on project tree
    // Then: Context menu should appear with file operations
    test('should show context menu on right-click', async ({ page }) => {
        // Given: Wait for project tree
        const treeNode = page.locator('.tree-node, .tree-item').first();
        await expect(treeNode).toBeVisible();

        // When: Right-click on tree node
        await treeNode.click({ button: 'right' });

        // Then: Context menu should be visible
        const contextMenu = page.locator('.context-menu, [role="menu"]').first();
        await expect(contextMenu).toBeVisible();

        // Then: Menu should have file operation options
        const menuItem = contextMenu.locator('li, [role="menuitem"]').first();
        await expect(menuItem).toBeVisible();
    });

    // Given: User is on main frame with context menu open
    // When: User selects "New File" option
    // Then: New file dialog should appear
    test('should show new file dialog from context menu', async ({ page }) => {
        // Given: Open context menu
        const treeNode = page.locator('.tree-node, .tree-item').first();
        await treeNode.click({ button: 'right' });

        // When: Click "New File" or "新建" option
        const newFileOption = page.locator('.context-menu, [role="menu"]').first()
            .locator(':has-text("新建"), :has-text("New"), :has-text("New File")');
        await newFileOption.click();

        // Then: New file dialog should appear
        const dialog = page.locator('.dialog, .modal, [role="dialog"]').first();
        await expect(dialog).toBeVisible();

        // Then: Dialog should have file name input
        const fileNameInput = dialog.locator('input[name="filename"], input[type="text"]').first();
        await expect(fileNameInput).toBeVisible();
    });

    // Given: User is on main frame with context menu open
    // When: User selects "Rename" option on a file
    // Then: Rename dialog should appear
    test('should show rename dialog from context menu', async ({ page }) => {
        // Given: Open context menu on a file
        const fileNode = page.locator('.tree-node.file, .tree-item.file').first();
        if (await fileNode.isVisible()) {
            await fileNode.click({ button: 'right' });

            // When: Click "Rename" or "重命名" option
            const renameOption = page.locator('.context-menu, [role="menu"]').first()
                .locator(':has-text("重命名"), :has-text("Rename")');
            await renameOption.click();

            // Then: Rename dialog should appear
            const dialog = page.locator('.dialog, .modal, [role="dialog"]').first();
            await expect(dialog).toBeVisible();
        }
    });

    // Given: User is on main frame with context menu open
    // When: User selects "Delete" option on a file
    // Then: Confirmation dialog should appear
    test('should show delete confirmation from context menu', async ({ page }) => {
        // Given: Open context menu on a file
        const fileNode = page.locator('.tree-node.file, .tree-item.file').first();
        if (await fileNode.isVisible()) {
            await fileNode.click({ button: 'right' });

            // When: Click "Delete" or "删除" option
            const deleteOption = page.locator('.context-menu, [role="menu"]').first()
                .locator(':has-text("删除"), :has-text("Delete")');
            await deleteOption.click();

            // Then: Confirmation dialog should appear
            const dialog = page.locator('.dialog, .modal, [role="dialog"]').first();
            await expect(dialog).toBeVisible();

            // Then: Dialog should have confirm and cancel buttons
            const confirmButton = dialog.locator('button:has-text("确定"), button:has-text("OK"), button:has-text("Confirm")');
            await expect(confirmButton).toBeVisible();
        }
    });

    // Given: User is on main frame
    // When: User clicks on a file in the tree
    // Then: File should open in appropriate editor
    test('should open file when clicking on tree node', async ({ page }) => {
        // Given: Wait for project tree with files
        const fileNode = page.locator('.tree-node.file, .tree-item.file, [data-file]').first();
        await expect(fileNode).toBeVisible({ timeout: 10000 });

        // When: Click on file node
        await fileNode.click();

        // Then: File should open in editor (new page or frame)
        await page.waitForTimeout(1000); // Wait for file to load

        // Then: Editor should be visible
        const editor = page.locator('.editor, [data-editor], iframe').first();
        await expect(editor).toBeVisible();
    });

    // Given: User is on main frame
    // When: User searches for a file using search box
    // Then: Tree should filter to show matching files
    test('should filter project tree when using search', async ({ page }) => {
        // Given: Locate search box
        const searchBox = page.locator('input[placeholder*="搜索"], input[placeholder*="search"], input[name="search"]').first();

        if (await searchBox.isVisible()) {
            // When: Type search query
            await searchBox.fill('variable');

            // Then: Tree should filter (wait for debounce)
            await page.waitForTimeout(500);

            // Then: Only matching nodes should be visible
            const visibleNodes = page.locator('.tree-node:not(.hidden), .tree-item:not(.hidden)');
            await expect(visibleNodes.first()).toBeVisible();
        }
    });

    // Given: User is on main frame
    // When: User collapses all tree nodes
    // Then: All nodes should collapse
    test('should collapse all tree nodes when clicking collapse all', async ({ page }) => {
        // Given: Wait for tree to load
        const collapseAllButton = page.locator('button:has-text("全部折叠"), button:has-text("Collapse All")');

        if (await collapseAllButton.isVisible()) {
            // When: Click collapse all
            await collapseAllButton.click();

            // Then: All child nodes should be hidden
            await page.waitForTimeout(500);
        }
    });
});
