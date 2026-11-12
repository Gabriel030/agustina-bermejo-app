import { del, get, keys, set } from 'idb-keyval';
import { buildAad, decryptText, encryptText } from './offlineCrypto';
import { OFFLINE_ENTRY_PREFIX, createOfflineQueryPersister } from './offlineQueryStore';
import { PROFILE_SLOT, createIdbKeyVault, hasWebCrypto } from './offlineKeyVault';

export { OFFLINE_ENTRY_PREFIX };
export const OFFLINE_CACHE_KEY = 'offline-query-cache';
export const OFFLINE_OWNER_KEY = 'offline-query-cache-owner';
export const OFFLINE_PROFILE_KEY = 'offline-session-profile';
export const OFFLINE_MAX_AGE_MS = 24 * 60 * 60 * 1000;
export const OFFLINE_BUSTER = import.meta.env.VITE_APP_VERSION ?? 'dev';

const PROFILE_SCOPE = 'profile';

const hasIndexedDb = () => typeof indexedDB !== 'undefined';

async function safely(operation, fallback) {
  if (!hasIndexedDb()) return fallback;
  try {
    return await operation();
  } catch {
    return fallback;
  }
}

const vault = createIdbKeyVault();

const readOwner = () => safely(() => get(OFFLINE_OWNER_KEY), undefined);

const rawStorage = {
  getItem: (key) => safely(() => get(key), null),
  setItem: (key, value) => safely(() => set(key, value), undefined),
  removeItem: (key) => safely(() => del(key), undefined),
  keys: () => safely(() => keys(), []),
};

export function createOfflinePersister(options = {}) {
  const store = createOfflineQueryPersister({
    vault,
    rawStorage,
    scope: OFFLINE_BUSTER,
    resolveUserId: readOwner,
    manifestKey: OFFLINE_CACHE_KEY,
    ...options,
  });
  return {
    ...store,
    restoreClient: async () => {
      if (!localStorage.getItem('refreshToken')) {
        await clearOfflineCache();
        return undefined;
      }
      return store.restoreClient();
    },
  };
}

export const offlinePersister = createOfflinePersister();

export const offlineDehydrateOptions = {
  shouldDehydrateQuery: (query) => query.state.status === 'success' && query.meta?.offline === true,
};

export async function clearOfflineCache() {
  offlinePersister.forget();
  await safely(() => vault.deleteAll(), undefined);
  const entryNames = (await rawStorage.keys()).filter(
    (name) => typeof name === 'string' && name.startsWith(OFFLINE_ENTRY_PREFIX),
  );
  await Promise.all([
    ...entryNames.map((name) => safely(() => del(name), undefined)),
    safely(() => del(OFFLINE_CACHE_KEY), undefined),
    safely(() => del(OFFLINE_OWNER_KEY), undefined),
    safely(() => del(OFFLINE_PROFILE_KEY), undefined),
  ]);
}

export async function purgeOfflineData(queryClient) {
  try {
    await clearOfflineCache();
  } finally {
    queryClient?.clear();
  }
}

export async function claimOfflineCache(userId, queryClient) {
  const owner = await readOwner();
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
  await safely(async () => {
    if (!hasWebCrypto()) return;
    const userId = await readOwner();
    if (userId === undefined || userId === null) return;
    const key = await vault.getOrCreate(userId, PROFILE_SLOT);
    if (!key) return;
    const envelope = await encryptText(key, JSON.stringify(minimal), buildAad(userId, PROFILE_SCOPE));
    await set(OFFLINE_PROFILE_KEY, envelope);
  }, undefined);
}

async function discardOfflineProfile(userId, reason) {
  console.warn(`offline cache discarded: ${reason}`);
  if (userId !== undefined && userId !== null) await safely(() => vault.delete(userId, PROFILE_SLOT), undefined);
  await safely(() => del(OFFLINE_PROFILE_KEY), undefined);
}

export async function readOfflineProfile() {
  const stored = await safely(() => get(OFFLINE_PROFILE_KEY), undefined);
  if (stored === undefined || stored === null) return null;
  const userId = await readOwner();
  try {
    if (!hasWebCrypto() || userId === undefined || userId === null || typeof stored !== 'string') {
      throw new Error('unreadable');
    }
    const key = await vault.find(userId, PROFILE_SLOT);
    if (!key) throw new Error('missing key');
    const profile = JSON.parse(await decryptText(key, stored, buildAad(userId, PROFILE_SCOPE)));
    if (!Array.isArray(profile.roles)) throw new Error('invalid profile');
    return { nombre: profile.nombre, apellido: profile.apellido, roles: profile.roles };
  } catch {
    await discardOfflineProfile(userId, 'profile unreadable');
    return null;
  }
}
