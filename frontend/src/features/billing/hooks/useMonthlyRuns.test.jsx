import { describe, expect, it } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { useMonthlyRuns, useRetryFailedMonthlyRunItems } from './useMonthlyRuns';

const API = 'http://localhost:8080';

function wrapper({ children }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function retriedItem(result) {
  return { id: 1, enrollmentId: 1, enrollmentType: 'COURSE', result, attempts: 1, errorCode: 'X', decisionCode: 'X', message: 'm', nextRetryAt: '2026-10-02T03:00:00', invoiceIds: [] };
}

describe('useMonthlyRuns', () => {
  it('reads the total from the X-Total-Count header and sends the period filter', async () => {
    let requested;
    server.use(
      http.get(`${API}/monthly-invoice-runs`, ({ request }) => {
        requested = new URL(request.url);
        return HttpResponse.json([], { headers: { 'X-Total-Count': '3' } });
      }),
    );
    const { result } = renderHook(() => useMonthlyRuns({ period: '2026-10' }), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data.total).toBe(3);
    expect(requested.searchParams.get('period')).toBe('2026-10');
  });
});

describe('useRetryFailedMonthlyRunItems', () => {
  it('counts recovered, still failing and errored retries without stopping at the first error', async () => {
    server.use(
      http.post(`${API}/monthly-invoice-runs/7/items/:itemId/retries`, ({ params }) => {
        if (params.itemId === '1') return HttpResponse.json(retriedItem('ISSUED'));
        if (params.itemId === '2') return HttpResponse.json(retriedItem('FAILED'));
        return new HttpResponse(null, { status: 500 });
      }),
    );
    const { result } = renderHook(() => useRetryFailedMonthlyRunItems(), { wrapper });
    const items = [1, 2, 3].map((id) => ({ ...retriedItem('FAILED'), id }));

    const outcome = await result.current.mutateAsync({ monthlyRunId: 7, items });

    expect(outcome).toEqual({ recovered: 1, stillFailing: 1, errored: 1 });
  });
});
