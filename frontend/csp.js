export const DEFAULT_API_ORIGIN = 'http://localhost:8080'

export function resolveApiOrigin(apiBaseUrl) {
  if (apiBaseUrl?.startsWith('/')) return "'self'"
  return new URL(apiBaseUrl || DEFAULT_API_ORIGIN).origin
}

export function extractInlineScripts(html) {
  return [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((match) => match[1])
}
