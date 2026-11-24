export const invitationWave = (overrides = {}) => ({
  id: 1,
  name: 'Oleada de noviembre',
  status: 'DRAFT',
  batchSize: 50,
  intervalMinutes: 30,
  dailyCap: 150,
  importBatchId: 7,
  createdByPersonId: 1,
  createdAt: '2026-11-23T12:00:00Z',
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
  excludedTotal: 9,
  activatedTotal: 0,
  importBatchName: 'Alumnos 2026 - primer cuatrimestre',
  invitableCount: 118,
  excludedByReason: { NO_EMAIL: 6, ALREADY_ACTIVE: 0, NOT_ELIGIBLE: 1, IN_OTHER_WAVE: 2, WAVE_CANCELLED: 0, SHARED_EMAIL: 0 },
  nextRunAt: null,
  ...overrides,
});

export const runningInvitationWave = (overrides = {}) =>
  invitationWave({
    id: 2,
    name: 'Oleada en curso',
    status: 'RUNNING',
    startedAt: '2026-11-23T13:00:00Z',
    startedByPersonId: 1,
    eligibleCount: 60,
    queuedTotal: 20,
    sentTotal: 38,
    failedTotal: 2,
    activatedTotal: 5,
    nextRunAt: null,
    ...overrides,
  });

export const pausedInvitationWave = (overrides = {}) =>
  runningInvitationWave({
    id: 3,
    name: 'Oleada pausada',
    status: 'PAUSED',
    pausedAt: '2026-11-23T13:45:00Z',
    pausedByPersonId: 1,
    nextRunAt: null,
    ...overrides,
  });

export const invitationWavePage = (items = [invitationWave()], overrides = {}) => ({
  items,
  page: 0,
  size: 25,
  totalElements: items.length,
  ...overrides,
});

export const emptyInvitationWavePage = () => invitationWavePage([]);

export const invitationRecipient = (overrides = {}) => ({
  personId: 101,
  status: 'SENT',
  exclusionReason: null,
  attempts: 1,
  lastErrorCode: null,
  sentAt: '2026-11-23T13:05:00Z',
  lastAttemptAt: '2026-11-23T13:05:00Z',
  displayName: 'Ana Pérez',
  email: 'ana.perez@example.com',
  ...overrides,
});

export const invitationRecipientsWithPersonalData = () =>
  invitationWavePage(
    [
      invitationRecipient(),
      invitationRecipient({ personId: 102, status: 'FAILED', attempts: 2, lastErrorCode: 'UNKNOWN', sentAt: null, lastAttemptAt: '2026-11-23T13:10:00Z', displayName: 'Bruno Díaz', email: 'bruno.diaz@example.com' }),
      invitationRecipient({ personId: 103, status: 'EXCLUDED', exclusionReason: 'NO_EMAIL', attempts: 0, sentAt: null, lastAttemptAt: null, displayName: 'Carla Gómez', email: null }),
    ],
    { size: 50 },
  );

export const invitationRecipientsWithoutPersonalData = () =>
  invitationWavePage(
    invitationRecipientsWithPersonalData().items.map((recipient) => ({ ...recipient, displayName: null, email: null })),
    { size: 50 },
  );

export const emptyInvitationRecipientPage = () => invitationWavePage([], { size: 50 });

export const invitationWaveSettings = (overrides = {}) => ({
  globalDailyCap: 300,
  maxBatchSize: 100,
  maxRecipients: 5000,
  sendWindow: { start: '09:00', end: '20:00', zone: 'America/Argentina/Buenos_Aires' },
  ...overrides,
});

export const invitationImportBatches = () => [
  { id: 7, name: 'Alumnos 2026 - primer cuatrimestre', committedAt: '2026-11-10T15:00:00', studentsPendingActivation: 127 },
  { id: 8, name: 'Reingresantes', committedAt: '2026-11-18T09:30:00', studentsPendingActivation: 14 },
];

export const activationTokenStatus = (status = 'VALID', overrides = {}) => {
  const institution = { name: 'Instituto Agustina Bermejo', email: 'contacto@instituto.test', phoneNumber: '011 5555-0000' };
  const byStatus = {
    VALID: { status: 'VALID', username: 'ana.perez', institution },
    EXPIRED: { status: 'EXPIRED', username: null, institution },
    INVALID: { status: 'INVALID', username: null, institution: null },
  };
  return { ...byStatus[status], ...overrides };
};

export const invitationWaveReportCsv = () =>
  '﻿personId,status,exclusionReason,lastErrorCode,attempts,sentAt\r\n101,SENT,,,1,2026-11-23T13:05:00Z\r\n';
