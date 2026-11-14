import { precacheAndRoute, cleanupOutdatedCaches, createHandlerBoundToURL } from 'workbox-precaching'
import { NavigationRoute, registerRoute } from 'workbox-routing'
import { CacheFirst } from 'workbox-strategies'
import { ExpirationPlugin } from 'workbox-expiration'
import { CacheableResponsePlugin } from 'workbox-cacheable-response'
import { NAVIGATION_DENYLIST, STATIC_ASSET_CACHE, isSkipWaitingMessage, isStaticAssetRequest } from './pwa/swMessages'

cleanupOutdatedCaches()
precacheAndRoute(self.__WB_MANIFEST)

registerRoute(new NavigationRoute(createHandlerBoundToURL('/index.html'), { denylist: NAVIGATION_DENYLIST }))

registerRoute(
  ({ url }) => isStaticAssetRequest(url, self.location.origin),
  new CacheFirst({
    cacheName: STATIC_ASSET_CACHE,
    plugins: [
      new CacheableResponsePlugin({ statuses: [200] }),
      new ExpirationPlugin({ maxEntries: 150, maxAgeSeconds: 30 * 24 * 60 * 60, purgeOnQuotaError: true }),
    ],
  }),
)

self.addEventListener('message', (event) => {
  if (isSkipWaitingMessage(event.data)) self.skipWaiting()
})

self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))
