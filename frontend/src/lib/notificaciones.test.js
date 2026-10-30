import { describe, expect, it } from 'vitest';
import { TIPO_NOTIFICACION_LABEL, TONO_NOTIFICACION } from './notificaciones';

describe('notificaciones', () => {
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

  it('todo tipo con etiqueta tiene tono', () => {
    Object.keys(TIPO_NOTIFICACION_LABEL).forEach((tipo) => {
      expect(TONO_NOTIFICACION[tipo], tipo).toBeDefined();
    });
  });
});
