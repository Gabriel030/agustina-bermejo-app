import { afterEach, describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { recordApiRequests } from '@/test/recordApiRequests';
import { countOf } from '@/test/apiRequestCount';
import { AuthProvider } from '@/context/AuthContext';
import { ThemeProvider } from '@/context/ThemeContext';
import { AppRouter } from '@/routes/AppRouter';
import { createQueryClient } from '@/api/queryClient';

describe('login bootstrap', () => {
  let tracker;

  afterEach(() => {
    tracker.stop();
    localStorage.clear();
  });

  it('starts the pending legal acceptances and the modules together with the profile request, once each', async () => {
    tracker = recordApiRequests();
    const queryClient = createQueryClient();
    render(
      <GoogleOAuthProvider clientId="test-client-id">
        <ThemeProvider>
          <QueryClientProvider client={queryClient}>
            <MemoryRouter initialEntries={['/login']}>
              <AuthProvider>
                <AppRouter />
              </AuthProvider>
            </MemoryRouter>
          </QueryClientProvider>
        </ThemeProvider>
      </GoogleOAuthProvider>,
    );
    const user = userEvent.setup();
    await user.type(screen.getByPlaceholderText('DNI o email'), 'prueba.login');
    await user.type(screen.getByPlaceholderText('••••••••'), 'Test1234');
    await user.click(screen.getByRole('button', { name: /ingresar/i }));

    await waitFor(() => expect(tracker.requests).toContain('GET /me/notifications/unread-count'));
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));

    const startOf = (call) => tracker.requests.indexOf(call);
    expect(countOf(tracker.requests, 'GET /me')).toBe(1);
    expect(countOf(tracker.requests, 'GET /me/legal-acceptances/pending')).toBe(1);
    expect(countOf(tracker.requests, 'GET /me/modules')).toBe(1);
    expect(startOf('GET /me/legal-acceptances/pending')).toBeLessThan(startOf('GET /me'));
    expect(startOf('GET /me/modules')).toBeLessThan(startOf('GET /me'));
  });
});
