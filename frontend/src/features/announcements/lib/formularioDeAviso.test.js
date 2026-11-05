import { describe, expect, it } from 'vitest';
import { cambiosQueMerecenAviso, fechaHoraLocalMinima, fechaLocalMinima, mensajeDeErrorDeAviso, unirCambios } from './formularioDeAviso';

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

describe('cambiosQueMerecenAviso', () => {
  const base = {
    title: 'Aviso',
    urgent: false,
    cuando: 'ahora',
    scheduledFor: null,
    caducidad: 'otra',
    expiresAt: '2026-12-01',
    body: 'Texto',
    category: 'REMINDER',
  };

  it('no detecta cambios si solo se tocan el mensaje o la categoría', () => {
    expect(cambiosQueMerecenAviso(base, { ...base, body: 'Otro', category: 'ADMIN' })).toEqual([]);
  });

  it('ignora espacios alrededor del título', () => {
    expect(cambiosQueMerecenAviso(base, { ...base, title: ' Aviso ' })).toEqual([]);
  });

  it('detecta título, urgencia, caducidad y envío programado, cada uno por separado', () => {
    expect(cambiosQueMerecenAviso(base, { ...base, title: 'Otro' })).toEqual(['el título']);
    expect(cambiosQueMerecenAviso(base, { ...base, urgent: true })).toEqual(['la urgencia']);
    expect(cambiosQueMerecenAviso(base, { ...base, expiresAt: '2026-12-02' })).toEqual(['la caducidad']);
    expect(cambiosQueMerecenAviso(base, { ...base, caducidad: 'siempre', expiresAt: null })).toEqual(['la caducidad']);
    expect(cambiosQueMerecenAviso(base, { ...base, cuando: 'programar', scheduledFor: '2099-01-01T10:00' })).toEqual([
      'el envío programado',
    ]);
  });

  it('acumula todos los cambios en orden', () => {
    const actual = { ...base, title: 'Otro', urgent: true, expiresAt: null, caducidad: 'siempre' };
    expect(cambiosQueMerecenAviso(base, actual)).toEqual(['el título', 'la urgencia', 'la caducidad']);
  });
});

describe('unirCambios', () => {
  it('une con comas y una y final', () => {
    expect(unirCambios([])).toBe('');
    expect(unirCambios(['el título'])).toBe('el título');
    expect(unirCambios(['el título', 'la urgencia'])).toBe('el título y la urgencia');
    expect(unirCambios(['el título', 'la urgencia', 'la caducidad'])).toBe('el título, la urgencia y la caducidad');
  });
});
