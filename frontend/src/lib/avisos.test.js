import { describe, expect, it } from 'vitest';
import { formatearFechaCorta, textoDeAlcance } from '@/lib/avisos';

globalThis.process.env.TZ ='America/Argentina/Buenos_Aires';

describe('formatearFechaCorta', () => {
  it('muestra el vencimiento en el mismo día que se guardó', () => {
    expect(formatearFechaCorta('2026-11-01')).toBe('01/11/2026');
  });

  it('formatea fechas con hora en la zona local', () => {
    expect(formatearFechaCorta('2026-09-23T10:00:00')).toBe('23/09/2026');
  });
});

describe('textoDeAlcance', () => {
  it('usa el total de la comisión, no la cantidad de destinatarios', () => {
    expect(textoDeAlcance(1, 4)).toBe('Lo ve 1 de los 4 alumnos de la comisión.');
  });
});
