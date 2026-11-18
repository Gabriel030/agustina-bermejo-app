const SENSITIVE_KEY = /pass|token|secret|clave|hash|otp|code|api[-_]?key/i;
export const MASK = '••••••••';

function maskSensitive(value) {
  if (Array.isArray(value)) return { value: value.map((item) => maskSensitive(item).value), masked: value.some((item) => maskSensitive(item).masked) };
  if (value && typeof value === 'object') {
    let masked = false;
    const entries = Object.entries(value).map(([key, inner]) => {
      if (SENSITIVE_KEY.test(key) && inner != null) {
        masked = true;
        return [key, MASK];
      }
      const result = maskSensitive(inner);
      masked ||= result.masked;
      return [key, result.value];
    });
    return { value: Object.fromEntries(entries), masked };
  }
  return { value, masked: false };
}

export function formatAuditValue(raw) {
  if (raw == null || raw === '') return { text: null, plain: false, masked: false };
  try {
    const result = maskSensitive(JSON.parse(raw));
    return { text: JSON.stringify(result.value, null, 2), plain: false, masked: result.masked };
  } catch {
    const pattern = /((?:pass|token|secret|clave|hash|otp|code|api[-_]?key)\S*\s*[:=]\s*)\S+/gi;
    const masked = pattern.test(raw);
    return { text: masked ? raw.replace(pattern, `$1${MASK}`) : raw, plain: true, masked };
  }
}

export function auditActorName(event) {
  if (event.actorName) return event.actorName;
  return event.actorPersonId ? `Persona #${event.actorPersonId}` : 'Sistema';
}
