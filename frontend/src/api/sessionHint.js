const HINT_KEY = 'sessionHint';
const LEGACY_REFRESH_KEY = 'refreshToken';

function safeStorage() {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

export function markSessionActive() {
  const storage = safeStorage();
  if (!storage) return;
  storage.setItem(HINT_KEY, '1');
  storage.removeItem(LEGACY_REFRESH_KEY);
}

export function clearSessionHint() {
  const storage = safeStorage();
  if (!storage) return;
  storage.removeItem(HINT_KEY);
  storage.removeItem(LEGACY_REFRESH_KEY);
}

export function readLegacyRefreshToken() {
  return safeStorage()?.getItem(LEGACY_REFRESH_KEY) ?? null;
}

export function hasSessionHint() {
  const storage = safeStorage();
  if (!storage) return false;
  return storage.getItem(HINT_KEY) === '1' || storage.getItem(LEGACY_REFRESH_KEY) !== null;
}

export function sessionRefreshRequest() {
  const legacyToken = readLegacyRefreshToken();
  return legacyToken
    ? { method: 'POST', body: JSON.stringify({ refreshToken: legacyToken }) }
    : { method: 'POST' };
}
