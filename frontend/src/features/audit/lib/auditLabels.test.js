import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { AUDIT_ACTION_CODES, auditActionLabel, auditActionTone } from './auditLabels';

const AUDIT_ACTION_JAVA_FILE = join(import.meta.dirname, '..', '..', '..', '..', '..', 'backend', 'src', 'main', 'java', 'com', 'red_escuela', 'backend', 'shared', 'audit', 'AuditAction.java');

function backendAuditActions() {
  const source = readFileSync(AUDIT_ACTION_JAVA_FILE, 'utf8');
  const body = source.slice(source.indexOf('enum AuditAction {') + 'enum AuditAction {'.length, source.indexOf('}', source.indexOf('enum AuditAction {')));
  return body
    .split(',')
    .map((constant) => constant.trim())
    .filter((constant) => /^[A-Z][A-Z0-9_]*$/.test(constant));
}

describe('auditLabels', () => {
  it('unknown action shows raw code', () => {
    expect(auditActionLabel('LEGACY_SYNC_FIXED')).toBe('LEGACY_SYNC_FIXED');
    expect(auditActionTone('LEGACY_SYNC_FIXED')).toBe('neutral');
  });

  it('known action shows spanish label', () => {
    expect(auditActionLabel('STAFF_WITHDRAWN')).toBe('Baja de personal');
  });

  it('every backend AuditAction has a spanish label', () => {
    const actions = backendAuditActions();
    expect(actions.length).toBeGreaterThan(0);
    const unlabeled = actions.filter((code) => !AUDIT_ACTION_CODES.includes(code) || auditActionLabel(code) === code);
    expect(unlabeled).toEqual([]);
  });
});
