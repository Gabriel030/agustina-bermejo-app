import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { Toaster } from 'sonner';
import { axe } from '@/test/axe';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { approvalCheck, reviewApplication } from '@/test/fixtures/programApplications';
import { SolicitudDeIngresoDetalle } from '@/features/enrollment/pages/SolicitudDeIngresoDetalle';

const API = 'http://localhost:8080';
const BASE = `${API}/program-enrollment-applications`;

const otherPending = reviewApplication({ id: 52, studentFullName: 'Joaquín Benítez', requestedAt: '2026-11-06T10:00:00' });

function mockReview({ application = reviewApplication(), check = approvalCheck(), pending = [application, otherPending] } = {}) {
  server.use(
    http.get(`${BASE}/pending`, () => HttpResponse.json(pending)),
    http.get(`${BASE}/${application.id}`, () => HttpResponse.json(application)),
    http.get(`${BASE}/${application.id}/approval-check`, () => HttpResponse.json(check)),
    http.get(`${BASE}/52`, () => HttpResponse.json(otherPending)),
    http.get(`${BASE}/52/approval-check`, () => HttpResponse.json(approvalCheck({ applicationId: 52 }))),
  );
}

function renderDetail(id = '51', basePath = '/preceptor') {
  return renderWithProviders(
    <>
      <Routes>
        <Route path={`${basePath}/solicitudes-de-ingreso/:id`} element={<SolicitudDeIngresoDetalle />} />
        <Route path={`${basePath}/solicitudes-de-ingreso`} element={<div>Bandeja de solicitudes</div>} />
      </Routes>
      <Toaster />
    </>,
    { route: `${basePath}/solicitudes-de-ingreso/${id}` },
  );
}

