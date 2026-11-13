import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Toaster } from 'sonner';
import { axe } from '@/test/axe';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { RegularCertificateCard } from './RegularCertificateCard';

const API = 'http://localhost:8080';
const CODE = '7KQ2M9XD4TFH8B3NVC6R5WJE2P';

function certificate(overrides = {}) {
  return {
    id: 31,
    code: CODE,
    kind: 'PROGRAM',
    enrollmentId: 5,
    status: 'VALID',
    issuedAt: '2026-11-04T10:00:00',
    expiresAt: '2026-12-04T10:00:00',
    standingBasis: 'MANUALLY_CONFIRMED',
    billingModeAtIssue: 'OFF',
    verificationUrl: `http://localhost:5173/verificar/${CODE}`,
    ...overrides,
  };
}

function stateBody(overrides = {}) {
  return {
    kind: 'PROGRAM',
    enrollmentId: 5,
    state: 'AVAILABLE',
    staffConfirmationRequired: true,
    certificate: null,
    pendingRequest: null,
    unavailableType: null,
    rejectionReason: null,
    reasons: [],
    history: [],
    ...overrides,
  };
}

const pendingRequestBody = {
  id: 77,
  studentId: 1,
  kind: 'PROGRAM',
  enrollmentId: 5,
  channel: 'STUDENT_PORTAL',
  outcome: 'PENDING_STAFF',
  reasons: [],
  requestedAt: '2026-11-13T09:00:00',
  certificateId: null,
  rejectionReason: null,
};

function mockState(body) {
  server.use(http.get(`${API}/me/regular-certificates`, () => HttpResponse.json(body)));
}

function renderCard() {
  return renderWithProviders(
    <>
      <RegularCertificateCard carreraId={5} />
      <Toaster />
    </>,
  );
}

