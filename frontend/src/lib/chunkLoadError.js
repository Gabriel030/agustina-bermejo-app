const CHUNK_ERROR_PATTERNS = [
  /Failed to fetch dynamically imported module/i,
  /error loading dynamically imported module/i,
  /Importing a module script failed/i,
  /Loading chunk [\w-]+ failed/i,
  /Loading CSS chunk [\w-]+ failed/i,
];

const RELOAD_GUARD_KEY = 'chunkReloadAttemptedAt';
const RELOAD_GUARD_WINDOW_MS = 60000;

export function isChunkLoadError(error) {
  if (!error) return false;
  if (error.name === 'ChunkLoadError') return true;
  const message = typeof error.message === 'string' ? error.message : '';
  return CHUNK_ERROR_PATTERNS.some((pattern) => pattern.test(message));
}

export function reloadOnceForChunkError({ reload = () => window.location.reload(), now = Date.now() } = {}) {
  try {
    const previous = Number(sessionStorage.getItem(RELOAD_GUARD_KEY));
    if (previous && now - previous < RELOAD_GUARD_WINDOW_MS) return false;
    sessionStorage.setItem(RELOAD_GUARD_KEY, String(now));
  } catch {
    return false;
  }
  reload();
  return true;
}
