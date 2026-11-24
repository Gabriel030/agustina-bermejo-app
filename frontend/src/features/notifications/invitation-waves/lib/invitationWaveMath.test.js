import { describe, expect, it } from 'vitest';
import {
  estimateSchedule,
  formatInstant,
  formatInstantDate,
  formatInstantTime,
  invitedCount,
  progressPercent,
  reportFilename,
  windowLabel,
  windowMinutes,
} from './invitationWaveMath';

const WINDOW = { start: '09:00', end: '20:00', zone: 'America/Argentina/Buenos_Aires' };

describe('invitationWaveMath', () => {
  it('countsInvitedAsSentPlusActivated', () => {
    expect(invitedCount({ sentTotal: 40, activatedTotal: 29 })).toBe(69);
  });

  it('computesTheProgressOverTheInvitableCountAndNeverOverflows', () => {
    expect(progressPercent({ sentTotal: 40, activatedTotal: 29, invitableCount: 118 })).toBe(58);
    expect(progressPercent({ sentTotal: 5, activatedTotal: 0, invitableCount: 0 })).toBe(0);
    expect(progressPercent({ sentTotal: 200, activatedTotal: 0, invitableCount: 100 })).toBe(100);
  });

  it('readsTheSendWindowFromTheSettings', () => {
    expect(windowMinutes(WINDOW)).toBe(660);
    expect(windowMinutes({ start: '20:00', end: '09:00' })).toBeNull();
    expect(windowMinutes(undefined)).toBeNull();
    expect(windowLabel({ ...WINDOW, start: '09:00:00', end: '20:00:00' })).toBe('09:00 a 20:00');
    expect(windowLabel(undefined)).toBe('');
  });

  it('estimatesBatchesAndDaysLimitedByTheDailyCap', () => {
    expect(estimateSchedule({ recipients: 118, batchSize: 30, intervalMinutes: 60, dailyCap: 120, sendWindow: WINDOW })).toEqual({ batches: 4, days: 1 });
    expect(estimateSchedule({ recipients: 118, batchSize: 10, intervalMinutes: 60, dailyCap: 40, sendWindow: WINDOW })).toEqual({ batches: 12, days: 3 });
  });

  it('estimatesMoreDaysWhenTheSendWindowFitsFewBatchesPerDay', () => {
    expect(estimateSchedule({ recipients: 100, batchSize: 10, intervalMinutes: 330, dailyCap: 300, sendWindow: WINDOW })).toEqual({ batches: 10, days: 4 });
  });

  it('ignoresTheWindowWhenItIsNotAvailable', () => {
    expect(estimateSchedule({ recipients: 100, batchSize: 10, intervalMinutes: 60, dailyCap: 50 })).toEqual({ batches: 10, days: 2 });
  });

  it('returnsZeroForEmptyOrInvalidInputs', () => {
    expect(estimateSchedule({ recipients: 0, batchSize: 30, intervalMinutes: 60, dailyCap: 120, sendWindow: WINDOW })).toEqual({ batches: 0, days: 0 });
    expect(estimateSchedule({ recipients: 10, batchSize: 'x', intervalMinutes: 60, dailyCap: 120, sendWindow: WINDOW })).toEqual({ batches: 0, days: 0 });
  });

  it('formatsInstantsInTheInstituteZoneNotInUtc', () => {
    expect(formatInstant('2026-11-23T02:30:00Z', WINDOW.zone)).toBe('22/11/2026 23:30');
    expect(formatInstantDate('2026-11-23T17:30:00Z', WINDOW.zone)).toBe('23/11/2026');
    expect(formatInstantTime('2026-11-23T17:30:00Z', WINDOW.zone)).toBe('14:30');
    expect(formatInstant(null)).toBe('');
    expect(formatInstant('not a date')).toBe('');
  });

  it('buildsTheDocumentedReportFilename', () => {
    expect(reportFilename(7, new Date(2026, 10, 3))).toBe('invitaciones-oleada-7-20261103.csv');
  });
});
