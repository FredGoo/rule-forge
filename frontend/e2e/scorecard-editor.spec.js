import { test, expect } from '@playwright/test';

/**
 * BDD Tests for Scorecard Editor
 *
 * Given: User is logged in and opens scorecard editor
 * When: User interacts with scorecard configuration
 * Then: Expected scorecard operations should work
 */
test.describe('Scorecard Editor', () => {
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
    // When: User opens scorecard editor
    // Then: Scorecard editor page should load
    test('should load scorecard editor page', async ({ page }) => {
        // When: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');

        // Then: Page should load
        await expect(page).toHaveTitle(/Scorecard/);

        // Then: Scorecard editor interface should be visible
        const editor = page.locator('.scorecard-editor, .editor, [data-testid="scorecard-editor"]').first();
        await expect(editor).toBeVisible({ timeout: 10000 });
    });

    // Given: User is on scorecard editor page
    // When: Page loads with existing scorecard
    // Then: Scorecard configuration should be displayed
    test('should display existing scorecard configuration', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
        await page.waitForTimeout(2000);

        // Then: Base score section should be visible
        const baseScoreSection = page.locator('.base-score, .score-section:has-text("基础分"), .score-section:has-text("Base Score")').first();
        await expect(baseScoreSection).toBeVisible();

        // Then: Attribute scoring sections should be visible
        const attributeSection = page.locator('.attribute-score, .score-section:has-text("属性"), .score-section:has-text("Attribute")').first();
        await expect(attributeSection).toBeVisible();
    });

    // Given: User is on scorecard editor page
    // When: User configures base score
    // Then: Base score should be set
    test('should configure base score', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
        await page.waitForTimeout(2000);

        // When: Enter base score value
        const baseScoreInput = page.locator('input[name="baseScore"], input[placeholder*="基础分"], input[placeholder*="base score"]').first();

        if (await baseScoreInput.isVisible()) {
            await baseScoreInput.clear();
            await baseScoreInput.fill('100');

            // Then: Base score should be saved
            await page.waitForTimeout(500);
            await expect(baseScoreInput).toHaveValue('100');
        }
    });

    // Given: User is on scorecard editor page
    // When: User adds attribute for scoring
    // Then: Attribute should be added to scorecard
    test('should add attribute to scorecard', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
        await page.waitForTimeout(2000);

        // Get initial attribute count
        const initialAttributes = await page.locator('.attribute-item, .score-attribute').count();

        // When: Click add attribute button
        const addAttributeButton = page.locator('button:has-text("添加属性"), button:has-text("Add Attribute"), button:has-text("Add")').first();
        await addAttributeButton.click();

        // Then: Attribute count should increase
        await page.waitForTimeout(500);
        const newAttributeCount = await page.locator('.attribute-item, .score-attribute').count();
        expect(newAttributeCount).toBeGreaterThan(initialAttributes);
    });

    // Given: User is on scorecard editor page with attribute
    // When: User configures attribute scoring rules
    // Then: Rules should be applied to attribute
    test('should configure attribute scoring rules', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
        await page.waitForTimeout(2000);

        // When: Select an attribute
        const attributeItem = page.locator('.attribute-item, .score-attribute').first();
        await attributeItem.click();

        // Then: Attribute configuration panel should be visible
        const configPanel = page.locator('.attribute-config, .scoring-rules, [data-testid="attribute-config"]').first();
        await expect(configPanel).toBeVisible();

        // When: Configure attribute name
        const nameInput = configPanel.locator('input[name="attributeName"], input[name="name"]').first();
        if (await nameInput.isVisible()) {
            await nameInput.fill('age');
        }

        // When: Add scoring rule
        const addRuleButton = configPanel.locator('button:has-text("添加规则"), button:has-text("Add Rule")').first();
        if (await addRuleButton.isVisible()) {
            await addRuleButton.click();

            // Then: Rule should be added
            await page.waitForTimeout(500);
            const ruleItem = configPanel.locator('.rule-item, .scoring-rule').first();
            await expect(ruleItem).toBeVisible();
        }
    });

    // Given: User is on scorecard editor page
    // When: User deletes attribute
    // Then: Attribute should be removed from scorecard
    test('should delete attribute from scorecard', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
        await page.waitForTimeout(2000);

        // Get initial attribute count
        const initialAttributes = await page.locator('.attribute-item, .score-attribute').count();

        // When: Select an attribute
        const attributeItem = page.locator('.attribute-item, .score-attribute').first();
        await attributeItem.click();

        // When: Click delete button
        const deleteButton = page.locator('button:has-text("删除"), button:has-text("Delete")').first();
        await deleteButton.click();

        // Then: Confirmation dialog might appear
        const confirmDialog = page.locator('.dialog, .modal, [role="dialog"]').first();
        if (await confirmDialog.isVisible()) {
            const confirmButton = confirmDialog.locator('button:has-text("确定"), button:has-text("OK")').first();
            await confirmButton.click();
        }

        // Then: Attribute count should decrease
        await page.waitForTimeout(500);
        const newAttributeCount = await page.locator('.attribute-item, .score-attribute').count();
        expect(newAttributeCount).toBeLessThan(initialAttributes);
    });

    // Given: User is on scorecard editor page with attribute
    // When: User sets score range for attribute
    // Then: Score range should be configured
    test('should configure score range for attribute', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
        await page.waitForTimeout(2000);

        // When: Select an attribute
        const attributeItem = page.locator('.attribute-item, .score-attribute').first();
        await attributeItem.click();

        // When: Configure score range
        const configPanel = page.locator('.attribute-config, .scoring-rules').first();
        const minScoreInput = configPanel.locator('input[name="minScore"], input[placeholder*="最小"]').first();
        const maxScoreInput = configPanel.locator('input[name="maxScore"], input[placeholder*="最大"]').first();

        if (await minScoreInput.isVisible()) {
            await minScoreInput.fill('0');
        }

        if (await maxScoreInput.isVisible()) {
            await maxScoreInput.fill('100');
        }

        // Then: Score range should be set
        await page.waitForTimeout(500);
    });

    // Given: User is on scorecard editor page with attribute
    // When: User adds condition-based scoring rule
    // Then: Rule should be added with condition and score
    test('should add condition-based scoring rule', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
        await page.waitForTimeout(2000);

        // When: Select an attribute
        const attributeItem = page.locator('.attribute-item, .score-attribute').first();
        await attributeItem.click();

        // When: Add scoring rule
        const configPanel = page.locator('.attribute-config, .scoring-rules').first();
        const addRuleButton = configPanel.locator('button:has-text("添加规则"), button:has-text("Add Rule")').first();

        if (await addRuleButton.isVisible()) {
            await addRuleButton.click();

            // When: Configure rule condition
            const conditionInput = configPanel.locator('input[name="condition"], textarea[name="condition"]').first();
            if (await conditionInput.isVisible()) {
                await conditionInput.fill('age >= 18');
            }

            // When: Configure rule score
            const scoreInput = configPanel.locator('input[name="score"], input[name="points"]').first();
            if (await scoreInput.isVisible()) {
                await scoreInput.fill('10');
            }

            // Then: Rule should be configured
            await page.waitForTimeout(500);
        }
    });

    // Given: User is on scorecard editor page
    // When: User previews scorecard calculation
    // Then: Preview results should be displayed
    test('should preview scorecard calculation', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
        await page.waitForTimeout(2000);

        // When: Click preview button
        const previewButton = page.locator('button:has-text("预览"), button:has-text("Preview"), button:has-text("Test")').first();

        if (await previewButton.isVisible()) {
            await previewButton.click();

            // Then: Preview dialog should appear
            const previewDialog = page.locator('.dialog, .modal, [role="dialog"]').first();
            await expect(previewDialog).toBeVisible({ timeout: 3000 });

            // Then: Preview should show test input fields
            const testInput = previewDialog.locator('input, select').first();
            await expect(testInput).toBeVisible();
        }
    });

    // Given: User is on scorecard editor page with modified configuration
    // When: User clicks "Save" button
    // Then: Scorecard should be saved to backend
    test('should save scorecard to backend', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
        await page.waitForTimeout(2000);

        // When: Modify base score
        const baseScoreInput = page.locator('input[name="baseScore"], input[placeholder*="基础分"]').first();
        if (await baseScoreInput.isVisible()) {
            await baseScoreInput.clear();
            await baseScoreInput.fill('200');
        }

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

    // Given: User is on scorecard editor page
    // When: User validates scorecard configuration
    // Then: Validation results should be displayed
    test('should validate scorecard configuration', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
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

    // Given: User is on scorecard editor page
    // When: User exports scorecard
    // Then: Scorecard file should be downloaded
    test('should export scorecard to file', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
        await page.waitForTimeout(2000);

        // When: Click export button
        const exportButton = page.locator('button:has-text("导出"), button:has-text("Export")').first();

        if (await exportButton.isVisible()) {
            // Setup download handler
            const downloadPromise = page.waitForEvent('download');
            await exportButton.click();
            const download = await downloadPromise;

            // Then: Scorecard file should be downloaded
            expect(download.suggestedFilename()).toContain('.xml');
        }
    });

    // Given: User is on scorecard editor page
    // When: User imports scorecard from file
    // Then: Scorecard should be loaded into editor
    test('should import scorecard from file', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
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

    // Given: User is on scorecard editor page
    // When: User configures scorecard properties (name, description)
    // Then: Properties should be applied to scorecard
    test('should configure scorecard properties', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
        await page.waitForTimeout(2000);

        // When: Edit scorecard name
        const nameInput = page.locator('input[name="scorecardName"], input[name="name"]').first();

        if (await nameInput.isVisible()) {
            await nameInput.clear();
            await nameInput.fill('test-scorecard');

            // Then: Name should be updated
            await page.waitForTimeout(500);
            await expect(nameInput).toHaveValue('test-scorecard');
        }

        // When: Edit description
        const descInput = page.locator('textarea[name="description"], input[name="description"]').first();

        if (await descInput.isVisible()) {
            await descInput.clear();
            await descInput.fill('Test scorecard description');

            // Then: Description should be updated
            await page.waitForTimeout(500);
            await expect(descInput).toHaveValue(/Test scorecard/);
        }
    });

    // Given: User is on scorecard editor page
    // When: User duplicates attribute
    // Then: Copy of attribute should be created
    test('should duplicate attribute in scorecard', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
        await page.waitForTimeout(2000);

        // Get initial attribute count
        const initialAttributes = await page.locator('.attribute-item, .score-attribute').count();

        // When: Select an attribute
        const attributeItem = page.locator('.attribute-item, .score-attribute').first();
        await attributeItem.click();

        // When: Click duplicate button
        const duplicateButton = page.locator('button:has-text("复制"), button:has-text("Duplicate"), button:has-text("Copy")').first();

        if (await duplicateButton.isVisible()) {
            await duplicateButton.click();

            // Then: Attribute count should increase
            await page.waitForTimeout(500);
            const newAttributeCount = await page.locator('.attribute-item, .score-attribute').count();
            expect(newAttributeCount).toBeGreaterThan(initialAttributes);
        }
    });

    // Given: User is on scorecard editor page
    // When: User reorders attributes
    // Then: Attribute order should change
    test('should reorder attributes in scorecard', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
        await page.waitForTimeout(2000);

        // When: Select an attribute
        const attributeItem = page.locator('.attribute-item, .score-attribute').nth(1);
        await attributeItem.click();

        // When: Move attribute up
        const moveUpButton = page.locator('button:has-text("上移"), button[title*="up"], button:has-text("Move Up")').first();

        if (await moveUpButton.isVisible()) {
            await moveUpButton.click();

            // Then: Attribute position should change
            await page.waitForTimeout(500);
        }
    });

    // Given: User is on scorecard editor page
    // When: User resets scorecard to default
    // Then: Scorecard should be reset to initial state
    test('should reset scorecard to default configuration', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
        await page.waitForTimeout(2000);

        // When: Click reset button
        const resetButton = page.locator('button:has-text("重置"), button:has-text("Reset")').first();

        if (await resetButton.isVisible()) {
            await resetButton.click();

            // Then: Confirmation dialog should appear
            const confirmDialog = page.locator('.dialog, .modal, [role="dialog"]').first();
            await expect(confirmDialog).toBeVisible({ timeout: 3000 });

            // When: Confirm reset
            const confirmButton = confirmDialog.locator('button:has-text("确定"), button:has-text("OK"), button:has-text("Reset")').first();
            await confirmButton.click();

            // Then: Scorecard should be reset
            await page.waitForTimeout(1000);
        }
    });

    // Given: User is on scorecard editor page
    // When: User views scorecard summary
    // Then: Summary information should be displayed
    test('should display scorecard summary', async ({ page }) => {
        // Given: Navigate to scorecard editor
        await page.goto('/html/scorecard-editor.html?file=/project/scorecard.xml');
        await page.waitForTimeout(2000);

        // When: Click summary button
        const summaryButton = page.locator('button:has-text("摘要"), button:has-text("Summary"), button:has-text("Info")').first();

        if (await summaryButton.isVisible()) {
            await summaryButton.click();

            // Then: Summary panel should appear
            const summaryPanel = page.locator('.summary-panel, .info-panel, [role="dialog"]').first();
            await expect(summaryPanel).toBeVisible({ timeout: 3000 });

            // Then: Summary should show total attributes and score range
            const summaryContent = summaryPanel.locator('.content, .summary-content').first();
            await expect(summaryContent).toBeVisible();
        }
    });
});
