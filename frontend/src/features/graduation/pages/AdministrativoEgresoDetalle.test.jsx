import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { Toaster } from 'sonner';
import { axe } from '@/test/axe';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { GRADUATION_DEBT_SUMMARY_PENDING_IN_CONTRACT, tolerateOperationsPendingInContract } from '@/test/contract/pendingOperations';
import { AdministrativoEgresoDetalle } from '@/features/graduation/pages/AdministrativoEgresoDetalle';

const API = 'http://localhost:8080';

tolerateOperationsPendingInContract(GRADUATION_DEBT_SUMMARY_PENDING_IN_CONTRACT);

function graduation(overrides = {}) {
  return {
    id: 41,
    programEnrollmentId: 2,
    studentId: 7,
    studentName: 'Valentina Suárez',
    programName: 'Tecnicatura en Administración',
    status: 'REQUESTED',
    requestedAt: '2026-09-12T10:00:00',
    decidedAt: null,
    rejectionReason: null,
    ...overrides,
  };
}

const debtSummary = {
  mustCheckDebt: true,
  totalDue: { amount: '48000.00', currency: 'ARS' },
  installments: [
    { period: '2026-03', dueDate: '2026-03-10', amount: { amount: '15000.00', currency: 'ARS' }, status: 'OVERDUE' },
    { period: '2026-04', dueDate: '2026-04-10', amount: { amount: '15000.00', currency: 'ARS' }, status: 'OVERDUE' },
    { period: '2026-05', dueDate: '2026-05-10', amount: { amount: '18000.00', currency: 'ARS' }, status: 'PENDING' },
  ],
};
const cleanSummary = { mustCheckDebt: true, totalDue: { amount: '0.00', currency: 'ARS' }, installments: [] };
const offSummary = { mustCheckDebt: false, totalDue: { amount: '0.00', currency: 'ARS' }, installments: [] };

function mockDetail({ graduationBody = graduation(), summary = debtSummary, verify } = {}) {
  server.use(
    http.get(`${API}/graduation-requests/41`, () => HttpResponse.json(graduationBody)),
    http.get(`${API}/graduation-requests/41/debt-summary`, () => HttpResponse.json(summary)),
    http.post(
      `${API}/graduation-requests/41/verify-debt`,
      verify ?? (() => HttpResponse.json(graduation({ status: 'ADMIN_VERIFIED' }))),
    ),
  );
}

function renderDetail() {
  return renderWithProviders(
    <>
      <Routes>
        <Route path="/administrativo/egresos-por-verificar/:id" element={<AdministrativoEgresoDetalle />} />
        <Route path="/administrativo/egresos-por-verificar" element={<div>Bandeja de egresos</div>} />
      </Routes>
      <Toaster />
    </>,
    { route: '/administrativo/egresos-por-verificar/41' },
  );
}

describe('AdministrativoEgresoDetalle', () => {
  it('shows the debt card with the installments and the total', async () => {
    mockDetail();
    renderDetail();

    expect(await screen.findByText('Con deuda')).toBeInTheDocument();
    expect(screen.getByText('Total adeudado')).toBeInTheDocument();
    expect(screen.getAllByRole('row')).toHaveLength(5);
    expect(screen.getByRole('button', { name: 'Verificar deuda' })).toBeEnabled();
  });

  it('keeps the request pending and offers the finance link when the student has debt', async () => {
    mockDetail({ verify: () => HttpResponse.json(errorBody('PENDING_DEBT'), { status: 409 }) });
    renderDetail();
    await screen.findByText('Con deuda');

    await userEvent.click(screen.getByRole('button', { name: 'Verificar deuda' }));

    expect(await screen.findByText(/No se puede verificar/)).toBeInTheDocument();
    expect(screen.getByText('Por verificar')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Verificar deuda' })).toBeEnabled();
    const links = screen.getAllByRole('link', { name: 'Ir al Financiero del alumno' });
    expect(links[0]).toHaveAttribute('href', '/administrativo/alumnos/7?view=financiero');
  });

  it('moves the request to the director when the student is up to date', async () => {
    mockDetail({ summary: cleanSummary });
    renderDetail();
    expect(await screen.findByText('Al día')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Verificar deuda' }));

    expect(await screen.findByText(/Deuda verificada\./)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Verificar deuda' })).not.toBeInTheDocument();
    expect(screen.getByText('En el Director')).toBeInTheDocument();
  });

  it('explains that debt is not checked when the control is off', async () => {
    mockDetail({ summary: offSummary });
    renderDetail();

    expect(await screen.findByText('No se controla')).toBeInTheDocument();
    expect(screen.getByText(/El control de deuda está desactivado en este instituto/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Verificar deuda' })).toBeEnabled();
  });

  it('offers a retry when the debt summary fails', async () => {
    mockDetail();
    server.use(http.get(`${API}/graduation-requests/41/debt-summary`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })));
    renderDetail();

    expect(await screen.findByText('No pudimos cargar la situación de deuda.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it.each([
    ['ADMIN_VERIFIED', 'En el Director'],
    ['APPROVED', 'Aprobado'],
    ['REJECTED', 'Rechazado'],
  ])('opens read only without actions or debt card when the status is %s', async (status, label) => {
    mockDetail({ graduationBody: graduation({ status, rejectionReason: status === 'REJECTED' ? 'Documentación incompleta' : null }) });
    renderDetail();

    expect(await screen.findByText(label)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Verificar deuda' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Rechazar egreso' })).not.toBeInTheDocument();
    expect(screen.queryByText('Situación de deuda')).not.toBeInTheDocument();
  });

  it('requires a reason before rejecting and returns to the tray', async () => {
    let body = null;
    mockDetail();
    server.use(
      http.post(`${API}/graduation-requests/41/reject`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(graduation({ status: 'REJECTED' }));
      }),
    );
    renderDetail();
    await screen.findByText('Con deuda');

    await userEvent.click(screen.getByRole('button', { name: 'Rechazar egreso' }));
    await userEvent.click(screen.getByRole('button', { name: 'Rechazar' }));
    expect(await screen.findByText('El motivo del rechazo es obligatorio.')).toBeInTheDocument();

    await userEvent.type(screen.getByRole('textbox'), 'Documentación incompleta');
    await userEvent.click(screen.getByRole('button', { name: 'Rechazar' }));

    expect(await screen.findByText('Bandeja de egresos')).toBeInTheDocument();
    await waitFor(() => expect(body).toEqual({ motivo: 'Documentación incompleta' }));
  });

  it('has no accessibility violations', async () => {
    mockDetail();
    const { container } = renderDetail();
    await screen.findByText('Con deuda');

    expect(await axe(container)).toHaveNoViolations();
  });
});
