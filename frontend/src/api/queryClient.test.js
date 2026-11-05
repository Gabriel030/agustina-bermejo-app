import { afterEach, describe, expect, it, vi } from 'vitest'
import { createQueryClient } from './queryClient'

afterEach(() => vi.restoreAllMocks())

describe('createQueryClient', () => {
  it('mutationsFailFastWhenOffline', async () => {
    vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(false)
    const client = createQueryClient()
    const mutationFn = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    const mutation = client.getMutationCache().build(client, { mutationFn })
    await expect(mutation.execute()).rejects.toThrow('Failed to fetch')
    expect(mutationFn).toHaveBeenCalledOnce()
    expect(mutation.state.status).toBe('error')
  })

  it('queriesDefaultToOfflineFirst', () => {
    expect(createQueryClient().getDefaultOptions().queries.networkMode).toBe('offlineFirst')
  })
})
