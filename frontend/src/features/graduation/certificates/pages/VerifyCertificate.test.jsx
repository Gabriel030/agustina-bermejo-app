import { describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { ThemeProvider } from '@/context/ThemeContext';
import { axe } from '@/test/axe';
import { server } from '@/test/mocks/server';
import { createDeferred } from '@/test/deferred';
import { errorBody } from '@/test/fixtures/errors';
import { CERTIFICATE_CODE, CERTIFICATE_CODE_PRINTED } from '@/test/fixtures/certificates';
import { PUBLIC_DOCUMENT_VERIFICATION_BY_TYPE, publicDocumentVerification } from '@/test/fixtures/constancias';
import {
  publicDocumentExpiredHandler,
  publicDocumentNotFoundHandler,
  publicDocumentRateLimitedHandler,
  publicDocumentReplacedHandler,
  publicDocumentRevokedHandler,
} from '@/test/mocks/constanciasStateHandlers';
import { VerifyCertificate } from '@/features/graduation/certificates/pages/VerifyCertificate';

const API = 'http://localhost:8080';
const ENDPOINT = `${API}/public/documents/:code`;
const UNKNOWN_CODE = '00000000000000000000000000';

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

const verdictHeading = (name) => screen.findByRole('heading', { level: 1, name });

describe('VerifyCertificate', () => {
  it('showsValidDocumentWithVerdictFirstAndMaskedData', async () => {
    respondWith(publicDocumentVerification({ holderFullName: 'L*** M*** F*******', maskedDocument: '**.***.088', registryNumber: '0004217/2026' }));
    renderPage();

    expect(await verdictHeading('Este documento fue emitido por el instituto')).toBeInTheDocument();
    const verdict = screen.getByRole('status');
    expect(within(verdict).getByText('Auténtica y vigente')).toBeInTheDocument();
    expect(within(verdict).getByText('Coincide con el registro oficial y sigue vigente.')).toBeInTheDocument();
    expect(screen.getByText('Constancia de alumno regular')).toBeInTheDocument();
    expect(screen.getByText('L*** M*** F*******')).toBeInTheDocument();
    expect(screen.getByText('**.***.088')).toBeInTheDocument();
    expect(screen.getByText('0004217/2026')).toBeInTheDocument();
    expect(screen.getByText('18 de noviembre de 2026')).toBeInTheDocument();
    expect(screen.getByText('Vigente hasta el 18 de diciembre de 2026')).toBeInTheDocument();
    expect(screen.getByText(`verificar/${CERTIFICATE_CODE_PRINTED}`)).toBeInTheDocument();
    expect(screen.getByText(/esta página no muestra notas ni materias/)).toBeInTheDocument();
  });

  it.each([
    ['GRADUATION_CERTIFICATE', 'Certificado de egreso'],
    ['REGULAR_STUDENT_CERTIFICATE', 'Constancia de alumno regular'],
    ['APPROVED_SUBJECTS_CERTIFICATE', 'Constancia de materias aprobadas'],
    ['EXAM_REGISTRATION_CERTIFICATE', 'Constancia de inscripción a examen'],
    ['EXAM_CERTIFICATE', 'Constancia de examen'],
  ])('labelsTheDocumentTypeFor%s', async (documentType, label) => {
    respondWith(PUBLIC_DOCUMENT_VERIFICATION_BY_TYPE[documentType]());
    renderPage();

    await verdictHeading('Este documento fue emitido por el instituto');
    expect(screen.getByText(label)).toBeInTheDocument();
  });

  it('showsVigentWithoutEndDateWhenTheDocumentDoesNotExpire', async () => {
    respondWith(PUBLIC_DOCUMENT_VERIFICATION_BY_TYPE.GRADUATION_CERTIFICATE());
    renderPage();

    await verdictHeading('Este documento fue emitido por el instituto');
    expect(screen.getByText('Vigente')).toBeInTheDocument();
    expect(screen.getByText('EG-2026-000123')).toBeInTheDocument();
  });

  it('showsExpiredDocumentAsAuthenticButNoLongerValid', async () => {
    server.use(publicDocumentExpiredHandler);
    renderPage();

    expect(await verdictHeading('Este documento es auténtico, pero su vigencia terminó')).toBeInTheDocument();
    expect(screen.getByText('Auténtica · vencida')).toBeInTheDocument();
    expect(
      screen.getByText('Venció el 18 de diciembre de 2026. Pedí a la persona interesada una constancia nueva.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Venció el 18 de diciembre de 2026')).toBeInTheDocument();
  });

  it('showsRevokedDocumentWithTheRevocationDate', async () => {
    server.use(publicDocumentRevokedHandler);
    renderPage();

    expect(await verdictHeading('Este documento fue anulado por el instituto')).toBeInTheDocument();
    expect(screen.getByText('Revocada')).toBeInTheDocument();
    expect(screen.getByText('No tiene validez desde el 20 de noviembre de 2026.')).toBeInTheDocument();
    expect(screen.getByText('Revocada el 20 de noviembre de 2026')).toBeInTheDocument();
    expect(screen.queryByText(/motivo/i)).not.toBeInTheDocument();
  });

  it('showsReplacedDocumentWithoutExplainingWhy', async () => {
    server.use(publicDocumentReplacedHandler);
    renderPage();

    expect(await verdictHeading('Este documento fue reemplazado por una versión nueva')).toBeInTheDocument();
    expect(screen.getByText('Pedí a la persona interesada la versión vigente.')).toBeInTheDocument();
    expect(screen.getByText('Reemplazada el 20 de noviembre de 2026')).toBeInTheDocument();
  });

  it('showsGenericNotFoundForAnUnknownCode', async () => {
    server.use(publicDocumentNotFoundHandler);
    renderPage();

    expect(await verdictHeading('No encontramos un documento con ese código')).toBeInTheDocument();
    expect(screen.getByText('No encontrada')).toBeInTheDocument();
    expect(
      screen.getByText('Revisá el código impreso debajo del QR y volvé a intentar. Si el problema sigue, el documento podría no ser auténtico.'),
    ).toBeInTheDocument();
    expect(screen.queryByText(/venci|revoc|anul|reemplaz/i)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Verificar código' })).toBeInTheDocument();
  });

  it('rendersTheSameNotFoundPageForAnyCode', async () => {
    server.use(publicDocumentNotFoundHandler);
    const unknown = renderPage(`/verificar/${UNKNOWN_CODE}`);
    await verdictHeading('No encontramos un documento con ese código');
    const unknownText = unknown.container.firstChild.textContent;
    unknown.unmount();

    const malformed = renderPage('/verificar/ABC-12');
    await verdictHeading('No encontramos un documento con ese código');
    const malformedText = malformed.container.firstChild.textContent;
    malformed.unmount();

    const short = renderPage('/verificar/AB-7QK4-M2XD-91');
    await verdictHeading('No encontramos un documento con ese código');

    expect(malformedText).toBe(unknownText);
    expect(short.container.firstChild.textContent).toBe(unknownText);
  });

  it('doesNotQueryMalformedCodes', async () => {
    let calls = 0;
    server.use(
      http.get(ENDPOINT, () => {
        calls += 1;
        return HttpResponse.json(publicDocumentVerification());
      }),
    );
    renderPage('/verificar/ABC-123');

    await verdictHeading('No encontramos un documento con ese código');
    expect(calls).toBe(0);
  });

  it('neverRendersGradesSubjectsOrResultsOnExamCertificates', async () => {
    respondWith(publicDocumentVerification({ documentType: 'EXAM_CERTIFICATE' }));
    renderPage();

    await verdictHeading('Este documento fue emitido por el instituto');
    const labels = [...document.querySelectorAll('dt')].map((node) => node.textContent);
    expect(labels).toEqual(['Documento', 'A nombre de', 'DNI', 'Emitido el', 'Vigencia']);
    expect(screen.queryByText(/Matemática|Tecnicatura|aprobad|nota\b|resultado|condición/i)).not.toBeInTheDocument();
  });

  it('showsLoadingSkeletonUntilTheLookupResolves', async () => {
    const gate = createDeferred();
    server.use(
      http.get(ENDPOINT, async () => {
        await gate.promise;
        return HttpResponse.json(publicDocumentVerification());
      }),
    );
    const { container } = renderPage();

    await waitFor(() => expect(container.querySelector('[aria-busy="true"]')).toBeInTheDocument());
    expect(screen.queryByRole('heading', { level: 1 })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Verificar código' })).not.toBeInTheDocument();

    gate.resolve();
    expect(await verdictHeading('Este documento fue emitido por el instituto')).toBeInTheDocument();
  });

  it('retriesAfterNetworkErrorAndShowsTheResult', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(new TypeError('Failed to fetch'));
    renderPage();

    expect(await verdictHeading('No pudimos consultar el documento')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toBeInTheDocument();
    respondWith(publicDocumentVerification());
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await verdictHeading('Este documento fue emitido por el instituto')).toBeInTheDocument();
    fetchSpy.mockRestore();
  });

  it('treatsServerErrorAsRetryableFailure', async () => {
    server.use(http.get(ENDPOINT, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })));
    renderPage();

    expect(await verdictHeading('No pudimos consultar el documento')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeEnabled();
  });

  it('showsAFriendlyRateLimitMessageWithTheDefaultWait', async () => {
    server.use(publicDocumentRateLimitedHandler);
    renderPage();

    expect(await verdictHeading('Hiciste muchas verificaciones seguidas')).toBeInTheDocument();
    expect(screen.getByText('Probá de nuevo en 60 s.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeDisabled();
  });

  it('countsDownFromRetryAfterAndEnablesRetryWhenItEnds', async () => {
    server.use(
      http.get(ENDPOINT, () =>
        HttpResponse.json(errorBody('RATE_LIMIT_EXCEEDED'), { status: 429, headers: { 'Retry-After': '3' } }),
      ),
    );
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'], shouldAdvanceTime: true });
    try {
      renderPage();

      expect(await verdictHeading('Hiciste muchas verificaciones seguidas')).toBeInTheDocument();
      expect(screen.getByText('Probá de nuevo en 3 s.')).toBeInTheDocument();

      for (let second = 0; second < 3; second += 1) {
        await act(() => vi.advanceTimersByTimeAsync(1000));
      }

      expect(screen.getByText('Ya podés probar de nuevo.')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Reintentar' })).toBeEnabled();
    } finally {
      vi.useRealTimers();
    }
  });

  it('sendsNormalizedCodeToThePublicEndpointWithoutCredentialsCacheOrReferrer', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    respondWith(publicDocumentVerification());
    renderPage(`/verificar/${CERTIFICATE_CODE_PRINTED.toLowerCase()}`);

    await verdictHeading('Este documento fue emitido por el instituto');
    const [url, init] = fetchSpy.mock.calls.find(([target]) => String(target).includes('/public/documents/'));
    expect(String(url)).toBe(`${API}/public/documents/${CERTIFICATE_CODE}`);
    expect(init).toMatchObject({ credentials: 'omit', cache: 'no-store', referrerPolicy: 'no-referrer' });
    expect(init.headers).not.toHaveProperty('Authorization');
    fetchSpy.mockRestore();
  });

  it('navigatesToTheNormalizedCodeWhenVerifyingAnotherOne', async () => {
    respondWith(publicDocumentVerification());
    renderPage(`/verificar/${UNKNOWN_CODE}`);
    await verdictHeading('Este documento fue emitido por el instituto');

    await userEvent.type(screen.getByLabelText('¿Tenés otro documento? Verificá otro código'), ' 7qk3m-9xh2v bn4t8 rgjda 1b2c3x ');
    await userEvent.click(screen.getByRole('button', { name: 'Verificar código' }));

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(`/verificar/${CERTIFICATE_CODE}`));
  });

  it('keepsTheVerifyButtonDisabledUntilThereIsACode', async () => {
    server.use(publicDocumentNotFoundHandler);
    renderPage();
    await verdictHeading('No encontramos un documento con ese código');

    expect(screen.getByRole('button', { name: 'Verificar código' })).toBeDisabled();
    await userEvent.type(screen.getByLabelText('Código de verificación'), '  - ');
    expect(screen.getByRole('button', { name: 'Verificar código' })).toBeDisabled();
  });

  it.each([
    ['valid', () => respondWith(publicDocumentVerification())],
    ['revoked', () => server.use(publicDocumentRevokedHandler)],
    ['notFound', () => server.use(publicDocumentNotFoundHandler)],
  ])('hasNoAccessibilityViolationsWhen%s', async (_name, arrange) => {
    arrange();
    const { container } = renderPage();
    await screen.findByRole('heading', { level: 1 });

    expect(await axe(container)).toHaveNoViolations();
  });
});
