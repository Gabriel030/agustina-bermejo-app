import { describe, expect, it } from 'vitest';
import {
  countEventsByType,
  filterCalendarEvents,
  formatAgendaDayTitle,
  formatDayLong,
  formatUpdatedAt,
  formatWeekTitle,
  groupEventsByDay,
  institutionTodayKey,
  monthRange,
  shiftSelection,
  weekDayKeys,
} from '@/features/workspace/lib/studentCalendar';
import { canceledClassEvent, classEvent, examBoardEvent, holidayEvent } from '@/test/fixtures/studentCalendar';

const ALL_TYPES = new Set(['CLASS', 'HOLIDAY', 'EXAM_BOARD']);

describe('institutionTodayKey', () => {
  it('reads the day in the institute time zone, not in the browser time zone', () => {
    expect(institutionTodayKey(new Date('2026-11-21T02:30:00Z'))).toBe('2026-11-20');
    expect(institutionTodayKey(new Date('2026-11-21T14:30:00Z'))).toBe('2026-11-21');
  });
});

describe('monthRange', () => {
  it('spans whole Monday-to-Sunday weeks around the month', () => {
    const range = monthRange(new Date(2026, 10, 15));
    expect(range.from).toBe('2026-10-26');
    expect(range.to).toBe('2026-12-06');
    expect(range.days).toHaveLength(42);
  });

  it('never exceeds 42 days nor the 62-day contract limit', () => {
    for (let month = 0; month < 36; month += 1) {
      const range = monthRange(new Date(2025, month, 1));
      expect(range.days.length).toBeLessThanOrEqual(42);
      expect(range.days.length % 7).toBe(0);
    }
  });

  it('uses only four weeks when the month fits exactly', () => {
    expect(monthRange(new Date(2027, 1, 10)).days).toHaveLength(28);
  });

  it('crosses the year boundary in December', () => {
    const range = monthRange(new Date(2026, 11, 10));
    expect(range.from).toBe('2026-11-30');
    expect(range.to).toBe('2027-01-03');
  });
});

describe('week helpers', () => {
  it('lists the seven days of a week crossing months and years starting on Monday', () => {
    expect(weekDayKeys(new Date(2026, 11, 31))).toEqual([
      '2026-12-28', '2026-12-29', '2026-12-30', '2026-12-31', '2027-01-01', '2027-01-02', '2027-01-03',
    ]);
  });

  it('titles a week that crosses months with both month names', () => {
    expect(formatWeekTitle(new Date(2026, 10, 30))).toBe('30 de noviembre al 6 de diciembre de 2026');
    expect(formatWeekTitle(new Date(2026, 10, 17))).toBe('16 al 22 de noviembre de 2026');
  });

  it('shifts by a week in week view and lands on today when returning to the current month', () => {
    const today = new Date(2026, 10, 21);
    expect(shiftSelection(new Date(2026, 10, 30), 'week', 1, today)).toEqual(new Date(2026, 11, 7));
    expect(shiftSelection(new Date(2026, 11, 3), 'month', -1, today)).toEqual(today);
    expect(shiftSelection(new Date(2026, 10, 21), 'month', 1, today)).toEqual(new Date(2026, 11, 1));
  });
});

describe('formatting', () => {
  it('names days in Spanish with the today marker', () => {
    expect(formatDayLong('2026-11-20', '2026-11-20')).toBe('viernes 20 de noviembre, hoy');
    expect(formatDayLong('2026-11-20', '2026-11-21')).toBe('viernes 20 de noviembre');
    expect(formatAgendaDayTitle('2026-11-20', '2026-11-20')).toBe('viernes 20 de noviembre · hoy');
  });

  it('shows the update time as today or with its date', () => {
    expect(formatUpdatedAt('2026-11-21T09:30:00', '2026-11-21')).toBe('Actualizado hoy 09:30');
    expect(formatUpdatedAt('2026-11-20T18:05:00', '2026-11-21')).toBe('Actualizado el 20/11 18:05');
  });
});

describe('filtering', () => {
  const events = [classEvent(), canceledClassEvent(), holidayEvent(), examBoardEvent()];

  it('hides event types that are switched off', () => {
    const result = filterCalendarEvents(events, { types: new Set(['CLASS']), subject: '' });
    expect(result.map((event) => event.type)).toEqual(['CLASS', 'CLASS']);
  });

  it('keeps holidays visible when filtering by subject', () => {
    const result = filterCalendarEvents(events, { types: ALL_TYPES, subject: 'Programación II' });
    expect(result.map((event) => event.type).sort()).toEqual(['CLASS', 'HOLIDAY']);
  });

  it('hides holidays only when the holiday type is switched off', () => {
    const result = filterCalendarEvents(events, { types: new Set(['CLASS', 'EXAM_BOARD']), subject: 'Matemática' });
    expect(result.map((event) => event.type).sort()).toEqual(['CLASS', 'EXAM_BOARD']);
  });

  it('counts events of the visible month by type, honoring the subject but not the holidays', () => {
    const december = holidayEvent({ id: 'h-dec', date: '2026-12-08' });
    const counts = countEventsByType([...events, december], '2026-11', 'Programación II');
    expect(counts).toEqual({ CLASS: 1, HOLIDAY: 1, EXAM_BOARD: 0 });
  });

  it('groups events by day', () => {
    const groups = groupEventsByDay(events);
    expect(groups.get('2026-11-20')).toHaveLength(1);
    expect([...groups.keys()]).toHaveLength(4);
  });
});
