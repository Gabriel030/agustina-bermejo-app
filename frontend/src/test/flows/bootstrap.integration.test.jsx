import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { act, render, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { ThemeProvider } from '@/context/ThemeContext';
import { AppRouter } from '@/routes/AppRouter';
import { createQueryClient } from '@/api/queryClient';
import { useAcceptLegalDocuments } from '@/features/identity/hooks/useLegal';

const API = 'http://localhost:8080';

function trackRequests() {
  const calls = [];
  const listener = ({ request }) => {
    const url = new URL(request.url);
    calls.push({ method: request.method, path: url.pathname, at: calls.length });
  };
  server.events.on('request:start', listener);
  return { calls, stop: () => server.events.removeListener('request:start', listener) };
}

const countOf = (calls, method, path) => calls.filter((call) => call.method === method && call.path === path).length;
const indexOf = (calls, method, path) => calls.findIndex((call) => call.method === method && call.path === path);

describe('authenticated bootstrap', () => {
  let tracker;
  let queryClient;

  beforeEach(() => {
    localStorage.setItem('sessionHint', '1');
    tracker = trackRequests();
    queryClient = createQueryClient();
  });

  afterEach(() => {
    tracker.stop();
    localStorage.clear();
  });

  function mountAuthenticatedTree() {
    return render(
      <GoogleOAuthProvider clientId="test-client-id">
        <ThemeProvider>
          <QueryClientProvider client={queryClient}>
            <MemoryRouter initialEntries={['/preceptor/inicio']}>
              <AuthProvider>
                <AppRouter />
              </AuthProvider>
            </MemoryRouter>
          </QueryClientProvider>
        </ThemeProvider>
      </GoogleOAuthProvider>,
    );
  }

  async function waitForSettledBootstrap() {
    await waitFor(() => expect(countOf(tracker.calls, 'GET', '/me/legal-acceptances/pending')).toBeGreaterThan(0));
    await waitFor(() => expect(countOf(tracker.calls, 'GET', '/me/modules')).toBeGreaterThan(0));
    await waitFor(() => expect(countOf(tracker.calls, 'GET', '/me/notifications/unread-count')).toBeGreaterThan(0));
    await waitUntilQuiet();
  }

  async function waitUntilQuiet() {
    let lastCount = -1;
    let quietChecks = 0;
    await waitFor(() => {
      const count = tracker.calls.length;
      quietChecks = queryClient.isFetching() === 0 && count === lastCount ? quietChecks + 1 : 0;
      lastCount = count;
      expect(quietChecks).toBeGreaterThanOrEqual(5);
    }, { timeout: 8000, interval: 50 });
  }

  it('requests the profile and the pending legal acceptances exactly once', async () => {
    mountAuthenticatedTree();
    await waitForSettledBootstrap();

    expect(countOf(tracker.calls, 'POST', '/auth/refresh')).toBe(1);
    expect(countOf(tracker.calls, 'GET', '/me')).toBe(1);
    expect(countOf(tracker.calls, 'GET', '/me/legal-acceptances/pending')).toBe(1);
    expect(countOf(tracker.calls, 'GET', '/me/modules')).toBe(1);
  });

  it('starts the legal acceptances and the modules before the profile request', async () => {
    mountAuthenticatedTree();
    await waitForSettledBootstrap();

    const refreshStart = indexOf(tracker.calls, 'POST', '/auth/refresh');
    const profileStart = indexOf(tracker.calls, 'GET', '/me');
    const pendingStart = indexOf(tracker.calls, 'GET', '/me/legal-acceptances/pending');
    const modulesStart = indexOf(tracker.calls, 'GET', '/me/modules');
    expect(refreshStart).toBeGreaterThanOrEqual(0);
    expect(refreshStart).toBeLessThan(pendingStart);
    expect(refreshStart).toBeLessThan(modulesStart);
    expect(pendingStart).toBeLessThan(profileStart);
    expect(modulesStart).toBeLessThan(profileStart);
  });

  it('accepting the legal documents after the bootstrap refetches the pending acceptances once', async () => {
    mountAuthenticatedTree();
    await waitForSettledBootstrap();
    const { result } = renderHook(() => useAcceptLegalDocuments(), {
      wrapper: ({ children }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
    });

    await act(async () => {
      await result.current.mutateAsync();
    });

    await waitFor(() => expect(countOf(tracker.calls, 'GET', '/me/legal-acceptances/pending')).toBe(2));
    await waitUntilQuiet();
    expect(countOf(tracker.calls, 'POST', '/me/legal-acceptances')).toBe(1);
    expect(countOf(tracker.calls, 'GET', '/me/legal-acceptances/pending')).toBe(2);
    expect(countOf(tracker.calls, 'GET', '/me')).toBe(1);
  });

  it('a rejected refresh ends the session without a loop or a cascade of requests', async () => {
    server.use(http.post(`${API}/auth/refresh`, () => HttpResponse.json({ code: 'INVALID_REFRESH_TOKEN', requestId: 'req-test-0001', retryable: false, violations: [] }, { status: 401 })));
    mountAuthenticatedTree();

    await waitFor(() => expect(localStorage.getItem('sessionHint')).toBeNull());
    await waitUntilQuiet();

    expect(countOf(tracker.calls, 'POST', '/auth/refresh')).toBe(1);
    expect(countOf(tracker.calls, 'GET', '/me')).toBe(0);
    expect(countOf(tracker.calls, 'GET', '/me/notifications/unread-count')).toBe(0);
    expect(queryClient.getQueryData(['me'])).toBeUndefined();
  });

  it('a rejected profile request ends the session cleaning the cache and without a cascade', async () => {
    server.use(http.get(`${API}/me`, () => HttpResponse.json({ code: 'UNAUTHORIZED', requestId: 'req-test-0001', retryable: false, violations: [] }, { status: 401 })));
    mountAuthenticatedTree();

    await waitFor(() => expect(localStorage.getItem('sessionHint')).toBeNull());
    await waitUntilQuiet();

    expect(countOf(tracker.calls, 'GET', '/me')).toBe(2);
    expect(countOf(tracker.calls, 'GET', '/me/notifications/unread-count')).toBe(0);
    expect(queryClient.getQueryCache().getAll().filter((query) => query.state.data !== undefined)).toHaveLength(0);
  });
});
