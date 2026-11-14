export const BATCH_STATUS_LABELS = {
  DRAFT: 'Borrador',
  FILES_UPLOADED: 'Borrador',
  VALIDATING: 'Revisando',
  VALIDATED: 'Listo para importar',
  VALIDATION_FAILED: 'Con errores',
  COMMITTING: 'Importando',
  COMMITTED: 'Importado',
  PARTIAL: 'Importada en parte',
  FAILED: 'Falló',
  SEALED: 'Cerrada',
  ROLLING_BACK: 'Deshaciendo',
  ROLLED_BACK: 'Deshecha',
};

export const BATCH_STATUS_TONES = {
  DRAFT: 'neutral',
  FILES_UPLOADED: 'neutral',
  VALIDATING: 'accent',
  VALIDATED: 'accent',
  VALIDATION_FAILED: 'danger',
  COMMITTING: 'accent',
  COMMITTED: 'success',
  PARTIAL: 'warning',
  FAILED: 'danger',
  SEALED: 'neutral',
  ROLLING_BACK: 'accent',
  ROLLED_BACK: 'neutral',
};

export const ROW_STATUS_LABELS = {
  PENDING: 'Pendientes',
  VALID: 'Correctas',
  ERROR: 'Con error',
  UNCHANGED: 'Ya existían',
  CONFLICT: 'En conflicto',
  COMMITTED: 'Importadas',
  FAILED: 'Fallaron',
};

const REVIEW_STATUSES = ['VALIDATED', 'VALIDATION_FAILED'];
const PROGRESS_STATUSES = ['VALIDATING', 'COMMITTING', 'ROLLING_BACK'];
const REPORT_STATUSES = ['COMMITTED', 'PARTIAL', 'FAILED', 'SEALED', 'ROLLED_BACK'];

export function phaseOf(status) {
  if (REVIEW_STATUSES.includes(status)) return 'review';
  if (PROGRESS_STATUSES.includes(status)) return 'progress';
  if (REPORT_STATUSES.includes(status)) return 'report';
  return 'files';
}

export function countOf(counters, status) {
  return (counters ?? []).reduce((sum, block) => sum + (block.rowsByStatus?.[status] ?? 0), 0);
}

export function totalRows(block) {
  return Object.values(block.rowsByStatus ?? {}).reduce((sum, value) => sum + value, 0);
}

export const MAX_IMPORT_FILE_BYTES = 5 * 1024 * 1024;

export function blockProgress(block) {
  const rows = block.rowsByStatus ?? {};
  const total = totalRows(block) - (rows.UNCHANGED ?? 0) - (rows.ERROR ?? 0) - (rows.CONFLICT ?? 0);
  const done = (rows.COMMITTED ?? 0) + (rows.FAILED ?? 0);
  return total <= 0 ? 0 : Math.round((done / total) * 100);
}

export function contentSummary(batch) {
  const files = batch.files?.length ?? 0;
  if (files === 0) return 'Sin archivos';
  const rows = batch.files.reduce((sum, file) => sum + file.rowCount, 0);
  return `${files} ${files === 1 ? 'bloque' : 'bloques'} · ${rows.toLocaleString('es-AR')} filas`;
}

export function formatSize(bytes) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
}

export function toCsv(rows) {
  const escape = (value) => `"${String(value ?? '').replaceAll('"', '""')}"`;
  return rows.map((row) => row.map(escape).join(',')).join('\r\n');
}

export function downloadTextFile(name, text) {
  const url = URL.createObjectURL(new Blob(['﻿', text], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  URL.revokeObjectURL(url);
}
