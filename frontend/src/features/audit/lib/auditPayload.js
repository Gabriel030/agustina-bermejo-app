const SENSITIVE_TOKENS = new Set([
  'password', 'passwd', 'pwd', 'token', 'secret', 'authorization', 'cbu', 'cuit', 'dni', 'cvv', 'otp', 'pin', 'card',
  'hash', 'credential', 'credentials', 'apikey', 'privatekey', 'cardnumber', 'clave',
]);
const CODE_QUALIFIERS = new Set([
  'verification', 'reset', 'confirmation', 'security', 'sms', 'auth', 'access', 'recovery', 'otp', 'twofactor', '2fa',
]);
const LEGACY_SENSITIVE_KEY = /pass|token|secret|clave|hash|otp|code|api[-_]?key/i;
const KEY_SEGMENT_SEPARATOR = /[^A-Za-z0-9]+/;
const CAMEL_CASE_BOUNDARY = /(?<=[a-z0-9])(?=[A-Z])/;
const KEY_BEFORE_SEPARATOR = /([A-Za-z][A-Za-z0-9_-]*)("?\s*[:=]\s*)(\S+)/g;
export const MASK = '••••••••';

function keyTokens(key) {
  return key
    .split(KEY_SEGMENT_SEPARATOR)
    .flatMap((part) => part.split(CAMEL_CASE_BOUNDARY))
    .filter(Boolean)
    .map((token) => token.toLowerCase());
}

function isSensitiveKey(key) {
  if (LEGACY_SENSITIVE_KEY.test(key)) return true;
  const tokens = keyTokens(key);
  if (SENSITIVE_TOKENS.has(tokens.join('')) || tokens.some((token) => SENSITIVE_TOKENS.has(token))) return true;
  if (tokens.some((token, index) => index + 1 < tokens.length && SENSITIVE_TOKENS.has(token + tokens[index + 1]))) return true;
  if (tokens.includes('code')) return tokens.length === 1 || tokens.some((token) => CODE_QUALIFIERS.has(token));
  return false;
}

function parseEmbeddedJson(text) {
  if (!/^\s*[{[]/.test(text)) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

function maskPlainText(text) {
  let masked = false;
  const value = text.replace(KEY_BEFORE_SEPARATOR, (match, key, separator) => {
    if (!isSensitiveKey(key)) return match;
    masked = true;
    return `${key}${separator}${MASK}`;
  });
  return { value, masked };
}

function maskString(text) {
  const embedded = parseEmbeddedJson(text);
  if (embedded === undefined) return maskPlainText(text);
  const result = maskSensitive(embedded);
  return result.masked ? { value: JSON.stringify(result.value), masked: true } : { value: text, masked: false };
}

function maskSensitive(value) {
  if (typeof value === 'string') return maskString(value);
  if (Array.isArray(value)) {
    const items = value.map(maskSensitive);
    return { value: items.map((item) => item.value), masked: items.some((item) => item.masked) };
  }
  if (value && typeof value === 'object') {
    let masked = false;
    const entries = Object.entries(value).map(([key, inner]) => {
      if (isSensitiveKey(key) && inner != null) {
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
    const result = maskPlainText(raw);
    return { text: result.value, plain: true, masked: result.masked };
  }
}

export function auditActorName(event) {
  if (event.actorName) return event.actorName;
  return event.actorPersonId ? `Persona #${event.actorPersonId}` : 'Sistema';
}

const INVALID_PAYLOAD = Symbol('invalidPayload');

function parseRawPayload(raw) {
  if (raw == null || raw === '') return {};
  try {
    const parsed = JSON.parse(raw);
    return parsed === null ? {} : parsed;
  } catch {
    return INVALID_PAYLOAD;
  }
}

const isSensitivePath = (path) => path.some((segment) => isSensitiveKey(segment));

function flattenPayload(value, path = []) {
  if (value && typeof value === 'object') {
    const entries = Array.isArray(value) ? value.map((inner, index) => [String(index), inner]) : Object.entries(value);
    return entries.flatMap(([key, inner]) => flattenPayload(inner, [...path, key]));
  }
  return [[path.join('.'), { value, sensitive: isSensitivePath(path) }]];
}

function showPayloadLeaf({ value, sensitive }) {
  if (value === undefined || value === null) return '—';
  if (sensitive) return MASK;
  return typeof value === 'object' ? JSON.stringify(maskSensitive(value).value) : String(value);
}

export function diffAuditValues(beforeRaw, afterRaw) {
  const before = parseRawPayload(beforeRaw);
  const after = parseRawPayload(afterRaw);
  if (before === INVALID_PAYLOAD || after === INVALID_PAYLOAD) return null;
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
