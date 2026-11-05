export const SKIP_WAITING_MESSAGE = 'SKIP_WAITING'

export const NAVIGATION_DENYLIST = [/^\/api\//, /^\/__/, /\.[a-z0-9]+$/i]

export function isSkipWaitingMessage(data) {
  return Boolean(data) && data.type === SKIP_WAITING_MESSAGE
}
