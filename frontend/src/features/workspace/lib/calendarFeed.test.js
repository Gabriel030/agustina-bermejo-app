import { describe, expect, it } from 'vitest';
import { formatEventHours, formatFeedDate, formatFeedLastRead } from '@/features/workspace/lib/calendarFeed';

const NOW = new Date('2026-11-21T15:00:00Z').getTime();

describe('calendarFeed', () => {
  it('formats the creation date as dd/mm/yyyy in the institution time zone', () => {
    expect(formatFeedDate('2026-11-20T02:30:00Z')).toBe('19/11/2026');
  });

  it('returns a dash when the creation date is missing', () => {
    expect(formatFeedDate(null)).toBe('-');
  });

  it('says nobody read the link yet when there is no last read', () => {
    expect(formatFeedLastRead(null, NOW)).toBe('todavía no lo leyó ningún calendario');
  });

  it('describes the last read as minutes, hours or days ago', () => {
    expect(formatFeedLastRead('2026-11-21T14:59:50Z', NOW)).toBe('hace instantes');
    expect(formatFeedLastRead('2026-11-21T14:30:00Z', NOW)).toBe('hace 30 min');
    expect(formatFeedLastRead('2026-11-21T12:00:00Z', NOW)).toBe('hace 3 h');
    expect(formatFeedLastRead('2026-11-20T14:00:00Z', NOW)).toBe('hace 1 día');
    expect(formatFeedLastRead('2026-11-18T14:00:00Z', NOW)).toBe('hace 3 días');
  });

  it('builds the hours label only when the event has times', () => {
    expect(formatEventHours({ startTime: '18:30', endTime: '21:30' })).toBe('18:30 a 21:30 h');
    expect(formatEventHours({ startTime: '09:00', endTime: null })).toBe('09:00 h');
    expect(formatEventHours({ startTime: null, endTime: null })).toBeNull();
  });
});
