import { beforeEach, describe, expect, it, vi } from 'vitest'
import { QueryClient } from '@tanstack/react-query'

const store = new Map()

vi.mock('idb-keyval', () => ({
  get: vi.fn(async (key) => (store.has(key) ? structuredClone(store.get(key)) : undefined)),
  set: vi.fn(async (key, value) => {
    store.set(key, structuredClone(value))
  }),
  del: vi.fn(async (key) => {
    store.delete(key)
  }),
  keys: vi.fn(async () => [...store.keys()]),
}))

import * as idb from 'idb-keyval'

import {
  OFFLINE_BUSTER,
  OFFLINE_CACHE_KEY,
  OFFLINE_PROFILE_KEY,
  OFFLINE_OWNER_KEY,
  claimOfflineCache,
  OFFLINE_ENTRY_PREFIX,
  createOfflinePersister,
  offlineDehydrateOptions,
  purgeOfflineData,
  readOfflineProfile,
  storeOfflineProfile,
} from './offlinePersistence'

let offlinePersister

function build(client, queryKey, options) {
  return client.getQueryCache().build(client, { queryKey, ...options })
}

beforeEach(() => {
  vi.unstubAllGlobals()
  store.clear()
  localStorage.clear()
  vi.stubGlobal('indexedDB', {})
  vi.restoreAllMocks()
  idb.del.mockImplementation(async (name) => {
    store.delete(name)
  })
  vi.spyOn(console, 'warn').mockImplementation(() => {})
  offlinePersister = createOfflinePersister({ throttleMs: 0 })
})

const SECRET = 'confidential-notification-text'
const entryNames = () => [...store.keys()].filter((name) => String(name).startsWith(OFFLINE_ENTRY_PREFIX))
const keyNames = () => [...store.keys()].filter((name) => String(name).startsWith('offline-key:'))

function persistedPayload(overrides = {}) {
  return {
    buster: OFFLINE_BUSTER,
    timestamp: Date.now(),
    clientState: { queries: [{ queryKey: ['n'], queryHash: '["n"]', state: { data: SECRET, dataUpdatedAt: 1000 } }], mutations: [] },
    ...overrides,
  }
}

async function persistAs(userId, payload = persistedPayload()) {
  localStorage.setItem('refreshToken', 'token')
  store.set(OFFLINE_OWNER_KEY, userId)
  await offlinePersister.persistClient(payload)
  await vi.waitFor(() => expect(store.has(OFFLINE_CACHE_KEY)).toBe(true))
  return payload
}

describe('offlineDehydrateOptions', () => {
  it('onlyQueriesFlaggedOfflineAreDehydrated', () => {
    const client = new QueryClient()
    const flagged = build(client, ['flagged'], { meta: { offline: true } })
    const plain = build(client, ['plain'])
    flagged.setData(1)
    plain.setData(2)
    expect(offlineDehydrateOptions.shouldDehydrateQuery(flagged)).toBe(true)
    expect(offlineDehydrateOptions.shouldDehydrateQuery(plain)).toBe(false)
  })

  it('dehydrationSkipsErroredQueries', async () => {
    const client = new QueryClient()
    const query = build(client, ['failing'], { meta: { offline: true }, queryFn: () => Promise.reject(new Error('boom')) })
    await query.fetch().catch(() => {})
    expect(query.state.status).toBe('error')
    expect(offlineDehydrateOptions.shouldDehydrateQuery(query)).toBe(false)
  })
})

describe('purgeOfflineData', () => {
  it('purgeRemovesPersistedCacheOwnerKeyAndMemoryQueries', async () => {
    const client = new QueryClient()
    client.setQueryData(['me', 'notifications'], [1])
    await persistAs(7)
    expect(keyNames()).toHaveLength(1)
    expect(entryNames()).toHaveLength(1)
    await purgeOfflineData(client)
    expect(client.getQueryCache().getAll()).toHaveLength(0)
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
    expect(store.has(OFFLINE_OWNER_KEY)).toBe(false)
    expect(keyNames()).toHaveLength(0)
    expect(entryNames()).toHaveLength(0)
  })

  it('logoutDeletesKeyFirstEvenWhenBlobDeleteFails', async () => {
    await persistAs(7)
    idb.del.mockImplementation(async (name) => {
      if (!String(name).startsWith('offline-key:')) throw new Error('blob delete failed')
      store.delete(name)
    })
    await purgeOfflineData(new QueryClient())
    expect(keyNames()).toHaveLength(0)
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(true)
    localStorage.setItem('refreshToken', 'token')
    store.set(OFFLINE_OWNER_KEY, 7)
    expect(await offlinePersister.restoreClient()).toBeUndefined()
  })
})

