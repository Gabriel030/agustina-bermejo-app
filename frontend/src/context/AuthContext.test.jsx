import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, restoreStoredSession, useAuth } from './AuthContext';
import {
  OFFLINE_CACHE_KEY,
  OFFLINE_OWNER_KEY,
  OFFLINE_PROFILE_KEY,
  readOfflineProfile,
  storeOfflineProfile,
} from '@/api/offlinePersistence';
import { apiFetch, resetCircuits, setOfflineSessionMode } from '@/api/client';
import { LEGAL_KEYS } from '@/features/identity/hooks/useLegal';

const store = new Map();

vi.mock('idb-keyval', () => ({
  get: vi.fn(async (key) => store.get(key)),
  set: vi.fn(async (key, value) => {
    store.set(key, value);
  }),
  del: vi.fn(async (key) => {
    store.delete(key);
  }),
  keys: vi.fn(async () => [...store.keys()]),
}));

vi.mock('@/api/client', () => ({
  apiFetch: vi.fn(),
  setAccessToken: vi.fn(),
  setSessionExpiredHandler: vi.fn(),
  setOfflineSessionMode: vi.fn(),
  resetCircuits: vi.fn(),
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
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(localStorage.getItem('sessionHint')).toBe('1');
  });

  it('leavesOfflineSessionModeBeforeLoadingTheProfile', async () => {
    localStorage.setItem('sessionHint', '1');
    setOfflineSessionMode.mockClear();
    apiFetch.mockImplementation(async (path) =>
      path === '/auth/refresh' ? { accessToken: 'a' } : { id: 1 },
    );

    await restoreStoredSession();

    const profileCall = apiFetch.mock.calls.findIndex(([path]) => path === '/me');
    expect(setOfflineSessionMode).toHaveBeenCalledWith(false);
    expect(setOfflineSessionMode.mock.invocationCallOrder[0]).toBeLessThan(apiFetch.mock.invocationCallOrder[profileCall]);
  });

  it('migrates a legacy stored token by sending it once in the body and then drops it', async () => {
    localStorage.setItem('refreshToken', 'legacy');
    apiFetch.mockImplementation(async (path) =>
      path === '/auth/refresh' ? { accessToken: 'a', refreshToken: 'new' } : { id: 1 },
    );

    await restoreStoredSession();

    const refreshCall = apiFetch.mock.calls.find(([path]) => path === '/auth/refresh');
    expect(JSON.parse(refreshCall[1].body)).toEqual({ refreshToken: 'legacy' });
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(localStorage.getItem('sessionHint')).toBe('1');
  });

  it('refreshes without a body when only the session hint exists', async () => {
    localStorage.setItem('sessionHint', '1');
    apiFetch.mockImplementation(async (path) =>
      path === '/auth/refresh' ? { accessToken: 'a', refreshToken: 'new' } : { id: 1 },
    );

    await restoreStoredSession();

    const refreshCall = apiFetch.mock.calls.find(([path]) => path === '/auth/refresh');
    expect(refreshCall[1].body).toBeUndefined();
    expect(localStorage.getItem('refreshToken')).toBeNull();
  });

  it('returns null without calling the api when there is no stored token', async () => {
    expect(await restoreStoredSession()).toBeNull();
    expect(apiFetch).not.toHaveBeenCalled();
  });
});

