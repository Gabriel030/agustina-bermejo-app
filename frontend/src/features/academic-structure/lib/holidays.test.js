import { describe, expect, it } from 'vitest';
import {
  countByType,
  defaultNewHolidayDate,
  formatLongDate,
  groupHolidaysByMonth,
  holidayCountLabel,
  holidaysOfYear,
  holidayTypeInfo,
} from './holidays';

const HOLIDAYS = [
  { id: 3, date: '2027-05-25', name: 'Revolución de Mayo', type: 'NATIONAL' },
  { id: 1, date: '2026-12-25', name: 'Navidad', type: 'NATIONAL' },
  { id: 2, date: '2026-01-01', name: 'Año Nuevo', type: 'NATIONAL' },
  { id: 4, date: '2026-07-09', name: 'Independencia', type: 'PROVINCIAL' },
];

describe('holidays lib', () => {
  it('keeps only the holidays of the requested year sorted by date', () => {
    expect(holidaysOfYear(HOLIDAYS, 2026).map((holiday) => holiday.id)).toEqual([2, 4, 1]);
  });

  it('groups consecutive holidays by month', () => {
    const groups = groupHolidaysByMonth(holidaysOfYear(HOLIDAYS, 2026));
    expect(groups.map((group) => [group.month, group.holidays.length])).toEqual([[0, 1], [6, 1], [11, 1]]);
  });

  it('counts holidays per type and omits empty types', () => {
    expect(countByType(holidaysOfYear(HOLIDAYS, 2026)).map((type) => [type.value, type.count])).toEqual([
      ['NATIONAL', 2],
      ['PROVINCIAL', 1],
    ]);
  });

  it('formats a long Spanish date', () => {
    expect(formatLongDate('2026-12-25')).toBe('viernes 25 de diciembre de 2026');
  });

  it('defaults the new holiday date to today in the visible month and to the first day otherwise', () => {
    const today = new Date(2026, 10, 5);
    expect(defaultNewHolidayDate(2026, 10, today)).toBe('2026-11-05');
    expect(defaultNewHolidayDate(2026, 2, today)).toBe('2026-03-01');
    expect(defaultNewHolidayDate(2027, 10, today)).toBe('2027-11-01');
  });

  it('falls back to the generic type for unknown values', () => {
    expect(holidayTypeInfo('UNKNOWN').value).toBe('OTHER');
  });

  it('pluralizes the count label', () => {
    expect(holidayCountLabel(0, 2027)).toBe('Sin feriados cargados en 2027');
    expect(holidayCountLabel(1, 2027)).toBe('1 feriado en 2027');
    expect(holidayCountLabel(12, 2027)).toBe('12 feriados en 2027');
  });
});
