import { describe, expect, it } from 'vitest';
import { applicationProgress } from '@/lib/applicationProgress';

const invoice = (overrides) => ({ id: 5, paymentStatus: 'PENDING', receiptStatus: null, ...overrides });

describe('applicationProgress', () => {
  it('una solicitud pendiente está en el paso de revisión', () => {
    expect(applicationProgress({ status: 'PENDING' }).pasoActual).toBe('review');
  });

  it('una solicitud aprobada con la matrícula impaga está en el paso de pago', () => {
    expect(applicationProgress({ status: 'APPROVED', admissionInvoice: invoice() }).pasoActual).toBe('payment');
    expect(applicationProgress({ status: 'APPROVED' }).pasoActual).toBe('payment');
  });

  it('con la matrícula pagada y sin inscripción todavía se está confirmando la inscripción', () => {
    const solicitud = { status: 'APPROVED', admissionInvoice: invoice({ paymentStatus: 'PAID' }), programEnrollmentId: null };
    expect(applicationProgress(solicitud).pasoActual).toBe('enrolled');
  });

  it('con la inscripción creada todos los pasos quedan completos', () => {
    const solicitud = { status: 'APPROVED', admissionInvoice: invoice({ paymentStatus: 'PAID' }), programEnrollmentId: 9 };
    const progress = applicationProgress(solicitud);
    expect(progress.pasoActual).toBeNull();
    expect(Object.values(progress.stepValidity).every(Boolean)).toBe(true);
  });

  it('una solicitud vencida corta el recorrido en el paso vencida', () => {
    const progress = applicationProgress({ status: 'EXPIRED' });
    expect(progress.pasos.map((step) => step.key)).toEqual(['submitted', 'expired']);
    expect(progress.stepValidity.expired).toBe(false);
  });

  it('una solicitud rechazada corta el recorrido en el paso rechazada', () => {
    const progress = applicationProgress({ status: 'REJECTED' });
    expect(progress.pasos.map((step) => step.key)).toEqual(['submitted', 'rejected']);
    expect(progress.stepValidity.rejected).toBe(false);
  });
});
