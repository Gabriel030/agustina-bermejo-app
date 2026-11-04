import { describe, expect, it } from 'vitest';
import { fechaHoraLocalMinima, fechaLocalMinima, mensajeDeErrorDeAviso } from './formularioDeAviso';

describe('mensajeDeErrorDeAviso', () => {
  it.each([
    ['EMPTY_AUDIENCE', 'Ese grupo no tiene ningún alumno hoy. Elegí otro grupo de destinatarios.'],
    ['SCHEDULED_DATE_IN_THE_PAST', 'La fecha de envío ya pasó. Elegí una fecha y hora futuras.'],
    ['ANNOUNCEMENT_ALREADY_SENT', 'Este aviso ya se envió, así que no se puede reprogramar.'],
  ])('traduce %s por su causa', (code, mensaje) => {
    expect(mensajeDeErrorDeAviso({ code }, 'genérico')).toBe(mensaje);
  });

  it('usa el texto genérico cuando el código no se conoce', () => {
    expect(mensajeDeErrorDeAviso({ code: 'OTHER' }, 'genérico')).toBe('genérico');
    expect(mensajeDeErrorDeAviso(undefined, 'genérico')).toBe('genérico');
  });
});

describe('fechas mínimas', () => {
  it('formatea la fecha y hora local con cero a la izquierda', () => {
    const ahora = new Date(2026, 0, 5, 9, 7);
    expect(fechaHoraLocalMinima(ahora)).toBe('2026-01-05T09:07');
    expect(fechaLocalMinima(ahora)).toBe('2026-01-05');
  });
});
