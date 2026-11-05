import { describe, expect, it } from 'vitest';
import { creditNoteFixture, invoiceFixture, money, pendingAuthorizationFixture } from '@/test/fixtures/invoices';
import {
  creditNoteAuthorshipOf,
  creditNoteDiffRowsOf,
  creditNoteDraftOf,
  creditNoteErrorOf,
  invoiceActionsOf,
  isAuthorizationAck,
  studentEffectOf,
} from './creditNotes';

function actionsFor(invoice, others = []) {
  return invoiceActionsOf(invoice, { invoices: [invoice, ...others] });
}

describe('invoiceActionsOf', () => {
  it('offers both actions on an overdue installment without payments', () => {
    const actions = actionsFor(invoiceFixture({ id: 1, paymentStatus: 'OVERDUE' }));
    expect(actions.canCredit).toBe(true);
    expect(actions.canCancel).toBe(true);
  });

  it('offers only the credit note on a paid installment and explains why it is not cancelled', () => {
    const actions = actionsFor(invoiceFixture({ id: 1, paymentStatus: 'PAID', paid: '48000.00' }));
    expect(actions.canCredit).toBe(true);
    expect(actions.canCancel).toBe(false);
    expect(actions.note).toMatch(/nota de crédito/);
  });

  it('does not cancel an installment with a partial payment', () => {
    const actions = actionsFor(invoiceFixture({ id: 1, paymentStatus: 'PENDING', paid: '10000.00' }));
    expect(actions.canCredit).toBe(true);
    expect(actions.canCancel).toBe(false);
  });

  it('only annuls a late fee and never offers a credit note on it', () => {
    const actions = actionsFor(invoiceFixture({ id: 1, invoiceType: 'LATE_FEE', paymentStatus: 'OVERDUE' }));
    expect(actions.lateFee).toBe(true);
    expect(actions.canCancel).toBe(true);
    expect(actions.canCredit).toBe(false);
  });

  it('disables both actions with a reason while a receipt is under review', () => {
    const actions = actionsFor(invoiceFixture({ id: 1, paymentStatus: 'PAYMENT_UNDER_REVIEW' }));
    expect(actions.canCredit).toBe(false);
    expect(actions.canCancel).toBe(false);
    expect(actions.tip).toMatch(/comprobante/);
  });

  it('disables both actions when another installment already has a pending request', () => {
    const other = invoiceFixture({ id: 2, pendingAuthorizations: [pendingAuthorizationFixture()] });
    const actions = actionsFor(invoiceFixture({ id: 1 }), [other]);
    expect(actions.canCredit).toBe(false);
    expect(actions.canCancel).toBe(false);
    expect(actions.tip).toMatch(/pedido pendiente/);
  });

  it('exposes the pending request of the installment itself and offers nothing else', () => {
    const invoice = invoiceFixture({ id: 1, pendingAuthorizations: [pendingAuthorizationFixture()] });
    const actions = actionsFor(invoice);
    expect(actions.pending.id).toBe(90);
    expect(actions.canCancel).toBe(false);
  });

  it('offers nothing on an installment already without effect', () => {
    const actions = actionsFor(invoiceFixture({ id: 1, paymentStatus: 'CANCELLED' }));
    expect(actions.canCredit).toBe(false);
    expect(actions.canCancel).toBe(false);
    expect(actions.note).toMatch(/sin efecto/);
  });

  it('blocks the credit note when every line is already credited', () => {
    const actions = actionsFor(invoiceFixture({ id: 1, creditable: '0.00' }));
    expect(actions.canCredit).toBe(false);
    expect(actions.creditTip).toMatch(/acreditó/);
  });
});

