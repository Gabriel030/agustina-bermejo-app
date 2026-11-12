import { buildAad, decryptFromBinary, digestHex, encryptToBinary } from './offlineCrypto';
import { hasWebCrypto } from './offlineKeyVault';

export const OFFLINE_ENTRY_PREFIX = 'offline-query-entry:';
export const OFFLINE_BUDGET_BYTES = 2 * 1024 * 1024;
export const OFFLINE_WRITE_THROTTLE_MS = 1000;

const MANIFEST_VERSION = 2;

const isEntryName = (name) => typeof name === 'string' && name.startsWith(OFFLINE_ENTRY_PREFIX);

export function createOfflineQueryPersister({
  vault,
  rawStorage,
  resolveUserId,
  scope,
  manifestKey,
  budgetBytes = OFFLINE_BUDGET_BYTES,
  throttleMs = OFFLINE_WRITE_THROTTLE_MS,
}) {
  const known = new Map();
  let latest;
  let scheduled;
  let stats = { entries: 0, bytes: 0, writes: 0, durationMs: 0 };

  const entryAad = (userId, entryName) => buildAad(userId, `${scope}|${entryName}`);

  const removeEntries = async () => {
    const names = (await rawStorage.keys()).filter(isEntryName);
    await Promise.allSettled(names.map((name) => rawStorage.removeItem(name)));
    await Promise.allSettled([rawStorage.removeItem(manifestKey)]);
    known.clear();
  };

  const discardAll = async (reason, userId) => {
    console.warn(`offline cache discarded: ${reason}`);
    if (userId !== undefined && userId !== null) await Promise.allSettled([vault.delete(userId)]);
    await removeEntries();
  };

  const serialize = (query) =>
    JSON.stringify({ queryKey: query.queryKey, queryHash: query.queryHash, state: query.state, meta: query.meta });

  const selectWithinBudget = (items) => {
    const kept = [];
    let total = 0;
    [...items]
      .sort((a, b) => b.updatedAt - a.updatedAt)
      .forEach((item) => {
        if (total + item.size > budgetBytes) return;
        total += item.size;
        kept.push(item);
      });
    return { kept, total };
  };

  const flush = async () => {
    const client = latest;
    latest = undefined;
    if (!client || !hasWebCrypto()) return;
    const startedAt = Date.now();
    const userId = await resolveUserId();
    if (userId === undefined || userId === null) return;
    const key = await vault.getOrCreate(userId);
    if (!key) return;

    const queries = client.clientState?.queries ?? [];
    const items = await Promise.all(
      queries.map(async (query) => {
        const entryName = OFFLINE_ENTRY_PREFIX + (await digestHex(query.queryHash));
        const updatedAt = query.state?.dataUpdatedAt ?? 0;
        const previous = known.get(entryName);
        if (previous && previous.updatedAt === updatedAt) return { entryName, updatedAt, size: previous.size };
        const text = serialize(query);
        return { entryName, updatedAt, size: text.length, text };
      }),
    );
    const { kept, total } = selectWithinBudget(items);
    const keptNames = new Set(kept.map((item) => item.entryName));
    const writes = kept.filter((item) => item.text !== undefined);
    await Promise.all(
      writes.map(async (item) => {
        const sealed = await encryptToBinary(key, item.text, entryAad(userId, item.entryName));
        if ((await resolveUserId()) !== userId) return;
        await rawStorage.setItem(item.entryName, { ...sealed, updatedAt: item.updatedAt });
        known.set(item.entryName, { updatedAt: item.updatedAt, size: item.size });
      }),
    );
    const stale = [...new Set([...known.keys(), ...(await rawStorage.keys()).filter(isEntryName)])].filter(
      (name) => !keptNames.has(name),
    );
    await Promise.all(
      stale.map(async (name) => {
        known.delete(name);
        await rawStorage.removeItem(name);
      }),
    );
    if ((await resolveUserId()) !== userId) return;
    if (writes.length > 0 || stale.length > 0 || (await rawStorage.getItem(manifestKey)) === null) {
      await rawStorage.setItem(manifestKey, { v: MANIFEST_VERSION, buster: client.buster, timestamp: client.timestamp });
    }
    stats = { entries: kept.length, bytes: total, writes: writes.length, durationMs: Date.now() - startedAt };
    if (import.meta.env.DEV) console.debug('offline cache', stats);
  };

  const persistClient = (client) => {
    latest = client;
    if (!scheduled) {
      scheduled = new Promise((resolve) => {
        setTimeout(async () => {
          scheduled = undefined;
          try {
            await flush();
          } catch {
            return;
          } finally {
            resolve();
          }
        }, throttleMs);
      });
    }
    return scheduled;
  };

  const restoreClient = async () => {
    try {
      const manifest = await rawStorage.getItem(manifestKey);
      if (manifest === null || manifest === undefined) return undefined;
      if (manifest.v !== MANIFEST_VERSION || manifest.buster !== scope) {
        await discardAll('manifest unrecognized or from another version');
        return undefined;
      }
      if (!hasWebCrypto()) {
        await discardAll('webcrypto unavailable');
        return undefined;
      }
      const userId = await resolveUserId();
      if (userId === undefined || userId === null) {
        await discardAll('no owner');
        return undefined;
      }
      const key = await vault.find(userId);
      if (!key) {
        await discardAll('missing key', userId);
        return undefined;
      }
      const names = (await rawStorage.keys()).filter(isEntryName);
      const restored = await Promise.all(
        names.map(async (name) => {
          try {
            const sealed = await rawStorage.getItem(name);
            const query = JSON.parse(await decryptFromBinary(key, sealed, entryAad(userId, name)));
            known.set(name, { updatedAt: query.state?.dataUpdatedAt ?? 0, size: Math.max(0, sealed.ct.byteLength - 16) });
            return query;
          } catch {
            console.warn('offline cache discarded: entry unreadable');
            await rawStorage.removeItem(name);
            return null;
          }
        }),
      );
      const queries = restored.filter(Boolean);
      if (queries.length === 0) return undefined;
      return { buster: manifest.buster, timestamp: manifest.timestamp, clientState: { mutations: [], queries } };
    } catch {
      await discardAll('restore failed', await resolveUserId().catch(() => undefined));
      return undefined;
    }
  };

  const removeClient = async () => {
    const userId = await resolveUserId().catch(() => undefined);
    if (userId !== undefined && userId !== null) await Promise.allSettled([vault.delete(userId)]);
    await removeEntries();
  };

  return {
    persistClient,
    restoreClient,
    removeClient,
    forget: () => {
      known.clear();
      latest = undefined;
    },
    stats: () => stats,
  };
}
