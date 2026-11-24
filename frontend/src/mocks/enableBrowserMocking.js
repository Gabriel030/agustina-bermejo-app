import { setupWorker } from 'msw/browser';
import { markSessionActive } from '@/api/sessionHint';
import { buildBrowserHandlers } from '@/mocks/browserHandlers';
import { MOCK_ROLE_STORAGE_KEY, resolveMockRole } from '@/mocks/mockRoles';

export async function enableBrowserMocking() {
  const roleKey = resolveMockRole({ search: window.location.search, storage: window.localStorage });
  window.localStorage.setItem(MOCK_ROLE_STORAGE_KEY, roleKey);
  markSessionActive();
  const worker = setupWorker(...buildBrowserHandlers(roleKey));
  await worker.start({ onUnhandledRequest: 'bypass', quiet: true });
  return roleKey;
}
