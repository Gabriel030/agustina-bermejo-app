import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Toaster } from 'sonner';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoMisCarreras } from '@/features/workspace/pages/AlumnoMisCarreras';

const API = 'http://localhost:8080';

function ownRequest(overrides = {}) {
  return {
    programEnrollmentId: 5,
    programName: 'Tecnicatura en Gestión Cultural',
    graduated: false,
    eligible: true,
    accumulatedCredits: 144,
    requiredCredits: 144,
    pendingMandatorySubjects: [],
    unfulfilledElectiveGroups: [],
    request: null,
    ...overrides,
  };
}

function detail(status, overrides = {}) {
  return { id: 9, status, requestedAt: '2026-10-28T10:00:00', verifiedAt: null, decidedAt: null, rejectionReason: null, ...overrides };
}

function mockEgreso({ own = ownRequest(), post } = {}) {
  let current = own;
  server.use(
    http.get(`${API}/me/program-enrollments`, () =>
      HttpResponse.json([
        { id: 5, studentId: 1, programId: 2, programName: 'Tecnicatura en Gestión Cultural', status: 'ENROLLED', enrollmentDate: '2024-03-01T10:00:00', completionDate: '2027-12-15T00:00:00' },
      ]),
    ),
    http.get(`${API}/me/program-enrollments/5/progress`, () =>
      HttpResponse.json({
        programEnrollmentId: 5,
        creditosAcumulados: 100,
        creditosRequeridos: 144,
        obligatoriasPendientes: [],
        gruposElectivos: [],
        elegibleParaCompletar: false,
        esRegular: true,
        promedioGeneral: 8.1,
      }),
    ),
    http.get(`${API}/me/program-enrollments/5/graduation-request`, () => HttpResponse.json(current)),
    http.post(`${API}/me/program-enrollments/5/graduation-request`, () => {
      if (post) return post();
      current = ownRequest({ request: detail('REQUESTED') });
      return HttpResponse.json({ id: 9, status: 'REQUESTED' }, { status: 201 });
    }),
    http.get(`${API}/*`, () => HttpResponse.json([])),
  );
}

function renderEgreso() {
  return renderWithProviders(
    <>
      <AlumnoMisCarreras />
      <Toaster />
    </>,
    { route: '/alumno/mis-carreras?tab=egreso' },
  );
}

describe('AlumnoMisCarreras · Egreso', () => {
  it('lists what is missing and offers no request button when the student is not eligible', async () => {
    mockEgreso({
      own: ownRequest({
        eligible: false,
        accumulatedCredits: 100,
        pendingMandatorySubjects: ['Práctica Profesional'],
        unfulfilledElectiveGroups: ['Optativas de Gestión'],
      }),
    });

    renderEgreso();

    expect(await screen.findByText('Todavía no elegible')).toBeInTheDocument();
    expect(screen.getByText('Práctica Profesional')).toBeInTheDocument();
    expect(screen.getByText('Optativas de Gestión')).toBeInTheDocument();
    expect(screen.getByText('Te faltan 44 créditos para completar el plan.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Solicitar egreso' })).not.toBeInTheDocument();
  });

  it('asks for confirmation and then sends the request when eligible', async () => {
    mockEgreso();
    const user = userEvent.setup();
    renderEgreso();

    await user.click(await screen.findByRole('button', { name: 'Solicitar egreso' }));
    expect(await screen.findByText('¿Solicitar el egreso?')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Solicitar' }));

    expect(await screen.findByText('Solicitud de egreso enviada')).toBeInTheDocument();
    expect(await screen.findByText('Enviaste tu solicitud el 28/10/2026.')).toBeInTheDocument();
    expect(screen.getByText('Solicitada')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Solicitar egreso' })).not.toBeInTheDocument();
  });

  it('does not send anything when the confirmation is cancelled', async () => {
    let posted = false;
    mockEgreso({
      post: () => {
        posted = true;
        return HttpResponse.json({}, { status: 201 });
      },
    });
    const user = userEvent.setup();
    renderEgreso();

    await user.click(await screen.findByRole('button', { name: 'Solicitar egreso' }));
    await user.click(await screen.findByRole('button', { name: 'Cancelar' }));

    await waitFor(() => expect(screen.queryByText('¿Solicitar el egreso?')).not.toBeInTheDocument());
    expect(posted).toBe(false);
  });

  it('shows an error toast and keeps the request button when the backend rejects the request', async () => {
    mockEgreso({ post: () => HttpResponse.json({ code: 'ANY', violations: [] }, { status: 409 }) });
    const user = userEvent.setup();
    renderEgreso();

    await user.click(await screen.findByRole('button', { name: 'Solicitar egreso' }));
    await user.click(await screen.findByRole('button', { name: 'Solicitar' }));

    expect(await screen.findByText('Ya tenés una solicitud de egreso activa para esta carrera.')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Solicitar egreso' })).toBeInTheDocument();
  });

  it('shows the requested state with the next step and no request button', async () => {
    mockEgreso({ own: ownRequest({ request: detail('REQUESTED') }) });

    renderEgreso();

    expect(await screen.findByText('Solicitada')).toBeInTheDocument();
    expect(screen.getByText('Enviaste tu solicitud el 28/10/2026.')).toBeInTheDocument();
    expect(screen.getByText('Paso siguiente: Administración verifica que no tengas deuda pendiente.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Solicitar egreso' })).not.toBeInTheDocument();
  });

  it('shows the debt verified state waiting for the director', async () => {
    mockEgreso({ own: ownRequest({ request: detail('ADMIN_VERIFIED', { verifiedAt: '2026-10-29T09:00:00' }) }) });

    renderEgreso();

    expect(await screen.findByText('En revisión de Dirección')).toBeInTheDocument();
    expect(screen.getByText('Administración verificó tu situación de deuda el 29/10/2026.')).toBeInTheDocument();
    expect(screen.getByText('Paso siguiente: la Dirección aprueba o rechaza.')).toBeInTheDocument();
  });

  it('shows the approved state without a request button', async () => {
    mockEgreso({ own: ownRequest({ graduated: true, request: detail('APPROVED', { decidedAt: '2026-10-30T12:00:00' }) }) });

    renderEgreso();

    expect(await screen.findByText('Egreso aprobado')).toBeInTheDocument();
    expect(screen.getByText('La Dirección aprobó tu egreso el 30/10/2026.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Solicitar egreso/ })).not.toBeInTheDocument();
  });

  it('shows the rejection reason and allows requesting again while eligible', async () => {
    mockEgreso({
      own: ownRequest({
        request: detail('REJECTED', { decidedAt: '2026-10-30T12:00:00', rejectionReason: 'Tenés una cuota de septiembre sin regularizar.' }),
      }),
    });

    renderEgreso();

    expect(await screen.findByText('Rechazada')).toBeInTheDocument();
    expect(screen.getByText('La Dirección rechazó tu solicitud el 30/10/2026.')).toBeInTheDocument();
    expect(screen.getByText(/Tenés una cuota de septiembre sin regularizar\./)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Volver a solicitar egreso' })).toBeInTheDocument();
  });

  it('shows a retryable error instead of an endless skeleton when the state cannot be loaded', async () => {
    mockEgreso();
    server.use(http.get(`${API}/me/program-enrollments/5/graduation-request`, () => new HttpResponse(null, { status: 500 })));

    renderEgreso();

    expect(await screen.findByText('No pudimos cargar tu egreso')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
