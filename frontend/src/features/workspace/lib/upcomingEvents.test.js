import { describe, expect, it } from 'vitest';
import { upcomingEvents, upcomingWindow } from '@/features/workspace/lib/upcomingEvents';

const NOW = new Date(2026, 10, 5, 12);

function board(id, examDate, subjectName = 'Didáctica') {
  return { id, examDate: `${examDate}T09:00:00`, subjectName, status: 'SCHEDULED' };
}

function holiday(id, date, name = 'Feriado') {
  return { id, date, name, type: 'NATIONAL', description: '' };
}

function term(id, overrides = {}) {
  return {
    id,
    type: 'FIRST_SEMESTER',
    startDate: '2026-12-20T00:00:00',
    endDate: '2027-03-30T00:00:00',
    enrollmentStart: '2026-11-01T00:00:00',
    enrollmentEnd: '2026-11-30T00:00:00',
    ...overrides,
  };
}

describe('upcomingWindow', () => {
  it('spans today through thirty calendar days ahead', () => {
    expect(upcomingWindow(NOW)).toEqual({ from: '2026-11-05', to: '2026-12-05' });
  });

  it('crosses the year boundary when the window does', () => {
    expect(upcomingWindow(new Date(2026, 11, 20, 8))).toEqual({ from: '2026-12-20', to: '2027-01-19' });
  });
});

describe('upcomingEvents', () => {
  it('keeps an event on day 30 and drops one on day 31', () => {
    const events = upcomingEvents(
      {
        boards: [board(1, '2026-12-05'), board(2, '2026-12-06')],
        holidays: [holiday(3, '2026-12-05', 'Día del último día'), holiday(4, '2026-12-06', 'Feriado lejano')],
      },
      NOW,
    );
    expect(events.map((event) => event.title)).toEqual(['Didáctica', 'Día del último día']);
  });

  it('keeps an event that happens today and drops one from yesterday', () => {
    const events = upcomingEvents(
      { holidays: [holiday(1, '2026-11-04', 'Ayer'), holiday(2, '2026-11-05', 'Hoy')] },
      NOW,
    );
    expect(events.map((event) => event.title)).toEqual(['Hoy']);
  });

  it('orders events by date ascending across mesas, holidays and terms', () => {
    const events = upcomingEvents(
      {
        boards: [board(1, '2026-11-20')],
        holidays: [holiday(2, '2026-11-10', 'Feriado')],
        terms: [term(3, { enrollmentStart: '2026-11-15T00:00:00' })],
      },
      NOW,
    );
    expect(events.map((event) => [event.date, event.type])).toEqual([
      ['2026-11-10', 'holiday'],
      ['2026-11-15', 'enrollment'],
      ['2026-11-20', 'exam'],
      ['2026-11-30', 'enrollment'],
    ]);
  });

  it('breaks ties between events on the same day by type, then title', () => {
    const events = upcomingEvents(
      {
        boards: [board(4, '2026-11-12', 'Zoología')],
        holidays: [holiday(2, '2026-11-12', 'Bravo'), holiday(1, '2026-11-12', 'Alfa')],
      },
      NOW,
    );
    expect(events.map((event) => event.title)).toEqual(['Zoología', 'Alfa', 'Bravo']);
  });

  it('keeps the same order whatever the input order of same-day events', () => {
    const forward = upcomingEvents({ holidays: [holiday(1, '2026-11-12', 'Alfa'), holiday(2, '2026-11-12', 'Bravo')] }, NOW);
    const backward = upcomingEvents({ holidays: [holiday(2, '2026-11-12', 'Bravo'), holiday(1, '2026-11-12', 'Alfa')] }, NOW);
    expect(backward).toEqual(forward);
  });

  it('labels term events with the translated period type and their pill type', () => {
    const events = upcomingEvents({ terms: [term(1, { startDate: '2026-11-25T00:00:00' })] }, NOW);
    expect(events.map((event) => [event.date, event.type, event.title])).toEqual([
      ['2026-11-25', 'start', 'Primer cuatrimestre'],
      ['2026-11-30', 'enrollment', 'Primer cuatrimestre'],
    ]);
  });

  it('caps the list at five events, keeping the earliest ones', () => {
    const holidays = [6, 5, 4, 3, 2, 1].map((day) => holiday(day, `2026-11-${String(day + 5).padStart(2, '0')}`, `Feriado ${day}`));
    const events = upcomingEvents({ holidays }, NOW);
    expect(events).toHaveLength(5);
    expect(events.map((event) => event.date)).toEqual(['2026-11-06', '2026-11-07', '2026-11-08', '2026-11-09', '2026-11-10']);
  });

  it('routes each event type to the destination of the given role', () => {
    const events = upcomingEvents(
      {
        boards: [board(1, '2026-11-10')],
        holidays: [holiday(2, '2026-11-11', 'Feriado')],
        terms: [term(3, { startDate: '2026-11-12T00:00:00', endDate: '2026-11-13T00:00:00', enrollmentStart: '2026-11-14T00:00:00', enrollmentEnd: '2026-11-15T00:00:00' })],
        role: 'administrativo',
      },
      NOW,
    );
    expect(events.map((event) => [event.type, event.to])).toEqual([
      ['exam', '/administrativo/mesas-de-examen'],
      ['holiday', '/administrativo/feriados'],
      ['start', '/administrativo/periodos'],
      ['close', '/administrativo/cierres-de-comision'],
      ['enrollment', '/administrativo/mesas-de-examen'],
    ]);
  });

  it('has no destination for holidays when the role has no holidays screen', () => {
    const events = upcomingEvents({ holidays: [holiday(1, '2026-11-11', 'Feriado')], role: 'director' }, NOW);
    expect(events).toEqual([expect.objectContaining({ type: 'holiday', to: null })]);
  });

  it('routes exam and closure events to the director screens', () => {
    const events = upcomingEvents({ boards: [board(1, '2026-11-10')], terms: [term(2, { endDate: '2026-11-12T00:00:00' })], role: 'director' }, NOW);
    expect(events.map((event) => [event.type, event.to])).toEqual([
      ['exam', '/director/mesas-de-examen'],
      ['close', '/director/cierres-de-comision'],
      ['enrollment', '/director/mesas-de-examen'],
    ]);
  });

  it('returns an empty list when nothing falls inside the window', () => {
    expect(upcomingEvents({ boards: [board(1, '2027-01-10')] }, NOW)).toEqual([]);
  });
});
