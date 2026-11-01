import { expect } from '@playwright/test';
import { LoginPage } from '../pages/LoginPage.js';

export async function signedInPage(browser, role, homePattern) {
  const context = await browser.newContext();
  const page = await context.newPage();
  const loginPage = new LoginPage(page);
  await loginPage.open();
  await loginPage.signInAs(role);
  await expect(page).toHaveURL(homePattern);
  return { context, page };
}
