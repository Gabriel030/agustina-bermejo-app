export const DEFAULT_API_ORIGIN = 'http://localhost:8080'

export function resolveApiOrigin(apiBaseUrl) {
  return new URL(apiBaseUrl || DEFAULT_API_ORIGIN).origin
}
