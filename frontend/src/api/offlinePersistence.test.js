import { beforeEach, describe, expect, it, vi } from 'vitest'
import { QueryClient } from '@tanstack/react-query'

const store = new Map()

vi.mock('idb-keyval', () => ({
  get: vi.fn(async (key) => store.get(key)),
  set: vi.fn(async (key, value) => {
    store.set(key, value)
  }),
  del: vi.fn(async (key) => {
    store.delete(key)
  }),
}))

import {
  OFFLINE_CACHE_KEY,
  OFFLINE_OWNER_KEY,
  claimOfflineCache,
  offlineDehydrateOptions,
  offlinePersister,
  purgeOfflineData,
} from './offlinePersistence'

function build(client, queryKey, options) {
  return client.getQueryCache().build(client, { queryKey, ...options })
}

beforeEach(() => {
  store.clear()
  localStorage.clear()
  vi.stubGlobal('indexedDB', {})
})

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
  it('purgeRemovesPersistedCacheOwnerAndMemoryQueries', async () => {
    const client = new QueryClient()
    client.setQueryData(['me', 'notifications'], [1])
    store.set(OFFLINE_CACHE_KEY, 'cache')
    store.set(OFFLINE_OWNER_KEY, 7)
    await purgeOfflineData(client)
    expect(client.getQueryCache().getAll()).toHaveLength(0)
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
    expect(store.has(OFFLINE_OWNER_KEY)).toBe(false)
  })
})

describe('claimOfflineCache', () => {
  it('differentOwnerPurgesPreviousUserCache', async () => {
    const client = new QueryClient()
    client.setQueryData(['me', 'notifications'], [1])
    store.set(OFFLINE_CACHE_KEY, 'cache-of-user-1')
    store.set(OFFLINE_OWNER_KEY, 1)
    await claimOfflineCache(2, client)
    expect(client.getQueryCache().getAll()).toHaveLength(0)
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
    expect(store.get(OFFLINE_OWNER_KEY)).toBe(2)
  })

  it('sameOwnerKeepsCache', async () => {
    const client = new QueryClient()
    client.setQueryData(['me', 'notifications'], [1])
    store.set(OFFLINE_CACHE_KEY, 'cache')
    store.set(OFFLINE_OWNER_KEY, 1)
    await claimOfflineCache(1, client)
    expect(client.getQueryCache().getAll()).toHaveLength(1)
    expect(store.get(OFFLINE_CACHE_KEY)).toBe('cache')
  })
})

describe('offlinePersister', () => {
  it('restoreDiscardsCacheWhenThereIsNoSession', async () => {
    store.set(OFFLINE_CACHE_KEY, JSON.stringify({ buster: 'x', timestamp: 1, clientState: { queries: [], mutations: [] } }))
    expect(await offlinePersister.restoreClient()).toBeUndefined()
    expect(store.has(OFFLINE_CACHE_KEY)).toBe(false)
  })

  it('restoreReturnsCacheWhenSessionExists', async () => {
    localStorage.setItem('refreshToken', 'token')
    const persisted = { buster: 'x', timestamp: 1, clientState: { queries: [], mutations: [] } }
    store.set(OFFLINE_CACHE_KEY, JSON.stringify(persisted))
    expect(await offlinePersister.restoreClient()).toEqual(persisted)
  })
})
