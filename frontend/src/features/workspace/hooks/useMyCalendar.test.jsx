import { beforeEach, describe, expect, it } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { calendarHandler, calendarResponse, classEvent } from '@/test/fixtures/studentCalendar';
import { useMyCalendar } from '@/features/workspace/hooks/useMyCalendar';

function renderCalendarHook(from, to, queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  const wrapper = ({ children }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  return { queryClient, ...renderHook(() => useMyCalendar(from, to), { wrapper }) };
}

describe('useMyCalendar', () => {
  beforeEach(() => {
    server.use(calendarHandler());
  });

  it('requests the given range and exposes the events', async () => {
    let requestedUrl;
    server.use(
      calendarHandler(({ request }) => {
        requestedUrl = new URL(request.url);
        return HttpResponse.json(calendarResponse({ events: [classEvent()] }));
      }),
    );

    const { result } = renderCalendarHook('2026-10-26', '2026-12-06');

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(requestedUrl.searchParams.get('from')).toBe('2026-10-26');
    expect(requestedUrl.searchParams.get('to')).toBe('2026-12-06');
    expect(result.current.data.events).toHaveLength(1);
    expect(result.current.data.generatedAt).toBe('2026-11-21T09:30:00');
  });

  it('normalizes missing optional fields to null', async () => {
    server.use(
      calendarHandler(() =>
        HttpResponse.json({ generatedAt: '2026-11-21T09:30:00', events: [{ id: 'x', type: 'HOLIDAY', date: '2026-11-23', title: 'Feriado', status: 'SCHEDULED' }] }),
      ),
    );

    const { result } = renderCalendarHook('2026-10-26', '2026-12-06');

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data.events[0]).toMatchObject({ startTime: null, place: null, subjectName: null });
  });

  it('keeps one cache entry per range and refetches on window focus', async () => {
    const { queryClient, result } = renderCalendarHook('2026-10-26', '2026-12-06');

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const query = queryClient.getQueryCache().find({ queryKey: ['me', 'calendar', '2026-10-26', '2026-12-06'] });
    expect(query).toBeDefined();
    expect(query.observers[0].options.refetchOnWindowFocus).toBe(true);
    expect(query.observers[0].options.refetchInterval).toBeUndefined();
  });
});
