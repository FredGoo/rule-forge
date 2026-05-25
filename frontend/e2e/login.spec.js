import { test, expect } from '@playwright/test';

/**
 * BDD Tests for Login Flow
 *
 * Given: User is on the login page
 * When: User performs login actions
 * Then: Expected outcomes occur
 */
test.describe('Login Flow', () => {
    // Given: Login page is accessible
    test.beforeEach(async ({ page }) => {
        await page.goto('/html/login.html');
    });

    // Given: User is on login page
    // When: Page loads
    // Then: Login form is displayed correctly
    test('should display login page with all required elements', async ({ page }) => {
        // Then: Page title should be correct
        await expect(page).toHaveTitle(/RuleForge/);

        // Then: Username input should be visible
        const usernameInput = page.locator('input[name="username"], input[type="text"]').first();
        await expect(usernameInput).toBeVisible();

        // Then: Password input should be visible
        const passwordInput = page.locator('input[name="password"], input[type="password"]').first();
        await expect(passwordInput).toBeVisible();

        // Then: Login button should be visible
        const loginButton = page.locator('button[type="submit"], button:has-text("登录"), button:has-text("Login")').first();
        await expect(loginButton).toBeVisible();
    });

    // Given: User is on login page with valid credentials
    // When: User enters correct username and password
    // Then: User should be redirected to index page
    test('should login successfully with valid credentials', async ({ page }) => {
        // When: Enter username
        await page.locator('input[name="username"], input[type="text"]').first().fill('admin');

        // When: Enter password
        await page.locator('input[name="password"], input[type="password"]').first().fill('admin');

        // When: Click login button
        await page.locator('button[type="submit"], button:has-text("登录"), button:has-text("Login")').first().click();

        // Then: Should be redirected to index page
        await expect(page).toHaveURL(/\/html\/index\.html/);

        // Then: Main frame should be visible
        await expect(page.locator('frame, iframe').first()).toBeVisible();
    });

    // Given: User is on login page with invalid credentials
    // When: User enters incorrect username or password
    // Then: Error message should be displayed
    test('should show error message with invalid credentials', async ({ page }) => {
        // When: Enter invalid username
        await page.locator('input[name="username"], input[type="text"]').first().fill('invalid');

        // When: Enter invalid password
        await page.locator('input[name="password"], input[type="password"]').first().fill('invalid');

        // When: Click login button
        await page.locator('button[type="submit"], button:has-text("登录"), button:has-text("Login")').first().click();

        // Then: Error message should be visible
        const errorMessage = page.locator('.error, .alert, [role="alert"]').first();
        await expect(errorMessage).toBeVisible();

        // Then: Should remain on login page
        await expect(page).toHaveURL(/\/html\/login\.html/);
    });

    // Given: User is on login page
    // When: User tries to login with empty fields
    // Then: Validation error should be shown
    test('should validate required fields are not empty', async ({ page }) => {
        // When: Click login button without entering credentials
        await page.locator('button[type="submit"], button:has-text("登录"), button:has-text("Login")').first().click();

        // Then: Should show validation error
        const validationError = page.locator('.error, .required, [required]').first();
        await expect(validationError).toBeVisible();

        // Then: Should remain on login page
        await expect(page).toHaveURL(/\/html\/login\.html/);
    });

    // Given: User is on login page
    // When: User enters only username but empty password
    // Then: Password field validation should trigger
    test('should validate password field is required', async ({ page }) => {
        // When: Enter username only
        await page.locator('input[name="username"], input[type="text"]').first().fill('admin');

        // When: Click login button
        await page.locator('button[type="submit"], button:has-text("登录"), button:has-text("Login")').first().click();

        // Then: Should show password validation error
        await expect(page.locator('input[name="password"], input[type="password"]').first()).toBeFocused();
    });

    // Given: User has successfully logged in
    // When: User navigates to login page again
    // Then: User should be redirected to index page (already logged in)
    test('should redirect to index if already logged in', async ({ page }) => {
        // Given: Login first
        await page.locator('input[name="username"], input[type="text"]').first().fill('admin');
        await page.locator('input[name="password"], input[type="password"]').first().fill('admin');
        await page.locator('button[type="submit"], button:has-text("登录"), button:has-text("Login")').first().click();
        await page.waitForURL(/\/html\/index\.html/);

        // When: Navigate to login page again
        await page.goto('/html/login.html');

        // Then: Should redirect to index page
        await expect(page).toHaveURL(/\/html\/index\.html/);
    });
});
