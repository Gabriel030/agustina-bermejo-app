import { afterEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { ThemeProvider } from '@/context/ThemeContext';
import { AppRouter } from '@/routes/AppRouter';
import { PUBLIC_ROUTES } from '@/routes/featureRoutes';
import { CERTIFICATE_CODE } from '@/test/fixtures/certificates';
import { publicDocumentVerification } from '@/test/fixtures/constancias';

const API = 'http://localhost:8080';

function renderApp(path) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return {
    queryClient,
    ...render(
      <GoogleOAuthProvider clientId="test-client-id">
        <ThemeProvider>
          <QueryClientProvider client={queryClient}>
            <MemoryRouter initialEntries={[path]}>
              <AuthProvider>
                <AppRouter />
              </AuthProvider>
            </MemoryRouter>
          </QueryClientProvider>
        </ThemeProvider>
      </GoogleOAuthProvider>,
    ),
  };
}

describe('VerifyCertificate route', () => {
  afterEach(() => {
    localStorage.clear();
  });

  it('declaresTheVerificationPathAsPublic', () => {
    expect(PUBLIC_ROUTES.map((route) => route.path)).toContain('/verificar/:code');
  });

  it('opensWithoutASessionAndNeverRedirectsToLogin', async () => {
    let publicCalls = 0;
    server.use(
      http.get(`${API}/public/documents/:code`, () => {
        publicCalls += 1;
        return HttpResponse.json(publicDocumentVerification());
      }),
    );
    const { queryClient } = renderApp(`/verificar/${CERTIFICATE_CODE}`);

    expect(await screen.findByRole('heading', { level: 1, name: 'Este documento fue emitido por el instituto' })).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('DNI o email')).not.toBeInTheDocument();
    expect(publicCalls).toBe(1);
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
  });
});
