import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { ThemeProvider } from '@/context/ThemeContext';
import { AppRouter } from '@/routes/AppRouter';
import { createQueryClient } from '@/api/queryClient';

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

  beforeEach(() => {
    localStorage.setItem('sessionHint', '1');
    tracker = trackRequests();
  });

  afterEach(() => {
    tracker.stop();
    localStorage.clear();
  });

  function mountAuthenticatedTree() {
    return render(
      <GoogleOAuthProvider clientId="test-client-id">
        <ThemeProvider>
          <QueryClientProvider client={createQueryClient()}>
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
    await new Promise((resolve) => setTimeout(resolve, 300));
  }

  it('requests the profile and the pending legal acceptances exactly once', async () => {
    mountAuthenticatedTree();
    await waitForSettledBootstrap();

    expect(countOf(tracker.calls, 'GET', '/me')).toBe(1);
    expect(countOf(tracker.calls, 'GET', '/me/legal-acceptances/pending')).toBe(1);
    expect(countOf(tracker.calls, 'GET', '/me/modules')).toBe(1);
  });

  it('does not wait for the profile to request the pending legal acceptances', async () => {
    mountAuthenticatedTree();
    await waitForSettledBootstrap();

    const profileStart = indexOf(tracker.calls, 'GET', '/me');
    const pendingStart = indexOf(tracker.calls, 'GET', '/me/legal-acceptances/pending');
    expect(Math.abs(pendingStart - profileStart)).toBeLessThanOrEqual(2);
    expect(pendingStart).toBeLessThan(profileStart + 3);
    expect(indexOf(tracker.calls, 'POST', '/auth/refresh')).toBeLessThan(profileStart);
  });
});
