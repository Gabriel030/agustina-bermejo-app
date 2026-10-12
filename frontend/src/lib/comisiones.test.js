import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { agruparComisionesPorFase, diasHastaVencimientoMasProximo, formatearFecha } from '@/lib/comisiones';

globalThis.process.env.TZ ='America/Argentina/Buenos_Aires';

describe('formatearFecha', () => {
  it('muestra el mismo día de una fecha sin hora aunque la zona esté al oeste de UTC', () => {
    expect(formatearFecha('2026-04-24')).toBe(new Date(2026, 3, 24).toLocaleDateString('es-AR', { day: 'numeric', month: 'short', year: 'numeric' }));
    expect(formatearFecha('2026-04-24')).toContain('24');
  });

  it('sigue formateando fechas con hora en la zona local', () => {
    expect(formatearFecha('2026-12-16T18:00:00')).toContain('16');
  });
});

describe('agruparComisionesPorFase', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 9, 3, 10, 0));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  const vigente = { termStartDate: '2026-03-02T00:00:00', termEndDate: '2026-12-11T00:00:00' };

  it('manda a Cerradas las comisiones de ciclos que ya terminaron aunque no tengan configuración', () => {
    const vieja = { id: 'PROGRAM-1', type: 'PROGRAM', termStartDate: '2024-03-04T00:00:00', termEndDate: '2024-12-13T00:00:00' };
    expect(agruparComisionesPorFase([vieja]).cerradas).toEqual([vieja]);
  });

  it('deja en la bandeja solo borradores, pendientes y rechazadas del ciclo vigente', () => {
    const borrador = { id: 1, status: 'DRAFT', ...vigente };
    const pendiente = { id: 2, status: 'PENDING_APPROVAL', ...vigente };
    const aprobada = { id: 3, status: 'APPROVED', ...vigente };
    const curso = { id: 'COURSE-1', type: 'COURSE', ...vigente };
    const { bandejaDeAprobacion, enCurso } = agruparComisionesPorFase([borrador, pendiente, aprobada, curso]);
    expect(bandejaDeAprobacion).toEqual([borrador, pendiente]);
    expect(enCurso).toEqual([aprobada, curso]);
  });
});

describe('diasHastaVencimientoMasProximo', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 9, 3, 10, 0));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('marca como vencido un borrador cuyo ciclo ya empezó', () => {
    expect(diasHastaVencimientoMasProximo([{ status: 'DRAFT', termStartDate: '2026-03-02T00:00:00' }])).toBe('vencido');
  });

  it('prioriza el vencimiento futuro más próximo', () => {
    const comisiones = [
      { status: 'DRAFT', termStartDate: '2026-03-02T00:00:00' },
      { status: 'DRAFT', termStartDate: '2026-10-13T00:00:00' },
    ];
    expect(diasHastaVencimientoMasProximo(comisiones)).toBe(10);
  });

  it('no cuenta las rechazadas: se pueden reenviar aunque el ciclo haya empezado', () => {
    expect(diasHastaVencimientoMasProximo([{ status: 'REJECTED', termStartDate: '2026-03-02T00:00:00' }])).toBeNull();
  });
});
