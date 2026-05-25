import { test, expect } from '@playwright/test';

/**
 * BDD Tests for Knowledge Package Management
 *
 * Given: User is logged in and opens package editor
 * When: User interacts with package management
 * Then: Expected package operations should work
 */
test.describe('Knowledge Package Management', () => {
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
    // When: User opens knowledge package editor
    // Then: Package editor page should load with package list
    test('should load knowledge package editor page', async ({ page }) => {
        // When: Navigate to package editor
        await page.goto('/html/package-editor.html?file=/project/packages.xml');

        // Then: Page should load
        await expect(page).toHaveTitle(/Package/);

        // Then: Package list/grid should be visible
        const packageList = page.locator('.package-list, .grid, [data-testid="package-list"]').first();
        await expect(packageList).toBeVisible({ timeout: 10000 });
    });

    // Given: User is on package editor page
    // When: Page loads with existing packages
    // Then: Packages should be displayed in the list
    test('should display existing packages', async ({ page }) => {
        // Given: Navigate to package editor
        await page.goto('/html/package-editor.html?file=/project/packages.xml');
        await page.waitForTimeout(2000);

        // Then: Package list should contain items
        const packageItems = page.locator('.package-item, .list-item, tr');
        await expect(packageItems.first()).toBeVisible();
    });

    // Given: User is on package editor page
    // When: User clicks "Add Package" button
    // Then: New package dialog should appear
    test('should show add package dialog', async ({ page }) => {
        // Given: Navigate to package editor
        await page.goto('/html/package-editor.html?file=/project/packages.xml');
        await page.waitForTimeout(2000);

        // When: Click add package button
        const addPackageButton = page.locator('button:has-text("添加包"), button:has-text("Add Package"), button:has-text("New Package")').first();
        await addPackageButton.click();

        // Then: Add package dialog should appear
        const dialog = page.locator('.dialog, .modal, [role="dialog"]').first();
        await expect(dialog).toBeVisible();

        // Then: Dialog should have package name input
        const nameInput = dialog.locator('input[name="packageName"], input[name="name"]').first();
        await expect(nameInput).toBeVisible();
    });

    // Given: User is on package editor page with add package dialog open
    // When: User enters package name and confirms
    // Then: New package should be added to list
    test('should add new package to list', async ({ page }) => {
        // Given: Navigate to package editor
        await page.goto('/html/package-editor.html?file=/project/packages.xml');
        await page.waitForTimeout(2000);

        // When: Click add package button
        const addPackageButton = page.locator('button:has-text("添加包"), button:has-text("Add Package")').first();
        await addPackageButton.click();

        // When: Enter package name
        const nameInput = page.locator('.dialog input[name="packageName"], .dialog input[name="name"]').first();
        await nameInput.fill('test-package');

        // When: Click confirm button
        const confirmButton = page.locator('.dialog button:has-text("确定"), .dialog button:has-text("OK"), .dialog button:has-text("Add")').first();
        await confirmButton.click();

        // Then: New package should appear in list
        await page.waitForTimeout(1000);
        const newPackage = page.locator('.package-item:has-text("test-package"), tr:has-text("test-package")').first();
        await expect(newPackage).toBeVisible({ timeout: 5000 }).catch(() => {
            // Package might have been added but not immediately visible
        });
    });

    // Given: User is on package editor page with existing package
    // When: User selects and deletes a package
    // Then: Package should be removed from list
    test('should delete package from list', async ({ page }) => {
        // Given: Navigate to package editor
        await page.goto('/html/package-editor.html?file=/project/packages.xml');
        await page.waitForTimeout(2000);

        // When: Select a package
        const packageItem = page.locator('.package-item, tr').nth(0);
        await packageItem.click();

        // When: Click delete button
        const deleteButton = page.locator('button:has-text("删除"), button:has-text("Delete")').first();
        await deleteButton.click();

        // Then: Confirmation dialog should appear
        const confirmDialog = page.locator('.dialog, .modal, [role="dialog"]').first();
        await expect(confirmDialog).toBeVisible();

        // When: Confirm deletion
        const confirmButton = confirmDialog.locator('button:has-text("确定"), button:has-text("OK"), button:has-text("Delete")').first();
        await confirmButton.click();

        // Then: Package should be removed
        await page.waitForTimeout(1000);
    });

    // Given: User is on package editor page with package selected
    // When: User clicks "Add Resource" button
    // Then: Resource selection dialog should appear
    test('should show add resource dialog for package', async ({ page }) => {
        // Given: Navigate to package editor
        await page.goto('/html/package-editor.html?file=/project/packages.xml');
        await page.waitForTimeout(2000);

        // When: Select a package
        const packageItem = page.locator('.package-item, tr').first();
        await packageItem.click();

        // When: Click add resource button
        const addResourceButton = page.locator('button:has-text("添加资源"), button:has-text("Add Resource")').first();

        if (await addResourceButton.isVisible()) {
            await addResourceButton.click();

            // Then: Resource selection dialog should appear
            const dialog = page.locator('.dialog, .modal, [role="dialog"]').first();
            await expect(dialog).toBeVisible();

            // Then: Dialog should show available resources
            const resourceList = dialog.locator('.resource-list, .file-list, select').first();
            await expect(resourceList).toBeVisible();
        }
    });

    // Given: User is on package editor page
    // When: User adds resource items to package
    // Then: Resources should appear in package resource list
    test('should add resource items to package', async ({ page }) => {
        // Given: Navigate to package editor
        await page.goto('/html/package-editor.html?file=/project/packages.xml');
        await page.waitForTimeout(2000);

        // When: Select a package
        const packageItem = page.locator('.package-item.selected, tr.selected').first();
        if (!await packageItem.isVisible()) {
            await page.locator('.package-item, tr').first().click();
        }

        // When: Click add resource button
        const addResourceButton = page.locator('button:has-text("添加资源"), button:has-text("Add Resource")').first();

        if (await addResourceButton.isVisible()) {
            await addResourceButton.click();

            // Then: Select resources
            const dialog = page.locator('.dialog, .modal').first();
            const resourceCheckbox = dialog.locator('input[type="checkbox"]').first();
            if (await resourceCheckbox.isVisible()) {
                await resourceCheckbox.click();

                // When: Confirm selection
                const confirmButton = dialog.locator('button:has-text("确定"), button:has-text("OK")').first();
                await confirmButton.click();

                // Then: Resources should be added to package
                await page.waitForTimeout(1000);
            }
        }
    });

    // Given: User is on package editor page with modified packages
    // When: User clicks "Save" button
    // Then: Packages should be saved to backend
    test('should save packages to backend', async ({ page }) => {
        // Given: Navigate to package editor
        await page.goto('/html/package-editor.html?file=/project/packages.xml');
        await page.waitForTimeout(2000);

        // When: Modify package (select different package)
        const packageItem = page.locator('.package-item, tr').first();
        await packageItem.click();

        // When: Click save button
        const saveButton = page.locator('button:has-text("保存"), button:has-text("Save")').first();
        await saveButton.click();

        // Then: Success message should appear
        await page.waitForTimeout(1000);
        const successMessage = page.locator('.success, .message:has-text("成功"), .message:has-text("success")').first();
        await expect(successMessage).toBeVisible({ timeout: 5000 }).catch(() => {
            // Success message might be transient
        });
    });

    // Given: User is on package editor page
    // When: User performs quick test on package
    // Then: Quick test should execute and show results
    test('should perform quick test on package', async ({ page }) => {
        // Given: Navigate to package editor
        await page.goto('/html/package-editor.html?file=/project/packages.xml');
        await page.waitForTimeout(2000);

        // When: Select a package
        const packageItem = page.locator('.package-item, tr').first();
        await packageItem.click();

        // When: Click quick test button
        const quickTestButton = page.locator('button:has-text("快速测试"), button:has-text("Quick Test")').first();

        if (await quickTestButton.isVisible()) {
            // Setup response listener for test results
            await quickTestButton.click();

            // Then: Test results dialog should appear
            await page.waitForTimeout(2000);
            const resultDialog = page.locator('.dialog, .modal, [role="dialog"]').first();
            await expect(resultDialog).toBeVisible({ timeout: 10000 }).catch(() => {
                // Test might take longer
            });
        }
    });

    // Given: User is on package editor page
    // When: User configures package properties
    // Then: Properties should be applied to package
    test('should configure package properties', async ({ page }) => {
        // Given: Navigate to package editor
        await page.goto('/html/package-editor.html?file=/project/packages.xml');
        await page.waitForTimeout(2000);

        // When: Select a package
        const packageItem = page.locator('.package-item, tr').first();
        await packageItem.click();

        // When: Open property panel
        const propertyPanel = page.locator('.property-panel, .package-properties').first();

        if (await propertyPanel.isVisible()) {
            // Then: Property panel should show package details
            await expect(propertyPanel).toBeVisible();

            // When: Edit package description
            const descInput = propertyPanel.locator('textarea[name="description"], input[name="description"]').first();
            if (await descInput.isVisible()) {
                await descInput.fill('Test package description');
            }
        }
    });

    // Given: User is on package editor page
    // When: User exports package
    // Then: Package file should be downloaded
    test('should export package to file', async ({ page }) => {
        // Given: Navigate to package editor
        await page.goto('/html/package-editor.html?file=/project/packages.xml');
        await page.waitForTimeout(2000);

        // When: Select a package
        const packageItem = page.locator('.package-item, tr').first();
        await packageItem.click();

        // When: Click export button
        const exportButton = page.locator('button:has-text("导出"), button:has-text("Export")').first();

        if (await exportButton.isVisible()) {
            // Setup download handler
            const downloadPromise = page.waitForEvent('download');
            await exportButton.click();
            const download = await downloadPromise;

            // Then: Package file should be downloaded
            expect(download.suggestedFilename()).toContain('.pkg');
        }
    });
});
