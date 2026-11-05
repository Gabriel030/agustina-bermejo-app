import { SKIP_WAITING_MESSAGE } from './swMessages'

const listeners = new Set()
let state = { updateAvailable: false, offlineReady: false }
let waitingWorker = null

function publish(patch) {
  state = { ...state, ...patch }
  listeners.forEach((listener) => listener(state))
}

export function getServiceWorkerState() {
  return state
}

export function subscribeServiceWorkerState(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function applyServiceWorkerUpdate() {
  if (!waitingWorker) return false
  waitingWorker.postMessage({ type: SKIP_WAITING_MESSAGE })
  return true
}

export function registerServiceWorker({ serviceWorker = globalThis.navigator?.serviceWorker, scriptUrl = '/sw.js', reload = () => globalThis.location.reload() } = {}) {
  if (!serviceWorker) return Promise.resolve(null)

  const trackWaiting = (registration) => {
    if (registration.waiting && serviceWorker.controller) {
      waitingWorker = registration.waiting
      publish({ updateAvailable: true })
    }
  }

  let reloading = false
  serviceWorker.addEventListener('controllerchange', () => {
    if (!waitingWorker || reloading) return
    reloading = true
    reload()
  })

  return serviceWorker.register(scriptUrl, { scope: '/' }).then((registration) => {
    trackWaiting(registration)
    registration.addEventListener('updatefound', () => {
      const installing = registration.installing
      if (!installing) return
      installing.addEventListener('statechange', () => {
        if (installing.state !== 'installed') return
        if (serviceWorker.controller) trackWaiting(registration)
        else publish({ offlineReady: true })
      })
    })
    return registration
  })
}

export function resetServiceWorkerState() {
  state = { updateAvailable: false, offlineReady: false }
  waitingWorker = null
  listeners.clear()
}
