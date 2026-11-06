import { useSyncExternalStore } from 'react'

const listeners = new Set()
let deferredPrompt = null

function notify() {
  listeners.forEach((listener) => listener())
}

export function captureInstallPrompt(target = window) {
  const onPrompt = (event) => {
    event.preventDefault()
    deferredPrompt = event
    notify()
  }
  const onInstalled = () => {
    deferredPrompt = null
    notify()
  }
  target.addEventListener('beforeinstallprompt', onPrompt)
  target.addEventListener('appinstalled', onInstalled)
  return () => {
    target.removeEventListener('beforeinstallprompt', onPrompt)
    target.removeEventListener('appinstalled', onInstalled)
  }
}

export function isStandalone() {
  return window.matchMedia?.('(display-mode: standalone)').matches === true || window.navigator.standalone === true
}

export function isIos() {
  return /iphone|ipad|ipod/i.test(window.navigator.userAgent)
}

export async function promptInstall() {
  if (!deferredPrompt) return null
  const prompt = deferredPrompt
  deferredPrompt = null
  notify()
  await prompt.prompt()
  const choice = await prompt.userChoice
  return choice?.outcome ?? null
}

function subscribe(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function useCanPromptInstall() {
  return useSyncExternalStore(subscribe, () => deferredPrompt !== null, () => false)
}

export function resetInstallPrompt() {
  deferredPrompt = null
  listeners.clear()
}
