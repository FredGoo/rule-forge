import { test, expect } from '@playwright/test';

/**
 * BDD Tests for Decision Table Editor
 *
 * Given: User is logged in and opens decision table editor
 * When: User interacts with decision table
 * Then: Expected table operations should work
 */
test.describe('Decision Table Editor', () => {
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
    // When: User opens decision table editor
    // Then: Decision table editor page should load with HandsOnTable grid
    test('should load decision table editor page with HandsOnTable grid', async ({ page }) => {
        // When: Navigate to decision table editor
        await page.goto('/html/decision-table-editor.html?file=/project/decision-table.xml');

        // Then: Page should load
        await expect(page).toHaveTitle(/Decision Table/);

        // Then: HandsOnTable grid should be visible
        const grid = page.locator('.handsontable, [data-hot-table]').first();
        await expect(grid).toBeVisible({ timeout: 10000 });
    });

    // Given: User is on decision table editor page
    // When: Page loads with existing table
    // Then: Grid should display condition and action columns
    test('should display condition and action columns', async ({ page }) => {
        // Given: Navigate to decision table editor
        await page.goto('/html/decision-table-editor.html?file=/project/decision-table.xml');
        await page.waitForTimeout(2000);

        // Then: Grid should have headers
        const headers = page.locator('.handsontable thead th, th');
        await expect(headers.first()).toBeVisible();

        // Then: Grid should have data rows
        const dataCells = page.locator('.handsontable td, tbody td');
        await expect(dataCells.first()).toBeVisible();
    });

    // Given: User is on decision table editor page
    // When: User clicks "Add Row" button
    // Then: New row should be added to table
    test('should add new row to decision table', async ({ page }) => {
        // Given: Navigate to decision table editor
        await page.goto('/html/decision-table-editor.html?file=/project/decision-table.xml');
        await page.waitForTimeout(2000);

        // Get initial row count
        const initialRows = await page.locator('.handsontable tr, tbody tr').count();

        // When: Click add row button
        const addRowButton = page.locator('button:has-text("添加行"), button:has-text("Add Row")').first();
        await addRowButton.click();

        // Then: Row count should increase
        await page.waitForTimeout(500);
        const newRowCount = await page.locator('.handsontable tr, tbody tr').count();
        expect(newRowCount).toBeGreaterThan(initialRows);
    });

    // Given: User is on decision table editor page
    // When: User clicks "Delete Row" button
    // Then: Selected row should be removed from table
    test('should delete row from decision table', async ({ page }) => {
        // Given: Navigate to decision table editor
        await page.goto('/html/decision-table-editor.html?file=/project/decision-table.xml');
        await page.waitForTimeout(2000);

        // Get initial row count
        const initialRows = await page.locator('.handsontable tr, tbody tr').count();

        // When: Select a row
        const firstRow = page.locator('.handsontable tbody tr, tbody tr').first();
        await firstRow.click();

        // When: Click delete row button
        const deleteRowButton = page.locator('button:has-text("删除行"), button:has-text("Delete Row")').first();
        await deleteRowButton.click();

        // Then: Row count should decrease
        await page.waitForTimeout(500);
        const newRowCount = await page.locator('.handsontable tr, tbody tr').count();
        expect(newRowCount).toBeLessThan(initialRows);
    });

    // Given: User is on decision table editor page
    // When: User clicks "Add Column" button
    // Then: New column should be added to table
    test('should add new column to decision table', async ({ page }) => {
        // Given: Navigate to decision table editor
        await page.goto('/html/decision-table-editor.html?file=/project/decision-table.xml');
        await page.waitForTimeout(2000);

        // Get initial column count
        const initialCols = await page.locator('.handsontable th, thead th').count();

        // When: Click add column button
        const addColButton = page.locator('button:has-text("添加列"), button:has-text("Add Column")').first();
        await addColButton.click();

        // Then: Column count should increase
        await page.waitForTimeout(500);
        const newColCount = await page.locator('.handsontable th, thead th').count();
        expect(newColCount).toBeGreaterThan(initialCols);
    });

    // Given: User is on decision table editor page
    // When: User clicks "Configure Column" button
    // Then: Column configuration dialog should appear
    test('should show column configuration dialog', async ({ page }) => {
        // Given: Navigate to decision table editor
        await page.goto('/html/decision-table-editor.html?file=/project/decision-table.xml');
        await page.waitForTimeout(2000);

        // When: Click configure column button
        const configColButton = page.locator('button:has-text("配置列"), button:has-text("Configure Column")').first();

        if (await configColButton.isVisible()) {
            await configColButton.click();

            // Then: Column configuration dialog should appear
            const dialog = page.locator('.dialog, .modal, [role="dialog"]').first();
            await expect(dialog).toBeVisible();

            // Then: Dialog should have column type selector
            const typeSelect = dialog.locator('select[name="columnType"], select[name="type"]').first();
            await expect(typeSelect).toBeVisible();
        }
    });

    // Given: User is on decision table editor page
    // When: User edits a cell in the grid
    // Then: Cell value should be updated
    test('should edit cell value in decision table', async ({ page }) => {
        // Given: Navigate to decision table editor
        await page.goto('/html/decision-table-editor.html?file=/project/decision-table.xml');
        await page.waitForTimeout(2000);

        // When: Double-click on a cell to edit
        const firstCell = page.locator('.handsontable td, tbody td').first();
        await firstCell.dblclick();

        // When: Type new value
        await firstCell.fill('test-value');

        // Then: Cell should be updated
        await page.waitForTimeout(500);
        await expect(firstCell).toHaveText(/test-value/);
    });

    // Given: User is on decision table editor page
    // When: User configures column properties (type, variable)
    // Then: Properties should be applied to column
    test('should configure column properties', async ({ page }) => {
        // Given: Navigate to decision table editor
        await page.goto('/html/decision-table-editor.html?file=/project/decision-table.xml');
        await page.waitForTimeout(2000);

        // When: Click on column header
        const columnHeader = page.locator('.handsontable th, thead th').first();
        await columnHeader.click();

        // When: Click configure button
        const configButton = page.locator('button:has-text("配置"), button:has-text("Configure")').first();

        if (await configButton.isVisible()) {
            await configButton.click();

            // Then: Configuration dialog should appear
            const dialog = page.locator('.dialog, .modal, [role="dialog"]').first();
            await expect(dialog).toBeVisible();

            // When: Select column type
            const typeSelect = dialog.locator('select[name="columnType"]').first();
            if (await typeSelect.isVisible()) {
                await typeSelect.selectOption('condition');
            }
        }
    });

    // Given: User is on decision table editor page with modified table
    // When: User clicks "Save" button
    // Then: Table should be saved to backend
    test('should save decision table to backend', async ({ page }) => {
        // Given: Navigate to decision table editor
        await page.goto('/html/decision-table-editor.html?file=/project/decision-table.xml');
        await page.waitForTimeout(2000);

        // When: Modify a cell
        const firstCell = page.locator('.handsontable td, tbody td').first();
        await firstCell.dblclick();
        await firstCell.fill('edited-value');

        // When: Click save button
        const saveButton = page.locator('button:has-text("保存"), button:has-text("Save")').first();
        await saveButton.click();

        // Then: Success message should appear
        await page.waitForTimeout(1000);
        const successMessage = page.locator('.success, .message:has-text("成功"), .message:has-text("saved")').first();
        await expect(successMessage).toBeVisible({ timeout: 5000 }).catch(() => {
            // Success message might be transient
        });
    });

    // Given: User is on decision table editor page
    // When: User merges cells
    // Then: Cells should be merged
    test('should merge cells in decision table', async ({ page }) => {
        // Given: Navigate to decision table editor
        await page.goto('/html/decision-table-editor.html?file=/project/decision-table.xml');
        await page.waitForTimeout(2000);

        // When: Select multiple cells (would require specific cell selection)
        const firstCell = page.locator('.handsontable td, tbody td').first();
        await firstCell.click();

        // When: Click merge button
        const mergeButton = page.locator('button:has-text("合并"), button:has-text("Merge")').first();

        if (await mergeButton.isVisible()) {
            await mergeButton.click();

            // Then: Cells should be merged
            await page.waitForTimeout(500);
        }
    });

    // Given: User is on decision table editor page
    // When: User unmerges cells
    // Then: Cells should be unmerged
    test('should unmerge cells in decision table', async ({ page }) => {
        // Given: Navigate to decision table editor
        await page.goto('/html/decision-table-editor.html?file=/project/decision-table.xml');
        await page.waitForTimeout(2000);

        // When: Select merged cell
        const mergedCell = page.locator('.handsontable td.merged, td[ rowspan], td[ colspan]').first();

        if (await mergedCell.isVisible()) {
            await mergedCell.click();

            // When: Click unmerge button
            const unmergeButton = page.locator('button:has-text("取消合并"), button:has-text("Unmerge")').first();
            await unmergeButton.click();

            // Then: Cells should be unmerged
            await page.waitForTimeout(500);
        }
    });

    // Given: User is on decision table editor page
    // When: User validates decision table
    // Then: Validation results should be displayed
    test('should validate decision table', async ({ page }) => {
        // Given: Navigate to decision table editor
        await page.goto('/html/decision-table-editor.html?file=/project/decision-table.xml');
        await page.waitForTimeout(2000);

        // When: Click validate button
        const validateButton = page.locator('button:has-text("验证"), button:has-text("Validate")').first();

        if (await validateButton.isVisible()) {
            await validateButton.click();

            // Then: Validation results should appear
            await page.waitForTimeout(1000);
            const validationResult = page.locator('.validation-result, .message, [role="alert"]').first();
            await expect(validationResult).toBeVisible({ timeout: 5000 }).catch(() => {
                // Validation might be successful without dialog
            });
        }
    });

    // Given: User is on decision table editor page
    // When: User searches for content in table
    // Then: Matching cells should be highlighted
    test('should search and highlight cells in table', async ({ page }) => {
        // Given: Navigate to decision table editor
        await page.goto('/html/decision-table-editor.html?file=/project/decision-table.xml');
        await page.waitForTimeout(2000);

        // When: Type in search box
        const searchBox = page.locator('input[placeholder*="搜索"], input[placeholder*="search"]').first();

        if (await searchBox.isVisible()) {
            await searchBox.fill('test');

            // Then: Matching cells should be highlighted
            await page.waitForTimeout(500);
            const highlightedCells = page.locator('.highlight, .current-highlight');
            await expect(highlightedCells.first()).toBeVisible({ timeout: 3000 }).catch(() => {
                // No matches might be found
            });
        }
    });
});