describe('claimOfflineCache', () => {
  it('ownerChangeDiscardsPreviousEntriesAndKey', async () => {
    const client = new QueryClient()
    client.setQueryData(['me', 'notifications'], [1])
    await persistAs(1)
    await claimOfflineCache(2, client)
    expect(client.getQueryCache().getAll()).toHaveLength(0)
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
    expect(entryNames()).toHaveLength(0)
    expect(keyNames()).toHaveLength(0)
    expect(store.get(OFFLINE_OWNER_KEY)).toBe(2)
  })

  it('sameOwnerKeepsCacheAndKey', async () => {
    const client = new QueryClient()
    client.setQueryData(['me', 'notifications'], [1])
    const payload = await persistAs(1)
    await claimOfflineCache(1, client)
    expect(client.getQueryCache().getAll()).toHaveLength(1)
    expect(await offlinePersister.restoreClient()).toEqual(payload)
  })

  it('entriesDoNotDecryptWithAnotherUsersKey', async () => {
    await persistAs(1)
    const [name] = entryNames()
    const entry = store.get(name)
    store.clear()
    offlinePersister = createOfflinePersister({ throttleMs: 0 })
    await persistAs(2)
    store.set(name, entry)
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(entryNames()).toHaveLength(0)
  })
})

describe('encrypted cache', () => {
  it('rawIndexedDbValuesContainNoPlaintextAndNoBase64', async () => {
    await persistAs(5)
    const [name] = entryNames()
    const entry = store.get(name)
    expect(Object.keys(entry).sort()).toEqual(['ct', 'iv', 'updatedAt', 'v'])
    expect(Object.prototype.toString.call(entry.ct)).toBe('[object ArrayBuffer]')
    expect(new TextDecoder('latin1').decode(entry.ct)).not.toContain(SECRET)
    expect(name).not.toContain('"n"')
    expect(JSON.stringify(store.get(OFFLINE_CACHE_KEY))).not.toContain(SECRET)
  })

  it('exportKeyRejectsForNonExtractableKey', async () => {
    await persistAs(5)
    const [name] = keyNames()
    const key = store.get(name)
    expect(key.extractable).toBe(false)
    await expect(globalThis.crypto.subtle.exportKey('raw', key)).rejects.toThrow()
  })

  it('ivIsDifferentOnEveryWrite', async () => {
    await persistAs(5)
    const [name] = entryNames()
    const first = Array.from(store.get(name).iv).join(',')
    const changed = persistedPayload()
    changed.clientState.queries[0].state.dataUpdatedAt = 2000
    await offlinePersister.persistClient(changed)
    expect(Array.from(store.get(name).iv).join(',')).not.toBe(first)
  })

  it('restoreRoundTripsThePersistedClient', async () => {
    const payload = await persistAs(5)
    expect(await offlinePersister.restoreClient()).toEqual(payload)
  })

  it('entriesDoNotDecryptWhenAadUserDiffers', async () => {
    await persistAs(5)
    store.set(OFFLINE_OWNER_KEY, 6)
    store.set('offline-key:6', store.get('offline-key:5'))
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(entryNames()).toHaveLength(0)
  })

  it('entryMovedToAnotherNameDoesNotDecrypt', async () => {
    await persistAs(5)
    const [name] = entryNames()
    store.set(`${OFFLINE_ENTRY_PREFIX}moved`, store.get(name))
    store.delete(name)
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(entryNames()).toHaveLength(0)
  })

  it('manifestFromAnotherBusterDiscardsEverything', async () => {
    vi.resetModules()
    await persistAs(5)
    vi.stubEnv('VITE_APP_VERSION', 'another-version')
    const reloaded = await import('./offlinePersistence')
    expect(reloaded.OFFLINE_BUSTER).toBe('another-version')
    expect(await reloaded.createOfflinePersister().restoreClient()).toBeUndefined()
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
    expect(entryNames()).toHaveLength(0)
    vi.unstubAllEnvs()
  })

  it('tamperedCiphertextIsDiscardedAndStartsEmpty', async () => {
    await persistAs(5)
    const [name] = entryNames()
    new Uint8Array(store.get(name).ct)[0] ^= 1
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(entryNames()).toHaveLength(0)
  })

  it('tamperedIvIsDiscarded', async () => {
    await persistAs(5)
    const [name] = entryNames()
    store.get(name).iv[0] ^= 1
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(entryNames()).toHaveLength(0)
  })

  it('oneTamperedEntryDoesNotDiscardTheOthers', async () => {
    const payload = persistedPayload()
    payload.clientState.queries.push({ queryKey: ['m'], queryHash: '["m"]', state: { data: 'other', dataUpdatedAt: 5 } })
    await persistAs(5, payload)
    const [first] = entryNames()
    new Uint8Array(store.get(first).ct)[0] ^= 1
    const restored = await offlinePersister.restoreClient()
    expect(restored.clientState.queries).toHaveLength(1)
    expect(entryNames()).toHaveLength(1)
  })

  it('garbageManifestIsDiscardedWithoutThrowing', async () => {
    await persistAs(5)
    store.set(OFFLINE_CACHE_KEY, 'not json at all')
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
    expect(entryNames()).toHaveLength(0)
  })

  it('missingKeyWithExistingEntriesDiscardsThem', async () => {
    await persistAs(5)
    store.delete('offline-key:5')
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
    expect(entryNames()).toHaveLength(0)
  })

  it('legacySingleBlobIsDiscardedOnFirstRestore', async () => {
    localStorage.setItem('refreshToken', 'token')
    store.set(OFFLINE_OWNER_KEY, 5)
    store.set(OFFLINE_CACHE_KEY, JSON.stringify(persistedPayload()))
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
  })

  it('entriesWithoutOwnerAreDiscarded', async () => {
    await persistAs(5)
    store.delete(OFFLINE_OWNER_KEY)
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
    expect(entryNames()).toHaveLength(0)
  })

  it('nothingIsPersistedWithoutOwner', async () => {
    localStorage.setItem('refreshToken', 'token')
    await offlinePersister.persistClient(persistedPayload())
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
    expect(keyNames()).toHaveLength(0)
    expect(entryNames()).toHaveLength(0)
  })

  it('noWebCryptoMeansNothingIsPersisted', async () => {
    vi.stubGlobal('crypto', {})
    localStorage.setItem('refreshToken', 'token')
    vi.spyOn(localStorage, 'setItem')
    store.set(OFFLINE_OWNER_KEY, 5)
    await offlinePersister.persistClient(persistedPayload())
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
    expect(entryNames()).toHaveLength(0)
    expect(localStorage.setItem).not.toHaveBeenCalled()
  })

  it('indexedDbUnavailableNeverFallsBackToLocalStorage', async () => {
    vi.stubGlobal('indexedDB', undefined)
    localStorage.setItem('refreshToken', 'token')
    vi.spyOn(localStorage, 'setItem')
    await offlinePersister.persistClient(persistedPayload())
    expect(localStorage.setItem).not.toHaveBeenCalled()
    expect(store.size).toBe(0)
  })
})

