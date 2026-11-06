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
  offlineDehydrateOptions,
  offlinePersister,
  purgeOfflineData,
  readOfflineProfile,
  storeOfflineProfile,
} from './offlinePersistence'

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
})

const SECRET = 'confidential-notification-text'
const keyNames = () => [...store.keys()].filter((name) => String(name).startsWith('offline-key:'))

function persistedPayload(overrides = {}) {
  return {
    buster: OFFLINE_BUSTER,
    timestamp: Date.now(),
    clientState: { queries: [{ queryKey: ['n'], state: { data: SECRET } }], mutations: [] },
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
    await purgeOfflineData(client)
    expect(client.getQueryCache().getAll()).toHaveLength(0)
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
    expect(store.has(OFFLINE_OWNER_KEY)).toBe(false)
    expect(keyNames()).toHaveLength(0)
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
  it('ownerChangeDiscardsPreviousBlobAndKey', async () => {
    const client = new QueryClient()
    client.setQueryData(['me', 'notifications'], [1])
    await persistAs(1)
    await claimOfflineCache(2, client)
    expect(client.getQueryCache().getAll()).toHaveLength(0)
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
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

  it('blobDoesNotDecryptWithAnotherUsersKey', async () => {
    await persistAs(1)
    const blob = store.get(OFFLINE_CACHE_KEY)
    store.clear()
    await persistAs(2)
    store.set(OFFLINE_CACHE_KEY, blob)
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
  })
})

describe('encrypted cache', () => {
  it('rawIndexedDbValueContainsNoPlaintext', async () => {
    await persistAs(5)
    const raw = String(store.get(OFFLINE_CACHE_KEY))
    expect(raw).not.toContain(SECRET)
    expect(raw).not.toContain('queries')
    expect(JSON.parse(raw)).toEqual({ v: 1, iv: expect.any(String), ct: expect.any(String) })
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
    const first = JSON.parse(store.get(OFFLINE_CACHE_KEY)).iv
    store.delete(OFFLINE_CACHE_KEY)
    await offlinePersister.persistClient(persistedPayload({ timestamp: 2 }))
    await vi.waitFor(() => expect(store.has(OFFLINE_CACHE_KEY)).toBe(true))
    expect(JSON.parse(store.get(OFFLINE_CACHE_KEY)).iv).not.toBe(first)
  })

  it('restoreRoundTripsThePersistedClient', async () => {
    const payload = await persistAs(5)
    expect(await offlinePersister.restoreClient()).toEqual(payload)
  })

  it('blobDoesNotDecryptWhenAadUserDiffers', async () => {
    await persistAs(5)
    store.set(OFFLINE_OWNER_KEY, 6)
    store.set('offline-key:6', store.get('offline-key:5'))
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
  })

  it('blobDoesNotDecryptWhenAadBusterDiffers', async () => {
    vi.resetModules()
    await persistAs(5)
    const blob = store.get(OFFLINE_CACHE_KEY)
    vi.stubEnv('VITE_APP_VERSION', 'another-version')
    const reloaded = await import('./offlinePersistence')
    expect(reloaded.OFFLINE_BUSTER).toBe('another-version')
    store.set(OFFLINE_CACHE_KEY, blob)
    expect(await reloaded.offlinePersister.restoreClient()).toBeUndefined()
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
    vi.unstubAllEnvs()
  })

  it('tamperedCiphertextIsDiscardedAndStartsEmpty', async () => {
    await persistAs(5)
    const envelope = JSON.parse(store.get(OFFLINE_CACHE_KEY))
    const flipped = envelope.ct.startsWith('A') ? `B${envelope.ct.slice(1)}` : `A${envelope.ct.slice(1)}`
    store.set(OFFLINE_CACHE_KEY, JSON.stringify({ ...envelope, ct: flipped }))
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
    expect(keyNames()).toHaveLength(0)
  })

  it('tamperedIvIsDiscarded', async () => {
    await persistAs(5)
    const envelope = JSON.parse(store.get(OFFLINE_CACHE_KEY))
    const flipped = envelope.iv.startsWith('A') ? `B${envelope.iv.slice(1)}` : `A${envelope.iv.slice(1)}`
    store.set(OFFLINE_CACHE_KEY, JSON.stringify({ ...envelope, iv: flipped }))
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
  })

  it('garbageBlobIsDiscardedWithoutThrowing', async () => {
    await persistAs(5)
    store.set(OFFLINE_CACHE_KEY, 'not json at all')
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
  })

  it('missingKeyWithExistingBlobDiscardsBlob', async () => {
    await persistAs(5)
    store.delete('offline-key:5')
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
  })

  it('legacyPlaintextBlobIsDiscardedOnFirstRestore', async () => {
    localStorage.setItem('refreshToken', 'token')
    store.set(OFFLINE_OWNER_KEY, 5)
    store.set(OFFLINE_CACHE_KEY, JSON.stringify(persistedPayload()))
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
  })

  it('blobWithoutOwnerIsDiscarded', async () => {
    await persistAs(5)
    store.delete(OFFLINE_OWNER_KEY)
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
  })

  it('nothingIsPersistedWithoutOwner', async () => {
    localStorage.setItem('refreshToken', 'token')
    await offlinePersister.persistClient(persistedPayload())
    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
    expect(keyNames()).toHaveLength(0)
  })

  it('noWebCryptoMeansNothingIsPersisted', async () => {
    vi.stubGlobal('crypto', {})
    localStorage.setItem('refreshToken', 'token')
    vi.spyOn(localStorage, 'setItem')
    store.set(OFFLINE_OWNER_KEY, 5)
    await offlinePersister.persistClient(persistedPayload())
    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
    expect(localStorage.setItem).not.toHaveBeenCalled()
  })

  it('indexedDbUnavailableNeverFallsBackToLocalStorage', async () => {
    vi.stubGlobal('indexedDB', undefined)
    localStorage.setItem('refreshToken', 'token')
    vi.spyOn(localStorage, 'setItem')
    await offlinePersister.persistClient(persistedPayload())
    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(localStorage.setItem).not.toHaveBeenCalled()
    expect(store.size).toBe(0)
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
