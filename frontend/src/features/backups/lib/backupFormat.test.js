import { describe, expect, it } from 'vitest';
import { RESTORE_TEST_MAX_AGE_DAYS, diskFreePercent, formatBytes, formatHours, restoreTestReminder } from './backupFormat';

const NOW = new Date('2026-11-20T12:00:00Z');
const daysAgo = (days) => new Date(NOW.getTime() - days * 24 * 60 * 60 * 1000).toISOString();

describe('restoreTestReminder', () => {
  it('warnsWhenThereIsNoRestoreTest', () => {
    expect(restoreTestReminder(null, NOW)).toBe('Todavía no se hizo una prueba completa de restauración.');
  });

  it('staysSilentAtThirteenDays', () => {
    expect(restoreTestReminder({ ranAt: daysAgo(13) }, NOW)).toBeNull();
  });

  it('staysSilentAtTheThreshold', () => {
    expect(restoreTestReminder({ ranAt: daysAgo(RESTORE_TEST_MAX_AGE_DAYS) }, NOW)).toBeNull();
  });

  it('warnsAtFifteenDays', () => {
    expect(restoreTestReminder({ ranAt: daysAgo(15) }, NOW)).toBe('Pasaron más de 14 días sin una prueba completa de restauración.');
  });
});

describe('backup formatting', () => {
  it('formatsHoursBytesAndDisk', () => {
    expect(formatHours(2)).toBe('2 h');
    expect(formatHours(null)).toBe('—');
    expect(formatBytes(412 * 1024 ** 2)).toBe('412 MB');
    expect(formatBytes(3 * 1024 ** 3)).toBe('3 GB');
    expect(diskFreePercent({ freeBytes: 62, totalBytes: 100 })).toBe(62);
    expect(diskFreePercent(null)).toBeNull();
  });
});