describe('SolicitudDeIngresoDetalle', () => {
  it('shows the student data, the documents and the approval conditions', async () => {
    mockReview();
    renderDetail();

    expect(await screen.findByRole('heading', { name: 'Valentina Suárez' })).toBeInTheDocument();
    expect(screen.getByLabelText('DNI')).toHaveValue('41234567');
    expect(screen.getByLabelText('Cohorte')).toHaveValue('Primer ingreso 2026');
    expect(screen.getAllByRole('button', { name: /^Ver / })).toHaveLength(3);
    expect(await screen.findByText('Sin deuda que bloquee la inscripción.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aprobar solicitud' })).not.toHaveAttribute('aria-disabled');
  });

  it('blocks approving with a visible reason when the documentation is incomplete', async () => {
    mockReview({
      check: approvalCheck({ canApprove: false, documentation: { complete: false, missingTypes: ['DNI_DORSO'], documents: [] } }),
    });
    renderDetail();

    const approve = await screen.findByRole('button', { name: 'Aprobar solicitud' });
    await waitFor(() => expect(approve).toHaveAttribute('aria-disabled', 'true'));
    expect(screen.getAllByText(/Aprobar está deshabilitado: falta DNI dorso\./).length).toBeGreaterThan(0);

    await userEvent.click(approve);
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('shows the blocking reasons and blocks approving when the student is not active', async () => {
    mockReview({ check: approvalCheck({ canApprove: false, blockingReasons: ['STUDENT_NOT_ACTIVE', 'ALREADY_ENROLLED_IN_PROGRAM'] }) });
    renderDetail();

    expect(await screen.findByText('El alumno no está activo.')).toBeInTheDocument();
    expect(screen.getByText('El alumno ya está inscripto en esta carrera.')).toBeInTheDocument();
    const approve = screen.getByRole('button', { name: 'Aprobar solicitud' });
    expect(approve).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getAllByText(/Aprobar está deshabilitado: el alumno no está activo/).length).toBeGreaterThan(0);
  });

  it('shows the debt before approving and blocks the approval', async () => {
    mockReview({
      check: approvalCheck({ canApprove: false, debt: { blocksApproval: true, violationCodes: ['OVERDUE_DEBT'] } }),
    });
    renderDetail('51', '/administrativo');

    expect(await screen.findByText('Tiene cuotas vencidas sin pagar.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir al Financiero del alumno' })).toHaveAttribute(
      'href',
      '/administrativo/alumnos/7?view=financiero',
    );
    expect(screen.getByRole('button', { name: 'Aprobar solicitud' })).toHaveAttribute('aria-disabled', 'true');
  });

  it('approves with an idempotency key and opens the next pending application', async () => {
    mockReview();
    const keys = [];
    server.use(
      http.post(`${BASE}/51/approve`, ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        return HttpResponse.json(reviewApplication({ status: 'APPROVED' }));
      }),
    );
    renderDetail();
    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar solicitud' }));

    const dialog = await screen.findByRole('alertdialog');
    expect(within(dialog).getByText(/Se genera la factura de matrícula de ingreso/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Aprobar' }));

    expect(await screen.findByRole('heading', { name: 'Joaquín Benítez' })).toBeInTheDocument();
    expect(keys).toHaveLength(1);
    expect(keys[0]).toMatch(/\S{16,}/);
  });

  it('goes back to the list when the next application is not requested', async () => {
    mockReview();
    server.use(http.post(`${BASE}/51/approve`, () => HttpResponse.json(reviewApplication({ status: 'APPROVED' }))));
    renderDetail();
    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar solicitud' }));
    const dialog = await screen.findByRole('alertdialog');
    await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Abrir la siguiente solicitud pendiente' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Aprobar' }));

    expect(await screen.findByText('Bandeja de solicitudes')).toBeInTheDocument();
  });

  it('reuses the same idempotency key when the approval is retried', async () => {
    mockReview();
    const keys = [];
    let calls = 0;
    server.use(
      http.post(`${BASE}/51/approve`, ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        calls += 1;
        return calls === 1
          ? HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })
          : HttpResponse.json(reviewApplication({ status: 'APPROVED' }));
      }),
    );
    renderDetail();
    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar solicitud' }));
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Aprobar' }));
    await waitFor(() => expect(keys).toHaveLength(1));

    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar solicitud' }));
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Aprobar' }));
    await waitFor(() => expect(keys).toHaveLength(2));

    expect(keys[1]).toBe(keys[0]);
  });

  it('shows that someone else resolved it and removes the actions on a state conflict', async () => {
    mockReview();
    server.use(
      http.post(`${BASE}/51/approve`, () =>
        HttpResponse.json(errorBody('INVALID_PROGRAM_ENROLLMENT_APPLICATION_STATE'), { status: 409 }),
      ),
    );
    renderDetail();
    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar solicitud' }));
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Aprobar' }));

    expect(await screen.findByText('Otra persona ya resolvió esta solicitud.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Aprobar solicitud' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a la bandeja' })).toBeInTheDocument();
  });

  it('keeps the dialog values and shows the server message when approving fails for another reason', async () => {
    mockReview();
    server.use(
      http.post(`${BASE}/51/approve`, () => HttpResponse.json(errorBody('APPLICATION_DECISION_IDEMPOTENCY_KEY_CONFLICT'), { status: 409 })),
    );
    renderDetail();
    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar solicitud' }));
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Aprobar' }));

    expect(await screen.findByText(/Esta decisión ya se registró de otra forma/)).toBeInTheDocument();
    expect(screen.queryByText('Otra persona ya resolvió esta solicitud.')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aprobar solicitud' })).toBeInTheDocument();
  });

  it('requires at least 10 characters to reject and sends the reason', async () => {
    mockReview();
    const bodies = [];
    server.use(
      http.post(`${BASE}/51/reject`, async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(reviewApplication({ status: 'REJECTED' }));
      }),
    );
    renderDetail();
    await userEvent.click(await screen.findByRole('button', { name: 'Rechazar' }));

    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByPlaceholderText('Motivo del rechazo'), 'corto');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Rechazar' }));
    expect(await within(dialog).findByText(/al menos 10 caracteres/)).toBeInTheDocument();
    expect(bodies).toHaveLength(0);

    await userEvent.type(within(dialog).getByPlaceholderText('Motivo del rechazo'), ' el analítico está cortado');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Rechazar' }));

    expect(await screen.findByText('Bandeja de solicitudes')).toBeInTheDocument();
    expect(bodies).toEqual([{ reason: 'corto el analítico está cortado' }]);
  });

  it('shows the resolution and no actions for a resolved application', async () => {
    mockReview({
      application: reviewApplication({
        status: 'REJECTED',
        decidedByName: 'Marcela Díaz',
        decidedAt: '2026-11-07T10:00:00',
        rejectionReason: 'El analítico está cortado',
      }),
    });
    renderDetail();

    expect(await screen.findByText('Resolución')).toBeInTheDocument();
    expect(screen.getByLabelText('Resuelta por')).toHaveValue('Marcela Díaz');
    expect(screen.getByText(/El analítico está cortado/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Aprobar solicitud' })).not.toBeInTheDocument();
    expect(screen.queryByText('Antes de aprobar')).not.toBeInTheDocument();
  });

  it('offers a retry when the approval check cannot be loaded', async () => {
    mockReview();
    server.use(http.get(`${BASE}/51/approval-check`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })));
    renderDetail();

    expect(await screen.findByText(/No pudimos verificar la documentación y la deuda/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('shows the unavailable state when the application does not exist', async () => {
    server.use(
      http.get(`${BASE}/pending`, () => HttpResponse.json([])),
      http.get(`${BASE}/51`, () => HttpResponse.json(errorBody('PROGRAM_ENROLLMENT_APPLICATION_NOT_FOUND'), { status: 404 })),
    );
    renderDetail();

    expect(await screen.findByText('Este trámite no existe o ya no está disponible')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    mockReview();
    const { container } = renderDetail();
    await screen.findByText('Sin deuda que bloquee la inscripción.');

    expect(await axe(container)).toHaveNoViolations();
  });
});
