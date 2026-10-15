import { describe, expect, it } from 'vitest';
import { clasificarMateriaDelPlan, inscripcionVigente } from '@/lib/notas';

const pedagogia = { subjectId: 5, curricularYear: 1 };

describe('clasificarMateriaDelPlan', () => {
  it('una cursada desaprobada queda como desaprobada, no como disponible', () => {
    const inscripcion = { id: 1, subjectId: 5, status: 'FAILED' };
    expect(clasificarMateriaDelPlan(pedagogia, inscripcion)).toEqual({ estado: 'desaprobada', inscripcion });
  });
});

describe('inscripcionVigente', () => {
  it('si recursa, toma la cursada actual y no la desaprobada', () => {
    const desaprobada = { id: 1, status: 'FAILED' };
    const recursada = { id: 2, status: 'IN_PROGRESS' };
    expect(inscripcionVigente([desaprobada, recursada])).toBe(recursada);
  });

  it('picks the approved retake over the failed first attempt', () => {
    const failed = { id: 1, status: 'FAILED', attemptNumber: 1 };
    const approved = { id: 2, status: 'APPROVED', attemptNumber: 2 };
    expect(inscripcionVigente([failed, approved])).toBe(approved);
  });

  it('picks the most recent attempt when every attempt failed', () => {
    const first = { id: 1, status: 'FAILED', attemptNumber: 1 };
    const second = { id: 2, status: 'FAILED', attemptNumber: 2 };
    expect(inscripcionVigente([first, second])).toBe(second);
  });

  it('ignora las bajas', () => {
    expect(inscripcionVigente([{ id: 1, status: 'WITHDRAWN' }])).toBeUndefined();
  });
});
