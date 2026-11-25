export const CLIENT_EVENT_MAX_PER_SESSION = 20;
export const CLIENT_EVENT_DEDUPE_MS = 60_000;

function fingerprintOf(event) {
  const exception = event.exception?.values?.[0];
  if (!exception) return JSON.stringify(['message', event.message ?? '']);
  const firstFrame = exception.stacktrace?.frames?.[0];
  return JSON.stringify([exception.type, firstFrame?.filename, firstFrame?.function, firstFrame?.lineno, firstFrame?.colno]);
}

export function createClientEventThrottle({
  maxPerSession = CLIENT_EVENT_MAX_PER_SESSION,
  dedupeMs = CLIENT_EVENT_DEDUPE_MS,
  now = Date.now,
} = {}) {
  const lastSentAtByFingerprint = new Map();
  let sentCount = 0;

  return function throttleClientEvent(event) {
    if (sentCount >= maxPerSession) return null;

    const fingerprint = fingerprintOf(event);
    const currentTime = now();
    const lastSentAt = lastSentAtByFingerprint.get(fingerprint);
    if (lastSentAt !== undefined && currentTime - lastSentAt < dedupeMs) return null;

    lastSentAtByFingerprint.set(fingerprint, currentTime);
    sentCount += 1;
    return event;
  };
}
