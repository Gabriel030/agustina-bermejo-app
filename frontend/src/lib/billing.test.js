import { describe, expect, it } from 'vitest';
import {
  ADJUSTMENT_TYPE_LABELS,
  INVOICE_DOCUMENT_STATUS_LABELS,
  discountLabel,
  formatPaymentMoment,
  hasInvoiceDetail,
  invoiceDisplayStatus,
  invoiceStatusInfo,
  isInvoicePayable,
} from '@/lib/billing';

describe('ADJUSTMENT_TYPE_LABELS', () => {
  it.each([
    ['SCHOLARSHIP', 'Beca'],
    ['PROMOTION', 'Promoción'],
    ['ANNUAL_UPFRONT', 'Pago anual'],
    ['COHORT_ENTRY', 'Ingreso de cohorte'],
    ['INDIVIDUAL', 'Descuento individual'],
  ])('traduce el tipo de descuento %s', (type, label) => {
    expect(ADJUSTMENT_TYPE_LABELS[type]).toBe(label);
  });
});

describe('estado de la factura', () => {
  const issued = { documentStatus: 'ISSUED', paymentStatus: 'PENDING', receipts: [] };

  it.each([
    ['PENDING', 'Pendiente'],
    ['PAYMENT_UNDER_REVIEW', 'En revisión'],
    ['OVERDUE', 'Vencida'],
    ['PAID', 'Pagada'],
    ['CANCELLED', 'Cuota sin efecto'],
  ])('traduce el estado de cobro %s', (status, label) => {
    expect(invoiceStatusInfo(status).label).toBe(label);
  });

  it.each([
    ['DRAFT', 'Borrador'],
    ['AWAITING_PAYMENT', 'A la espera del pago'],
    ['ISSUED', 'Emitida'],
    ['DISCARDED', 'Factura anulada'],
  ])('traduce el estado del documento %s', (status, label) => {
    expect(INVOICE_DOCUMENT_STATUS_LABELS[status]).toBe(label);
  });

  it('solo permite pagar una factura emitida pendiente o vencida', () => {
    expect(isInvoicePayable(issued)).toBe(true);
    expect(isInvoicePayable({ ...issued, paymentStatus: 'OVERDUE' })).toBe(true);
    expect(isInvoicePayable({ ...issued, paymentStatus: 'PAID' })).toBe(false);
    expect(isInvoicePayable({ ...issued, paymentStatus: 'PAYMENT_UNDER_REVIEW' })).toBe(false);
    expect(isInvoicePayable({ ...issued, documentStatus: 'DISCARDED', paymentStatus: 'CANCELLED' })).toBe(false);
  });

  it('muestra en revisión una factura pendiente con comprobante en revisión', () => {
    expect(invoiceDisplayStatus({ ...issued, receipts: [{ status: 'PENDING_REVIEW' }] })).toBe('PAYMENT_UNDER_REVIEW');
    expect(invoiceDisplayStatus(issued, { status: 'PENDING_REVIEW' })).toBe('PAYMENT_UNDER_REVIEW');
    expect(invoiceDisplayStatus(issued, { status: 'REJECTED' })).toBe('PENDING');
  });
});

describe('descuentos del renglón', () => {
  it('arma la etiqueta con el avance de cuotas', () => {
    expect(discountLabel({ discountName: 'Beca 50 %', discountSource: 'SCHOLARSHIP', installmentNumber: 1, installmentsTotal: 3 })).toBe('Beca 50 % (cuota 1 de 3)');
    expect(discountLabel({ discountName: 'Promo', discountSource: 'PROMOTION', installmentNumber: 2, installmentsTotal: null })).toBe('Promo (cuota 2)');
    expect(discountLabel({ discountName: '', discountSource: 'SCHOLARSHIP', installmentNumber: null })).toBe('Beca');
  });

  it('marca como expandible una factura con descuento o anulada', () => {
    expect(hasInvoiceDetail({ lines: [{ discount: {} }] })).toBe(true);
    expect(hasInvoiceDetail({ lines: [{ discount: null }] })).toBe(false);
    expect(hasInvoiceDetail({ lines: [{ discount: null }], creditNotes: [{ creditNoteId: 1 }] })).toBe(true);
    expect(hasInvoiceDetail({ lines: [{ discount: null }], creditNotes: [] })).toBe(false);
    expect(hasInvoiceDetail({ lines: [{ discount: null }, { discount: null }] })).toBe(true);
    expect(hasInvoiceDetail({ lines: [], documentStatus: 'DISCARDED' })).toBe(true);
  });
});

describe('formatPaymentMoment', () => {
  it('shows only the date when the payment has no time of day', () => {
    expect(formatPaymentMoment('2027-04-20T00:00:00')).toBe('20/04/2027');
  });

  it('shows date and time when the payment has a real time', () => {
    expect(formatPaymentMoment('2027-04-20T09:05:00')).toBe('20/04/2027 09:05');
  });

  it('shows nothing without a value', () => {
    expect(formatPaymentMoment(null)).toBe('');
  });
});
