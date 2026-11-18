import { describe, expect, it } from 'vitest';
import { formatAuditValue, MASK } from './auditPayload';

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
