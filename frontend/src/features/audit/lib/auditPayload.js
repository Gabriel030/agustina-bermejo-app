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

function parseRawPayload(raw) {
  if (raw == null || raw === '') return {};
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function flattenPayload(value, path = []) {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return Object.entries(value).flatMap(([key, inner]) => flattenPayload(inner, [...path, key]));
  }
  return [[path.join('.'), { value, sensitive: path.some((segment) => SENSITIVE_KEY.test(segment)) }]];
}

function showPayloadLeaf({ value, sensitive }) {
  if (value === undefined || value === null) return '—';
  if (sensitive) return MASK;
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}

export function diffAuditValues(beforeRaw, afterRaw) {
  const before = parseRawPayload(beforeRaw);
  const after = parseRawPayload(afterRaw);
  if (before === null || after === null) return null;
  const beforeLeaves = new Map(flattenPayload(before));
  const afterLeaves = new Map(flattenPayload(after));
  const fields = [...new Set([...beforeLeaves.keys(), ...afterLeaves.keys()])];
  const changes = fields
    .filter((field) => JSON.stringify(beforeLeaves.get(field)?.value) !== JSON.stringify(afterLeaves.get(field)?.value))
    .map((field) => {
      const leafBefore = beforeLeaves.get(field) ?? { value: undefined, sensitive: false };
      const leafAfter = afterLeaves.get(field) ?? { value: undefined, sensitive: false };
      return {
        field: field || 'valor',
        before: showPayloadLeaf(leafBefore),
        after: showPayloadLeaf(leafAfter),
      };
    });
  return { count: changes.length, changes };
}
