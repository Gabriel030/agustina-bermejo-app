import { describe, expect, it } from 'vitest';
import { differenceLabel, draftErrorMessage, groupKey, pruneSelection, sharedInvoicesWarning, summarizeDraftSelection } from './draftSelection';

function group(overrides) {
  return {
    name: 'Enfermería',
    enrollmentType: 'PROGRAM',
    feeType: 'MONTHLY_INSTALLMENT',
    draftCount: 2,
    draftTotal: { amount: '1000.00', currency: 'ARS' },
    previousPeriodIssuedTotal: { amount: '900.00', currency: 'ARS' },
    difference: { amount: '100.00', currency: 'ARS' },
    differencePercentage: 11.1,
    invoiceIds: [1, 2],
    ...overrides,
  };
}

const PROGRAM = group();
const COURSE = group({ name: 'Primeros Auxilios', enrollmentType: 'COURSE', draftTotal: { amount: '250.50', currency: 'ARS' }, invoiceIds: [2, 3] });
const OTHER = group({ name: 'Radiología', draftTotal: { amount: '500.00', currency: 'ARS' }, invoiceIds: [4] });

describe('summarizeDraftSelection', () => {
  it('deduplicates an invoice that appears in both selected groups and counts it once', () => {
    const summary = summarizeDraftSelection([PROGRAM, COURSE, OTHER], new Set([groupKey(PROGRAM), groupKey(COURSE)]));

    expect(summary.invoiceIds).toEqual([1, 2, 3]);
    expect(summary.count).toBe(3);
    expect(summary.sharedCount).toBe(1);
    expect(summary.hasOutsideGroups).toBe(false);
    expect(summary.total).toEqual({ amount: '1250.50', currency: 'ARS' });
  });

  it('flags the groups outside the selection that hold lines of a selected invoice', () => {
    const summary = summarizeDraftSelection([PROGRAM, COURSE, OTHER], new Set([groupKey(PROGRAM)]));

    expect(summary.invoiceIds).toEqual([1, 2]);
    expect(summary.sharedCount).toBe(1);
    expect(summary.hasOutsideGroups).toBe(true);
    expect(summary.outsideGroupNames).toEqual(['Primeros Auxilios']);
  });

  it('reports no shared invoices when groups are disjoint', () => {
    const summary = summarizeDraftSelection([PROGRAM, OTHER], new Set([groupKey(PROGRAM), groupKey(OTHER)]));

    expect(summary.count).toBe(3);
    expect(summary.sharedCount).toBe(0);
    expect(sharedInvoicesWarning(summary)).toBeNull();
  });

  it('returns an empty summary without a selection', () => {
    const summary = summarizeDraftSelection([PROGRAM], new Set());

    expect(summary.count).toBe(0);
    expect(summary.total.amount).toBe('0.00');
  });
});

describe('sharedInvoicesWarning', () => {
  it('names the groups that hold the remaining lines', () => {
    const summary = summarizeDraftSelection([PROGRAM, COURSE], new Set([groupKey(PROGRAM)]));

    expect(sharedInvoicesWarning(summary)).toBe('Una factura abarca más de un grupo: se procesa completa, con todas sus líneas. Incluye líneas de: Primeros Auxilios.');
  });
});

describe('pruneSelection', () => {
  it('drops the groups that no longer exist after a refresh', () => {
    const pruned = pruneSelection([OTHER], new Set([groupKey(PROGRAM), groupKey(OTHER)]));

    expect([...pruned]).toEqual([groupKey(OTHER)]);
  });
});

describe('differenceLabel', () => {
  it('marks groups without a previous period as new', () => {
    expect(differenceLabel(group({ previousPeriodIssuedTotal: { amount: '0.00', currency: 'ARS' } })).text).toBe('Nuevo este mes');
  });

  it('formats raises and drops with a sign and a decimal comma', () => {
    expect(differenceLabel(group({ differencePercentage: 8.3 }))).toEqual({ text: '+8,3%', tone: 'warning' });
    expect(differenceLabel(group({ differencePercentage: -2 }))).toEqual({ text: '−2,0%', tone: 'success' });
    expect(differenceLabel(group({ differencePercentage: 0 })).text).toBe('Igual');
  });
});

describe('draftErrorMessage', () => {
  it('translates the known codes and falls back otherwise', () => {
    expect(draftErrorMessage({ code: 'DRAFT_NOT_PENDING' }, 'x')).toContain('ya no estaban pendientes');
    expect(draftErrorMessage({ code: 'OTHER' }, 'fallback')).toBe('fallback');
  });
});