describe('offline cache lifecycle', () => {
  let auth;
  let lastLogin;
  let queryClient;

  function Probe() {
    auth = useAuth();
    return <div>{auth.usuario ? `user-${auth.usuario.id}` : 'anonymous'}</div>;
  }

  function renderProvider() {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    queryClient.setQueryData(['me'], { id: 1, roles: ['ADMIN'] });
    queryClient.setQueryData(['me', 'modules'], { modules: ['ADMIN_ONLY'] });
    queryClient.setQueryData(LEGAL_KEYS.pending, { documents: [{ type: 'ANA' }] });
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
      if (path === '/auth/login') {
        lastLogin = JSON.parse(options.body).username;
        return { accessToken: 'a', refreshToken: `r-${lastLogin}` };
      }
      if (path === '/me') return { id: lastLogin === 'ana' ? 1 : 2, roles: [] };
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
    expect(queryClient.getQueryData(['me'])).toEqual({ id: 2, roles: [] });
    expect(queryClient.getQueryData(['me', 'modules'])).toBeUndefined();
    expect(queryClient.getQueryData(LEGAL_KEYS.pending)).toBeUndefined();
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false);
    expect(store.get(OFFLINE_OWNER_KEY)).toBe(2);
    expect(screen.getByText('user-2')).toBeInTheDocument();
  });

  it('restoredSessionOfAnotherOwnerPurgesStaleCache', async () => {
    store.set(OFFLINE_OWNER_KEY, 1);
    store.set(OFFLINE_CACHE_KEY, 'cache-of-ana');
    localStorage.setItem('sessionHint', '1');
    apiFetch.mockImplementation(async (path) => (path === '/auth/refresh' ? { accessToken: 'a', refreshToken: 'r-beto' } : { id: 2, roles: [] }));

    renderProvider();

    await screen.findByText('user-2');
    expect(queryClient.getQueryData(['me'])).toEqual({ id: 2, roles: [] });
    expect(queryClient.getQueryData(['me', 'notifications'])).toBeUndefined();
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false);
  });

  it('restoredSessionOfAnotherOwnerNeverExposesPreviousUserQueries', async () => {
    store.set(OFFLINE_OWNER_KEY, 1);
    localStorage.setItem('sessionHint', '1');
    apiFetch.mockImplementation(async (path) => (path === '/auth/refresh' ? { accessToken: 'a', refreshToken: 'r-beto' } : { id: 2, roles: [] }));

    renderProvider();

    await screen.findByText('user-2');
    expect(queryClient.getQueryData(['me'])).toEqual({ id: 2, roles: [] });
    const cachedData = JSON.stringify(queryClient.getQueryCache().getAll().map((query) => query.state.data));
    expect(cachedData).not.toContain('ADMIN_ONLY');
    expect(cachedData).not.toContain('ANA');
    expect(queryClient.getQueryData(['me', 'notifications'])).toBeUndefined();
  });

  it('logoutThenLoginAsAnotherUserNeverExposesPreviousUserQueries', async () => {
    renderProvider();
    await act(async () => {
      await auth.login('ana', 'x');
    });

    await act(async () => {
      await auth.logout();
    });
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0);

    await act(async () => {
      await auth.login('beto', 'x');
    });

    expect(queryClient.getQueryData(['me'])).toEqual({ id: 2, roles: [] });
    expect(queryClient.getQueryData(['me', 'modules'])).toBeUndefined();
    expect(queryClient.getQueryData(LEGAL_KEYS.pending)).toBeUndefined();
  });

  it('loginAsAnotherUserWithoutLogoutNeverExposesPreviousUserData', async () => {
    store.set(OFFLINE_CACHE_KEY, 'cache-of-ana');
    renderProvider();

    await act(async () => {
      await auth.login('beto', 'x');
    });

    expect(apiFetch.mock.calls.filter(([path]) => path === '/me')).toHaveLength(1);
    expect(queryClient.getQueryData(['me'])).toEqual({ id: 2, roles: [] });
    expect(queryClient.getQueryData(['me', 'modules'])).toBeUndefined();
    expect(queryClient.getQueryData(LEGAL_KEYS.pending)).toBeUndefined();
    expect(queryClient.getQueryData(['me', 'notifications'])).toBeUndefined();
    const cachedData = JSON.stringify(queryClient.getQueryCache().getAll().map((query) => query.state.data));
    expect(cachedData).not.toContain('ADMIN_ONLY');
    expect(cachedData).not.toContain('ANA');
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false);
    expect(store.get(OFFLINE_OWNER_KEY)).toBe(2);
    expect(screen.getByText('user-2')).toBeInTheDocument();
  });

  it('purgeCancelsInFlightQueriesOfThePreviousUser', async () => {
    renderProvider();
    await act(async () => {
      await auth.login('ana', 'x');
    });
    let release;
    const inFlight = queryClient.fetchQuery({
      queryKey: ['me', 'modules'],
      queryFn: () => new Promise((resolve) => { release = () => resolve({ modules: ['ADMIN_ONLY'] }); }),
    }).then(() => 'resolved', () => 'cancelled');

    await act(async () => {
      await auth.logout();
    });
    release();

    expect(await inFlight).toBe('cancelled');
    expect(queryClient.getQueryData(['me', 'modules'])).toBeUndefined();
  });

  it('restoredSessionOfAnotherOwnerRefetchesBootstrapQueriesAfterThePurge', async () => {
    store.set(OFFLINE_OWNER_KEY, 1);
    localStorage.setItem('sessionHint', '1');
    apiFetch.mockImplementation(async (path) => (path === '/auth/refresh' ? { accessToken: 'a', refreshToken: 'r-beto' } : { id: 2, roles: [], path }));

    renderProvider();

    await screen.findByText('user-2');
    await waitFor(() => expect(queryClient.getQueryData(['me', 'modules'])).toEqual({ id: 2, roles: [], path: '/me/modules' }));
    expect(queryClient.getQueryData(LEGAL_KEYS.pending)).toEqual({ id: 2, roles: [], path: '/me/legal-acceptances/pending' });
  });
});

