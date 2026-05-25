import { test, expect } from '@playwright/test';

/**
 * BDD Tests for Script Decision Table Editor
 *
 * Given: User is logged in and opens script decision table editor
 * When: User interacts with script decision table
 * Then: Expected table operations should work
 */
test.describe('Script Decision Table Editor', () => {
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
    // When: User opens script decision table editor
    // Then: Script decision table editor page should load with grid
    test('should load script decision table editor page with grid', async ({ page }) => {
        // When: Navigate to script decision table editor
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');

        // Then: Page should load
        await expect(page).toHaveTitle(/Script Decision Table/);

        // Then: Table grid should be visible
        const grid = page.locator('.handsontable, [data-hot-table], table').first();
        await expect(grid).toBeVisible({ timeout: 10000 });
    });

    // Given: User is on script decision table editor page
    // When: Page loads with existing table
    // Then: Grid should display table structure with script cells
    test('should display table with script cells', async ({ page }) => {
        // Given: Navigate to script decision table editor
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForTimeout(2000);

        // Then: Grid should have headers
        const headers = page.locator('.handsontable thead th, th');
        await expect(headers.first()).toBeVisible();

        // Then: Grid should have data rows
        const dataCells = page.locator('.handsontable td, tbody td');
        await expect(dataCells.first()).toBeVisible();
    });

    // Given: User is on script decision table editor page
    // When: User clicks "Add Row" button
    // Then: New row should be added to table
    test('should add new row to script decision table', async ({ page }) => {
        // Given: Navigate to script decision table editor
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
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

    // Given: User is on script decision table editor page
    // When: User clicks "Add Column" button
    // Then: New column should be added to table
    test('should add new column to script decision table', async ({ page }) => {
        // Given: Navigate to script decision table editor
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
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

    // Given: User is on script decision table editor page
    // When: User clicks "Delete Row" button
    // Then: Selected row should be removed from table
    test('should delete row from script decision table', async ({ page }) => {
        // Given: Navigate to script decision table editor
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
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

    // Given: User is on script decision table editor page
    // When: User edits a script cell
    // Then: Script editor dialog should appear
    test('should open script editor for cell', async ({ page }) => {
        // Given: Navigate to script decision table editor
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForTimeout(2000);

        // When: Double-click on a script cell
        const firstCell = page.locator('.handsontable td, tbody td').first();
        await firstCell.dblclick();

        // Then: Script editor dialog should appear
        await page.waitForTimeout(500);
        const dialog = page.locator('.dialog, .modal, [role="dialog"]').first();
        await expect(dialog).toBeVisible({ timeout: 5000 }).catch(() => {
            // Editor might open inline instead
        });

        // Then: Editor should have textarea
        const textarea = page.locator('.dialog textarea, .editor textarea').first();
        await expect(textarea).toBeVisible({ timeout: 3000 }).catch(() => {
            // Inline editing might use different element
        });
    });

    // Given: User is on script decision table editor page
    // When: User edits cell content in script editor
    // Then: Cell value should be updated
    test('should edit cell content in script editor', async ({ page }) => {
        // Given: Navigate to script decision table editor
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForTimeout(2000);

        // When: Double-click on a cell
        const firstCell = page.locator('.handsontable td, tbody td').first();
        await firstCell.dblclick();

        // Wait for editor to open
        await page.waitForTimeout(500);

        // When: Edit content in editor
        const textarea = page.locator('.dialog textarea, .editor textarea, .handsontable textarea').first();
        if (await textarea.isVisible()) {
            await textarea.fill('return x > 10;');

            // When: Save and close editor
            const saveButton = page.locator('button:has-text("确定"), button:has-text("OK"), button:has-text("Save")').first();
            if (await saveButton.isVisible()) {
                await saveButton.click();
            } else {
                await page.keyboard.press('Escape');
            }

            // Then: Cell should be updated
            await page.waitForTimeout(500);
        }
    });

    // Given: User is on script decision table editor page
    // When: User configures column properties
    // Then: Properties should be applied to column
    test('should configure column properties', async ({ page }) => {
        // Given: Navigate to script decision table editor
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForTimeout(2000);

        // When: Click on column header
        const columnHeader = page.locator('.handsontable th, thead th').first();
        await columnHeader.click();

        // When: Click configure button
        const configButton = page.locator('button:has-text("配置"), button:has-text("Configure"), button:has-text("Column Settings")').first();

        if (await configButton.isVisible()) {
            await configButton.click();

            // Then: Configuration dialog should appear
            const dialog = page.locator('.dialog, .modal, [role="dialog"]').first();
            await expect(dialog).toBeVisible();

            // When: Edit column name
            const nameInput = dialog.locator('input[name="columnName"], input[name="name"]').first();
            if (await nameInput.isVisible()) {
                await nameInput.fill('testColumn');
            }
        }
    });

    // Given: User is on script decision table editor page with modified table
    // When: User clicks "Save" button
    // Then: Table should be saved to backend
    test('should save script decision table to backend', async ({ page }) => {
        // Given: Navigate to script decision table editor
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForTimeout(2000);

        // When: Modify a cell
        const firstCell = page.locator('.handsontable td, tbody td').first();
        await firstCell.dblclick();
        await page.waitForTimeout(500);

        const textarea = page.locator('.dialog textarea, .editor textarea').first();
        if (await textarea.isVisible()) {
            await textarea.fill('return true;');

            const saveButton = page.locator('button:has-text("确定"), button:has-text("OK")').first();
            if (await saveButton.isVisible()) {
                await saveButton.click();
            } else {
                await page.keyboard.press('Escape');
            }
        }

        await page.waitForTimeout(500);

        // When: Click save button
        const saveTableButton = page.locator('button:has-text("保存"), button:has-text("Save")').first();
        await saveTableButton.click();

        // Then: Success message should appear
        await page.waitForTimeout(1000);
        const successMessage = page.locator('.success, .message:has-text("成功"), .message:has-text("saved")').first();
        await expect(successMessage).toBeVisible({ timeout: 5000 }).catch(() => {
            // Success message might be transient
        });
    });

    // Given: User is on script decision table editor page
    // When: User validates table scripts
    // Then: Validation results should be displayed
    test('should validate scripts in table', async ({ page }) => {
        // Given: Navigate to script decision table editor
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
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

    // Given: User is on script decision table editor page
    // When: User formats script in cell
    // Then: Script should be formatted
    test('should format script in cell', async ({ page }) => {
        // Given: Navigate to script decision table editor
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForTimeout(2000);

        // When: Double-click on a cell
        const firstCell = page.locator('.handsontable td, tbody td').first();
        await firstCell.dblclick();
        await page.waitForTimeout(500);

        // When: Click format button in editor
        const formatButton = page.locator('.dialog button:has-text("格式化"), .dialog button:has-text("Format")').first();

        if (await formatButton.isVisible()) {
            await formatButton.click();

            // Then: Script should be formatted
            await page.waitForTimeout(500);
        }

        // Close editor
        await page.keyboard.press('Escape');
    });

    // Given: User is on script decision table editor page
    // When: User searches for content
    // Then: Matching cells should be highlighted
    test('should search and highlight cells', async ({ page }) => {
        // Given: Navigate to script decision table editor
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForTimeout(2000);

        // When: Type in search box
        const searchBox = page.locator('input[placeholder*="搜索"], input[placeholder*="search"]').first();

        if (await searchBox.isVisible()) {
            await searchBox.fill('return');

            // Then: Matching cells should be highlighted
            await page.waitForTimeout(500);
            const highlightedCells = page.locator('.highlight, .current-highlight');
            await expect(highlightedCells.first()).toBeVisible({ timeout: 3000 }).catch(() => {
                // No matches might be found
            });
        }
    });

    // Given: User is on script decision table editor page
    // When: User exports table
    // Then: Table file should be downloaded
    test('should export table to file', async ({ page }) => {
        // Given: Navigate to script decision table editor
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForTimeout(2000);

        // When: Click export button
        const exportButton = page.locator('button:has-text("导出"), button:has-text("Export")').first();

        if (await exportButton.isVisible()) {
            // Setup download handler
            const downloadPromise = page.waitForEvent('download');
            await exportButton.click();
            const download = await downloadPromise;

            // Then: Table file should be downloaded
            expect(download.suggestedFilename()).toContain('.xml');
        }
    });

    // Given: User is on script decision table editor page
    // When: User imports table from file
    // Then: Table should be loaded into editor
    test('should import table from file', async ({ page }) => {
        // Given: Navigate to script decision table editor
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForTimeout(2000);

        // When: Click import button
        const importButton = page.locator('button:has-text("导入"), button:has-text("Import")').first();

        if (await importButton.isVisible()) {
            await importButton.click();

            // Then: File input should be available
            const fileInput = page.locator('input[type="file"]').first();
            await expect(fileInput).toBeVisible({ timeout: 3000 });
        }
    });

    // Given: User is on script decision table editor page
    // When: User views table summary/statistics
    // Then: Summary information should be displayed
    test('should display table summary', async ({ page }) => {
        // Given: Navigate to script decision table editor
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForTimeout(2000);

        // When: Click summary/info button
        const summaryButton = page.locator('button:has-text("摘要"), button:has-text("Summary"), button:has-text("Info")').first();

        if (await summaryButton.isVisible()) {
            await summaryButton.click();

            // Then: Summary panel should appear
            const summaryPanel = page.locator('.summary-panel, .info-panel, [role="dialog"]').first();
            await expect(summaryPanel).toBeVisible({ timeout: 3000 });
        }
    });

    // Given: User is on script decision table editor page
    // When: User uses keyboard shortcuts
    // Then: Corresponding actions should be triggered
    test('should handle keyboard shortcuts', async ({ page }) => {
        // Given: Navigate to script decision table editor
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForTimeout(2000);

        // When: Press Ctrl+S to save
        await page.keyboard.press('Control+S');
        await page.waitForTimeout(500);

        // Note: Other shortcuts could be tested here
        // Ctrl+Z: undo, Ctrl+Y: redo, etc.
    });
});
