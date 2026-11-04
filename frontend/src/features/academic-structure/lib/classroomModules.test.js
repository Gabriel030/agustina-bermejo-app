import { describe, expect, it } from 'vitest';
import { SHIFTS, buildModules, selectionRange, toggleModule } from './classroomModules';
import { classifyClassroom } from './classroomAvailability';

describe('buildModules', () => {
  it('lays out 40 minute modules with a recess every three modules', () => {
    const modules = buildModules(SHIFTS[0]);
    expect(modules.map((m) => `${m.startTime}-${m.endTime}`)).toEqual([
      '08:00-08:40',
      '08:40-09:20',
      '09:20-10:00',
      '10:15-10:55',
      '10:55-11:35',
      '11:35-12:15',
    ]);
    expect(modules.filter((m) => m.recessAfter).map((m) => m.index)).toEqual([2, 5]);
  });

  it('drops the module that does not fit in the shift', () => {
    expect(buildModules(SHIFTS[2])).toHaveLength(5);
  });
});

describe('toggleModule', () => {
  it('deselects a selected module when it is touched again', () => {
    expect(toggleModule([2], 2).selection).toEqual([]);
  });

  it('extends the range only with adjacent modules', () => {
    expect(toggleModule([1, 2], 3).selection).toEqual([1, 2, 3]);
    expect(toggleModule([1, 2], 0).selection).toEqual([0, 1, 2]);
    const rejected = toggleModule([1, 2], 5);
    expect(rejected.selection).toEqual([1, 2]);
    expect(rejected.hint).toMatch(/pegado/);
  });

  it('removes only from the ends of the range', () => {
    expect(toggleModule([1, 2, 3], 1).selection).toEqual([2, 3]);
    expect(toggleModule([1, 2, 3], 3).selection).toEqual([1, 2]);
    const rejected = toggleModule([1, 2, 3], 2);
    expect(rejected.selection).toEqual([1, 2, 3]);
    expect(rejected.hint).toMatch(/extremos/);
  });
});

describe('selectionRange', () => {
  it('spans from the first start to the last end', () => {
    expect(selectionRange(buildModules(SHIFTS[0]), [2, 3])).toEqual({ startTime: '09:20', endTime: '10:55' });
    expect(selectionRange(buildModules(SHIFTS[0]), [])).toBeNull();
  });
});

describe('classifyClassroom', () => {
  const base = { id: 1, name: 'Aula 1', capacity: 30, available: true };
  it('gives priority to the capacity shortfall', () => {
    expect(classifyClassroom({ ...base, available: false, capacityShortfall: 5 })).toBe('SHORT_CAPACITY');
  });
  it('tells an occupied classroom from one occupied only in another period', () => {
    const occupant = { overlapsInDates: false };
    expect(classifyClassroom({ ...base, available: false, overlapsInDates: true, occupant })).toBe('OCCUPIED');
    expect(classifyClassroom({ ...base, overlapsInDates: false, occupant })).toBe('FREE_IN_PERIOD');
    expect(classifyClassroom(base)).toBe('FREE');
  });
});
