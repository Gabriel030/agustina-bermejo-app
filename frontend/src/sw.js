import { precacheAndRoute, cleanupOutdatedCaches, createHandlerBoundToURL } from 'workbox-precaching'
import { NavigationRoute, registerRoute } from 'workbox-routing'
import { NAVIGATION_DENYLIST, isSkipWaitingMessage } from './pwa/swMessages'

cleanupOutdatedCaches()
precacheAndRoute(self.__WB_MANIFEST)

registerRoute(new NavigationRoute(createHandlerBoundToURL('/index.html'), { denylist: NAVIGATION_DENYLIST }))

self.addEventListener('message', (event) => {
  if (isSkipWaitingMessage(event.data)) self.skipWaiting()
})

self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))
