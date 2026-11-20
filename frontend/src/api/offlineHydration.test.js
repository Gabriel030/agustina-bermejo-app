import { describe, expect, it } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import { persistQueryClientRestore, persistQueryClientSave } from '@tanstack/react-query-persist-client'
import { OFFLINE_MAX_AGE_MS, offlineDehydrateOptions } from './offlinePersistence'
import { offlineQuery } from './offlineQuery'

function memoryPersister() {
  let stored
  return {
    persistClient: async (client) => {
      stored = structuredClone(client)
    },
    restoreClient: async () => stored && structuredClone(stored),
    removeClient: async () => {
      stored = undefined
    },
    age: (ms) => {
      stored.timestamp -= ms
    },
  }
}

const UPDATED_AT = 1_700_000_000_000

async function saveClientWithScheduleAndBilling(persister) {
  const source = new QueryClient()
  const schedule = offlineQuery('N1', { queryKey: ['me', 'schedule'], queryFn: () => ({ items: [] }) })
  source.getQueryCache().build(source, schedule).setData({ items: [] }, { updatedAt: UPDATED_AT })
  source.setQueryData(['billing', 'standing'], { balance: 10 })
  await persistQueryClientSave({ queryClient: source, persister, dehydrateOptions: offlineDehydrateOptions })
}

describe('offline hydration', () => {
  it('dataUpdatedAtSurvivesHydration', async () => {
    const persister = memoryPersister()
    await saveClientWithScheduleAndBilling(persister)
    const target = new QueryClient()
    await persistQueryClientRestore({ queryClient: target, persister, maxAge: OFFLINE_MAX_AGE_MS })
    expect(target.getQueryState(['me', 'schedule']).dataUpdatedAt).toBe(UPDATED_AT)
    expect(target.getQueryState(['billing', 'standing'])).toBeUndefined()
  })

  it('expiredCacheIsNotRendered', async () => {
    const persister = memoryPersister()
    await saveClientWithScheduleAndBilling(persister)
    persister.age(OFFLINE_MAX_AGE_MS + 60_000)
    const target = new QueryClient()
    await persistQueryClientRestore({ queryClient: target, persister, maxAge: OFFLINE_MAX_AGE_MS })
    expect(target.getQueryData(['me', 'schedule'])).toBeUndefined()
  })

  it('catalogQueriesMarkedN0cSurviveHydration', async () => {
    const persister = memoryPersister()
    const source = new QueryClient()
    const catalog = offlineQuery('N0c', { queryKey: ['terminos'], queryFn: () => [] })
    source.getQueryCache().build(source, catalog).setData([{ id: 1 }], { updatedAt: UPDATED_AT })
    await persistQueryClientSave({ queryClient: source, persister, dehydrateOptions: offlineDehydrateOptions })
    const target = new QueryClient()
    await persistQueryClientRestore({ queryClient: target, persister, maxAge: OFFLINE_MAX_AGE_MS })
    expect(target.getQueryData(['terminos'])).toEqual([{ id: 1 }])
    expect(target.getQueryCache().find({ queryKey: ['terminos'] }).meta.offlineLevel).toBe('N0c')
  })
})
