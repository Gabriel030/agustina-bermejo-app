import { describe, expect, it } from 'vitest';
import { unexpectedDeviations } from './render';

describe('unexpectedDeviations', () => {
  it('swallowsOnlyTheMissingRetryFailedEndpoint', () => {
    expect(unexpectedDeviations([{ key: 'POST /invitation-waves/{id}/retry-failed', violations: ['operation not found in the contract'] }])).toEqual([]);
  });

  it('failsOnAnotherRouteWithTheSameStatusZeroViolation', () => {
    const deviation = { key: 'POST /invitation-waves/{id}/pause', violations: ['status 0 not documented'] };
    expect(unexpectedDeviations([deviation])).toEqual([deviation]);
  });

  it('failsOnAnotherRouteWithAnyOtherViolation', () => {
    const deviation = { key: 'POST /invitation-waves/{id}/start', violations: ['operation not found in the contract'] };
    expect(unexpectedDeviations([deviation])).toEqual([deviation]);
  });

  it('keepsTheRoutesThatMerelyContainRetryFailedInTheMiddle', () => {
    const deviation = { key: 'POST /invitation-waves/retry-failed/other', violations: ['status 0 not documented'] };
    expect(unexpectedDeviations([deviation])).toEqual([deviation]);
  });
});