describe('creditNoteDraftOf', () => {
  const invoice = invoiceFixture({ id: 1, total: '48000.00', creditable: '48000.00' });

  function draftOf(amount, justification = 'Error') {
    return creditNoteDraftOf(invoice, { justification, amounts: { 1: amount } });
  }

  it('blocks an empty draft and asks for an amount', () => {
    expect(draftOf('').blockReason).toBe('Ingresá al menos un importe.');
  });

  it('reads es-AR amounts and builds the exact payload', () => {
    const draft = draftOf('5.000,50');
    expect(draft.blocked).toBe(false);
    expect(draft.payload).toEqual({
      reason: 'BILLING_ERROR',
      justification: 'Error',
      lines: [{ lineNumber: 1, amount: money('5000.50') }],
    });
  });

  it('flags a line above the creditable maximum', () => {
    const draft = creditNoteDraftOf(invoiceFixture({ id: 1, creditable: '3000.00' }), { justification: 'x', amounts: { 1: '3500' } });
    expect(draft.lines[0].error).toMatch(/Supera el máximo/);
    expect(draft.blocked).toBe(true);
  });

  it('blocks a note that equals the whole debt and points to the cancellation', () => {
    const draft = draftOf('48000');
    expect(draft.equalsDebt).toBe(true);
    expect(draft.blockReason).toMatch(/Cancelar cuota/);
  });

  it('requires the justification', () => {
    expect(draftOf('1000', '   ').blockReason).toBe('Falta la justificación.');
  });

  it('turns a paid installment into free credit instead of reducing debt', () => {
    const paid = invoiceFixture({ id: 1, paymentStatus: 'PAID', paid: '48000.00' });
    const draft = creditNoteDraftOf(paid, { justification: 'x', amounts: { 1: '6000' } });
    expect(draft.freeCents).toBe(600000);
    expect(draft.offsetCents).toBe(0);
    const rows = creditNoteDiffRowsOf(draft, { unallocatedCredit: money('1000.00') });
    expect(rows).toEqual([
      { key: 'credit', label: 'Crédito a favor del alumno', today: money('1000.00'), next: money('7000.00'), diff: money('6000.00') },
    ]);
  });

  it('shows the debt before, after and the difference for an unpaid installment', () => {
    const rows = creditNoteDiffRowsOf(draftOf('5000'), null);
    expect(rows[0]).toMatchObject({ today: money('48000.00'), next: money('43000.00'), diff: money('-5000.00') });
  });
});

describe('response helpers', () => {
  it('tells an authorization acknowledgement from an issued note', () => {
    expect(isAuthorizationAck({ authorizationRequestId: 5 })).toBe(true);
    expect(isAuthorizationAck(creditNoteFixture())).toBe(false);
  });

  it('maps each blocking code to its own message', () => {
    expect(creditNoteErrorOf({ code: 'LATE_FEE_UNDER_PAYMENT_REVIEW' }).title).toMatch(/recargo/);
    expect(creditNoteErrorOf({ code: 'CREDIT_NOTE_EXCEEDS_CREDITABLE_AMOUNT' }).title).toMatch(/supera/);
    expect(creditNoteErrorOf({ status: 403 }).title).toMatch(/permiso/);
  });

  it('describes the effect for the student in plain language', () => {
    expect(studentEffectOf(creditNoteFixture()).label).toBe('A tu favor');
    expect(studentEffectOf(creditNoteFixture({ freeCreditAmount: money('0.00'), reason: 'CANCELLATION' })).label).toBe('Sin saldo a pagar');
    expect(studentEffectOf(creditNoteFixture({ freeCreditAmount: money('0.00') })).label).toBe('Descontado de la cuota');
  });

  it('shows who requested and who authorized, with a fallback when nobody had to', () => {
    expect(creditNoteAuthorshipOf(creditNoteFixture())).toEqual({ requestedBy: 'Marcela Ibarra', authorizedBy: 'Rodolfo Fernández · 02/09/2026' });
    expect(creditNoteAuthorshipOf(creditNoteFixture({ authorizedByName: null, authorizationRequestId: null, requestedByName: null }))).toEqual({
      requestedBy: 'Sistema',
      authorizedBy: 'No requirió autorización',
    });
  });
});
