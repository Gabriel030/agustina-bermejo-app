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

  it('etiqueta y da tono al aviso de correccion de recargo fuera de plazo', () => {
    expect(TIPO_NOTIFICACION_LABEL.LATE_FEE_CORRECTION_AFTER_FISCAL_WINDOW).toBe('Corrección de recargo fuera de plazo');
    expect(TONO_NOTIFICACION.LATE_FEE_CORRECTION_AFTER_FISCAL_WINDOW).toBe('danger');
  });

  it('etiqueta y da tono a la alerta de comprobante sin revisar', () => {
    expect(TIPO_NOTIFICACION_LABEL.PAYMENT_RECEIPT_REVIEW_OVERDUE).toBe('Comprobante sin revisar');
    expect(TONO_NOTIFICACION.PAYMENT_RECEIPT_REVIEW_OVERDUE).toBe('warning');
  });

  it('todo tipo con etiqueta tiene tono', () => {
    Object.keys(TIPO_NOTIFICACION_LABEL).forEach((tipo) => {
      expect(TONO_NOTIFICACION[tipo], tipo).toBeDefined();
    });
  });
});
