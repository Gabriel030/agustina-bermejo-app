import { expect } from '@playwright/test';

export const API_URL = 'http://localhost:8090';
export const STUDENT_PROFILE = { id: 7, nombre: 'Ana', apellido: 'Paz', email: 'ana@example.test', roles: ['ALUMNO'] };

export async function mockApi(context, { slowMs = 0, refreshStatus = 200 } = {}) {
  const state = { refreshStatus, requests: [], offline: false };
  await context.route(`${API_URL}/**`, async (route) => {
    const request = route.request();
    if (state.offline) return route.abort('internetdisconnected');
    const origin = request.headers().origin ?? '';
    const cors = {
      'access-control-allow-origin': origin,
      'access-control-allow-credentials': 'true',
      'access-control-allow-headers': '*',
      'access-control-allow-methods': '*',
    };
    if (request.method() === 'OPTIONS') return route.fulfill({ status: 204, headers: cors });
    if (slowMs > 0) await new Promise((resolve) => setTimeout(resolve, slowMs));
    const { pathname } = new URL(request.url());
    state.requests.push(pathname);
    const json = (status, body) =>
      route.fulfill({ status, headers: { ...cors, 'content-type': 'application/json' }, body: JSON.stringify(body) });
    if (pathname === '/auth/login') return json(200, { accessToken: 'access-token', refreshToken: 'refresh-token' });
    if (pathname === '/auth/refresh') {
      return state.refreshStatus === 200 ? json(200, { accessToken: 'access-token' }) : json(state.refreshStatus, {});
    }
    if (pathname === '/me') return json(200, STUDENT_PROFILE);
    if (pathname === '/me/legal-acceptances/pending') {
      return json(200, { documents: [], suggestedDeadline: '2026-11-30', gracePeriodExpired: false, guardianConfirmationRequired: false });
    }
    if (pathname === '/me/modules') {
      return json(200, { billing: { mode: 'FULL' }, fiscalInvoicing: { enabled: false }, analytics: { enabled: false }, paymentMethods: [], billingNotifications: { enabled: false }, externalBilling: { enabled: false } });
    }
    if (pathname === '/institutions') return json(200, [{ id: 1, name: 'Instituto de prueba' }]);
    return json(200, []);
  });
  return state;
}

export async function signIn(page, { timeout = 15_000 } = {}) {
  await page.goto('/login');
  await page.getByPlaceholder('DNI o email').fill('alumno.demo');
  await page.getByPlaceholder('••••••••').fill('Test1234');
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await expect(page).toHaveURL(/\/alumno\/inicio$/, { timeout });
}

export function homeScreen(page) {
  return page.getByRole('heading', { name: 'Mis materias' });
}

export async function waitForControllingWorker(page) {
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
    if (navigator.serviceWorker.controller) return;
    await new Promise((resolve) => navigator.serviceWorker.addEventListener('controllerchange', resolve, { once: true }));
  });
}

export async function goOffline(context, api) {
  api.offline = true;
  await context.setOffline(true);
}

export async function goOnline(context, page, api) {
  api.offline = false;
  await context.setOffline(false);
  await page.evaluate(() => window.dispatchEvent(new Event('online')));
}
