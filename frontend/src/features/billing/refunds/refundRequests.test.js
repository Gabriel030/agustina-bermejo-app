import { describe, expect, it } from 'vitest';
import { groupRefundsByRequest, summarizeRequestStatus } from './refundRequests';

function part(overrides) {
  return {
    id: 1,
    refundRequestId: 10,
    status: 'APPROVED',
    requestedOn: '2026-10-01',
    amount: { amount: '100.10', currency: 'ARS' },
    ...overrides,
  };
}

describe('refundRequests', () => {
  it('groups parts by request and sums their amounts without float drift', () => {
    const groups = groupRefundsByRequest([
      part({ id: 1, amount: { amount: '0.10', currency: 'ARS' } }),
      part({ id: 2, amount: { amount: '0.20', currency: 'ARS' } }),
      part({ id: 3, refundRequestId: 11 }),
    ]);

    expect(groups).toHaveLength(2);
    expect(groups[0].total).toEqual({ amount: '0.30', currency: 'ARS' });
    expect(groups[0].parts).toHaveLength(2);
  });

  it('keeps the single status when every part shares it', () => {
    expect(summarizeRequestStatus([part({ status: 'COMPLETED' }), part({ status: 'COMPLETED' })]).label).toBe('Hecho');
  });

  it('reports progress while some parts are still open', () => {
    const status = summarizeRequestStatus([part({ status: 'COMPLETED' }), part({ status: 'APPROVED' })]);

    expect(status).toMatchObject({ key: 'PROGRESS', label: 'En curso · 1 de 2 hechas' });
  });

  it('reports completed with failures when nothing is open anymore', () => {
    expect(summarizeRequestStatus([part({ status: 'COMPLETED' }), part({ status: 'FAILED' })]).key).toBe('PARTIAL');
  });
});
