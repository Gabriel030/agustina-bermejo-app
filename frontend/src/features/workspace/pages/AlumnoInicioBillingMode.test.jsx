import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { studentHomeHandlers } from '@/test/fixtures/studentHome';
import { AlumnoInicio } from '@/features/workspace/pages/AlumnoInicio';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez' } }),
}));

const API = 'http://localhost:8080';

const paymentEntry = (paymentStatus) => ({
  enrollmentId: 1,
  enrollmentType: 'CAREER',
  paymentStatus,
  paymentStatusUpdatedAt: '2026-11-01T10:00:00',
});

const modulesWith = (mode) =>
  http.get(`${API}/me/modules`, () =>
    HttpResponse.json({
      billing: { mode },
      fiscalInvoicing: { enabled: false },
      analytics: { enabled: false },
      paymentMethods: [],
      billingNotifications: { enabled: false },
      externalBilling: { enabled: false },
    }),
  );

const pendingAdmission = () =>
  http.get(`${API}/me/program-enrollment-applications`, () =>
    HttpResponse.json([
      {
        id: 1,
        programName: 'Tecnicatura',
        status: 'APPROVED',
        admissionInvoice: {
          id: 10,
          total: { amount: '50000.00', currency: 'ARS' },
          dueDate: '2099-01-01',
          paymentStatus: 'PENDING',
        },
      },
    ]),
  );

function mountWith(...handlers) {
  server.use(
    ...studentHomeHandlers(),
    http.get(`${API}/me/announcements`, () => HttpResponse.json([])),
    ...handlers,
  );
  return renderWithProviders(
    <Routes>
      <Route path="/alumno/inicio" element={<AlumnoInicio />} />
    </Routes>,
    { route: '/alumno/inicio' },
  );
}

describe('AlumnoInicio balance notice by billing mode', () => {
  it('hidesTheBalanceNoticeWhenBillingIsOffAndNeverAsksForTheBalance', async () => {
    const balanceCalls = vi.fn();
    mountWith(
      modulesWith('OFF'),
      http.get(`${API}/me/enrollment-payment-status`, () => {
        balanceCalls();
        return new HttpResponse(null, { status: 404 });
      }),
    );
    await screen.findByText('Todavía no hay novedades');
    expect(screen.queryByText('Saldo pendiente')).not.toBeInTheDocument();
    expect(screen.queryByText('Pagos al día')).not.toBeInTheDocument();
    expect(balanceCalls).not.toHaveBeenCalled();
  });

  it('showsThePendingBalanceWithALinkToPaymentsWhenBillingIsOnAndTheStudentIsOverdue', async () => {
    mountWith(
      modulesWith('FULL'),
      http.get(`${API}/me/enrollment-payment-status`, () => HttpResponse.json([paymentEntry('OVERDUE')])),
    );
    expect(await screen.findByText('Saldo pendiente')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Ver pagos/ })).toHaveAttribute('href', '/alumno/pagos');
  });

  it('showsUpToDateWhenBillingIsOnAndThereIsNoOverdueBalance', async () => {
    mountWith(
      modulesWith('FULL'),
      http.get(`${API}/me/enrollment-payment-status`, () => HttpResponse.json([paymentEntry('UP_TO_DATE')])),
    );
    expect(await screen.findByText('Pagos al día')).toBeInTheDocument();
    expect(screen.queryByText('Saldo pendiente')).not.toBeInTheDocument();
  });

  it('doesNotClaimThereIsNoBalanceWhenTheBalanceRequestFails', async () => {
    mountWith(
      modulesWith('FULL'),
      http.get(`${API}/me/enrollment-payment-status`, () => new HttpResponse(null, { status: 404 })),
    );
    expect(await screen.findByText('No pudimos verificar tu saldo')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument());
    expect(screen.queryByText('Pagos al día')).not.toBeInTheDocument();
  });
});

describe('AlumnoInicio admission fee card by billing mode', () => {
  it('hides the admission fee payment card when billing is off', async () => {
    mountWith(modulesWith('OFF'), pendingAdmission());
    await screen.findByText('Todavía no hay novedades');
    expect(screen.queryByText('Pagá la matrícula de ingreso para confirmar tu inscripción')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pagar matrícula' })).not.toBeInTheDocument();
  });

  it('shows the admission fee payment card when billing is on', async () => {
    mountWith(
      modulesWith('FULL'),
      pendingAdmission(),
      http.get(`${API}/me/enrollment-payment-status`, () => HttpResponse.json([paymentEntry('UP_TO_DATE')])),
    );
    expect(await screen.findByRole('button', { name: 'Pagar matrícula' })).toBeInTheDocument();
  });
});
