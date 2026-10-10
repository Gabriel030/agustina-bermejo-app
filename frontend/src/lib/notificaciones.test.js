import { describe, expect, it } from 'vitest';
import { TIPO_NOTIFICACION_LABEL, TONO_NOTIFICACION } from './notificaciones';

describe('notificaciones', () => {
  it('etiqueta y da tono a la nota de credito emitida', () => {
    expect(TIPO_NOTIFICACION_LABEL.CREDIT_NOTE_ISSUED).toBe('Nota de crédito emitida');
    expect(TONO_NOTIFICACION.CREDIT_NOTE_ISSUED).toBe('accent2');
  });

  it('todo tipo con etiqueta tiene tono', () => {
    Object.keys(TIPO_NOTIFICACION_LABEL).forEach((tipo) => {
      expect(TONO_NOTIFICACION[tipo], tipo).toBeDefined();
    });
  });
});
