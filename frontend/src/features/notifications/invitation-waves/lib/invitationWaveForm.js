import { z } from 'zod';
import { formatDecimal } from '@/lib/formatNumber';

export const MIN_INTERVAL_MINUTES = 1;
export const MAX_INTERVAL_MINUTES = 1440;
export const NAME_MAX_LENGTH = 120;
const DEFAULT_BATCH_SIZE = 30;
const DEFAULT_INTERVAL_MINUTES = 60;
const DEFAULT_DAILY_CAP = 120;

function wholeNumber(value) {
  const text = String(value ?? '').trim();
  return /^\d+$/.test(text) ? Number(text) : null;
}

export function defaultFormValues(settings) {
  return {
    name: '',
    importBatchId: '',
    batchSize: String(Math.min(DEFAULT_BATCH_SIZE, settings?.maxBatchSize ?? DEFAULT_BATCH_SIZE)),
    intervalMinutes: String(DEFAULT_INTERVAL_MINUTES),
    dailyCap: String(Math.min(DEFAULT_DAILY_CAP, settings?.globalDailyCap ?? DEFAULT_DAILY_CAP)),
  };
}

export function buildWaveSchema(settings, batches = []) {
  return z
    .object({
      name: z.string().trim().min(1, 'Ponele un nombre a la oleada.').max(NAME_MAX_LENGTH, `El nombre no puede superar los ${NAME_MAX_LENGTH} caracteres.`),
      importBatchId: z.string().min(1, 'Elegí un lote de importación.'),
      batchSize: z.string(),
      intervalMinutes: z.string(),
      dailyCap: z.string(),
    })
    .superRefine((values, ctx) => {
      const issue = (path, message) => ctx.addIssue({ code: 'custom', path: [path], message });
      const dailyCap = wholeNumber(values.dailyCap);
      const batchSize = wholeNumber(values.batchSize);
      const interval = wholeNumber(values.intervalMinutes);

      if (dailyCap == null || dailyCap < 1) issue('dailyCap', 'Ingresá un tope diario entero mayor a 0.');
      else if (settings && dailyCap > settings.globalDailyCap) {
        issue('dailyCap', `El tope diario no puede superar el máximo del instituto (${settings.globalDailyCap}).`);
      }

      if (batchSize == null || batchSize < 1) issue('batchSize', 'Ingresá un tamaño entero mayor a 0.');
      else if (settings && batchSize > settings.maxBatchSize) {
        issue('batchSize', `La tanda no puede superar el máximo permitido (${settings.maxBatchSize}).`);
      } else if (dailyCap != null && dailyCap >= 1 && batchSize > dailyCap) {
        issue('batchSize', `La tanda no puede superar el tope diario (${dailyCap}).`);
      }

      if (interval == null || interval < MIN_INTERVAL_MINUTES || interval > MAX_INTERVAL_MINUTES) {
        issue('intervalMinutes', `El ritmo tiene que estar entre ${MIN_INTERVAL_MINUTES} y ${MAX_INTERVAL_MINUTES} minutos.`);
      }

      const batch = batches.find((entry) => String(entry.id) === values.importBatchId);
      if (batch && settings && batch.studentsPendingActivation > settings.maxRecipients) {
        issue('importBatchId', `El lote tiene más personas de las que admite una oleada (máximo ${formatDecimal(settings.maxRecipients)}). Elegí un lote más chico.`);
      }
    });
}

export function toCreatePayload(values) {
  return {
    name: values.name.trim(),
    importBatchId: Number(values.importBatchId),
    batchSize: Number(values.batchSize),
    intervalMinutes: Number(values.intervalMinutes),
    dailyCap: Number(values.dailyCap),
  };
}
