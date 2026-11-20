import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { axe } from '@/test/axe';
import { installFixedClock } from '@/test/fixedClock';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { ReviewEmailChangePage } from '@/features/identity/pages/ReviewEmailChangePage';

const API = 'http://localhost:8080';
const TOKEN = 'a'.repeat(43);
const PREVIEW_URL = `${API}/email-change-preview`;
const APPROVE_URL = `${API}/approve-email-change`;
const CANCEL_URL = `${API}/cancel-email-change`;

installFixedClock({ now: '2026-11-20T12:00:00-03:00' });

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location">{`${location.pathname}${location.search}`}</span>;
}

function renderPage(route = `/revisar-cambio-email?token=${TOKEN}`, mode = 'review') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <ReviewEmailChangePage mode={mode} />
      <LocationProbe />
    </MemoryRouter>,
  );
}

const invalidToken = () => HttpResponse.json(errorBody('INVALID_EMAIL_VERIFICATION_TOKEN'), { status: 400 });

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ReviewEmailChangePage', () => {
  it('showsSkeletonWhilePreviewLoads', async () => {
    server.use(http.post(PREVIEW_URL, () => new Promise(() => {})));
    renderPage();

    expect(await screen.findByLabelText('Cargando el cambio de email')).toHaveAttribute('aria-busy', 'true');
  });

  it('showsReviewStepWithMaskedEmailsAndExpiry', async () => {
    renderPage();

    expect(await screen.findByText('¿Cambiamos tu email?')).toBeInTheDocument();
    expect(screen.getByText(/Lo pidió Administrativo el 19\/11 a las 20:41\./)).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: 'Hoy' })).toBeInTheDocument();
    expect(screen.getByText('m•••a@gmail.com')).toBeInTheDocument();
    expect(screen.getByText('m•••z@alumnos.instituto.edu.ar')).toBeInTheDocument();
    expect(screen.getByText('Paso 1 de 2')).toBeInTheDocument();
    expect(screen.getByText('Vence en 33 h')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Revisar y aprobar' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'No lo pedí, cancelar' })).toBeInTheDocument();
  });

  it('keepsTokenOutOfTheUrlAndSendsItOnlyInTheBody', async () => {
    const received = [];
    server.use(
      http.post(PREVIEW_URL, async ({ request }) => {
        received.push({ url: request.url, body: await request.json() });
        return HttpResponse.json({
          currentEmailMasked: 'm•••a@gmail.com',
          newEmailMasked: 'm•••z@hotmail.com',
          requestedByRole: 'ADMINISTRATIVO',
          requestedAt: '2026-11-19T20:41:00',
          expiresAt: '2026-11-21T20:41:00',
        });
      }),
    );
    renderPage();

    await screen.findByText('¿Cambiamos tu email?');
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/revisar-cambio-email$/));
    expect(received).toHaveLength(1);
    expect(received[0].body).toEqual({ token: TOKEN });
    expect(received[0].url).not.toContain('token=');
  });

  it('goesToConfirmStepAndBackToReview', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Revisar y aprobar' }));
    expect(await screen.findByText('Confirmá el cambio')).toBeInTheDocument();
    expect(screen.getByText('Paso 2 de 2')).toBeInTheDocument();
    expect(screen.getByText('Te mandamos un enlace al email nuevo.')).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: 'Sesiones' })).toBeInTheDocument();
    expect(screen.getByText('Se cierran al verificar')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Volver' }));
    expect(await screen.findByText('¿Cambiamos tu email?')).toBeInTheDocument();
  });

  it('approvesTheChangeAndAsksToOpenTheLinkSentToTheNewEmail', async () => {
    let body;
    server.use(
      http.post(APPROVE_URL, async ({ request }) => {
        body = await request.json();
        return new HttpResponse(null, { status: 200 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Revisar y aprobar' }));
    await user.click(screen.getByRole('button', { name: 'Aprobar cambio' }));

    expect(await screen.findByText('Falta un paso')).toBeInTheDocument();
    expect(screen.getByText('Abrí el enlace que enviamos a m•••z@alumnos.instituto.edu.ar.')).toBeInTheDocument();
    expect(body).toEqual({ token: TOKEN });
    expect(screen.getByRole('link', { name: 'Ir a ingresar' })).toHaveAttribute('href', '/login');
  });

  it('cancelsFromTheFirstStep', async () => {
    let body;
    server.use(
      http.post(CANCEL_URL, async ({ request }) => {
        body = await request.json();
        return new HttpResponse(null, { status: 200 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'No lo pedí, cancelar' }));

    expect(await screen.findByText('Cambio cancelado')).toBeInTheDocument();
    expect(screen.getByText('Tu cuenta sigue con m•••a@gmail.com.')).toBeInTheDocument();
    expect(body).toEqual({ token: TOKEN });
  });

  it('sendsASingleRequestOnDoubleClick', async () => {
    let calls = 0;
    server.use(
      http.post(APPROVE_URL, () => {
        calls += 1;
        return new HttpResponse(null, { status: 200 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Revisar y aprobar' }));
    await user.dblClick(screen.getByRole('button', { name: 'Aprobar cambio' }));
    await screen.findByText('Falta un paso');

    expect(calls).toBe(1);
  });

  it('showsInvalidLinkWhenThePreviewRejectsTheToken', async () => {
    server.use(http.post(PREVIEW_URL, invalidToken));
    renderPage();

    expect(await screen.findByText('Enlace no válido')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Venció o ya se usó. Si no fuiste vos, revisalo en Mi perfil.');
    expect(screen.getByRole('link', { name: 'Ir a ingresar' })).toBeInTheDocument();
  });

  it('showsInvalidLinkWhenApprovingAnExpiredToken', async () => {
    server.use(http.post(APPROVE_URL, invalidToken));
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Revisar y aprobar' }));
    await user.click(screen.getByRole('button', { name: 'Aprobar cambio' }));

    expect(await screen.findByText('Enlace no válido')).toBeInTheDocument();
  });

  it('showsInvalidLinkWithoutRequestWhenTheTokenIsMissing', () => {
    let calls = 0;
    server.use(
      http.post(PREVIEW_URL, () => {
        calls += 1;
        return HttpResponse.json({});
      }),
    );
    renderPage('/revisar-cambio-email');

    expect(screen.getByText('Enlace no válido')).toBeInTheDocument();
    expect(calls).toBe(0);
  });

  it('offersRetryAfterANetworkErrorOnPreview', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(new TypeError('Failed to fetch'));
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Sin conexión')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('¿Cambiamos tu email?')).toBeInTheDocument();
  });

  it('treatsTooManyRequestsAsRetryable', async () => {
    let calls = 0;
    server.use(
      http.post(PREVIEW_URL, () => {
        calls += 1;
        if (calls === 1) return HttpResponse.json(errorBody('RATE_LIMITED', { retryable: true }), { status: 429, headers: { 'Retry-After': '1' } });
        return HttpResponse.json({
          currentEmailMasked: 'm•••a@gmail.com',
          newEmailMasked: 'm•••z@hotmail.com',
          requestedByRole: 'ADMINISTRATIVO',
          requestedAt: '2026-11-19T20:41:00',
          expiresAt: '2026-11-21T20:41:00',
        });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Sin conexión')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('¿Cambiamos tu email?')).toBeInTheDocument();
  });

  it('retriesTheApprovalAfterANetworkError', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Revisar y aprobar' }));
    vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(new TypeError('Failed to fetch'));
    await user.click(screen.getByRole('button', { name: 'Aprobar cambio' }));
    expect(await screen.findByText('Sin conexión')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Falta un paso')).toBeInTheDocument();
  });

  it('movesFocusToTheMessageOnEveryStateChange', async () => {
    const user = userEvent.setup();
    renderPage();

    const region = await screen.findByRole('status');
    await waitFor(() => expect(region).toHaveFocus());
    await user.click(screen.getByRole('button', { name: 'Revisar y aprobar' }));
    await screen.findByText('Confirmá el cambio');
    await waitFor(() => expect(screen.getByRole('status')).toHaveFocus());
  });

  it('showsExpiredInsteadOfOneHourWhenTheTokenAlreadyExpired', async () => {
    server.use(
      http.post(PREVIEW_URL, () =>
        HttpResponse.json({
          currentEmailMasked: 'm•••a@gmail.com',
          newEmailMasked: 'm•••z@hotmail.com',
          requestedByRole: 'ADMINISTRATIVO',
          requestedAt: '2026-11-19T20:41:00',
          expiresAt: '2026-11-20T11:00:00',
        }),
      ),
    );
    renderPage();

    expect(await screen.findByText('Venció')).toBeInTheDocument();
    expect(screen.queryByText(/Vence en/)).not.toBeInTheDocument();
  });

  it.each([
    [404, 'No encontramos el cambio'],
    [409, 'El cambio ya no se puede resolver'],
    [400, 'Enlace no válido'],
  ])('tellsStatus%dApartFromTheOthers', async (status, title) => {
    server.use(http.post(PREVIEW_URL, () => HttpResponse.json(errorBody(status === 400 ? 'INVALID_EMAIL_VERIFICATION_TOKEN' : 'SOME_ERROR'), { status })));
    renderPage();

    expect(await screen.findByText(title)).toBeInTheDocument();
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('offersRetryOnAServerErrorInsteadOfADeadEnd', async () => {
    let calls = 0;
    server.use(
      http.post(PREVIEW_URL, () => {
        calls += 1;
        if (calls === 1) return HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 });
        return HttpResponse.json({
          currentEmailMasked: 'm•••a@gmail.com',
          newEmailMasked: 'm•••z@hotmail.com',
          requestedByRole: 'ADMINISTRATIVO',
          requestedAt: '2026-11-19T20:41:00',
          expiresAt: '2026-11-21T20:41:00',
        });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Sin conexión')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('¿Cambiamos tu email?')).toBeInTheDocument();
  });

  it('retriesTheApprovalAfterAServerError', async () => {
    let calls = 0;
    server.use(
      http.post(APPROVE_URL, () => {
        calls += 1;
        return calls === 1 ? HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 503 }) : new HttpResponse(null, { status: 200 });
      }),
    );
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Revisar y aprobar' }));
    await user.click(screen.getByRole('button', { name: 'Aprobar cambio' }));
    expect(await screen.findByText('Sin conexión')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Falta un paso')).toBeInTheDocument();
  });

  describe('legacy cancel link', () => {
    const legacyRoute = `/cancelar-cambio-email?token=${TOKEN}`;

    it('cancelsEndToEndWithoutAskingForThePreview', async () => {
      let previews = 0;
      let approvals = 0;
      let cancelBody;
      server.use(
        http.post(PREVIEW_URL, () => {
          previews += 1;
          return invalidToken();
        }),
        http.post(APPROVE_URL, () => {
          approvals += 1;
          return new HttpResponse(null, { status: 200 });
        }),
        http.post(CANCEL_URL, async ({ request }) => {
          cancelBody = await request.json();
          return new HttpResponse(null, { status: 200 });
        }),
      );
      const user = userEvent.setup();
      renderPage(legacyRoute, 'cancel');

      expect(screen.getByText('¿No pediste cambiar tu email?')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /aprobar/i })).not.toBeInTheDocument();
      await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/cancelar-cambio-email$/));
      await user.click(screen.getByRole('button', { name: 'Cancelar el cambio' }));

      expect(await screen.findByText('Cambio cancelado')).toBeInTheDocument();
      expect(screen.getByText('Tu cuenta sigue con el mismo correo.')).toBeInTheDocument();
      expect(cancelBody).toEqual({ token: TOKEN });
      expect(previews).toBe(0);
      expect(approvals).toBe(0);
    });

    it('sendsASingleCancelRequestOnDoubleClick', async () => {
      let calls = 0;
      server.use(
        http.post(CANCEL_URL, () => {
          calls += 1;
          return new HttpResponse(null, { status: 200 });
        }),
      );
      renderPage(legacyRoute, 'cancel');

      await userEvent.dblClick(screen.getByRole('button', { name: 'Cancelar el cambio' }));
      await screen.findByText('Cambio cancelado');

      expect(calls).toBe(1);
    });

    it('retriesTheCancelAfterANetworkError', async () => {
      vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(new TypeError('Failed to fetch'));
      const user = userEvent.setup();
      renderPage(legacyRoute, 'cancel');

      await user.click(screen.getByRole('button', { name: 'Cancelar el cambio' }));
      expect(await screen.findByText('Sin conexión')).toBeInTheDocument();
      await user.click(screen.getByRole('button', { name: 'Reintentar' }));

      expect(await screen.findByText('Cambio cancelado')).toBeInTheDocument();
    });

    it('showsInvalidLinkWhenTheCancelTokenIsRejected', async () => {
      server.use(http.post(CANCEL_URL, invalidToken));
      renderPage(legacyRoute, 'cancel');

      await userEvent.click(screen.getByRole('button', { name: 'Cancelar el cambio' }));

      expect(await screen.findByText('Enlace no válido')).toBeInTheDocument();
    });

    it('showsInvalidLinkWithoutRequestWhenTheTokenIsMissing', () => {
      renderPage('/cancelar-cambio-email', 'cancel');

      expect(screen.getByText('Enlace no válido')).toBeInTheDocument();
    });
  });

  it('hasNoAxeViolationsInEveryStep', async () => {
    const user = userEvent.setup();
    const { container } = renderPage();
    await screen.findByText('¿Cambiamos tu email?');
    expect(await axe(container)).toHaveNoViolations();

    await user.click(screen.getByRole('button', { name: 'Revisar y aprobar' }));
    await screen.findByText('Confirmá el cambio');
    expect(await axe(container)).toHaveNoViolations();

    await user.click(screen.getByRole('button', { name: 'Aprobar cambio' }));
    await screen.findByText('Falta un paso');
    expect(await axe(container)).toHaveNoViolations();
  });
});
