import { describe, expect, it } from 'vitest';
import { errorBody } from '@/test/fixtures/errors';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { Toaster } from 'sonner';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorExcepcionDetalle } from '@/features/enrollment/pages/DirectorExcepcionDetalle';

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
        return HttpResponse.json(errorBody('INVALID_DIRECTOR_APPROVAL_REQUEST_STATE'), { status: 409 });
      }),
    );
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Rechazar' }));
    await user.type(screen.getByPlaceholderText('Motivo del rechazo'), 'No corresponde');
    await user.click(screen.getByRole('button', { name: 'Rechazar' }));

    expect((await screen.findAllByText('Otra persona de dirección ya decidió este trámite.')).length).toBeGreaterThan(0);
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

    expect((await screen.findAllByText('No se pudo guardar la decisión. Probá de nuevo.')).length).toBeGreaterThan(0);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Motivo del rechazo')).toHaveValue('Falta el certificado');
  });

  it('approves with an optional note after confirming the summary dialog', async () => {
    let sent;
    server.use(
      http.get(`${API}/director-approval-requests/9`, () => HttpResponse.json(exception())),
      http.post(`${API}/director-approval-requests/9/approve`, async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(exception({ status: 'APPROVED' }));
      }),
    );
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Aprobar excepción' }));
    expect(await screen.findByText('Condonación de correlatividad', { selector: '.font-semibold' })).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText('Nota para el solicitante (opcional)'), 'Con documentación');
    await user.click(screen.getByRole('button', { name: 'Aprobar' }));

    expect(await screen.findByText('Bandeja de excepciones')).toBeInTheDocument();
    expect(sent).toEqual({ notas: 'Con documentación' });
  });

  it('requires a rejection reason of at least 10 characters', async () => {
    server.use(http.get(`${API}/director-approval-requests/9`, () => HttpResponse.json(exception())));
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Rechazar' }));
    expect(await screen.findByText('Rechazar excepción')).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText('Motivo del rechazo'), 'corto');
    await user.click(screen.getAllByRole('button', { name: 'Rechazar' }).at(-1));

    expect(await screen.findByRole('alert')).toHaveTextContent('al menos 10 caracteres');
  });

  it('is read only for a decided exception', async () => {
    server.use(http.get(`${API}/director-approval-requests/9`, () => HttpResponse.json(exception({ status: 'APPROVED', decidedAt: '2026-10-01T10:00:00' }))));
    renderDetail();

    expect(await screen.findByText('Aprobado')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Aprobar excepción' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Rechazar' })).not.toBeInTheDocument();
  });

  it('shows a server error as retryable instead of not found', async () => {
    server.use(http.get(`${API}/director-approval-requests/9`, () => new HttpResponse(null, { status: 500 })));
    renderDetail();

    expect(await screen.findByText('No pudimos cargar el trámite')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
