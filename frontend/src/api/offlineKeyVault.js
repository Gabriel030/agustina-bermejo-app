import { del, get, keys, set } from 'idb-keyval';

const KEY_PREFIX = 'offline-key:';

export const CACHE_SLOT = 'cache';
export const PROFILE_SLOT = 'profile';

const keyName = (userId, slot) => (slot === CACHE_SLOT ? `${KEY_PREFIX}${userId}` : `${KEY_PREFIX}${userId}:${slot}`);

export const hasWebCrypto = () =>
  typeof globalThis.crypto !== 'undefined' &&
  typeof globalThis.crypto.subtle?.generateKey === 'function' &&
  typeof globalThis.crypto.getRandomValues === 'function';

const hasIndexedDb = () => typeof indexedDB !== 'undefined';

export function createIdbKeyVault() {
  let generation = 0;
  const pending = new Map();

  const find = async (userId, slot = CACHE_SLOT) => {
    if (!hasWebCrypto() || !hasIndexedDb()) return null;
    const stored = await get(keyName(userId, slot));
    return stored ?? null;
  };

  const getOrCreate = async (userId, slot = CACHE_SLOT) => {
    if (!hasWebCrypto() || !hasIndexedDb()) return null;
    const existing = await find(userId, slot);
    if (existing) return existing;
    if (pending.has(keyName(userId, slot))) return pending.get(keyName(userId, slot));
    const startedAt = generation;
    const creation = (async () => {
      const key = await globalThis.crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, false, [
        'encrypt',
        'decrypt',
      ]);
      if (startedAt !== generation) return null;
      await set(keyName(userId, slot), key);
      if (startedAt !== generation) {
        await del(keyName(userId, slot));
        return null;
      }
      return key;
    })().finally(() => pending.delete(keyName(userId, slot)));
    pending.set(keyName(userId, slot), creation);
    return creation;
  };

  const remove = async (userId, slot = CACHE_SLOT) => {
    if (!hasIndexedDb()) return;
    await del(keyName(userId, slot));
  };

  const removeAll = async () => {
    generation += 1;
    if (!hasIndexedDb()) return;
    const all = await keys();
    await Promise.all(all.filter((name) => typeof name === 'string' && name.startsWith(KEY_PREFIX)).map((name) => del(name)));
  };

  return { find, getOrCreate, delete: remove, deleteAll: removeAll };
}
