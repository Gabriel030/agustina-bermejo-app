import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister';
import { del, get, set } from 'idb-keyval';

export const OFFLINE_CACHE_KEY = 'offline-query-cache';
export const OFFLINE_OWNER_KEY = 'offline-query-cache-owner';
export const OFFLINE_PROFILE_KEY = 'offline-session-profile';
export const OFFLINE_MAX_AGE_MS = 24 * 60 * 60 * 1000;

const hasIndexedDb = () => typeof indexedDB !== 'undefined';

async function safely(operation, fallback) {
  if (!hasIndexedDb()) return fallback;
  try {
    return await operation();
  } catch {
    return fallback;
  }
}

const storagePersister = createAsyncStoragePersister({
  key: OFFLINE_CACHE_KEY,
  storage: {
    getItem: (key) => safely(() => get(key), null),
    setItem: (key, value) => safely(() => set(key, value), undefined),
    removeItem: (key) => safely(() => del(key), undefined),
  },
});

export const offlinePersister = {
  ...storagePersister,
  restoreClient: async () => {
    if (!localStorage.getItem('refreshToken')) {
      await clearOfflineCache();
      return undefined;
    }
    return storagePersister.restoreClient();
  },
};

export const offlineDehydrateOptions = {
  shouldDehydrateQuery: (query) => query.state.status === 'success' && query.meta?.offline === true,
};

export async function clearOfflineCache() {
  await safely(() => Promise.all([del(OFFLINE_CACHE_KEY), del(OFFLINE_OWNER_KEY), del(OFFLINE_PROFILE_KEY)]), undefined);
}

export async function purgeOfflineData(queryClient) {
  queryClient?.clear();
  await clearOfflineCache();
}

export async function claimOfflineCache(userId, queryClient) {
  const owner = await safely(() => get(OFFLINE_OWNER_KEY), undefined);
  if (owner !== undefined && owner !== userId) {
    await purgeOfflineData(queryClient);
  }
  await safely(() => set(OFFLINE_OWNER_KEY, userId), undefined);
}

export async function storeOfflineProfile(profile) {
  const minimal = {
    nombre: profile.nombre,
    apellido: profile.apellido,
    roles: Array.isArray(profile.roles) ? profile.roles : [],
  };
  await safely(() => set(OFFLINE_PROFILE_KEY, minimal), undefined);
}

export async function readOfflineProfile() {
  const stored = await safely(() => get(OFFLINE_PROFILE_KEY), undefined);
  if (!stored || !Array.isArray(stored.roles)) return null;
  return { nombre: stored.nombre, apellido: stored.apellido, roles: stored.roles };
}
