import { describe, expect, it } from 'vitest';
import { auditActionLabel, auditActionTone } from './auditLabels';

describe('auditLabels', () => {
  it('unknown action shows raw code', () => {
    expect(auditActionLabel('LEGACY_SYNC_FIXED')).toBe('LEGACY_SYNC_FIXED');
    expect(auditActionTone('LEGACY_SYNC_FIXED')).toBe('neutral');
  });

  it('known action shows spanish label', () => {
    expect(auditActionLabel('STAFF_WITHDRAWN')).toBe('Baja de personal');
  });
});
