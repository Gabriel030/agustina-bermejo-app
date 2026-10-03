import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { Toaster } from 'sonner';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorEgresoDetalle } from '@/pages/DirectorEgresoDetalle';

const API = 'http://localhost:8080';

function graduation(overrides = {}) {
  return {
    id: 3,
    programEnrollmentId: 2,
    studentId: 7,
    studentName: 'Martina Acosta',
    programName: 'Profesorado de Educación Inicial',
    status: 'ADMIN_VERIFIED',
    requestedAt: '2026-09-20T10:00:00',
    decidedAt: null,
    rejectionReason: null,
    ...overrides,
  };
}

function progress(overrides = {}) {
  return {
    creditosAcumulados: 144,
    creditosRequeridos: 144,
    promedioGeneral: 7.5,
    obligatoriasPendientes: [],
    elegibleParaCompletar: true,
    ...overrides,
  };
}

function mockGraduation({ graduationBody = () => graduation(), progressBody = progress(), approve } = {}) {
  server.use(
    http.get(`${API}/graduation-requests/3`, () => HttpResponse.json(graduationBody())),
    http.get(`${API}/program-enrollments/2/progress`, () => HttpResponse.json(progressBody)),
    http.get(`${API}/program-enrollments/2/attendance-summary`, () => HttpResponse.json([])),
    http.get(`${API}/students/7/billing-overview`, () => HttpResponse.json([{ billingStanding: 'UP_TO_DATE' }])),
    http.post(`${API}/graduation-requests/3/approve`, approve ?? (() => HttpResponse.json(graduation({ status: 'APPROVED' })))),
  );
}

function renderDetail(id = '3') {
  return renderWithProviders(
    <>
      <Routes>
        <Route path="/director/aprobaciones/egresos/:id" element={<DirectorEgresoDetalle />} />
        <Route path="/director/aprobaciones/egresos" element={<div>Bandeja de egresos</div>} />
      </Routes>
      <Toaster />
    </>,
    { route: `/director/aprobaciones/egresos/${id}` },
  );
}

describe('DirectorEgresoDetalle', () => {
  it('shows a not found state instead of an endless skeleton when the graduation request does not exist', async () => {
    server.use(http.get(`${API}/graduation-requests/99999`, () => new HttpResponse(null, { status: 404 })));

    renderDetail('99999');

    expect(await screen.findByText('Este trámite no existe o ya no está disponible')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a Egresos' })).toBeInTheDocument();
  });

  it('lists which mandatory subjects are still missing', async () => {
    mockGraduation({
      progressBody: progress({
        creditosAcumulados: 72,
        obligatoriasPendientes: ['Práctica Docente IV', 'Didáctica de la Matemática'],
        elegibleParaCompletar: false,
      }),
    });

    renderDetail();

    expect(await screen.findByText('Práctica Docente IV')).toBeInTheDocument();
    expect(screen.getByText('Didáctica de la Matemática')).toBeInTheDocument();
  });

  it('does not offer approving a student who does not meet the graduation requirements', async () => {
    mockGraduation({ progressBody: progress({ obligatoriasPendientes: ['Práctica Docente IV'], elegibleParaCompletar: false }) });

    renderDetail();

    expect(await screen.findByText('Práctica Docente IV')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aprobar egreso' })).toBeDisabled();
    expect(screen.getByText(/todavía no cumple los requisitos del plan/)).toBeInTheDocument();
  });

  it('warns and refreshes when another director account already decided the graduation', async () => {
    let decided = false;
    mockGraduation({
      graduationBody: () => (decided ? graduation({ status: 'REJECTED', rejectionReason: 'Adeuda materias', decidedAt: '2026-10-01T10:00:00' }) : graduation()),
      approve: () => {
        decided = true;
        return HttpResponse.json({ code: 'INVALID_GRADUATION_REQUEST_STATE' }, { status: 409 });
      },
    });
    const user = userEvent.setup();
    renderDetail();

    const approveButton = await screen.findByRole('button', { name: 'Aprobar egreso' });
    await waitFor(() => expect(approveButton).toBeEnabled());
    await user.click(approveButton);

    expect(await screen.findByText('Otra persona de dirección ya decidió este trámite.')).toBeInTheDocument();
    expect(await screen.findByText('Rechazado')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Aprobar egreso' })).not.toBeInTheDocument();
  });

  it('explains that the student has overdue debt when the backend blocks the approval', async () => {
    mockGraduation({ approve: () => HttpResponse.json({ code: 'PENDING_DEBT' }, { status: 409 }) });
    const user = userEvent.setup();
    renderDetail();

    const approveButton = await screen.findByRole('button', { name: 'Aprobar egreso' });
    await waitFor(() => expect(approveButton).toBeEnabled());
    await user.click(approveButton);

    expect(await screen.findByText('No se puede aprobar: el alumno tiene cuotas vencidas.')).toBeInTheDocument();
  });
});
