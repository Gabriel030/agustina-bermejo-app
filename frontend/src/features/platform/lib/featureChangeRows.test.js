import { describe, expect, it } from 'vitest';
import { changeRows, isSameState, toChangeRequest } from './featureChangeRows';

describe('featureChangeRows', () => {
  it('omitsTheModeForFeaturesWithoutOne', () => {
    expect(toChangeRequest('ANALYTICS', { enabled: false }, '  motivo  ')).toEqual({ enabled: false, reason: 'motivo' });
  });

  it('sendsTheModeAndTheMatchingEnabledFlagForBilling', () => {
    expect(toChangeRequest('BILLING', { mode: 'MIRROR' }, 'motivo')).toEqual({ enabled: true, mode: 'MIRROR', reason: 'motivo' });
    expect(toChangeRequest('BILLING', { mode: 'OFF' }, 'motivo')).toEqual({ enabled: false, mode: 'OFF', reason: 'motivo' });
  });

  it('listsOnlyTheBillingEffectsThatChange', () => {
    const rows = changeRows('BILLING', { mode: 'OFF' }, { mode: 'MIRROR' });
    expect(rows.map((row) => row.label)).toEqual(['Facturación', 'Calcula y emite las cuotas']);
    expect(rows[1]).toMatchObject({ today: 'No', next: 'Sí', diff: 'Se activa' });
  });

  it('comparesTheComparedFieldPerFeatureKind', () => {
    expect(isSameState('BILLING', { mode: 'FULL', enabled: true }, { mode: 'FULL' })).toBe(true);
    expect(isSameState('ANALYTICS', { enabled: true }, { enabled: false })).toBe(false);
  });
});
