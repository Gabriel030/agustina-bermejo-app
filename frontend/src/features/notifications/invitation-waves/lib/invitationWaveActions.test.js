import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { RETRY_FAILED_AVAILABLE, SEND_REQUIRED_REASON, orderedSecondary, setRetryFailedAvailableForTests, waveActions } from './invitationWaveActions';
import { buildWave } from '../test/fixtures';

const FULL = { read: true, manage: true, send: true };

function keys(actions) {
  return { primary: actions.primary?.key ?? null, secondary: orderedSecondary(actions).map((entry) => entry.key) };
}

describe('waveActions', () => {
  beforeEach(() => setRetryFailedAvailableForTests(true));
  afterEach(() => setRetryFailedAvailableForTests(RETRY_FAILED_AVAILABLE));

  it('hidesRetryWhileTheEndpointIsNotAvailable', () => {
    setRetryFailedAvailableForTests(false);
    expect(RETRY_FAILED_AVAILABLE).toBe(false);
    expect(keys(waveActions(buildWave('RUNNING'), FULL)).secondary).toEqual(['csv', 'cancel']);
  });

  it.each([
    ['DRAFT', { primary: 'start', secondary: ['csv', 'cancel'] }],
    ['RUNNING', { primary: 'pause', secondary: ['retry', 'csv', 'cancel'] }],
    ['PAUSED', { primary: 'resume', secondary: ['retry', 'csv', 'cancel'] }],
    ['COMPLETED', { primary: null, secondary: ['csv'] }],
    ['CANCELLED', { primary: null, secondary: ['csv'] }],
  ])('%s offers the right actions', (status, expected) => {
    expect(keys(waveActions(buildWave(status), FULL))).toEqual(expected);
  });

  it('offersRetryOnlyWhenSomethingFailed', () => {
    expect(keys(waveActions(buildWave('RUNNING', { failedTotal: 0 }), FULL)).secondary).not.toContain('retry');
    expect(keys(waveActions(buildWave('COMPLETED', { failedTotal: 3 }), FULL)).secondary).toEqual(['retry', 'csv']);
  });

  it('leavesOnlyTheCsvToReaders', () => {
    expect(keys(waveActions(buildWave('RUNNING'), { read: true, manage: false, send: false }))).toEqual({ primary: null, secondary: ['csv'] });
  });

  it('leavesNothingWithoutReadAndManage', () => {
    expect(keys(waveActions(buildWave('RUNNING'), { read: false, manage: false, send: false }))).toEqual({ primary: null, secondary: [] });
  });

  it('disablesTheSensitiveActionsWithoutSendButNotPausing', () => {
    const access = { read: true, manage: true, send: false };
    const running = waveActions(buildWave('RUNNING'), access);
    expect(running.primary).toMatchObject({ key: 'pause', disabled: false, disabledReason: null });
    expect(running.secondary.every((entry) => entry.disabled && entry.disabledReason === SEND_REQUIRED_REASON)).toBe(true);
    expect(waveActions(buildWave('DRAFT'), access).primary).toMatchObject({ key: 'start', disabled: true, disabledReason: SEND_REQUIRED_REASON });
  });

  it('asksForConfirmationOnEverythingButPausing', () => {
    const actions = waveActions(buildWave('RUNNING'), FULL);
    expect(actions.primary.confirm).toBe(false);
    expect(actions.secondary.every((entry) => entry.confirm)).toBe(true);
  });
});
