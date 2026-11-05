import { test, expect } from '@playwright/test';
import { BACKEND_URL } from '../../fixtures/accounts.js';
import { LoginPage } from '../../pages/LoginPage.js';

test.describe('@smoke session', () => {
  test('sessionSurvivesPageReload', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.signInAs('administrativo');
    await expect(page).toHaveURL(/\/administrativo\/inicio$/);

    await page.goto('/administrativo/facturacion');

    await expect(page).toHaveURL(/\/administrativo\/facturacion$/);
    await expect(page.getByRole('heading', { name: 'Facturación' })).toBeVisible();
  });

  test('expiredAccessTokenRefreshesWithoutLogin', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.signInAs('director');
    await expect(page).toHaveURL(/\/director\/inicio$/);

    await page.route(`${BACKEND_URL}/**`, async (route) => {
      const headers = route.request().headers();
      if (headers.authorization && !route.request().url().includes('/auth/')) {
        const expired = { ...headers, authorization: 'Bearer expired.token.value' };
        await route.continue({ headers: expired });
        return;
      }
      await route.continue();
    });
    const refreshResponse = page.waitForResponse((response) => response.url().endsWith('/auth/refresh') && response.ok());

    await page.getByRole('link', { name: 'Financiero' }).click();

    await refreshResponse;
    await expect(page).toHaveURL(/\/director\/financiero$/);
  });
});
