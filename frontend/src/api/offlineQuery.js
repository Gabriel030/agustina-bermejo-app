export const OFFLINE_LEVELS = Object.freeze({
  N0: 'N0',
  N1: 'N1',
  N2: 'N2',
})

export function offlineQuery(level, options) {
  if (!OFFLINE_LEVELS[level]) throw new Error(`Unknown offline level: ${level}`)
  return { ...options, meta: { ...options.meta, offline: true, offlineLevel: level } }
}
