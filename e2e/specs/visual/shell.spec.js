import { test, expect } from '@playwright/test';

const DESIGNS = ['vl', 'b'];
const THEMES = ['light', 'dark'];
const VIEWPORTS = [
  { name: 'desktop', width: 1280, height: 900 },
  { name: 'mobile', width: 390, height: 844 },
];
const ROLES = {
  director: { roles: ['DIRECTOR'], path: '/director/inicio' },
  alumno: { roles: ['ALUMNO'], path: '/alumno/inicio' },
};
const NOTIFICATIONS = [
  { id: 1, type: 'ANNOUNCEMENT_PUBLISHED', message: 'Aviso uno', read: false, createdAt: '2026-01-01T10:00:00Z' },
  { id: 2, type: 'ANNOUNCEMENT_PUBLISHED', message: 'Aviso dos', read: false, createdAt: '2026-01-01T09:00:00Z' },
  { id: 3, type: 'ANNOUNCEMENT_PUBLISHED', message: 'Aviso tres', read: true, createdAt: '2026-01-01T08:00:00Z' },
];

async function stubApi(page, roles) {
  await page.route('http://localhost:8080/**', (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname === '/auth/refresh') {
      return route.fulfill({ json: { accessToken: 'stub-access', refreshToken: 'stub-refresh' } });
    }
    if (pathname === '/me') {
      return route.fulfill({
        json: { id: 1, nombre: 'Ana', apellido: 'Perez', email: 'ana@example.com', roles, hasAvatar: false },
      });
    }
    if (pathname === '/me/modules') return route.fulfill({ json: { billing: { mode: 'ON' } } });
    if (pathname === '/me/notifications') return route.fulfill({ json: NOTIFICATIONS });
    return route.fulfill({ json: [] });
  });
}

for (const design of DESIGNS) {
  for (const theme of THEMES) {
    for (const viewport of VIEWPORTS) {
      const variant = `${design}-${theme}-${viewport.name}`;
      test.describe(`${variant} shell`, () => {
        test.use({ viewport: { width: viewport.width, height: viewport.height } });

        test.beforeEach(async ({ page }) => {
          await page.addInitScript(
            ([d, t]) => {
              localStorage.setItem('design', d);
              localStorage.setItem('tema', t);
              localStorage.setItem('refreshToken', 'stub-refresh');
            },
            [design, theme],
          );
        });

        for (const [role, { roles, path }] of Object.entries(ROLES)) {
          test(`shell ${role}`, async ({ page }) => {
            await stubApi(page, roles);
            await page.goto(path);
            await expect(page.getByRole('main')).toBeVisible();
            await page.evaluate(() => document.fonts.ready);
            await expect(page).toHaveScreenshot([variant, `shell-${role}.png`], { mask: [page.getByRole('main')] });
          });
        }

        if (viewport.name === 'mobile') {
          for (const [role, { roles, path }] of Object.entries(ROLES)) {
            test(`drawer ${role}`, async ({ page }) => {
              await stubApi(page, roles);
              await page.goto(path);
              await page.getByRole('button', { name: 'Abrir menú' }).click();
              await expect(page.getByRole('dialog')).toBeVisible();
              await page.evaluate(() => document.fonts.ready);
              await expect(page).toHaveScreenshot([variant, `drawer-${role}.png`], { mask: [page.getByRole('main')] });
            });
          }
        }
      });
    }
  }
}
