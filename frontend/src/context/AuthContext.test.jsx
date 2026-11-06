import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, restoreStoredSession, useAuth } from './AuthContext';
import { OFFLINE_CACHE_KEY, OFFLINE_OWNER_KEY } from '@/api/offlinePersistence';
import { apiFetch } from '@/api/client';

const store = new Map();

vi.mock('idb-keyval', () => ({
  get: vi.fn(async (key) => store.get(key)),
  set: vi.fn(async (key, value) => {
    store.set(key, value);
  }),
  del: vi.fn(async (key) => {
    store.delete(key);
  }),
}));

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

describe('offline cache lifecycle', () => {
  let auth;
  let queryClient;

  function Probe() {
    auth = useAuth();
    return <div>{auth.usuario ? `user-${auth.usuario.id}` : 'anonymous'}</div>;
  }

  function renderProvider() {
    queryClient = new QueryClient();
    queryClient.setQueryData(['me', 'notifications'], [{ id: 1 }]);
    return render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <Probe />
        </AuthProvider>
      </QueryClientProvider>,
    );
  }

  beforeEach(() => {
    localStorage.clear();
    store.clear();
    vi.stubGlobal('indexedDB', {});
    apiFetch.mockReset();
    apiFetch.mockImplementation(async (path, options) => {
      if (path === '/auth/login') return { accessToken: 'a', refreshToken: `r-${JSON.parse(options.body).username}` };
      if (path === '/me') return { id: localStorage.getItem('refreshToken') === 'r-ana' ? 1 : 2, roles: [] };
      return {};
    });
  });

  it('logoutClearsOfflineCacheAndQueryClient', async () => {
    renderProvider();
    await act(async () => {
      await auth.login('ana', 'x');
    });
    store.set(OFFLINE_CACHE_KEY, 'persisted');
    queryClient.setQueryData(['me', 'notifications'], [{ id: 1 }]);

    await act(async () => {
      await auth.logout();
    });

    await waitFor(() => expect(store.has(OFFLINE_CACHE_KEY)).toBe(false));
    expect(store.has(OFFLINE_OWNER_KEY)).toBe(false);
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0);
    expect(screen.getByText('anonymous')).toBeInTheDocument();
  });

  it('loginAsDifferentUserDoesNotExposePreviousUserCache', async () => {
    renderProvider();
    await act(async () => {
      await auth.login('ana', 'x');
    });
    store.set(OFFLINE_CACHE_KEY, 'cache-of-ana');
    queryClient.setQueryData(['me', 'notifications'], [{ id: 1 }]);

    await act(async () => {
      await auth.login('beto', 'x');
    });

    expect(queryClient.getQueryData(['me', 'notifications'])).toBeUndefined();
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false);
    expect(store.get(OFFLINE_OWNER_KEY)).toBe(2);
    expect(screen.getByText('user-2')).toBeInTheDocument();
  });

  it('restoredSessionOfAnotherOwnerPurgesStaleCache', async () => {
    store.set(OFFLINE_OWNER_KEY, 1);
    store.set(OFFLINE_CACHE_KEY, 'cache-of-ana');
    localStorage.setItem('refreshToken', 'r-beto');
    apiFetch.mockImplementation(async (path) => (path === '/auth/refresh' ? { accessToken: 'a', refreshToken: 'r-beto' } : { id: 2, roles: [] }));

    renderProvider();

    await screen.findByText('user-2');
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0);
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false);
  });
});
