const GIB = 1024 ** 3;

const BACKUP_TOTAL_BYTES = 500 * GIB;
const BACKUP_FREE_BYTES = Math.round(BACKUP_TOTAL_BYTES * 0.62);

const PLATFORM_TIERS = [
  {
    tier: 'INTRADAY',
    takenAt: '2026-11-20T08:00:00Z',
    label: null,
    dumpSizeBytes: 412 * 1024 ** 2,
    filesSizeBytes: 3 * GIB,
    verification: 'VERIFIED',
    kept: 4,
    fileNames: ['intraday-2026-11-20T0800.dump.age', 'intraday-2026-11-20T0800-files.tar.age'],
  },
  {
    tier: 'DAILY',
    takenAt: '2026-11-20T03:00:00Z',
    label: null,
    dumpSizeBytes: 410 * 1024 ** 2,
    filesSizeBytes: 3 * GIB,
    verification: 'VERIFIED',
    kept: 7,
    fileNames: ['daily-2026-11-20.dump.age', 'daily-2026-11-20-files.tar.age'],
  },
  {
    tier: 'WEEKLY',
    takenAt: '2026-11-16T03:00:00Z',
    label: null,
    dumpSizeBytes: 405 * 1024 ** 2,
    filesSizeBytes: 3 * GIB,
    verification: 'VERIFIED',
    kept: 4,
    fileNames: ['weekly-2026-W47.dump.age', 'weekly-2026-W47-files.tar.age'],
  },
  {
    tier: 'QUARTERLY',
    takenAt: '2026-10-01T03:00:00Z',
    label: null,
    dumpSizeBytes: 380 * 1024 ** 2,
    filesSizeBytes: 2 * GIB,
    verification: 'VERIFIED',
    kept: 4,
    fileNames: ['quarterly-2026-Q4.dump.age', 'quarterly-2026-Q4-files.tar.age'],
  },
  {
    tier: 'PRE_RELEASE',
    takenAt: '2026-11-18T21:30:00Z',
    label: 'v1.42.0',
    dumpSizeBytes: 408 * 1024 ** 2,
    filesSizeBytes: 3 * GIB,
    verification: 'VERIFIED',
    kept: 3,
    fileNames: ['pre-release-v1.42.0.dump.age', 'pre-release-v1.42.0-files.tar.age'],
  },
];

export function backupStatusForPlatformAdmin(overrides = {}) {
  return {
    state: 'OK',
    summary: {
      lastCompleteBackupAt: '2026-11-20T08:00:00Z',
      hoursSinceLastCompleteBackup: 2,
      potentialLossHours: 2,
      targetRpoHours: 4,
      consecutiveFailures: 0,
    },
    generatedAt: '2026-11-20T10:00:00Z',
    tiers: PLATFORM_TIERS.map((tier) => ({ ...tier, fileNames: [...tier.fileNames] })),
    lastRestoreTest: { ranAt: '2026-11-19T04:00:00Z', result: 'PASSED', failureCode: null },
    disk: { freeBytes: BACKUP_FREE_BYTES, totalBytes: BACKUP_TOTAL_BYTES, path: '/var/backups/agustina' },
    alerts: [],
    ...overrides,
  };
}

export function withoutTechnicalFields(status) {
  return {
    ...status,
    tiers: status.tiers.map((tier) => ({ ...tier, fileNames: null })),
    disk: status.disk ? { ...status.disk, path: null } : null,
  };
}

export function backupStatusForDirector(overrides = {}) {
  return { ...withoutTechnicalFields(backupStatusForPlatformAdmin()), ...overrides };
}

export function attentionBackupStatus(overrides = {}) {
  const base = backupStatusForDirector();
  return {
    ...base,
    state: 'ATTENTION',
    generatedAt: '2026-11-20T12:00:00Z',
    summary: { ...base.summary, lastCompleteBackupAt: '2026-11-20T03:00:00Z', hoursSinceLastCompleteBackup: 9, potentialLossHours: 9, consecutiveFailures: 1 },
    tiers: base.tiers.map((tier) => (tier.tier === 'INTRADAY' ? { ...tier, verification: 'DAMAGED' } : tier)),
    alerts: [
      { at: '2026-11-20T08:05:00Z', level: 'WARNING', code: 'BACKUP_DAMAGED' },
    ],
    ...overrides,
  };
}

export function staleBackupStatus(overrides = {}) {
  const base = backupStatusForDirector();
  return {
    ...base,
    state: 'STALE',
    summary: { ...base.summary, lastCompleteBackupAt: '2026-11-17T03:00:00Z', hoursSinceLastCompleteBackup: 79, potentialLossHours: 79, consecutiveFailures: 12 },
    lastRestoreTest: { ranAt: '2026-11-17T04:00:00Z', result: 'FAILED', failureCode: 'RESTORE_FAILED' },
    disk: { ...base.disk, freeBytes: Math.round(BACKUP_TOTAL_BYTES * 0.03) },
    alerts: [
      { at: '2026-11-18T03:05:00Z', level: 'CRITICAL', code: 'BACKUP_STALE' },
      { at: '2026-11-17T09:05:00Z', level: 'CRITICAL', code: 'DISK_CRITICAL' },
      { at: '2026-11-17T08:05:00Z', level: 'WARNING', code: 'BACKUP_FAILED' },
    ],
    ...overrides,
  };
}

export function notConfiguredBackupStatus(overrides = {}) {
  return {
    state: 'NOT_CONFIGURED',
    summary: {
      lastCompleteBackupAt: null,
      hoursSinceLastCompleteBackup: null,
      potentialLossHours: null,
      targetRpoHours: 4,
      consecutiveFailures: 0,
    },
    generatedAt: null,
    tiers: [],
    lastRestoreTest: null,
    disk: null,
    alerts: [],
    ...overrides,
  };
}
