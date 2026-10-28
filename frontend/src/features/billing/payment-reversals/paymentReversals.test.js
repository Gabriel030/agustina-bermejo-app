import { describe, expect, it } from 'vitest';
import { isHardBlock, menuEntriesOf, successMessage, validateReversalForm } from './paymentReversals';

const allowed = { allowed: true, blockedBy: null };

function payment(overrides) {
  return {
    id: 1,
    status: 'APPROVED',
    methodType: 'BANK_TRANSFER',
    paidOn: '2026-10-03',
    chargeback: null,
    actions: { registerChargeback: allowed, reverseChargeback: allowed, revokeApproval: allowed },
    ...overrides,
  };
}

describe('menuEntriesOf', () => {
  it('lists the chargeback and, for the director, the revocation of an approved transfer', () => {
    expect(menuEntriesOf(payment(), false).map((entry) => entry.kind)).toEqual(['chargeback']);
    expect(menuEntriesOf(payment(), true).map((entry) => entry.kind)).toEqual(['chargeback', 'revocation']);
  });

  it('lists the reversal only while the chargeback has not been reversed', () => {
    const chargeback = { chargebackId: 3, reversedOn: null };
    expect(menuEntriesOf(payment({ status: 'CHARGED_BACK', chargeback }), false).map((entry) => entry.kind)).toEqual(['reversal']);
    expect(menuEntriesOf(payment({ status: 'CHARGED_BACK', chargeback: { ...chargeback, reversedOn: '2026-10-10' } }), false)).toEqual([]);
  });

  it('keeps the pending refund block navigable and disables the other blocks', () => {
    const entries = menuEntriesOf(
      payment({
        actions: {
          registerChargeback: { allowed: false, blockedBy: { code: 'PAYMENT_HAS_PENDING_REFUND', message: 'x' } },
          reverseChargeback: allowed,
          revokeApproval: { allowed: false, blockedBy: { code: 'PAYMENT_ALREADY_REFUNDED', message: 'y' } },
        },
      }),
      true,
    );
    expect(entries.map((entry) => [entry.kind, entry.refundBlocked, entry.disabled])).toEqual([
      ['chargeback', true, false],
      ['revocation', false, true],
    ]);
  });
});

describe('validateReversalForm', () => {
  const form = { date: '2026-10-05', reference: 'CBK-1', reason: '' };

  it('accepts a valid chargeback', () => {
    expect(validateReversalForm('chargeback', form, payment(), '2026-10-27')).toEqual({});
  });

  it('rejects a date before the payment, in the future, or an empty or oversized reference', () => {
    expect(validateReversalForm('chargeback', { ...form, date: '2026-10-01' }, payment(), '2026-10-27').date).toBeDefined();
    expect(validateReversalForm('chargeback', { ...form, date: '2026-11-01' }, payment(), '2026-10-27').date).toBeDefined();
    expect(validateReversalForm('chargeback', { ...form, reference: '  ' }, payment(), '2026-10-27').reference).toBeDefined();
    expect(validateReversalForm('chargeback', { ...form, reference: 'x'.repeat(121) }, payment(), '2026-10-27').reference).toBeDefined();
  });

  it('requires a reason within the limit for the revocation', () => {
    expect(validateReversalForm('revocation', form, payment(), '2026-10-27').reason).toBeDefined();
    expect(validateReversalForm('revocation', { ...form, reason: 'Error' }, payment(), '2026-10-27')).toEqual({});
    expect(validateReversalForm('revocation', { ...form, reason: 'x'.repeat(501) }, payment(), '2026-10-27').reason).toBeDefined();
  });
});

describe('isHardBlock', () => {
  it('treats form field violations as not blocking and any other violation as blocking', () => {
    expect(isHardBlock({ allowed: false, blockedBy: { code: 'CHARGEBACK_REFERENCE_INVALID' } })).toBe(false);
    expect(isHardBlock({ allowed: false, blockedBy: { code: 'PAYMENT_ALREADY_REFUNDED' } })).toBe(true);
    expect(isHardBlock({ allowed: true, blockedBy: null })).toBe(false);
    expect(isHardBlock(undefined)).toBe(false);
  });
});

describe('successMessage', () => {
  it('counts reopened and settled invoices', () => {
    expect(successMessage('revocation', { reopenedInvoiceIds: [1, 2] })).toBe('Aprobación revocada. 2 cuotas reabiertas.');
    expect(successMessage('reversal', { settledInvoiceIds: [1] })).toBe('Contracargo revertido. 1 cuota saldada.');
    expect(successMessage('chargeback', { reference: 'CBK-9', reopenedInvoiceIds: [] })).toBe('Contracargo CBK-9 registrado. 0 cuotas reabiertas.');
  });
});
