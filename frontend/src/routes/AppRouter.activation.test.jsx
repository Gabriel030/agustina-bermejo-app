import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { formatContractDeviations, takeContractDeviations } from '@/test/contract/withContract';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { ThemeProvider } from '@/context/ThemeContext';
import { AppRouter } from '@/routes/AppRouter';

const API = 'http://localhost:8080';
const TOKEN = 'tok-router-456';
const NOT_YET_CONTRACTED = 'POST /auth/activation-token/status';

vi.mock('idb-keyval', () => ({
  get: vi.fn(async () => undefined),
  set: vi.fn(async () => {}),
  del: vi.fn(async () => {}),
  keys: vi.fn(async () => []),
}));

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>;
}

function renderApp(initialPath) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return {
    queryClient,
    ...render(
      <GoogleOAuthProvider clientId="test-client-id">
        <ThemeProvider>
          <QueryClientProvider client={queryClient}>
            <MemoryRouter initialEntries={[initialPath]}>
              <AuthProvider>
                <AppRouter />
              </AuthProvider>
              <LocationProbe />
            </MemoryRouter>
          </QueryClientProvider>
        </ThemeProvider>
      </GoogleOAuthProvider>,
    ),
  };
}

describe('AppRouter activation page', () => {
  beforeEach(() => {
    vi.stubGlobal('indexedDB', {});
  });

  afterEach(() => {
    const unexpected = takeContractDeviations().filter((deviation) => deviation.key !== NOT_YET_CONTRACTED);
    if (unexpected.length > 0) throw new Error(formatContractDeviations(unexpected));
    vi.unstubAllGlobals();
  });

  it('showsTheActivationFormToAVisitorWithoutASessionAndKeepsTheTokenOutOfTheUrlAndTheQueryCache', async () => {
    server.use(
      http.post(`${API}/auth/activation-token/status`, async ({ request }) => {
        expect((await request.json()).token).toBe(TOKEN);
        return HttpResponse.json({ status: 'VALID', username: 'lucia.acosta', institution: { name: 'Instituto', email: 'a@b.test', phoneNumber: '123' } });
      }),
    );
    const { queryClient } = renderApp(`/activar?token=${TOKEN}`);

    expect(await screen.findByRole('heading', { name: 'Activá tu cuenta' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/activar$/));
    expect(screen.queryByPlaceholderText('DNI o email')).not.toBeInTheDocument();
    const cached = queryClient.getQueryCache().getAll().map((query) => ({ key: query.queryKey, data: query.state.data }));
    expect(JSON.stringify(cached)).not.toContain(TOKEN);
  });

  it('showsTheInvalidLinkScreenWhenThereIsNoToken', async () => {
    renderApp('/activar');

    expect(await screen.findByRole('heading', { name: 'Enlace no válido' })).toBeInTheDocument();
  });
});
