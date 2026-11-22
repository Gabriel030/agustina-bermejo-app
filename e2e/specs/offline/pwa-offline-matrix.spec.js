import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { test, expect } from '@playwright/test';
import { goOnline, mockApi, signIn, waitForControllingWorker } from '../../fixtures/offlineApp.js';

const builtWorkerPath = fileURLToPath(new URL('../../../frontend/dist/sw.js', import.meta.url));

test.describe('@offline pwa offline matrix', () => {
  test('onlineSessionShowsNoOfflineBanner', async ({ context, page }) => {
    await mockApi(context);

    await signIn(page);

    await expect(page.getByText('Sin conexión')).toHaveCount(0);
    await expect(page.getByText('Hay una versión nueva')).toHaveCount(0);
    await expect(page.getByText('Algo salió mal')).toHaveCount(0);
  });

  test('losingTheConnectionShowsTheOfflineBannerAndKeepsTheShell', async ({ context, page }) => {
    await mockApi(context);
    await signIn(page);
    await waitForControllingWorker(page);

    await context.setOffline(true);
    await page.evaluate(() => window.dispatchEvent(new Event('offline')));

    await expect(page.getByText('Sin conexión').first()).toBeVisible();
    await expect(page).toHaveURL(/\/alumno\/inicio$/);
  });

  test('coldStartOfflineServesTheShellFromTheServiceWorker', async ({ context, page }) => {
    await mockApi(context);
    await signIn(page);
    await waitForControllingWorker(page);

    await context.setOffline(true);
    await page.reload();

    await expect(page.getByText('Sin conexión').first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Reintentar' }).or(page.getByText('Podés ver lo último que cargaste'))).not.toHaveCount(0);
  });

  test('slowNetworkStillCompletesTheLogin', async ({ context, page }) => {
    await mockApi(context, { slowMs: 1500 });

    await signIn(page);

    await expect(page.getByText('Sin conexión')).toHaveCount(0);
  });

  test('resumingTheConnectionClearsTheOfflineBannerAndRestoresTheSession', async ({ context, page }) => {
    await mockApi(context);
    await signIn(page);
    await waitForControllingWorker(page);
    await context.setOffline(true);
    await page.reload();
    await expect(page.getByText('Sin conexión').first()).toBeVisible();

    await goOnline(context, page);

    await expect(page.getByText('Sin conexión')).toHaveCount(0);
    await expect(page.getByText('Algo salió mal')).toHaveCount(0);
    await expect(page).toHaveURL(/\/alumno\/inicio$/);
  });

  test('aNewWorkerVersionOffersTheUpdateAndReloadsWhenApplied', async ({ context, page }) => {
    await mockApi(context);
    await signIn(page);
    await waitForControllingWorker(page);

    const originalWorker = await readFile(builtWorkerPath, 'utf8');
    try {
      await writeFile(builtWorkerPath, `${originalWorker}\n//new-version`);
      await page.evaluate(async () => {
        const registration = await navigator.serviceWorker.ready;
        await registration.update();
      });

      await expect(page.getByText('Hay una versión nueva')).toBeVisible();
      const reloaded = page.waitForEvent('load');
      await page.getByRole('button', { name: 'Actualizar' }).click();
      await reloaded;
      await expect(page.getByText('Hay una versión nueva')).toHaveCount(0);
    } finally {
      await writeFile(builtWorkerPath, originalWorker);
    }
  });

  test('anExpiredSessionDiscoveredAfterBeingOfflineSendsTheUserToLogin', async ({ context, page }) => {
    const api = await mockApi(context);
    await signIn(page);
    await waitForControllingWorker(page);
    await context.setOffline(true);
    await page.reload();
    await expect(page.getByText('Sin conexión').first()).toBeVisible();

    api.refreshStatus = 401;
    await goOnline(context, page);
    await page.reload();

    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('button', { name: 'Ingresar' })).toBeVisible();
  });
});
