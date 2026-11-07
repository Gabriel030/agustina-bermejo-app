import { describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { ThemeProvider } from '@/context/ThemeContext';
import { server } from '@/test/mocks/server';
import { createDeferred } from '@/test/deferred';
import { installFixedClock } from '@/test/fixedClock';
import { errorBody } from '@/test/fixtures/errors';
import {
  CERTIFICATE_CODE,
  CERTIFICATE_CODE_PRINTED,
  graduationCertificate,
  regularCertificate,
} from '@/test/fixtures/certificates';
import { VerifyCertificate } from '@/features/graduation/certificates/pages/VerifyCertificate';

const API = 'http://localhost:8080';
const ENDPOINT = `${API}/public/certificates/:code`;

function LocationProbe() {
  return <span data-testid="location">{useLocation().pathname}</span>;
}

function renderPage(path = `/verificar/${CERTIFICATE_CODE}`) {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/verificar/:code" element={<VerifyCertificate />} />
          </Routes>
          <LocationProbe />
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

function respondWith(body, init) {
  server.use(http.get(ENDPOINT, () => HttpResponse.json(body, init)));
}

describe('VerifyCertificate', () => {
  installFixedClock();

  it('showsValidGraduationCertificateWithRegistryAndHours', async () => {
    respondWith(graduationCertificate());
    renderPage();

    expect(await screen.findByRole('heading', { level: 1, name: 'Válido' })).toBeInTheDocument();
    expect(screen.getByText('Documento auténtico.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Certificado de egreso' })).toBeInTheDocument();
    expect(screen.getByText('Pérez, Ana')).toBeInTheDocument();
    expect(screen.getByText('*****678')).toBeInTheDocument();
    expect(screen.getByText('15/07/2026')).toBeInTheDocument();
    expect(screen.getByText('N.º 1284 · 1.860 horas')).toBeInTheDocument();
    expect(screen.getByText(CERTIFICATE_CODE_PRINTED)).toBeInTheDocument();
    expect(screen.getByText('Consultado el 05/11/2026 12:00')).toBeInTheDocument();
  });

  it('showsValidRegularCertificateWithValidityEnd', async () => {
    respondWith(regularCertificate());
    renderPage();

    expect(await screen.findByText('Documento auténtico y vigente.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Constancia de alumno regular' })).toBeInTheDocument();
    expect(screen.getByText('Vigente hasta')).toBeInTheDocument();
    expect(screen.getByText('06/12/2026')).toBeInTheDocument();
    expect(screen.queryByText('Registro')).not.toBeInTheDocument();
  });

  it('showsExpiredCertificateWithExpirationDate', async () => {
    respondWith(regularCertificate({ status: 'EXPIRED', expiresAt: '2026-10-06T23:59:59' }));
    renderPage();

    expect(await screen.findByRole('heading', { level: 1, name: 'Vencido' })).toBeInTheDocument();
    expect(
      screen.getByText('Venció el 06/10/2026. Ya no acredita la condición de alumno regular.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Venció')).toBeInTheDocument();
  });

  it('showsRevokedCertificateWithRevocationDate', async () => {
    respondWith(graduationCertificate({ status: 'REVOKED', revokedAt: '2026-11-02T08:30:00' }));
    renderPage();

    expect(await screen.findByRole('heading', { level: 1, name: 'Revocado' })).toBeInTheDocument();
    expect(screen.getByText('El instituto lo anuló. Ya no acredita el título.')).toBeInTheDocument();
    expect(screen.getByText('Revocado el')).toBeInTheDocument();
    expect(screen.getByText('02/11/2026')).toBeInTheDocument();
  });

  it('showsNeutralNotFoundWithoutImplyingTheCodeExisted', async () => {
    server.use(
      http.get(ENDPOINT, () => HttpResponse.json(errorBody('CERTIFICATE_NOT_FOUND'), { status: 404 })),
    );
    renderPage();

    expect(await screen.findByRole('heading', { level: 1, name: 'Sin resultados' })).toBeInTheDocument();
    expect(screen.getByText('No encontramos un documento con este código. Revisá que esté completo.')).toBeInTheDocument();
    expect(screen.queryByText(/venci|revoc|anul/i)).not.toBeInTheDocument();
  });

  it('showsLoadingSkeletonUntilTheLookupResolves', async () => {
    const gate = createDeferred();
    server.use(
      http.get(ENDPOINT, async () => {
        await gate.promise;
        return HttpResponse.json(graduationCertificate());
      }),
    );
    const { container } = renderPage();

    await waitFor(() => expect(container.querySelector('[aria-busy="true"]')).toBeInTheDocument());
    expect(screen.queryByRole('heading', { level: 1 })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Verificar otro código/ })).not.toBeInTheDocument();

    gate.resolve();
    expect(await screen.findByRole('heading', { level: 1, name: 'Válido' })).toBeInTheDocument();
  });

  it('retriesAfterNetworkErrorAndShowsTheResult', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(new TypeError('Failed to fetch'));
    renderPage();

    expect(await screen.findByRole('heading', { level: 1, name: 'No pudimos verificar' })).toBeInTheDocument();
    respondWith(graduationCertificate());
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Válido' })).toBeInTheDocument();
    fetchSpy.mockRestore();
  });

  it('treatsServerErrorAsRetryableFailure', async () => {
    server.use(http.get(ENDPOINT, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })));
    renderPage();

    expect(await screen.findByRole('heading', { level: 1, name: 'No pudimos verificar' })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('doesNotQueryMalformedCodesAndShowsNotFound', async () => {
    let calls = 0;
    server.use(
      http.get(ENDPOINT, () => {
        calls += 1;
        return HttpResponse.json(graduationCertificate());
      }),
    );
    renderPage('/verificar/ABC-123');

    expect(await screen.findByRole('heading', { level: 1, name: 'Sin resultados' })).toBeInTheDocument();
    expect(calls).toBe(0);
  });

  it('sendsNormalizedCodeWithoutCredentialsCacheOrReferrer', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    respondWith(graduationCertificate());
    renderPage(`/verificar/${CERTIFICATE_CODE_PRINTED.toLowerCase()}`);

    await screen.findByRole('heading', { level: 1, name: 'Válido' });
    const [url, init] = fetchSpy.mock.calls.find(([target]) => String(target).includes('/public/certificates/'));
    expect(String(url)).toBe(`${API}/public/certificates/${CERTIFICATE_CODE}`);
    expect(init).toMatchObject({ credentials: 'omit', cache: 'no-store', referrerPolicy: 'no-referrer' });
    expect(init.headers).not.toHaveProperty('Authorization');
    fetchSpy.mockRestore();
  });

  it('keepsTheOtherCodeFieldCollapsedUntilOpened', async () => {
    respondWith(graduationCertificate());
    renderPage();

    await screen.findByRole('heading', { level: 1, name: 'Válido' });
    const toggle = screen.getByRole('button', { name: /Verificar otro código/ });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByLabelText('Código del documento')).not.toBeInTheDocument();

    await userEvent.click(toggle);
    expect(screen.getByLabelText('Código del documento')).toBeInTheDocument();
  });

  it('formatsTypedCodeInGroupsAndAcceptsTwentySixCharacters', async () => {
    respondWith(graduationCertificate());
    renderPage();
    await screen.findByRole('heading', { level: 1, name: 'Válido' });
    await userEvent.click(screen.getByRole('button', { name: /Verificar otro código/ }));

    const input = screen.getByLabelText('Código del documento');
    await userEvent.type(input, ' 7qk3m 9xh2v-bn4t8rgjda1b2c3xEXTRA');

    expect(input).toHaveValue(CERTIFICATE_CODE_PRINTED);
    expect(screen.getByRole('button', { name: 'Verificar' })).toBeEnabled();
  });

  it('navigatesToTheNormalizedCodeWhenVerifyingAnotherOne', async () => {
    respondWith(graduationCertificate());
    renderPage('/verificar/00000000000000000000000000');
    await screen.findByRole('heading', { level: 1, name: 'Válido' });
    await userEvent.click(screen.getByRole('button', { name: /Verificar otro código/ }));
    await userEvent.type(screen.getByLabelText('Código del documento'), '7qk3m-9xh2v bn4t8 rgjda 1b2c3x');
    await userEvent.click(screen.getByRole('button', { name: 'Verificar' }));

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(`/verificar/${CERTIFICATE_CODE}`));
  });

  it('showsRateLimitCountdownFromRetryAfterAndBlocksActionsUntilItEnds', async () => {
    server.use(
      http.get(ENDPOINT, () =>
        HttpResponse.json(errorBody('RATE_LIMITED'), { status: 429, headers: { 'Retry-After': '3' } }),
      ),
    );
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'], shouldAdvanceTime: true });
    try {
      renderPage();

      expect(await screen.findByRole('heading', { level: 1, name: 'Demasiadas consultas' })).toBeInTheDocument();
      expect(screen.getByText('Probá de nuevo en 3 s.')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Reintentar' })).toBeDisabled();

      for (let second = 0; second < 3; second += 1) {
        await act(() => vi.advanceTimersByTimeAsync(1000));
      }

      expect(screen.getByText('Ya podés probar de nuevo.')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Reintentar' })).toBeEnabled();
    } finally {
      vi.useRealTimers();
    }
  });

  it('usesDefaultWaitWhenRateLimitHasNoRetryAfter', async () => {
    server.use(http.get(ENDPOINT, () => HttpResponse.json(errorBody('RATE_LIMITED'), { status: 429 })));
    renderPage();

    expect(await screen.findByText('Probá de nuevo en 60 s.')).toBeInTheDocument();
  });
});