describe('RegularCertificateCard', () => {
  it('offers to request the certificate when available and shows the pending state after the request', async () => {
    let current = stateBody();
    const posted = [];
    server.use(
      http.get(`${API}/me/regular-certificates`, () => HttpResponse.json(current)),
      http.post(`${API}/me/regular-certificates`, async ({ request }) => {
        posted.push(await request.json());
        current = stateBody({ state: 'PENDING', pendingRequest: pendingRequestBody });
        return HttpResponse.json({ certificate: null, request: pendingRequestBody }, { status: 202 });
      }),
    );
    const user = userEvent.setup();
    renderCard();

    expect(await screen.findByText('Disponible')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Pedir constancia' }));

    expect(await screen.findByText('En revisión')).toBeInTheDocument();
    expect(posted).toEqual([{ kind: 'PROGRAM', enrollmentId: 5 }]);
    expect(screen.getByText(/Pedida el 13\/11\/2026/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pedir constancia' })).not.toBeInTheDocument();
  });

  it('cancels the pending request and goes back to available', async () => {
    let current = stateBody({
      state: 'PENDING',
      pendingRequest: pendingRequestBody,
    });
    const cancelled = [];
    server.use(
      http.get(`${API}/me/regular-certificates`, () => HttpResponse.json(current)),
      http.delete(`${API}/me/regular-certificate-requests/:id`, ({ params }) => {
        cancelled.push(params.id);
        current = stateBody();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderCard();

    await user.click(await screen.findByRole('button', { name: 'Cancelar pedido' }));

    expect(await screen.findByText('Disponible')).toBeInTheDocument();
    expect(cancelled).toEqual(['77']);
  });

  it('shows the grouped code, the dates and downloads the PDF when valid', async () => {
    mockState(stateBody({ state: 'VALID', certificate: certificate(), history: [certificate()] }));
    let downloaded = 0;
    server.use(
      http.get(`${API}/me/regular-certificates/31/pdf`, () => {
        downloaded += 1;
        return new HttpResponse(new Uint8Array([37, 80, 68, 70]), {
          headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="constancia.pdf"' },
        });
      }),
    );
    URL.createObjectURL = vi.fn(() => 'blob:certificate');
    URL.revokeObjectURL = vi.fn();
    const user = userEvent.setup();
    renderCard();

    expect(await screen.findByText('Vigente')).toBeInTheDocument();
    expect(screen.getByText('7KQ2M-9XD4T-FH8B3-NVC6R-5WJE2P')).toBeInTheDocument();
    expect(screen.getByText('04/11/2026')).toBeInTheDocument();
    expect(screen.getByText('04/12/2026')).toBeInTheDocument();
    expect(screen.queryByText('Historial')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Descargar PDF/ }));

    await waitFor(() => expect(downloaded).toBe(1));
    expect(URL.createObjectURL).toHaveBeenCalled();
  });

  it('copies the verification link of the valid certificate', async () => {
    mockState(stateBody({ state: 'VALID', certificate: certificate(), history: [certificate()] }));
    const user = userEvent.setup();
    renderCard();
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue();

    await user.click(await screen.findByRole('button', { name: /Copiar enlace de verificación/ }));

    await waitFor(() => expect(writeText).toHaveBeenCalledWith(`http://localhost:5173/verificar/${CODE}`));
    expect(await screen.findByText('Enlace copiado')).toBeInTheDocument();
  });

  it('shows an expired certificate without download, in the history, and lets the student ask for a new one', async () => {
    const expired = certificate({ status: 'EXPIRED', issuedAt: '2026-10-01T10:00:00', expiresAt: '2026-10-31T10:00:00' });
    mockState(stateBody({ history: [expired] }));
    renderCard();

    expect(await screen.findByText('Venció el 31/10/2026')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pedir una nueva' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Descargar PDF/ })).not.toBeInTheDocument();
    expect(screen.getByText('Historial')).toBeInTheDocument();
    expect(screen.getByText(/Emitida el 01\/10\/2026/)).toBeInTheDocument();
    expect(screen.getByText(/sin\s+descarga/)).toBeInTheDocument();
    expect(screen.queryByText(CODE)).not.toBeInTheDocument();
  });

  it('explains the attendance reason and links to the attendance tab', async () => {
    mockState(stateBody({ state: 'UNAVAILABLE', unavailableType: 'ATTENDANCE', reasons: ['FREE_IN_ACTIVE_SECTION'] }));
    renderCard();

    expect(await screen.findByText('No disponible')).toBeInTheDocument();
    expect(screen.getByText(/libre en todas tus materias por inasistencias/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver mi asistencia' })).toHaveAttribute(
      'href',
      '/alumno/mis-carreras?carrera=5&tab=asistencia',
    );
    expect(screen.queryByRole('button', { name: 'Pedir constancia' })).not.toBeInTheDocument();
  });

  it('explains the payments reason without amounts and links to the payments page', async () => {
    mockState(stateBody({ state: 'UNAVAILABLE', unavailableType: 'PAYMENTS', staffConfirmationRequired: false }));
    renderCard();

    expect(await screen.findByText(/Tenés pagos pendientes/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver mis pagos' })).toHaveAttribute('href', '/alumno/pagos');
    expect(screen.queryByText(/\$/)).not.toBeInTheDocument();
  });

  it('shows the rejection reason written by administration', async () => {
    mockState(
      stateBody({ state: 'UNAVAILABLE', unavailableType: 'REJECTED', rejectionReason: 'Falta regularizar la cuota de octubre' }),
    );
    renderCard();

    expect(await screen.findByText(/Falta regularizar la cuota de octubre/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pedir constancia' })).not.toBeInTheDocument();
  });

  it('shows the not-regular message when the request is refused with NOT_REGULAR', async () => {
    mockState(stateBody());
    server.use(
      http.post(`${API}/me/regular-certificates`, () => HttpResponse.json(errorBody('NOT_REGULAR'), { status: 422 })),
    );
    const user = userEvent.setup();
    renderCard();

    await user.click(await screen.findByRole('button', { name: 'Pedir constancia' }));

    expect(await screen.findByText(/Ahora no figurás como alumno regular/)).toBeInTheDocument();
  });

  it('shows the expired message and refreshes the state when the PDF download is refused with CERTIFICATE_EXPIRED', async () => {
    mockState(stateBody({ state: 'VALID', certificate: certificate(), history: [certificate()] }));
    server.use(
      http.get(`${API}/me/regular-certificates/31/pdf`, () =>
        HttpResponse.json(errorBody('CERTIFICATE_EXPIRED'), { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderCard();

    await user.click(await screen.findByRole('button', { name: /Descargar PDF/ }));

    expect(await screen.findByText('La constancia venció. Pedí una nueva.')).toBeInTheDocument();
  });

  it('shows the error state and retries when the state cannot be loaded', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/me/regular-certificates`, () => {
        calls += 1;
        return calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(stateBody());
      }),
    );
    const user = userEvent.setup();
    renderCard();

    await user.click(await screen.findByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Disponible')).toBeInTheDocument();
  });

  it('has no accessibility violations in the valid state', async () => {
    mockState(stateBody({ state: 'VALID', certificate: certificate(), history: [certificate()] }));
    const { container } = renderCard();
    await screen.findByText('Vigente');

    expect(await axe(container)).toHaveNoViolations();
  });
});
