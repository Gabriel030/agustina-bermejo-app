import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'
import { useMisNotificaciones } from './useNotifications'

const pageOf = (items) => ({ items, page: 0, size: 10, totalElements: items.length })

const API = 'http://localhost:8080'

afterEach(() => vi.restoreAllMocks())

function wrapper(client) {
  return function Wrapper({ children }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>
  }
}

describe('useMisNotificaciones', () => {
  it('notificationsQueryIsMarkedForOfflineAndStopsPollingWhenOffline', async () => {
    server.use(http.get(`${API}/me/notifications`, () => HttpResponse.json(pageOf([]))))
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useMisNotificaciones(), { wrapper: wrapper(client) })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    const query = client.getQueryCache().find({ queryKey: ['me', 'notifications', { page: 0, size: 10 }] })
    expect(query.meta.offline).toBe(true)
    const interval = query.observers[0].options.refetchInterval
    vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(false)
    expect(interval()).toBe(false)
    window.navigator.onLine
    vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(true)
    expect(interval()).toBe(15_000)
  })
})