describe('incremental persistence', () => {
  const query = (name, dataUpdatedAt, data = name) => ({
    queryKey: [name],
    queryHash: JSON.stringify([name]),
    state: { data, dataUpdatedAt },
  })
  const client = (...queries) => persistedPayload({ clientState: { queries, mutations: [] } })
  const entryWrites = () => idb.set.mock.calls.filter(([name]) => String(name).startsWith(OFFLINE_ENTRY_PREFIX))

  async function prepare() {
    localStorage.setItem('refreshToken', 'token')
    store.set(OFFLINE_OWNER_KEY, 7)
    await offlinePersister.persistClient(client(query('a', 1), query('b', 2)))
    idb.set.mockClear()
  }

  it('onlyTheChangedQueryIsRewritten', async () => {
    await prepare()
    await offlinePersister.persistClient(client(query('a', 1), query('b', 3)))
    expect(entryWrites()).toHaveLength(1)
    expect(entryNames()).toHaveLength(2)
  })

  it('anUnchangedClientWritesNothing', async () => {
    await prepare()
    await offlinePersister.persistClient(client(query('a', 1), query('b', 2)))
    expect(idb.set).not.toHaveBeenCalled()
  })

  it('aQueryThatLeftTheClientIsRemovedFromStorage', async () => {
    await prepare()
    await offlinePersister.persistClient(client(query('a', 1)))
    expect(entryNames()).toHaveLength(1)
    const restored = await offlinePersister.restoreClient()
    expect(restored.clientState.queries.map((item) => item.queryKey)).toEqual([['a']])
  })

  it('restoredEntriesAreNotRewrittenWhenNothingChanged', async () => {
    await prepare()
    const fresh = createOfflinePersister({ throttleMs: 0 })
    await fresh.restoreClient()
    idb.set.mockClear()
    await fresh.persistClient(client(query('a', 1), query('b', 2)))
    expect(idb.set).not.toHaveBeenCalled()
  })

  it('burstsOfChangesAreCoalescedIntoOneFlush', async () => {
    localStorage.setItem('refreshToken', 'token')
    store.set(OFFLINE_OWNER_KEY, 7)
    const throttled = createOfflinePersister({ throttleMs: 30 })
    const flushes = [1, 2, 3].map((tick) => throttled.persistClient(client(query('a', tick))))
    await Promise.all(flushes)
    expect(entryWrites()).toHaveLength(1)
    expect(Array.from(store.values()).some((value) => value?.timestamp !== undefined)).toBe(true)
  })

  it('theOldestQueriesAreDroppedWhenTheBudgetIsExceeded', async () => {
    localStorage.setItem('refreshToken', 'token')
    store.set(OFFLINE_OWNER_KEY, 7)
    const small = createOfflinePersister({ throttleMs: 0, budgetBytes: 600 })
    const big = 'x'.repeat(120)
    const queries = [query('old', 1, big), query('mid', 2, big), query('new', 3, big)]
    await small.persistClient(client(...queries))
    const restored = await small.restoreClient()
    const names = restored.clientState.queries.map((item) => item.queryKey[0]).sort()
    expect(names).toEqual(['mid', 'new'])
    expect(small.stats().bytes).toBeLessThanOrEqual(600)
    idb.set.mockClear()
    await small.persistClient(client(...queries))
    expect(idb.set).not.toHaveBeenCalled()
  })
})

