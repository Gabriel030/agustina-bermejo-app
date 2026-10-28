import { test, expect } from '@playwright/test';
import { ROLES } from '../../fixtures/accounts.js';
import { LoginPage } from '../../pages/LoginPage.js';

test.describe('@smoke role access', () => {
  for (const [role, account] of Object.entries(ROLES)) {
    test(`loginLandsOnRoleHome ${role}`, async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.open();
      await loginPage.signInAs(role);

      await expect(page).toHaveURL(new RegExp(`${account.home}$`));
      await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible();
    });

    test(`foreignRoleRouteRedirectsToUnauthorized ${role}`, async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.open();
      await loginPage.signInAs(role);
      await expect(page).toHaveURL(new RegExp(`${account.home}$`));

      await page.goto(account.foreignRoute);

      await expect(page).toHaveURL(/\/no-autorizado$/);
      await expect(page.getByText('No autorizado')).toBeVisible();
    });
  }

  test('logoutClearsSession', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.signInAs('director');
    await expect(page).toHaveURL(/\/director\/inicio$/);

    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await expect(page).toHaveURL(/\/login$/);

    await page.goto('/director/inicio');
    await expect(page).toHaveURL(/\/login$/);
    expect(await page.evaluate(() => localStorage.getItem('refreshToken'))).toBeNull();
  });
});