async function seedProfile(profile, ownerId = 1) {
  store.set(OFFLINE_OWNER_KEY, ownerId);
  await storeOfflineProfile(profile);
}

describe('session restore failures', () => {
  let auth;
  let queryClient;

  function Probe() {
    auth = useAuth();
    const state = auth.cargando ? 'loading' : auth.sessionPending ? 'pending' : auth.usuario ? `user-${auth.usuario.id}` : 'anonymous';
    return <div>{state}</div>;
  }

  function renderProvider() {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <Probe />
        </AuthProvider>
      </QueryClientProvider>,
    );
  }

  function failRefreshWith(error) {
    let failing = true;
    apiFetch.mockImplementation(async (path) => {
      if (path === '/auth/refresh') {
        if (failing) throw error;
        return { accessToken: 'a', refreshToken: 'rotated' };
      }
      return { id: 7, roles: [] };
    });
    return () => {
      failing = false;
    };
  }

  beforeEach(() => {
    vi.useRealTimers();
    localStorage.clear();
    store.clear();
    vi.stubGlobal('indexedDB', {});
    apiFetch.mockReset();
    localStorage.setItem('sessionHint', '1');
  });

  it('networkErrorDuringRestoreKeepsTheSession', async () => {
    failRefreshWith(new TypeError('Failed to fetch'));
    store.set(OFFLINE_OWNER_KEY, 7);
    store.set(OFFLINE_CACHE_KEY, 'cache');

    renderProvider();

    await screen.findByText('pending');
    expect(localStorage.getItem('sessionHint')).toBe('1');
    expect(store.get(OFFLINE_CACHE_KEY)).toBe('cache');
    expect(auth.usuario).toBeNull();
  });

  it('unauthorizedRefreshClearsTheSession', async () => {
    failRefreshWith(Object.assign(new Error('unauthorized'), { status: 401 }));
    store.set(OFFLINE_CACHE_KEY, 'cache');

    renderProvider();

    await screen.findByText('anonymous');
    expect(localStorage.getItem('sessionHint')).toBeNull();
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false);
  });

  it('forbiddenRefreshClearsTheSession', async () => {
    failRefreshWith(Object.assign(new Error('forbidden'), { status: 403 }));

    renderProvider();

    await screen.findByText('anonymous');
    expect(localStorage.getItem('sessionHint')).toBeNull();
  });

  it('serverErrorDuringRestoreKeepsTheSession', async () => {
    failRefreshWith(Object.assign(new Error('unavailable'), { status: 503 }));

    renderProvider();

    await screen.findByText('pending');
    expect(localStorage.getItem('sessionHint')).toBe('1');
  });

  it('sessionRecoversWhenTheNetworkReturns', async () => {
    const restoreNetwork = failRefreshWith(new TypeError('Failed to fetch'));
    renderProvider();
    await screen.findByText('pending');

    restoreNetwork();
    await act(async () => {
      window.dispatchEvent(new Event('online'));
    });

    await screen.findByText('user-7');
    expect(localStorage.getItem('sessionHint')).toBe('1');
    expect(store.get(OFFLINE_OWNER_KEY)).toBe(7);
  });

  it('sessionRecoversWithBoundedBackoffRetries', async () => {
    const realSetTimeout = globalThis.setTimeout;
    vi.useFakeTimers();
    const restoreNetwork = failRefreshWith(new TypeError('Failed to fetch'));
    renderProvider();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(screen.getByText('pending')).toBeInTheDocument();

    restoreNetwork();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });

    await act(async () => {
      await new Promise((resolve) => realSetTimeout(resolve, 100));
    });

    expect(screen.getByText('user-7')).toBeInTheDocument();
  });

  it('retriesStopAfterTheBackoffScheduleIsExhausted', async () => {
    vi.useFakeTimers();
    failRefreshWith(new TypeError('Failed to fetch'));
    renderProvider();
    for (const delay of [0, 2000, 5000, 15000, 30000, 60000]) {
      await act(async () => {
        await vi.advanceTimersByTimeAsync(delay);
      });
    }
    const callsAfterSchedule = apiFetch.mock.calls.length;

    await act(async () => {
      await vi.advanceTimersByTimeAsync(300000);
    });

    expect(apiFetch.mock.calls.length).toBe(callsAfterSchedule);
    expect(callsAfterSchedule).toBe(6);
  });

  it('refreshReuseStillLogsOut', async () => {
    const restoreNetwork = failRefreshWith(new TypeError('Failed to fetch'));
    renderProvider();
    await screen.findByText('pending');

    restoreNetwork();
    apiFetch.mockImplementation(async (path) => {
      if (path === '/auth/refresh') throw Object.assign(new Error('reuse'), { status: 401 });
      return {};
    });
    await act(async () => {
      window.dispatchEvent(new Event('online'));
    });

    await screen.findByText('anonymous');
    expect(localStorage.getItem('sessionHint')).toBeNull();
  });

  it('noAuthenticatedCallsAreMadeWhileThePendingSessionIsOffline', async () => {
    failRefreshWith(new TypeError('Failed to fetch'));
    renderProvider();
    await screen.findByText('pending');

    expect(apiFetch.mock.calls.every(([path]) => path === '/auth/refresh')).toBe(true);
  });

  it('profileIsStoredWithOnlyNameAndRoles', async () => {
    apiFetch.mockImplementation(async (path) => {
      if (path === '/auth/login') return { accessToken: 'a', refreshToken: 'r' };
      return { id: 7, nombre: 'Ana', apellido: 'Paz', email: 'ana@x.test', dni: '123', telefono: '555', roles: ['DIRECTOR'] };
    });
    renderProvider();
    await act(async () => {
      await auth.login('ana', 'x');
    });

    expect(await readOfflineProfile()).toEqual({ nombre: 'Ana', apellido: 'Paz', roles: ['DIRECTOR'] });
  });

  it('storedProfileIsExposedWhilePendingAndDoesNotEnableAuthenticatedCalls', async () => {
    await seedProfile({ nombre: 'Ana', apellido: 'Paz', roles: ['DIRECTOR'] });
    failRefreshWith(new TypeError('Failed to fetch'));

    renderProvider();

    await screen.findByText('pending');
    expect(auth.usuario).toEqual({ nombre: 'Ana', apellido: 'Paz', roles: ['DIRECTOR'] });
    expect(setOfflineSessionMode).toHaveBeenLastCalledWith(true);
    expect(apiFetch.mock.calls.every(([path]) => path === '/auth/refresh')).toBe(true);
  });

  it('realProfileReplacesTheStoredOneWhenTheNetworkReturns', async () => {
    await seedProfile({ nombre: 'Ana', apellido: 'Paz', roles: ['DIRECTOR'] });
    const restoreNetwork = failRefreshWith(new TypeError('Failed to fetch'));
    renderProvider();
    await screen.findByText('pending');

    restoreNetwork();
    await act(async () => {
      window.dispatchEvent(new Event('online'));
    });

    await screen.findByText('user-7');
    expect(auth.usuario.roles).toEqual([]);
    expect(await readOfflineProfile()).toEqual({ nombre: undefined, apellido: undefined, roles: [] });
    expect(setOfflineSessionMode).toHaveBeenLastCalledWith(false);
  });

  it('resumingTheConnectionResetsTheCircuitsBeforeRestoring', async () => {
    await seedProfile({ nombre: 'Ana', apellido: 'Paz', roles: [] });
    const restoreNetwork = failRefreshWith(new TypeError('Failed to fetch'));
    renderProvider();
    await screen.findByText('pending');
    resetCircuits.mockClear();

    restoreNetwork();
    await act(async () => {
      window.dispatchEvent(new Event('online'));
    });

    await screen.findByText('user-7');
    expect(resetCircuits).toHaveBeenCalledTimes(1);
  });

  it('logoutPurgesTheStoredProfile', async () => {
    await seedProfile({ nombre: 'Ana', apellido: 'Paz', roles: [] });
    failRefreshWith(new TypeError('Failed to fetch'));
    renderProvider();
    await screen.findByText('pending');

    await act(async () => {
      await auth.logout();
    });

    await waitFor(() => expect(store.has(OFFLINE_PROFILE_KEY)).toBe(false));
  });

  it('rejectedRefreshPurgesTheStoredProfile', async () => {
    await seedProfile({ nombre: 'Ana', apellido: 'Paz', roles: [] });
    failRefreshWith(Object.assign(new Error('unauthorized'), { status: 401 }));

    renderProvider();

    await screen.findByText('anonymous');
    await waitFor(() => expect(store.has(OFFLINE_PROFILE_KEY)).toBe(false));
  });

  it('ownerChangePurgesTheStoredProfileOfThePreviousOwner', async () => {
    store.set(OFFLINE_OWNER_KEY, 1);
    await seedProfile({ nombre: 'Ana', apellido: 'Paz', roles: ['DIRECTOR'] });
    apiFetch.mockImplementation(async (path) => {
      if (path === '/auth/refresh') return { accessToken: 'a', refreshToken: 'r' };
      return { id: 2, nombre: 'Beto', apellido: 'Gil', roles: [] };
    });

    renderProvider();

    await screen.findByText('user-2');
    expect(await readOfflineProfile()).toEqual({ nombre: 'Beto', apellido: 'Gil', roles: [] });
  });
});
