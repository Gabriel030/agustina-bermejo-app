import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClientProvider, focusManager } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'
import { createQueryClient } from './queryClient'
import { STALE_TIME } from './cachePolicy'
import { useMisNotificaciones } from '@/features/notifications/hooks/useNotifications'

const API = 'http://localhost:8080'

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
  focusManager.setFocused(undefined)
})

function staleTimeOf(queryKey) {
  return createQueryClient().defaultQueryOptions({ queryKey }).staleTime
}

describe('cache policy staleTime by domain', () => {
  it.each([
    [['students', 1, 'billing-overview']],
    [['payments']],
    [['authorization-requests', 'list']],
    [['monthly-runs']],
    [['annual-upfront', 3]],
    [['attendance', 'roster', 'COURSE', 5]],
    [['me', 'invoices']],
    [['me', 'credit-balance']],
    [['me', 'section-enrollments', 4, 'grade-items']],
    [['staff-grade-grid']],
    [['program-enrollments', 2]],
  ])('moneyAndStateQueriesAreNeverCached %j', (queryKey) => {
    expect(staleTimeOf(queryKey)).toBe(STALE_TIME.CRITICAL)
  })

  it.each([
    [['catalog', 'programs']],
    [['programs', 'list']],
    [['subjects']],
    [['program-subjects', 1]],
    [['ciclos']],
    [['classrooms']],
    [['terms', 'all']],
    [['evaluation-schemes']],
  ])('academicStructureAndCatalogQueriesAreCachedLong %j', (queryKey) => {
    expect(staleTimeOf(queryKey)).toBe(STALE_TIME.LONG)
  })

  it.each([[['me', 'schedule']], [['me', 'announcements']]])('scheduleAndAnnouncementsAreCachedMedium %j', (queryKey) => {
    expect(staleTimeOf(queryKey)).toBe(STALE_TIME.MEDIUM)
  })

  it('holidaysAreCachedStatic', () => {
    expect(staleTimeOf(['holidays'])).toBe(STALE_TIME.STATIC)
  })

  it('unlistedQueriesFallBackToOperationalDefault', () => {
    expect(staleTimeOf(['something-else'])).toBe(STALE_TIME.OPERATIONAL)
  })

  it('windowFocusRefetchIsOffByDefault', () => {
    expect(createQueryClient().getDefaultOptions().queries.refetchOnWindowFocus).toBe(false)
  })
})

describe('notifications polling', () => {
  function wrapperFor(client) {
    return function Wrapper({ children }) {
      return <QueryClientProvider client={client}>{children}</QueryClientProvider>
    }
  }

  it('pollingPausesWhileTabIsHiddenAndRefetchesOnReturn', async () => {
    let calls = 0
    server.use(http.get(`${API}/me/notifications`, () => {
      calls += 1
      return HttpResponse.json([])
    }))
    const client = createQueryClient()
    const { result } = renderHook(() => useMisNotificaciones(), { wrapper: wrapperFor(client) })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(calls).toBe(1)

    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] })
    act(() => focusManager.setFocused(false))
    await act(async () => { await vi.advanceTimersByTimeAsync(60_000) })
    expect(calls).toBe(1)

    act(() => focusManager.setFocused(true))
    await act(async () => { await vi.advanceTimersByTimeAsync(500) })
    expect(calls).toBe(2)
  })

  it('notificationsQueryDeclaresNoBackgroundPollingAndRefetchOnFocus', async () => {
    server.use(http.get(`${API}/me/notifications`, () => HttpResponse.json([])))
    const client = createQueryClient()
    const { result } = renderHook(() => useMisNotificaciones(), { wrapper: wrapperFor(client) })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    const options = client.getQueryCache().find({ queryKey: ['me', 'notifications'] }).observers[0].options
    expect(options.refetchIntervalInBackground).toBe(false)
    expect(options.refetchOnWindowFocus).toBe(true)
    expect(options.staleTime).toBe(STALE_TIME.OPERATIONAL)
  })
})
