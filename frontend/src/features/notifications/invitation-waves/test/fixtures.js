import { invitationRecipient, invitationWave, invitationWavePage, invitationWaveSettings } from '@/test/fixtures/invitationWaves';

export const API = 'http://localhost:8080';

export const SETTINGS = invitationWaveSettings();

export const IMPORT_BATCHES = [
  { id: 11, name: 'Carga inicial 2026', committedAt: '2026-10-01T12:00:00', studentsPendingActivation: 140 },
  { id: 12, name: 'Ingresantes 2027', committedAt: '2026-11-10T12:00:00', studentsPendingActivation: 85 },
  { id: 13, name: 'Lote gigante', committedAt: '2026-11-12T12:00:00', studentsPendingActivation: 9000 },
];

const BASE_WAVE = {
  id: 1,
  name: 'Primer año 2026',
  status: 'DRAFT',
  batchSize: 30,
  intervalMinutes: 60,
  dailyCap: 120,
  importBatchId: 11,
  importBatchName: 'Carga inicial 2026',
  createdByPersonId: 7,
  createdAt: '2026-11-18T12:00:00Z',
  startedAt: null,
  startedByPersonId: null,
  pausedAt: null,
  pausedByPersonId: null,
  resumedAt: null,
  resumedByPersonId: null,
  completedAt: null,
  cancelledAt: null,
  cancelledByPersonId: null,
  eligibleCount: 118,
  queuedTotal: 0,
  sentTotal: 0,
  failedTotal: 0,
  excludedTotal: 22,
  activatedTotal: 0,
  invitableCount: 118,
  excludedByReason: { NO_EMAIL: 8, ALREADY_ACTIVE: 6, NOT_ELIGIBLE: 3, IN_OTHER_WAVE: 5 },
  nextRunAt: null,
};

const BY_STATUS = {
  DRAFT: {},
  RUNNING: {
    eligibleCount: 28,
    queuedTotal: 15,
    sentTotal: 40,
    failedTotal: 6,
    activatedTotal: 29,
    startedAt: '2026-11-22T12:00:00Z',
    nextRunAt: '2026-11-23T17:30:00Z',
  },
  PAUSED: { eligibleCount: 51, sentTotal: 48, failedTotal: 6, activatedTotal: 13, startedAt: '2026-11-22T12:00:00Z', pausedAt: '2026-11-22T18:00:00Z' },
  COMPLETED: { eligibleCount: 0, sentTotal: 61, activatedTotal: 57, excludedTotal: 0, invitableCount: 118, completedAt: '2026-11-22T20:00:00Z', excludedByReason: {} },
  CANCELLED: {
    eligibleCount: 0,
    sentTotal: 30,
    failedTotal: 0,
    activatedTotal: 9,
    excludedTotal: 98,
    invitableCount: 39,
    cancelledAt: '2026-11-23T15:00:00Z',
    excludedByReason: { WAVE_CANCELLED: 76, NO_EMAIL: 8, ALREADY_ACTIVE: 6, NOT_ELIGIBLE: 3, IN_OTHER_WAVE: 5 },
  },
};

export function buildWave(status = 'DRAFT', overrides = {}) {
  return invitationWave({ ...BASE_WAVE, status, ...BY_STATUS[status], ...overrides });
}

export function springPage(items, { page = 0, size = 20, total = items.length } = {}) {
  return invitationWavePage(items, { page, size, totalElements: total });
}

export function buildRecipient(personId, overrides = {}) {
  return invitationRecipient({
    personId,
    status: 'PENDING',
    attempts: 0,
    sentAt: null,
    lastAttemptAt: null,
    displayName: `Apellido${personId}, Nombre${personId}`,
    email: `persona${personId}@ejemplo.edu.ar`,
    ...overrides,
  });
}

export function withoutPersonalData(recipient) {
  return { ...recipient, displayName: null, email: null };
}
