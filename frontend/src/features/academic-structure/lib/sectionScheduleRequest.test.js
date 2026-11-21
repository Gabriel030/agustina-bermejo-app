import { describe, expect, it } from 'vitest';
import { SHIFTS } from './classroomModules';
import {
  buildScheduleRows,
  draftErrors,
  draftsToSlots,
  hasScheduleChanges,
  shiftForSection,
  slotsToDrafts,
} from './sectionScheduleRequest';

const EVENING = SHIFTS.find((shift) => shift.id === 'EVENING');

function aSlot(extra = {}) {
  return {
    classroomId: 3,
    classroomName: 'Aula 3',
    dayOfWeek: 'MONDAY',
    startTime: '18:30:00',
    endTime: '20:30:00',
    ...extra,
  };
}

describe('shiftForSection', () => {
  it('uses the declared shift', () => {
    expect(shiftForSection({ shift: 'MORNING', slots: [] }).id).toBe('MORNING');
  });

  it('infers the shift from the slots when it is not declared', () => {
    expect(shiftForSection({ shift: null, slots: [aSlot()] }).id).toBe('EVENING');
  });

  it('returns null when the shift cannot be inferred', () => {
    expect(shiftForSection({ shift: null, slots: [] })).toBeNull();
    expect(shiftForSection({ shift: null, slots: [aSlot({ startTime: '10:00:00', endTime: '19:00:00' })] })).toBeNull();
  });
});

describe('slotsToDrafts and draftsToSlots', () => {
  it('round-trips an aligned slot through module selection', () => {
    const drafts = slotsToDrafts([aSlot()], EVENING);
    expect(drafts[0].selection).toEqual([0, 1, 2]);
    expect(drafts[0].legacy).toBe(false);
    expect(draftsToSlots(drafts, EVENING)).toEqual([
      { classroomId: 3, dayOfWeek: 'MONDAY', startTime: '18:30', endTime: '20:30' },
    ]);
  });

  it('marks a slot that does not match the modules as legacy with an empty selection', () => {
    const drafts = slotsToDrafts([aSlot({ endTime: '20:50:00' })], EVENING);
    expect(drafts[0].legacy).toBe(true);
    expect(drafts[0].selection).toEqual([]);
    expect(draftErrors(drafts)).toEqual(['Franja 1: necesita al menos 3 módulos seguidos.']);
  });
});

describe('hasScheduleChanges', () => {
  it('ignores time seconds and slot order', () => {
    const a = aSlot();
    const b = aSlot({ dayOfWeek: 'TUESDAY' });
    expect(hasScheduleChanges([a, b], [{ ...b, startTime: '18:30' }, a])).toBe(false);
  });

  it('detects a different classroom or a different count', () => {
    expect(hasScheduleChanges([aSlot()], [aSlot({ classroomId: 4 })])).toBe(true);
    expect(hasScheduleChanges([aSlot()], [aSlot(), aSlot({ dayOfWeek: 'FRIDAY' })])).toBe(true);
  });
});

describe('buildScheduleRows', () => {
  it('keeps unchanged slots aligned when a middle slot is removed', () => {
    const first = aSlot();
    const middle = aSlot({ dayOfWeek: 'TUESDAY' });
    const last = aSlot({ dayOfWeek: 'FRIDAY' });
    const rows = buildScheduleRows([first, middle, last], [first, last], {});
    expect(rows.map((row) => [row.unchanged, row.removed])).toEqual([
      [true, false],
      [false, true],
      [true, false],
    ]);
  });

  it('pairs a replaced slot and lists an added one', () => {
    const rows = buildScheduleRows([aSlot()], [aSlot({ classroomId: 4 }), aSlot({ dayOfWeek: 'FRIDAY' })], { 4: 'Aula 4' });
    expect(rows[0]).toMatchObject({ unchanged: false, removed: false });
    expect(rows[0].next).toContain('Aula 4');
    expect(rows[1].today).toBe('—');
  });
});
