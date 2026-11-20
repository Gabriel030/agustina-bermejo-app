import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { ThemeProvider } from '@/context/ThemeContext';
import { AppRouter } from '@/routes/AppRouter';
import { createQueryClient } from '@/api/queryClient';

const API = 'http://localhost:8080';

function mountAuthenticatedApp() {
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

describe('first login consent gate in the app router', () => {
  beforeEach(() => localStorage.setItem('sessionHint', '1'));
  afterEach(() => localStorage.clear());

  it('wraps the protected routes when the account has no consent record', async () => {
    server.use(
      http.get(`${API}/me/contact-consents`, () =>
        HttpResponse.json([
          { channel: 'EMAIL', category: 'INSTITUTIONAL', granted: false, recordedAt: '2026-11-17T10:00:00Z', hasRecord: false },
        ]),
      ),
    );
    mountAuthenticatedApp();

    expect(await screen.findByRole('checkbox', { name: /avisos institucionales/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Continuar' })).toBeDisabled();
  });
});
