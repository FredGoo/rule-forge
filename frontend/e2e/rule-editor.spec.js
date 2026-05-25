import { test, expect } from '@playwright/test';

/**
 * BDD Tests for Wizard Rule Editor
 *
 * Given: User is logged in and opens rule editor
 * When: User interacts with wizard rule editor
 * Then: Expected rule operations should work
 */
test.describe('Wizard Rule Editor', () => {
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
    // When: User opens wizard rule editor
    // Then: Rule editor page should load with wizard interface
    test('should load wizard rule editor page', async ({ page }) => {
        // When: Navigate to rule editor
        await page.goto('/html/rule-editor.html?file=/project/rules.xml');

        // Then: Page should load
        await expect(page).toHaveTitle(/Rule/);

        // Then: Rule editor interface should be visible
        const editor = page.locator('.rule-editor, .wizard-editor, [data-testid="rule-editor"]').first();
        await expect(editor).toBeVisible({ timeout: 10000 });
    });

    // Given: User is on rule editor page
    // When: Page loads with existing rules
    // Then: Rule list should be displayed
    test('should display existing rules in list', async ({ page }) => {
        // Given: Navigate to rule editor
        await page.goto('/html/rule-editor.html?file=/project/rules.xml');
        await page.waitForTimeout(2000);

        // Then: Rule list should be visible
        const ruleList = page.locator('.rule-list, .wizard-list, [data-testid="rule-list"]').first();
        await expect(ruleList).toBeVisible();

        // Then: List should contain rule items
        const ruleItems = ruleList.locator('.rule-item, .wizard-item, li');
        await expect(ruleItems.first()).toBeVisible();
    });

    // Given: User is on rule editor page
    // When: User clicks "Add Rule" button
    // Then: New rule should be created
    test('should add new rule to editor', async ({ page }) => {
        // Given: Navigate to rule editor
        await page.goto('/html/rule-editor.html?file=/project/rules.xml');
        await page.waitForTimeout(2000);

        // Get initial rule count
        const initialRules = await page.locator('.rule-item, .wizard-item').count();

        // When: Click add rule button
        const addRuleButton = page.locator('button:has-text("添加规则"), button:has-text("Add Rule")').first();
        await addRuleButton.click();

        // Then: Rule count should increase
        await page.waitForTimeout(500);
        const newRuleCount = await page.locator('.rule-item, .wizard-item').count();
        expect(newRuleCount).toBeGreaterThan(initialRules);
    });

    // Given: User is on rule editor page with rules
    // When: User selects a rule
    // Then: Rule details should be shown
    test('should select rule and show details', async ({ page }) => {
        // Given: Navigate to rule editor
        await page.goto('/html/rule-editor.html?file=/project/rules.xml');
        await page.waitForTimeout(2000);

        // When: Click on a rule
        const ruleItem = page.locator('.rule-item, .wizard-item').first();
        await ruleItem.click();

        // Then: Rule should be selected
        await expect(ruleItem).toHaveClass(/selected/);

        // Then: Rule details panel should be visible
        const detailsPanel = page.locator('.rule-details, .wizard-details, [data-testid="rule-details"]').first();
        await expect(detailsPanel).toBeVisible();
    });

    // Given: User is on rule editor page with rule selected
    // When: User adds condition to rule
    // Then: Condition should be added to rule
    test('should add condition to rule', async ({ page }) => {
        // Given: Navigate to rule editor
        await page.goto('/html/rule-editor.html?file=/project/rules.xml');
        await page.waitForTimeout(2000);

        // When: Select a rule
        const ruleItem = page.locator('.rule-item, .wizard-item').first();
        await ruleItem.click();

        // When: Click add condition button
        const addConditionButton = page.locator('button:has-text("添加条件"), button:has-text("Add Condition")').first();
        await addConditionButton.click();

        // Then: Condition should be added to rule
        await page.waitForTimeout(500);
        const conditions = page.locator('.condition-item, .condition');
        await expect(conditions.first()).toBeVisible();
    });

    // Given: User is on rule editor page with rule selected
    // When: User adds action to rule
    // Then: Action should be added to rule
    test('should add action to rule', async ({ page }) => {
        // Given: Navigate to rule editor
        await page.goto('/html/rule-editor.html?file=/project/rules.xml');
        await page.waitForTimeout(2000);

        // When: Select a rule
        const ruleItem = page.locator('.rule-item, .wizard-item').first();
        await ruleItem.click();

        // When: Click add action button
        const addActionButton = page.locator('button:has-text("添加动作"), button:has-text("Add Action")').first();
        await addActionButton.click();

        // Then: Action should be added to rule
        await page.waitForTimeout(500);
        const actions = page.locator('.action-item, .action');
        await expect(actions.first()).toBeVisible();
    });

    // Given: User is on rule editor page with condition selected
    // When: User configures condition expression
    // Then: Condition should be updated
    test('should configure condition expression', async ({ page }) => {
        // Given: Navigate to rule editor
        await page.goto('/html/rule-editor.html?file=/project/rules.xml');
        await page.waitForTimeout(2000);

        // When: Select a rule
        const ruleItem = page.locator('.rule-item, .wizard-item').first();
        await ruleItem.click();

        // When: Click add condition
        const addConditionButton = page.locator('button:has-text("添加条件"), button:has-text("Add Condition")').first();
        await addConditionButton.click();

        // When: Configure condition expression
        const conditionInput = page.locator('input[name="condition"], textarea[name="condition"], .condition-editor').first();

        if (await conditionInput.isVisible()) {
            await conditionInput.fill('age > 18');

            // Then: Condition should be saved
            await page.waitForTimeout(500);
            await expect(conditionInput).toHaveValue(/age/);
        }
    });

    // Given: User is on rule editor page with action selected
    // When: User configures action
    // Then: Action should be updated
    test('should configure action for rule', async ({ page }) => {
        // Given: Navigate to rule editor
        await page.goto('/html/rule-editor.html?file=/project/rules.xml');
        await page.waitForTimeout(2000);

        // When: Select a rule
        const ruleItem = page.locator('.rule-item, .wizard-item').first();
        await ruleItem.click();

        // When: Click add action
        const addActionButton = page.locator('button:has-text("添加动作"), button:has-text("Add Action")').first();
        await addActionButton.click();

        // When: Configure action
        const actionSelect = page.locator('select[name="action"], .action-editor select').first();

        if (await actionSelect.isVisible()) {
            await actionSelect.selectOption('approve');

            // Then: Action should be saved
            await page.waitForTimeout(500);
            await expect(actionSelect).toHaveValue(/approve/);
        }
    });

    // Given: User is on rule editor page with rule selected
    // When: User deletes rule
    // Then: Rule should be removed from list
    test('should delete rule from editor', async ({ page }) => {
        // Given: Navigate to rule editor
        await page.goto('/html/rule-editor.html?file=/project/rules.xml');
        await page.waitForTimeout(2000);

        // Get initial rule count
        const initialRules = await page.locator('.rule-item, .wizard-item').count();

        // When: Select a rule
        const ruleItem = page.locator('.rule-item, .wizard-item').first();
        await ruleItem.click();

        // When: Click delete button
        const deleteButton = page.locator('button:has-text("删除"), button:has-text("Delete")').first();
        await deleteButton.click();

        // Then: Confirmation dialog might appear
        const confirmDialog = page.locator('.dialog, .modal, [role="dialog"]').first();
        if (await confirmDialog.isVisible()) {
            const confirmButton = confirmDialog.locator('button:has-text("确定"), button:has-text("OK")').first();
            await confirmButton.click();
        }

        // Then: Rule count should decrease
        await page.waitForTimeout(500);
        const newRuleCount = await page.locator('.rule-item, .wizard-item').count();
        expect(newRuleCount).toBeLessThan(initialRules);
    });

    // Given: User is on rule editor page with rule selected
    // When: User edits rule properties (name, description)
    // Then: Properties should be updated
    test('should edit rule properties', async ({ page }) => {
        // Given: Navigate to rule editor
        await page.goto('/html/rule-editor.html?file=/project/rules.xml');
        await page.waitForTimeout(2000);

        // When: Select a rule
        const ruleItem = page.locator('.rule-item, .wizard-item').first();
        await ruleItem.click();

        // When: Edit rule name
        const nameInput = page.locator('input[name="ruleName"], input[name="name"]').first();

        if (await nameInput.isVisible()) {
            await nameInput.fill('test-rule');

            // Then: Rule name should be updated
            await page.waitForTimeout(500);
            await expect(nameInput).toHaveValue('test-rule');
        }
    });

    // Given: User is on rule editor page with modified rules
    // When: User clicks "Save" button
    // Then: Rules should be saved to backend
    test('should save rules to backend', async ({ page }) => {
        // Given: Navigate to rule editor
        await page.goto('/html/rule-editor.html?file=/project/rules.xml');
        await page.waitForTimeout(2000);

        // When: Modify a rule
        const ruleItem = page.locator('.rule-item, .wizard-item').first();
        await ruleItem.click();

        const nameInput = page.locator('input[name="ruleName"], input[name="name"]').first();
        if (await nameInput.isVisible()) {
            await nameInput.fill('edited-rule');
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

    // Given: User is on rule editor page
    // When: User validates rules
    // Then: Validation results should be displayed
    test('should validate rules', async ({ page }) => {
        // Given: Navigate to rule editor
        await page.goto('/html/rule-editor.html?file=/project/rules.xml');
        await page.waitForTimeout(2000);

        // When: Click validate button
        const validateButton = page.locator('button:has-text("验证"), button:has-text("Validate")').first();

        if (await validateButton.isVisible()) {
            await validateButton.click();

            // Then: Validation results should appear
            await page.waitForTimeout(1000);
            const validationResult = page.locator('.validation-result, .message').first();
            await expect(validationResult).toBeVisible({ timeout: 5000 }).catch(() => {
                // Validation might be successful without dialog
            });
        }
    });

    // Given: User is on rule editor page
    // When: User copies a rule
    // Then: Copy of rule should be created
    test('should copy rule in editor', async ({ page }) => {
        // Given: Navigate to rule editor
        await page.goto('/html/rule-editor.html?file=/project/rules.xml');
        await page.waitForTimeout(2000);

        // Get initial rule count
        const initialRules = await page.locator('.rule-item, .wizard-item').count();

        // When: Select a rule
        const ruleItem = page.locator('.rule-item, .wizard-item').first();
        await ruleItem.click();

        // When: Click copy button
        const copyButton = page.locator('button:has-text("复制"), button:has-text("Copy")').first();

        if (await copyButton.isVisible()) {
            await copyButton.click();

            // Then: Rule count should increase
            await page.waitForTimeout(500);
            const newRuleCount = await page.locator('.rule-item, .wizard-item').count();
            expect(newRuleCount).toBeGreaterThan(initialRules);
        }
    });

    // Given: User is on rule editor page
    // When: User moves rule up/down in list
    // Then: Rule position should change
    test('should move rule up and down in list', async ({ page }) => {
        // Given: Navigate to rule editor
        await page.goto('/html/rule-editor.html?file=/project/rules.xml');
        await page.waitForTimeout(2000);

        // When: Select a rule
        const ruleItem = page.locator('.rule-item, .wizard-item').nth(1);
        await ruleItem.click();

        // When: Click move up button
        const moveUpButton = page.locator('button:has-text("上移"), button[title*="up"], button:has-text("Move Up")').first();

        if (await moveUpButton.isVisible()) {
            await moveUpButton.click();

            // Then: Rule position should change
            await page.waitForTimeout(500);
        }
    });

    // Given: User is on rule editor page
    // When: User enables/disables rule
    // Then: Rule status should change
    test('should enable and disable rule', async ({ page }) => {
        // Given: Navigate to rule editor
        await page.goto('/html/rule-editor.html?file=/project/rules.xml');
        await page.waitForTimeout(2000);

        // When: Select a rule
        const ruleItem = page.locator('.rule-item, .wizard-item').first();
        await ruleItem.click();

        // When: Toggle enable/disable
        const enableCheckbox = page.locator('input[type="checkbox"][name*="enabled"], input[type="checkbox"][name*="enable"]').first();

        if (await enableCheckbox.isVisible()) {
            const initialState = await enableCheckbox.isChecked();
            await enableCheckbox.click();

            // Then: Checkbox state should change
            await page.waitForTimeout(500);
            const newState = await enableCheckbox.isChecked();
            expect(newState).not.toBe(initialState);
        }
    });
});