describe('offline profile', () => {
  const profile = { id: 5, nombre: 'Ana', apellido: 'Gomez', roles: ['STUDENT'], email: 'x@y.z' }

  it('profileIsStoredEncryptedAndReadBack', async () => {
    store.set(OFFLINE_OWNER_KEY, 5)
    await storeOfflineProfile(profile)
    const raw = String(store.get(OFFLINE_PROFILE_KEY))
    expect(raw).not.toContain('Ana')
    expect(raw).not.toContain('STUDENT')
    expect(await readOfflineProfile()).toEqual({ nombre: 'Ana', apellido: 'Gomez', roles: ['STUDENT'] })
  })

  it('legacyPlaintextProfileIsDiscarded', async () => {
    store.set(OFFLINE_OWNER_KEY, 5)
    store.set(OFFLINE_PROFILE_KEY, { nombre: 'Ana', apellido: 'Gomez', roles: ['STUDENT'] })
    expect(await readOfflineProfile()).toBeNull()
    expect(store.has(OFFLINE_PROFILE_KEY)).toBe(false)
  })

  it('profileDoesNotDecryptForAnotherOwner', async () => {
    store.set(OFFLINE_OWNER_KEY, 5)
    await storeOfflineProfile(profile)
    store.set('offline-key:6:profile', store.get('offline-key:5:profile'))
    store.set(OFFLINE_OWNER_KEY, 6)
    expect(await readOfflineProfile()).toBeNull()
    expect(store.has(OFFLINE_PROFILE_KEY)).toBe(false)
  })

  it('tamperedProfileIsDiscarded', async () => {
    store.set(OFFLINE_OWNER_KEY, 5)
    await storeOfflineProfile(profile)
    const envelope = JSON.parse(store.get(OFFLINE_PROFILE_KEY))
    const flipped = envelope.ct.startsWith('A') ? `B${envelope.ct.slice(1)}` : `A${envelope.ct.slice(1)}`
    store.set(OFFLINE_PROFILE_KEY, JSON.stringify({ ...envelope, ct: flipped }))
    expect(await readOfflineProfile()).toBeNull()
    expect(store.has(OFFLINE_PROFILE_KEY)).toBe(false)
  })

  it('profileIsNotStoredWithoutWebCrypto', async () => {
    vi.stubGlobal('crypto', {})
    store.set(OFFLINE_OWNER_KEY, 5)
    await storeOfflineProfile(profile)
    expect(store.has(OFFLINE_PROFILE_KEY)).toBe(false)
  })

  it('blobTamperingKeepsProfileReadable', async () => {
    await persistAs(5)
    await storeOfflineProfile(profile)
    store.set(OFFLINE_CACHE_KEY, 'garbage')
    await offlinePersister.restoreClient()
    expect(await readOfflineProfile()).toEqual({ nombre: 'Ana', apellido: 'Gomez', roles: ['STUDENT'] })
  })
})

describe('offlinePersister', () => {
  it('restoreWithoutRefreshTokenClearsKeyAndBlob', async () => {
    await persistAs(5)
    localStorage.removeItem('refreshToken')
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
    expect(keyNames()).toHaveLength(0)
  })
})
