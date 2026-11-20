import { describe, expect, it } from 'vitest';
import { loadContract } from '@/test/contract/loadContract';
import {
  BACKUP_ALERT_LABELS,
  RESTORE_TEST_FAILURE_LABELS,
  backupAlertLabel,
  restoreTestFailureLabel,
} from './backupLabels';

function contractEnum(schemaName, property) {
  const node = loadContract().components.schemas[schemaName].properties[property];
  const values = node.enum ?? node.oneOf?.find((option) => option.enum)?.enum ?? node.anyOf?.find((option) => option.enum)?.enum;
  return values.filter((value) => value !== null);
}

describe('backupLabels', () => {
  it('everyAlertCodeInTheContractHasALabel', () => {
    expect(Object.keys(BACKUP_ALERT_LABELS).sort()).toEqual(contractEnum('BackupAlertResponse', 'code').sort());
  });

  it('everyRestoreFailureCodeInTheContractHasALabel', () => {
    expect(Object.keys(RESTORE_TEST_FAILURE_LABELS).sort()).toEqual(contractEnum('RestoreTestResponse', 'failureCode').sort());
  });

  it('knownCodesShowSpanishLabels', () => {
    expect(backupAlertLabel('ENCRYPTION_DISABLED')).toBe('Los backups se guardan sin cifrar');
    expect(restoreTestFailureLabel('COUNTS_MISMATCH')).toBe('Faltan datos en la copia restaurada');
  });

  it('unknownCodesFallBackToAGenericLabel', () => {
    expect(backupAlertLabel('SOMETHING_NEW')).toBe('Alerta de backups');
    expect(restoreTestFailureLabel('SOMETHING_NEW')).toBe('Falló la prueba de restauración');
  });
});
