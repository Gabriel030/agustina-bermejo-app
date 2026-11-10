import { describe, expect, it } from 'vitest';
import { SHIFT_LABELS, shiftLabel } from './shifts';
import { SHIFTS } from './classroomModules';

const BACKEND_SECTION_SHIFT_VALUES = ['MORNING', 'AFTERNOON', 'EVENING'];

describe('SHIFT_LABELS', () => {
  it('has exactly the keys of the backend SectionShift enum', () => {
    expect(Object.keys(SHIFT_LABELS).sort()).toEqual([...BACKEND_SECTION_SHIFT_VALUES].sort());
  });

  it('labels every shift in Spanish', () => {
    expect(SHIFT_LABELS).toEqual({ MORNING: 'Mañana', AFTERNOON: 'Tarde', EVENING: 'Noche' });
  });

  it('resolves unknown values to Sin turno', () => {
    expect(shiftLabel('EVENING')).toBe('Noche');
    expect(shiftLabel('NIGHT')).toBe('Sin turno');
    expect(shiftLabel(null)).toBe('Sin turno');
  });
});

describe('classroom SHIFTS', () => {
  it('uses exactly the backend SectionShift enum values in order', () => {
    expect(SHIFTS.map((shift) => shift.id)).toEqual(BACKEND_SECTION_SHIFT_VALUES);
  });

  it('shares the labels of SHIFT_LABELS', () => {
    expect(SHIFTS.map((shift) => shift.label)).toEqual(BACKEND_SECTION_SHIFT_VALUES.map((value) => SHIFT_LABELS[value]));
  });
});
