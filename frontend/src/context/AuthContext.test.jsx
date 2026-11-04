import { beforeEach, describe, expect, it, vi } from 'vitest';
import { restoreStoredSession } from './AuthContext';
import { apiFetch } from '@/api/client';

vi.mock('@/api/client', () => ({
  apiFetch: vi.fn(),
  setAccessToken: vi.fn(),
  setSessionExpiredHandler: vi.fn(),
}));

describe('restoreStoredSession', () => {
  beforeEach(() => {
    localStorage.clear();
    apiFetch.mockReset();
  });

  it('sends a single refresh request when called twice concurrently', async () => {
    localStorage.setItem('refreshToken', 'old');
    apiFetch.mockImplementation(async (path) =>
      path === '/auth/refresh' ? { accessToken: 'a', refreshToken: 'new' } : { id: 1 },
    );

    const [first, second] = await Promise.all([restoreStoredSession(), restoreStoredSession()]);

    expect(apiFetch.mock.calls.filter(([path]) => path === '/auth/refresh')).toHaveLength(1);
    expect(first).toEqual({ id: 1 });
    expect(second).toEqual({ id: 1 });
    expect(localStorage.getItem('refreshToken')).toBe('new');
  });

  it('returns null without calling the api when there is no stored token', async () => {
    expect(await restoreStoredSession()).toBeNull();
    expect(apiFetch).not.toHaveBeenCalled();
  });
});
