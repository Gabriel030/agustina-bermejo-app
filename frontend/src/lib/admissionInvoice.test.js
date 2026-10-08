import { describe, expect, it } from 'vitest';
import { admissionPaymentPhase, daysUntilDate, isAdmissionInvoicePending } from '@/lib/admissionInvoice';

const aprobada = (invoice) => ({ status: 'APPROVED', admissionInvoice: invoice });

describe('admissionInvoice', () => {
  it('distingue pagada, en revisión, vencida y pendiente', () => {
    expect(admissionPaymentPhase(aprobada({ paymentStatus: 'PAID' }))).toBe('PAID');
    expect(admissionPaymentPhase(aprobada({ paymentStatus: 'PENDING', receiptStatus: 'PENDING_REVIEW' }))).toBe('UNDER_REVIEW');
    expect(admissionPaymentPhase(aprobada({ paymentStatus: 'OVERDUE' }))).toBe('OVERDUE');
    expect(admissionPaymentPhase(aprobada({ paymentStatus: 'PENDING', receiptStatus: 'REJECTED' }))).toBe('PENDING');
    expect(admissionPaymentPhase({ status: 'PENDING' })).toBeNull();
    expect(admissionPaymentPhase({ status: 'APPROVED', admissionInvoice: null })).toBeNull();
  });

  it('una matrícula está pendiente mientras la solicitud esté aprobada y la factura no esté paga', () => {
    expect(isAdmissionInvoicePending(aprobada({ paymentStatus: 'PENDING' }))).toBe(true);
    expect(isAdmissionInvoicePending(aprobada({ paymentStatus: 'OVERDUE' }))).toBe(true);
    expect(isAdmissionInvoicePending(aprobada({ paymentStatus: 'PAID' }))).toBe(false);
    expect(isAdmissionInvoicePending({ status: 'REJECTED', admissionInvoice: null })).toBe(false);
  });

  it('cuenta los días hasta el vencimiento por fecha local', () => {
    const hoy = new Date(2026, 9, 8, 15, 30);
    expect(daysUntilDate('2026-10-18', hoy)).toBe(10);
    expect(daysUntilDate('2026-10-08', hoy)).toBe(0);
    expect(daysUntilDate('2026-10-05', hoy)).toBe(-3);
    expect(daysUntilDate(null, hoy)).toBeNull();
  });
});
