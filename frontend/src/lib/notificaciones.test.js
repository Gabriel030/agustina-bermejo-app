import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TIPO_NOTIFICACION_LABEL, TONO_NOTIFICACION, etiquetaNotificacion, tiempoRelativo } from './notificaciones';

describe('notificaciones', () => {
  it('etiqueta y da tono a la autorizacion pendiente y al resto de los tipos de configuracion', () => {
    expect(etiquetaNotificacion('AUTHORIZATION_REQUESTED')).toBe('Autorización pendiente');
    expect(TONO_NOTIFICACION.AUTHORIZATION_REQUESTED).toBe('warning');
    expect(etiquetaNotificacion('PRICE_CHANGE_APPROVED')).toBe('Precio aprobado');
    expect(TONO_NOTIFICACION.PRICE_CHANGE_REJECTED).toBe('danger');
    expect(etiquetaNotificacion('DISCOUNT_QUOTA_EXHAUSTED')).toBe('Cupo de promoción agotado');
  });

  it('etiqueta y da tono a la nota de credito emitida', () => {
    expect(TIPO_NOTIFICACION_LABEL.CREDIT_NOTE_ISSUED).toBe('Nota de crédito emitida');
    expect(TONO_NOTIFICACION.CREDIT_NOTE_ISSUED).toBe('accent2');
  });

  it('etiqueta y da tono al aviso de cobranza', () => {
    expect(TIPO_NOTIFICACION_LABEL.COLLECTION_REMINDER).toBe('Aviso de cobranza');
    expect(TONO_NOTIFICACION.COLLECTION_REMINDER).toBe('warning');
  });

  it('etiqueta y da tono a la lista de alumnos para contactar', () => {
    expect(TIPO_NOTIFICACION_LABEL.OVERDUE_CONTACT_LIST_UPDATED).toBe('Alumnos para contactar');
    expect(TONO_NOTIFICACION.OVERDUE_CONTACT_LIST_UPDATED).toBe('warning');
  });

  it('etiqueta y da tono al aviso de correccion de recargo fuera de plazo', () => {
    expect(TIPO_NOTIFICACION_LABEL.LATE_FEE_CORRECTION_AFTER_FISCAL_WINDOW).toBe('Corrección de recargo fuera de plazo');
    expect(TONO_NOTIFICACION.LATE_FEE_CORRECTION_AFTER_FISCAL_WINDOW).toBe('danger');
  });

  it('etiqueta y da tono a la alerta de comprobante sin revisar', () => {
    expect(TIPO_NOTIFICACION_LABEL.PAYMENT_RECEIPT_REVIEW_OVERDUE).toBe('Comprobante sin revisar');
    expect(TONO_NOTIFICACION.PAYMENT_RECEIPT_REVIEW_OVERDUE).toBe('warning');
  });

  it('etiqueta y da tono a las alertas del proceso nocturno y de cuentas inconsistentes', () => {
    expect(TIPO_NOTIFICACION_LABEL.NIGHTLY_RUN_FAILED).toBe('Falló el proceso nocturno');
    expect(TIPO_NOTIFICACION_LABEL.NIGHTLY_RUN_MISSED).toBe('No corrió el proceso nocturno');
    expect(TIPO_NOTIFICACION_LABEL.NIGHTLY_RUN_COMPLETED_WITH_ERRORS).toBe('Proceso nocturno con errores');
    expect(TIPO_NOTIFICACION_LABEL.INCONSISTENT_ACCOUNT_DETECTED).toBe('Cuenta con saldo inconsistente');
    expect(TONO_NOTIFICACION.NIGHTLY_RUN_FAILED).toBe('danger');
    expect(TONO_NOTIFICACION.NIGHTLY_RUN_COMPLETED_WITH_ERRORS).toBe('warning');
  });

  it('etiqueta y da tono a las alertas de la emisión mensual', () => {
    expect(TIPO_NOTIFICACION_LABEL.MONTHLY_INVOICE_RUN_MISSED).toBe('No corrió la emisión mensual');
    expect(TIPO_NOTIFICACION_LABEL.MONTHLY_INVOICE_ITEM_RETRIES_EXHAUSTED).toBe('Reintentos de emisión agotados');
    expect(TONO_NOTIFICACION.MONTHLY_INVOICE_RUN_MISSED).toBe('danger');
    expect(TONO_NOTIFICACION.MONTHLY_INVOICE_ITEM_RETRIES_EXHAUSTED).toBe('warning');
  });

  it('etiqueta y da tono al aviso de borradores de factura por revisar', () => {
    expect(TIPO_NOTIFICACION_LABEL.DRAFT_INVOICES_AWAITING_REVIEW).toBe('Facturas por revisar');
    expect(TONO_NOTIFICACION.DRAFT_INVOICES_AWAITING_REVIEW).toBe('warning');
  });

  it('etiqueta y da tono a las notas publicadas y a la nota corregida', () => {
    expect(TIPO_NOTIFICACION_LABEL.GRADES_PUBLISHED).toBe('Notas publicadas');
    expect(TONO_NOTIFICACION.GRADES_PUBLISHED).toBe('success');
    expect(TIPO_NOTIFICACION_LABEL.GRADE_CORRECTED).toBe('Nota corregida');
    expect(TONO_NOTIFICACION.GRADE_CORRECTED).toBe('accent2');
  });

  it('etiqueta y da tono al aviso de feriados faltantes', () => {
    expect(TIPO_NOTIFICACION_LABEL.HOLIDAYS_MISSING_FOR_NEXT_YEAR).toBe('Faltan los feriados del año que viene');
    expect(TONO_NOTIFICACION.HOLIDAYS_MISSING_FOR_NEXT_YEAR).toBe('warning');
  });

  it('todo tipo con etiqueta tiene tono', () => {
    Object.keys(TIPO_NOTIFICACION_LABEL).forEach((tipo) => {
      expect(TONO_NOTIFICACION[tipo], tipo).toBeDefined();
    });
  });

  it('labels and tones the graduation request notifications', () => {
    expect(TIPO_NOTIFICACION_LABEL.GRADUATION_REQUEST_CREATED).toBe('Solicitud de egreso registrada');
    expect(TIPO_NOTIFICACION_LABEL.GRADUATION_REQUEST_PENDING_REVIEW).toBe('Egreso pendiente de revisión');
    expect(TIPO_NOTIFICACION_LABEL.GRADUATION_REQUEST_VERIFIED).toBe('Egreso verificado por Administración');
    expect(TONO_NOTIFICACION.GRADUATION_REQUEST_PENDING_REVIEW).toBe('warning');
    expect(TONO_NOTIFICACION.GRADUATION_REQUEST_VERIFIED).toBe('accent2');
  });

  it('labels and tones the graduation request blocked by debt', () => {
    expect(TIPO_NOTIFICACION_LABEL.GRADUATION_REQUEST_DEBT_BLOCKED).toBe('Egreso detenido por deuda');
    expect(TONO_NOTIFICACION.GRADUATION_REQUEST_DEBT_BLOCKED).toBe('warning');
  });

  it('labels the real emitted types added in the audit', () => {
    expect(TIPO_NOTIFICACION_LABEL.ATTENDANCE_JUSTIFICATION_APPROVED).toBe('Justificación de inasistencia aprobada');
    expect(TIPO_NOTIFICACION_LABEL.ATTENDANCE_JUSTIFICATION_REJECTED).toBe('Justificación de inasistencia rechazada');
    expect(TIPO_NOTIFICACION_LABEL.ENROLLMENT_FEE_EXEMPTION_GRANTED).toBe('Matrícula exonerada');
    expect(TIPO_NOTIFICACION_LABEL.ENROLLMENT_FEE_EXEMPTION_REVOKED).toBe('Exoneración de matrícula revocada');
    expect(TIPO_NOTIFICACION_LABEL.PAYMENT_CHARGED_BACK).toBe('Pago revertido por contracargo');
    expect(TONO_NOTIFICACION.PAYMENT_CHARGED_BACK).toBe('danger');
  });

  it('falls back to a generic Spanish label for unknown types', () => {
    expect(etiquetaNotificacion('SOME_FUTURE_TYPE')).toBe('Notificación');
    expect(etiquetaNotificacion('CREDIT_NOTE_ISSUED')).toBe('Nota de crédito emitida');
  });

  describe('tiempoRelativo', () => {
    beforeEach(() => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date('2026-11-05T12:00:00'));
    });
    afterEach(() => vi.useRealTimers());

    it('handles recent, hours and days', () => {
      expect(tiempoRelativo('2026-11-05T11:30:00')).toBe('hace 30 min');
      expect(tiempoRelativo('2026-11-05T09:00:00')).toBe('hace 3 h');
      expect(tiempoRelativo('2026-11-02T12:00:00')).toBe('hace 3 d');
    });

    it('shows an absolute date from seven days', () => {
      expect(tiempoRelativo('2026-10-01T10:00:00')).toBe('01/10/2026');
    });

    it('never shows negative values for future dates', () => {
      expect(tiempoRelativo('2026-11-06T12:00:00')).toBe('recién');
    });

    it('returns empty text for invalid or missing dates', () => {
      expect(tiempoRelativo('not-a-date')).toBe('');
      expect(tiempoRelativo(undefined)).toBe('');
    });
  });
});
