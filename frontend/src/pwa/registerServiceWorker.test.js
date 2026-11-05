import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  applyServiceWorkerUpdate,
  getServiceWorkerState,
  registerServiceWorker,
  resetServiceWorkerState,
  subscribeServiceWorkerState,
} from './registerServiceWorker'

function fakeEmitter() {
  const handlers = {}
  return {
    handlers,
    addEventListener: (name, handler) => {
      handlers[name] = handler
    },
  }
}

function fakeEnvironment({ controller = {}, waiting = null } = {}) {
  const registration = { ...fakeEmitter(), waiting, installing: null }
  const serviceWorker = { ...fakeEmitter(), controller, register: vi.fn().mockResolvedValue(registration) }
  return { registration, serviceWorker }
}

afterEach(() => resetServiceWorkerState())

describe('registerServiceWorker', () => {
  it('doesNothingWhenServiceWorkersAreUnsupported', async () => {
    await expect(registerServiceWorker({ serviceWorker: undefined })).resolves.toBeNull()
  })

  it('registersTheGeneratedWorkerAtTheRootScope', async () => {
    const { serviceWorker } = fakeEnvironment()
    await registerServiceWorker({ serviceWorker })
    expect(serviceWorker.register).toHaveBeenCalledWith('/sw.js', { scope: '/' })
  })

  it('startsWithoutUpdateAvailableOrOfflineReady', async () => {
    const { serviceWorker } = fakeEnvironment()
    await registerServiceWorker({ serviceWorker })
    expect(getServiceWorkerState()).toEqual({ updateAvailable: false, offlineReady: false })
  })

  it('flagsUpdateAvailableWhenAWorkerIsAlreadyWaitingUnderAController', async () => {
    const { serviceWorker } = fakeEnvironment({ waiting: { postMessage: vi.fn() } })
    const listener = vi.fn()
    subscribeServiceWorkerState(listener)
    await registerServiceWorker({ serviceWorker })
    expect(getServiceWorkerState().updateAvailable).toBe(true)
    expect(listener).toHaveBeenCalledWith({ updateAvailable: true, offlineReady: false })
  })

  it('flagsOfflineReadyOnFirstInstallWithoutController', async () => {
    const { serviceWorker, registration } = fakeEnvironment({ controller: null })
    await registerServiceWorker({ serviceWorker })
    const installing = { state: 'installed', ...fakeEmitter() }
    registration.installing = installing
    registration.handlers.updatefound()
    installing.handlers.statechange()
    expect(getServiceWorkerState()).toEqual({ updateAvailable: false, offlineReady: true })
  })

  it('flagsUpdateAvailableWhenANewVersionInstallsUnderAController', async () => {
    const { serviceWorker, registration } = fakeEnvironment()
    await registerServiceWorker({ serviceWorker })
    const installing = { state: 'installed', ...fakeEmitter() }
    registration.installing = installing
    registration.waiting = { postMessage: vi.fn() }
    registration.handlers.updatefound()
    installing.handlers.statechange()
    expect(getServiceWorkerState().updateAvailable).toBe(true)
  })

  it('doesNotActivateTheWaitingWorkerUntilTheUserApplies', async () => {
    const waiting = { postMessage: vi.fn() }
    const { serviceWorker } = fakeEnvironment({ waiting })
    await registerServiceWorker({ serviceWorker })
    expect(waiting.postMessage).not.toHaveBeenCalled()
  })

  it('sendsSkipWaitingToTheWaitingWorkerWhenTheUserApplies', async () => {
    const waiting = { postMessage: vi.fn() }
    const { serviceWorker } = fakeEnvironment({ waiting })
    await registerServiceWorker({ serviceWorker })
    expect(applyServiceWorkerUpdate()).toBe(true)
    expect(waiting.postMessage).toHaveBeenCalledWith({ type: 'SKIP_WAITING' })
  })

  it('applyingWithoutWaitingWorkerDoesNothing', () => {
    expect(applyServiceWorkerUpdate()).toBe(false)
  })

  it('reloadsOnlyAfterAnAppliedUpdateTakesControl', async () => {
    const reload = vi.fn()
    const { serviceWorker } = fakeEnvironment({ waiting: { postMessage: vi.fn() } })
    await registerServiceWorker({ serviceWorker, reload })
    serviceWorker.handlers.controllerchange()
    serviceWorker.handlers.controllerchange()
    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('doesNotReloadWhenTheFirstWorkerClaimsTheClient', async () => {
    const reload = vi.fn()
    const { serviceWorker } = fakeEnvironment({ controller: null })
    await registerServiceWorker({ serviceWorker, reload })
    serviceWorker.handlers.controllerchange()
    expect(reload).not.toHaveBeenCalled()
  })
})
