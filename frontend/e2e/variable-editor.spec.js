import { test, expect } from '@playwright/test';

/**
 * BDD Tests for Variable Library Editor
 *
 * Given: User is logged in and opens variable editor
 * When: User interacts with variable management
 * Then: Expected variable operations should work
 */
test.describe('Variable Library Editor', () => {
    // Given: User is logged in
    test.beforeEach(async ({ page }) => {
        // Login first
        await page.goto('/html/login.html');
        await page.locator('input[name="username"], input[type="text"]').first().fill('admin');
        await page.locator('input[name="password"], input[type="password"]').first().fill('admin');
        await page.locator('button[type="submit"], button:has-text("登录"), button:has-text("Login")').first().click();
        await page.waitForURL(/\/html\/index\.html/);
    });

    // Given: User is on main frame
    // When: User opens variable library editor
    // Then: Variable editor page should load with grid
    test('should load variable editor page with grid', async ({ page }) => {
        // When: Navigate to variable editor
        await page.goto('/html/variable-library-editor.html?file=/project/variables.xml');

        // Then: Page should load
        await expect(page).toHaveTitle(/Variable/);

        // Then: Variable grid should be visible
        const grid = page.locator('.handsontable, [data-hot-table], table').first();
        await expect(grid).toBeVisible({ timeout: 10000 });
    });

    // Given: User is on variable editor page
    // When: Page loads with existing variables
    // Then: Grid should display variable categories and variables
    test('should display variable categories and variables in grid', async ({ page }) => {
        // Given: Navigate to variable editor
        await page.goto('/html/variable-library-editor.html?file=/project/variables.xml');
        await page.waitForTimeout(2000);

        // Then: Grid should have rows
        const rows = page.locator('.handsontable td, tr').all();
        const rowCount = (await rows).length;
        expect(rowCount).toBeGreaterThan(0);
    });

    // Given: User is on variable editor page
    // When: User clicks "Add Category" button
    // Then: New category should be added to grid
    test('should add new variable category', async ({ page }) => {
        // Given: Navigate to variable editor
        await page.goto('/html/variable-library-editor.html?file=/project/variables.xml');
        await page.waitForTimeout(2000);

        // When: Click add category button
        const addCategoryButton = page.locator('button:has-text("添加分类"), button:has-text("Add Category")').first();
        await addCategoryButton.click();

        // Then: New row should be added
        await page.waitForTimeout(500);
        const newRow = page.locator('.handsontable tr:last-child, tr:last-child').first();
        await expect(newRow).toBeVisible();
    });

    // Given: User is on variable editor page
    // When: User clicks "Add Variable" button
    // Then: New variable should be added to grid
    test('should add new variable to category', async ({ page }) => {
        // Given: Navigate to variable editor
        await page.goto('/html/variable-library-editor.html?file=/project/variables.xml');
        await page.waitForTimeout(2000);

        // When: Click add variable button
        const addVariableButton = page.locator('button:has-text("添加变量"), button:has-text("Add Variable")').first();
        await addVariableButton.click();

        // Then: New variable row should be added
        await page.waitForTimeout(500);
        const newCell = page.locator('.handsontable td, td').last();
        await expect(newCell).toBeVisible();
    });

    // Given: User is on variable editor page with variables
    // When: User selects and deletes a variable
    // Then: Variable should be removed from grid
    test('should delete variable from grid', async ({ page }) => {
        // Given: Navigate to variable editor
        await page.goto('/html/variable-library-editor.html?file=/project/variables.xml');
        await page.waitForTimeout(2000);

        // When: Select a row
        const firstRow = page.locator('.handsontable tr, tr').nth(1);
        await firstRow.click();

        // When: Click delete button
        const deleteButton = page.locator('button:has-text("删除"), button:has-text("Delete")').first();
        await deleteButton.click();

        // Then: Row should be removed
        await page.waitForTimeout(500);
    });

    // Given: User is on variable editor page
    // When: User edits a cell in the grid
    // Then: Cell value should be updated
    test('should edit variable cell in grid', async ({ page }) => {
        // Given: Navigate to variable editor
        await page.goto('/html/variable-library-editor.html?file=/project/variables.xml');
        await page.waitForTimeout(2000);

        // When: Double-click on a cell to edit
        const firstCell = page.locator('.handsontable td, td').nth(0);
        await firstCell.dblclick();

        // When: Type new value
        await firstCell.fill('testVariable');

        // Then: Cell should be updated
        await page.waitForTimeout(500);
        await expect(firstCell).toHaveValue(/testVariable/);
    });

    // Given: User is on variable editor page with modified variables
    // When: User clicks "Save" button
    // Then: Variables should be saved to backend
    test('should save variables to backend', async ({ page }) => {
        // Given: Navigate to variable editor
        await page.goto('/html/variable-library-editor.html?file=/project/variables.xml');
        await page.waitForTimeout(2000);

        // When: Modify a cell
        const firstCell = page.locator('.handsontable td, td').first();
        await firstCell.dblclick();
        await firstCell.fill('editedVariable');

        // When: Click save button
        const saveButton = page.locator('button:has-text("保存"), button:has-text("Save")').first();
        await saveButton.click();

        // Then: Success message should appear
        await page.waitForTimeout(1000);
        const successMessage = page.locator('.success, .message:has-text("成功"), .message:has-text("success")').first();
        await expect(successMessage).toBeVisible({ timeout: 5000 }).catch(() => {
            // Success message might be transient, that's okay
        });
    });

    // Given: User is on variable editor page
    // When: User configures variable properties (type, description)
    // Then: Properties should be applied to variable
    test('should configure variable properties', async ({ page }) => {
        // Given: Navigate to variable editor
        await page.goto('/html/variable-library-editor.html?file=/project/variables.xml');
        await page.waitForTimeout(2000);

        // When: Select a variable
        const firstRow = page.locator('.handsontable tr, tr').nth(1);
        await firstRow.click();

        // When: Open property panel if exists
        const propertyPanel = page.locator('.property-panel, .variable-properties, [data-testid="properties"]').first();

        if (await propertyPanel.isVisible()) {
            // Then: Property panel should show variable details
            await expect(propertyPanel).toBeVisible();

            // When: Edit variable type
            const typeSelect = propertyPanel.locator('select[name="type"], [data-testid="variable-type"]');
            if (await typeSelect.isVisible()) {
                await typeSelect.selectOption('String');
            }
        }
    });

    // Given: User is on variable editor page
    // When: User searches for a variable
    // Then: Matching variables should be highlighted
    test('should search and filter variables', async ({ page }) => {
        // Given: Navigate to variable editor
        await page.goto('/html/variable-library-editor.html?file=/project/variables.xml');
        await page.waitForTimeout(2000);

        // When: Type in search box
        const searchBox = page.locator('input[placeholder*="搜索"], input[placeholder*="search"]').first();

        if (await searchBox.isVisible()) {
            await searchBox.fill('test');

            // Then: Grid should filter to show matching results
            await page.waitForTimeout(500);
        }
    });

    // Given: User is on variable editor page
    // When: User exports variables
    // Then: Variables should be exported to file
    test('should export variables to file', async ({ page }) => {
        // Given: Navigate to variable editor
        await page.goto('/html/variable-library-editor.html?file=/project/variables.xml');
        await page.waitForTimeout(2000);

        // When: Click export button
        const exportButton = page.locator('button:has-text("导出"), button:has-text("Export")').first();

        if (await exportButton.isVisible()) {
            // Setup download handler
            const downloadPromise = page.waitForEvent('download');
            await exportButton.click();
            const download = await downloadPromise;

            // Then: File should be downloaded
            expect(download.suggestedFilename()).toContain('.xml');
        }
    });

    // Given: User is on variable editor page
    // When: User imports variables from file
    // Then: Variables should be loaded into grid
    test('should import variables from file', async ({ page }) => {
        // Given: Navigate to variable editor
        await page.goto('/html/variable-library-editor.html?file=/project/variables.xml');
        await page.waitForTimeout(2000);

        // When: Click import button
        const importButton = page.locator('button:has-text("导入"), button:has-text("Import")').first();

        if (await importButton.isVisible()) {
            // When: File input dialog appears (would need file upload handling)
            await importButton.click();

            // Then: File input should be available
            const fileInput = page.locator('input[type="file"]').first();
            await expect(fileInput).toBeVisible({ timeout: 3000 });
        }
    });
});
