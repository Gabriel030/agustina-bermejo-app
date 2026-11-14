import { afterEach, describe, expect, it, vi } from 'vitest'
import { registerServiceWorker, resetServiceWorkerState, startUpdateChecks } from './registerServiceWorker'

function fakeDocument(visibilityState = 'visible') {
  const handlers = {}
  return {
    visibilityState,
    handlers,
    addEventListener: (name, handler) => {
      handlers[name] = handler
    },
    removeEventListener: (name) => {
      delete handlers[name]
    },
  }
}

const online = { onLine: true }

afterEach(() => {
  vi.useRealTimers()
  resetServiceWorkerState()
})

describe('update checks', () => {
  it('checksForANewVersionPeriodicallyAndStopsWhenCancelled', () => {
    vi.useFakeTimers()
    const registration = { update: vi.fn().mockResolvedValue() }
    const stop = startUpdateChecks(registration, { intervalMs: 1000, doc: fakeDocument(), nav: online })
    vi.advanceTimersByTime(3000)
    expect(registration.update).toHaveBeenCalledTimes(3)
    stop()
    vi.advanceTimersByTime(3000)
    expect(registration.update).toHaveBeenCalledTimes(3)
  })

  it('checksWhenTheAppReturnsToTheForeground', () => {
    const registration = { update: vi.fn().mockResolvedValue() }
    const doc = fakeDocument('visible')
    startUpdateChecks(registration, { intervalMs: 60000, doc, nav: online })
    doc.handlers.visibilitychange()
    expect(registration.update).toHaveBeenCalledTimes(1)
  })

  it('doesNotCheckWhileTheAppIsHidden', () => {
    const registration = { update: vi.fn().mockResolvedValue() }
    const doc = fakeDocument('hidden')
    startUpdateChecks(registration, { intervalMs: 60000, doc, nav: online })
    doc.handlers.visibilitychange()
    expect(registration.update).not.toHaveBeenCalled()
  })

  it('skipsTheCheckWhileOffline', () => {
    const registration = { update: vi.fn().mockResolvedValue() }
    const doc = fakeDocument('visible')
    startUpdateChecks(registration, { intervalMs: 60000, doc, nav: { onLine: false } })
    doc.handlers.visibilitychange()
    expect(registration.update).not.toHaveBeenCalled()
  })

  it('swallowsFailedChecks', async () => {
    const registration = { update: vi.fn().mockRejectedValue(new Error('network')) }
    const doc = fakeDocument('visible')
    startUpdateChecks(registration, { intervalMs: 60000, doc, nav: online })
    doc.handlers.visibilitychange()
    await Promise.resolve()
    expect(registration.update).toHaveBeenCalledTimes(1)
  })

  it('startsCheckingOnceTheWorkerIsRegistered', async () => {
    const registration = { addEventListener: vi.fn(), waiting: null, installing: null, update: vi.fn().mockResolvedValue() }
    const serviceWorker = { addEventListener: vi.fn(), controller: {}, register: vi.fn().mockResolvedValue(registration) }
    const doc = fakeDocument('visible')
    await registerServiceWorker({ serviceWorker, updateChecks: { doc, nav: online } })
    doc.handlers.visibilitychange()
    expect(registration.update).toHaveBeenCalledTimes(1)
  })
})
