import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { Toaster } from 'sonner';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorExcepcionDetalle } from '@/pages/DirectorExcepcionDetalle';

const API = 'http://localhost:8080';

function exception(overrides = {}) {
  return {
    id: 9,
    type: 'PREREQUISITE_WAIVER',
    programEnrollmentId: 3,
    studentName: 'Benjamín Coronel',
    programName: 'Profesorado de Educación Inicial',
    subjectId: 40,
    subjectName: 'Práctica Docente II',
    justification: 'Equivalencia en trámite',
    status: 'REQUESTED',
    requestedAt: '2026-09-30T10:00:00',
    decidedAt: null,
    decisionNotes: null,
    ...overrides,
  };
}

function renderDetail(id = '9') {
  return renderWithProviders(
    <>
      <Routes>
        <Route path="/director/aprobaciones/excepciones/:id" element={<DirectorExcepcionDetalle />} />
        <Route path="/director/aprobaciones/excepciones" element={<div>Bandeja de excepciones</div>} />
      </Routes>
      <Toaster />
    </>,
    { route: `/director/aprobaciones/excepciones/${id}` },
  );
}

describe('DirectorExcepcionDetalle', () => {
  it('shows a not found state instead of an endless skeleton when the exception does not exist', async () => {
    server.use(http.get(`${API}/director-approval-requests/99999`, () => new HttpResponse(null, { status: 404 })));

    renderDetail('99999');

    expect(await screen.findByText('Este trámite no existe o ya no está disponible')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a Excepciones' })).toBeInTheDocument();
  });

  it('shows the decision notes of an approved exception', async () => {
    server.use(
      http.get(`${API}/director-approval-requests/9`, () =>
        HttpResponse.json(exception({ status: 'APPROVED', decidedAt: '2026-10-01T10:00:00', decisionNotes: 'Se autoriza por única vez' })),
      ),
    );

    renderDetail();

    expect(await screen.findByText(/Se autoriza por única vez/)).toBeInTheDocument();
  });

  it('warns and refreshes when another director account already decided the exception', async () => {
    let decided = false;
    server.use(
      http.get(`${API}/director-approval-requests/9`, () =>
        HttpResponse.json(decided ? exception({ status: 'REJECTED', decidedAt: '2026-10-01T10:00:00', decisionNotes: 'Lo rechazó Marcela' }) : exception()),
      ),
      http.post(`${API}/director-approval-requests/9/reject`, () => {
        decided = true;
        return HttpResponse.json({ code: 'INVALID_DIRECTOR_APPROVAL_REQUEST_STATE' }, { status: 409 });
      }),
    );
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Rechazar' }));
    await user.type(screen.getByPlaceholderText('Motivo del rechazo'), 'No corresponde');
    await user.click(screen.getByRole('button', { name: 'Rechazar' }));

    expect(await screen.findByText('Otra persona de dirección ya decidió este trámite.')).toBeInTheDocument();
    expect(await screen.findByText('Rechazado')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Aprobar excepción' })).not.toBeInTheDocument();
  });

  it('keeps the typed note when rejecting fails for another reason', async () => {
    server.use(
      http.get(`${API}/director-approval-requests/9`, () => HttpResponse.json(exception())),
      http.post(`${API}/director-approval-requests/9/reject`, () => new HttpResponse(null, { status: 500 })),
    );
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Rechazar' }));
    await user.type(screen.getByPlaceholderText('Motivo del rechazo'), 'Falta el certificado');
    await user.click(screen.getByRole('button', { name: 'Rechazar' }));

    expect(await screen.findByText('No se pudo guardar la decisión. Probá de nuevo.')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Motivo del rechazo')).toHaveValue('Falta el certificado');
  });
});
