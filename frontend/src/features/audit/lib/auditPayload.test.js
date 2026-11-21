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
});
