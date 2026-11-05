import { useSyncExternalStore } from 'react'
import { getServiceWorkerState, subscribeServiceWorkerState } from './registerServiceWorker'

export function useServiceWorkerState() {
  return useSyncExternalStore(subscribeServiceWorkerState, getServiceWorkerState, getServiceWorkerState)
}
