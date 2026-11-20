export const BACKUP_ALERT_LABELS = {
  BACKUP_FAILED: 'Falló el último backup',
  BACKUP_STALE: 'Hace demasiado que no hay un backup completo',
  BACKUP_DAMAGED: 'Un backup quedó dañado y no se puede usar',
  RESTORE_TEST_FAILED: 'Falló la prueba de restauración',
  DISK_LOW: 'Queda poco espacio en el disco de backups',
  DISK_CRITICAL: 'El disco de backups está casi lleno',
  ENCRYPTION_DISABLED: 'Los backups se guardan sin cifrar',
  DATA_DIR_EMPTY: 'No se encontraron archivos para respaldar',
};

export const RESTORE_TEST_FAILURE_LABELS = {
  CHECKSUM_MISMATCH: 'La copia restaurada no coincide con la original',
  RESTORE_FAILED: 'No se pudo restaurar la copia',
  COUNTS_MISMATCH: 'Faltan datos en la copia restaurada',
  SCHEMA_VALIDATION_FAILED: 'La versión de la base no coincide con la del código',
};

export function backupAlertLabel(code) {
  return BACKUP_ALERT_LABELS[code] ?? 'Alerta de backups';
}

export function restoreTestFailureLabel(code) {
  return RESTORE_TEST_FAILURE_LABELS[code] ?? 'Falló la prueba de restauración';
}
