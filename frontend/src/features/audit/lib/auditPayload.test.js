import { describe, expect, it } from 'vitest';
import { diffAuditValues, formatAuditValue, MASK } from './auditPayload';

describe('formatAuditValue', () => {
  it('pretty prints json and masks sensitive keys', () => {
    const result = formatAuditValue('{"email":"a@b.c","resetToken":"abc"}');
    expect(result.masked).toBe(true);
    expect(result.text).toContain(MASK);
    expect(result.text).not.toContain('abc"');
  });

  it('shows invalid json as plain text', () => {
    expect(formatAuditValue('{estado: lote pendiente')).toEqual({ text: '{estado: lote pendiente', plain: true, masked: false });
  });

  it('returns no text for null', () => {
    expect(formatAuditValue(null).text).toBeNull();
  });

  it.each(['pwd', 'passwd', 'authorization', 'cbu', 'cuit', 'dni', 'cvv', 'pin', 'card', 'credential', 'credentials', 'privateKey', 'cardNumber', 'api-key', 'hash', 'otp', 'secret', 'token', 'password'])(
    'masks the %s key',
    (key) => {
      const result = formatAuditValue(`{"${key}":"abc123"}`);
      expect(result.masked).toBe(true);
      expect(result.text).not.toContain('abc123');
    },
  );

  it('does not mask a key that only contains a token segment as substring', () => {
    const result = formatAuditValue('{"pinned":true}');
    expect(result.masked).toBe(false);
  });

  it('masks sensitive keys inside an embedded json string', () => {
    const result = formatAuditValue('{"payload":"{\\"pwd\\":\\"abc\\"}"}');
    expect(result.masked).toBe(true);
    expect(result.text).not.toContain('abc');
  });

  it('masks key=value pairs in plain text', () => {
    const result = formatAuditValue('login ok password=abc');
    expect(result.masked).toBe(true);
    expect(result.text).toBe(`login ok password=${MASK}`);
  });
});

describe('diffAuditValues', () => {
  it('lists only the fields whose value changed', () => {
    const diff = diffAuditValues('{"status":"ACTIVE","section":"A","year":2026}', '{"status":"WITHDRAWN","section":"A","year":2026}');
    expect(diff.count).toBe(1);
    expect(diff.changes).toEqual([{ field: 'status', before: 'ACTIVE', after: 'WITHDRAWN' }]);
  });

  it('counts nested and added fields', () => {
    const diff = diffAuditValues('{"contact":{"city":"Rosario"}}', '{"contact":{"city":"Santa Fe"},"phone":"341"}');
    expect(diff.count).toBe(2);
    expect(diff.changes.map((change) => change.field)).toEqual(['contact.city', 'phone']);
    expect(diff.changes[1]).toEqual({ field: 'phone', before: '—', after: '341' });
  });

  it('counts a changed sensitive value but never shows it', () => {
    const diff = diffAuditValues('{"passwordHash":"abc123"}', '{"passwordHash":"def456"}');
    expect(diff.count).toBe(1);
    expect(diff.changes[0]).toEqual({ field: 'passwordHash', before: MASK, after: MASK });
    expect(JSON.stringify(diff)).not.toContain('abc123');
    expect(JSON.stringify(diff)).not.toContain('def456');
  });

  it('returns null when a value is not json', () => {
    expect(diffAuditValues('texto libre', '{"status":"ACTIVE"}')).toBeNull();
  });

  it('reports zero changes for identical payloads', () => {
    expect(diffAuditValues('{"status":"ACTIVE"}', '{"status":"ACTIVE"}')).toEqual({ count: 0, changes: [] });
  });

  it('masks sensitive values inside objects nested in arrays', () => {
    const diff = diffAuditValues('{"users":[{"password":"abc"}]}', '{"users":[{"password":"def"}]}');
    expect(diff.count).toBe(1);
    expect(diff.changes).toEqual([{ field: 'users.0.password', before: MASK, after: MASK }]);
    expect(JSON.stringify(diff)).not.toContain('abc');
    expect(JSON.stringify(diff)).not.toContain('def');
  });

  it('masks sensitive values inside arrays of objects nested in objects', () => {
    const diff = diffAuditValues('{"account":{"tokens":[{"apiKey":"abc"},{"apiKey":"xyz"}]}}', '{"account":{"tokens":[{"apiKey":"def"},{"apiKey":"xyz"}]}}');
    expect(diff.changes).toEqual([{ field: 'account.tokens.0.apiKey', before: MASK, after: MASK }]);
    expect(JSON.stringify(diff)).not.toContain('abc');
    expect(JSON.stringify(diff)).not.toContain('def');
  });

  it('masks every item of a sensitive array', () => {
    const diff = diffAuditValues('{"otps":["111"]}', '{"otps":["222","333"]}');
    expect(diff.changes.map((change) => change.after)).toEqual([MASK, MASK]);
    expect(JSON.stringify(diff)).not.toContain('222');
  });

  it('lists array items that changed by index', () => {
    const diff = diffAuditValues('{"roles":["DIRECTOR","DOCENTE"]}', '{"roles":["DIRECTOR","PRECEPTOR"]}');
    expect(diff.changes).toEqual([{ field: 'roles.1', before: 'DOCENTE', after: 'PRECEPTOR' }]);
  });

  it('treats a json null payload as an empty object', () => {
    expect(diffAuditValues('null', '{"status":"ACTIVE"}')).toEqual({ count: 1, changes: [{ field: 'status', before: '—', after: 'ACTIVE' }] });
    expect(diffAuditValues('null', 'null')).toEqual({ count: 0, changes: [] });
  });

  it('still rejects text that is not json', () => {
    expect(diffAuditValues('null', 'texto libre')).toBeNull();
  });
});
