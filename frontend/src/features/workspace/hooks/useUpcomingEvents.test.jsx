import { describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider, onlineManager } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { contractList } from '@/test/fixtures/fromContract';
import { errorBody } from '@/test/fixtures/errors';
import { installFixedClock } from '@/test/fixedClock';
import { useUpcomingEvents } from '@/features/workspace/hooks/useUpcomingEvents';

const API = 'http://localhost:8080';
const at = (date) => `${date}T12:00:00.000Z`;

installFixedClock({ now: '2026-11-05T12:00:00-03:00' });

function board(id, date, subjectName = 'Didáctica') {
  return { id, examDate: at(date), subjectName, status: 'SCHEDULED', courseSubjectName: subjectName };
}

function holiday(id, date, name = 'Feriado') {
  return { id, date, name, type: 'NATIONAL', description: name };
}

function term(id, overrides = {}) {
  return {
    id,
    type: 'FIRST_SEMESTER',
    status: 'ACTIVE',
    year: 2026,
    description: null,
    enrollmentOpen: false,
    enrollmentOverride: null,
    startDate: at('2027-03-01'),
    endDate: at('2027-07-01'),
    enrollmentStart: at('2026-10-01'),
    enrollmentEnd: at('2026-10-15'),
    ...overrides,
  };
}

function renderUpcomingEvents() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  return renderHook(() => useUpcomingEvents(), { wrapper }).result;
}

function serve({ boards = [], holidays = [], terms = [], failing = [], repeatHolidaysForEveryYear = false } = {}) {
  const requestedHolidayYears = [];
  server.use(
    http.get(`${API}/exam-boards`, () => {
      if (failing.includes('exam-boards')) return HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 });
      return HttpResponse.json(contractList('/exam-boards', boards));
    }),
    http.get(`${API}/holidays`, ({ request }) => {
      const year = Number(new URL(request.url).searchParams.get('year'));
      requestedHolidayYears.push(year);
      if (failing.includes('holidays')) return HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 });
      const ofYear = repeatHolidaysForEveryYear ? holidays : holidays.filter((item) => item.date.startsWith(`${year}-`));
      return HttpResponse.json(contractList('/holidays', ofYear));
    }),
    http.get(`${API}/terms`, () => {
      if (failing.includes('terms')) return HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 });
      return HttpResponse.json(contractList('/terms', terms));
    }),
  );
  return requestedHolidayYears;
}

async function settledCard(result) {
  await waitFor(() => expect(result.current.state).not.toBe('loading'));
  return result.current;
}

describe('useUpcomingEvents', () => {
  it('merges mesas, holidays and term dates inside the window in date order', async () => {
    serve({
      boards: [board(1, '2026-11-10')],
      holidays: [holiday(2, '2026-11-11', 'Feriado de prueba')],
      terms: [term(3, { enrollmentStart: at('2026-11-20') })],
    });
    const card = await settledCard(renderUpcomingEvents());
    expect(card).toMatchObject({ id: 'upcomingEvents', title: 'Próximos 30 días', state: 'ready', count: 3 });
    expect(card.events.map((event) => event.title)).toEqual([
      'Mesa de Didáctica',
      'Feriado de prueba',
      'Abren inscripciones: Primer cuatrimestre',
    ]);
  });

  it('keeps an event on day 30 of the window and drops one on day 31', async () => {
    serve({
      boards: [board(1, '2026-12-05', 'Día 30'), board(2, '2026-12-06', 'Día 31')],
    });
    const card = await settledCard(renderUpcomingEvents());
    expect(card.events.map((event) => event.title)).toEqual(['Mesa de Día 30']);
  });

  it('shows at most five events even when more fall inside the window', async () => {
    serve({
      holidays: [1, 2, 3, 4, 5, 6].map((day) => holiday(day, `2026-11-${String(day + 9).padStart(2, '0')}`, `Feriado ${day}`)),
    });
    const card = await settledCard(renderUpcomingEvents());
    expect(card.count).toBe(5);
    expect(card.events.map((event) => event.title)).toEqual(['Feriado 1', 'Feriado 2', 'Feriado 3', 'Feriado 4', 'Feriado 5']);
  });

  it('reports an error when a source fails instead of showing an empty card', async () => {
    serve({ failing: ['holidays'] });
    const card = await settledCard(renderUpcomingEvents());
    expect(card.state).toBe('error');
    expect(card).not.toHaveProperty('events');
  });

  it('reports an error when the mesas source fails', async () => {
    serve({ failing: ['exam-boards'] });
    const card = await settledCard(renderUpcomingEvents());
    expect(card.state).toBe('error');
  });

  it('stays in loading until every source has answered', async () => {
    serve({ boards: [board(1, '2026-11-10')] });
    const result = renderUpcomingEvents();
    expect(result.current.state).toBe('loading');
    const card = await settledCard(result);
    expect(card.state).toBe('ready');
  });

  it('requests the holidays of a single year when the window stays inside it', async () => {
    const requestedHolidayYears = serve();
    await settledCard(renderUpcomingEvents());
    expect(requestedHolidayYears).toEqual([2026]);
  });

  it('requests both years and merges holidays when the window crosses a year boundary', async () => {
    vi.setSystemTime(new Date('2026-12-20T12:00:00-03:00'));
    const requestedHolidayYears = serve({ holidays: [holiday(1, '2027-01-01', 'Año nuevo')] });
    const card = await settledCard(renderUpcomingEvents());
    expect(requestedHolidayYears.sort()).toEqual([2026, 2027]);
    expect(card.events.map((event) => event.title)).toEqual(['Año nuevo']);
  });

  it('shows a holiday once when both year queries return it', async () => {
    vi.setSystemTime(new Date('2026-12-20T12:00:00-03:00'));
    serve({ holidays: [holiday(9, '2026-12-25', 'Navidad')], repeatHolidaysForEveryYear: true });
    const card = await settledCard(renderUpcomingEvents());
    expect(card.events.map((event) => event.title)).toEqual(['Navidad']);
  });

  it('stays in loading, never ready, while a source is paused without data', async () => {
    serve({ boards: [board(1, '2026-11-10')] });
    onlineManager.setOnline(false);
    try {
      const result = renderUpcomingEvents();
      await act(() => Promise.resolve());
      expect(result.current.state).toBe('loading');
      expect(result.current).not.toHaveProperty('count');
    } finally {
      onlineManager.setOnline(true);
    }
  });
});
