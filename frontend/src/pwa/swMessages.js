export const SKIP_WAITING_MESSAGE = 'SKIP_WAITING'

export const STATIC_ASSET_CACHE = 'static-assets'

export function isStaticAssetRequest(url, origin) {
  return url.origin === origin && url.pathname.startsWith('/assets/')
}

export const NAVIGATION_DENYLIST = [/^\/api\//, /^\/__/, /\.[a-z0-9]+$/i]

export function isSkipWaitingMessage(data) {
  return Boolean(data) && data.type === SKIP_WAITING_MESSAGE
}
