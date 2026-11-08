import { test, expect } from '@playwright/test';

const DESIGNS = ['vl', 'b'];
const THEMES = ['light', 'dark'];
const VIEWPORTS = [
  { name: 'desktop', width: 1280, height: 900 },
  { name: 'mobile', width: 390, height: 844 },
];
const SECTIONS = ['buttons', 'cards', 'pills', 'tabs', 'inputs', 'selects', 'table', 'skeleton', 'stepper', 'progress', 'overlays'];
const OVERLAYS = [
  { target: 'dialog', role: 'dialog' },
  { target: 'sheet', role: 'dialog' },
  { target: 'select', role: 'listbox' },
  { target: 'popover', role: 'dialog' },
];

for (const design of DESIGNS) {
  for (const theme of THEMES) {
    for (const viewport of VIEWPORTS) {
      const variant = `${design}-${theme}-${viewport.name}`;
      test.describe(variant, () => {
        test.use({ viewport: { width: viewport.width, height: viewport.height } });

        test.beforeEach(async ({ page }) => {
          await page.route('http://localhost:8080/**', (route) => route.fulfill({ status: 401, json: {} }));
          await page.addInitScript(
            ([d, t]) => {
              localStorage.setItem('design', d);
              localStorage.setItem('tema', t);
            },
            [design, theme],
          );
        });

        for (const section of SECTIONS) {
          test(`section ${section}`, async ({ page }) => {
            await page.goto('/dev/catalog');
            await page.evaluate(() => document.fonts.ready);
            await expect(page.getByTestId(`catalog-${section}`)).toHaveScreenshot([variant, `${section}.png`]);
          });
        }

        for (const { target, role } of OVERLAYS) {
          test(`overlay ${target}`, async ({ page }) => {
            await page.goto(`/dev/catalog?open=${target}`);
            await page.evaluate(() => document.fonts.ready);
            await expect(page.getByRole(role).last()).toHaveScreenshot([variant, `${target}.png`]);
          });
        }
      });
    }
  }
}
