import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
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
    http.get(`${API}/me/course-enrollments`, () => HttpResponse.json([])),
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

describe('AlumnoInicio account card by billing mode', () => {
  it('hides the account card when billing is off and never asks for the balance', async () => {
    const balanceCalls = vi.fn();
    mountWith(
      modulesWith('OFF'),
      http.get(`${API}/me/enrollment-payment-status`, () => {
        balanceCalls();
        return new HttpResponse(null, { status: 404 });
      }),
    );
    await screen.findByText('Todavía no hay novedades');
    await waitFor(() => expect(screen.queryByText('Estado de cuenta')).not.toBeInTheDocument());
    expect(screen.queryByText('No tenés saldo pendiente.')).not.toBeInTheDocument();
    expect(balanceCalls).not.toHaveBeenCalled();
  });

  it('shows the pending balance when billing is on and the student is overdue', async () => {
    mountWith(
      modulesWith('FULL'),
      http.get(`${API}/me/enrollment-payment-status`, () => HttpResponse.json([paymentEntry('OVERDUE')])),
    );
    expect(await screen.findByText('Saldo pendiente')).toBeInTheDocument();
  });

  it('shows no pending balance when billing is on and the endpoint answers 200 with zero balance', async () => {
    mountWith(
      modulesWith('FULL'),
      http.get(`${API}/me/enrollment-payment-status`, () => HttpResponse.json([paymentEntry('UP_TO_DATE')])),
    );
    expect(await screen.findByText('No tenés saldo pendiente.')).toBeInTheDocument();
  });

  it('does not claim there is no balance when the balance request fails', async () => {
    mountWith(
      modulesWith('FULL'),
      http.get(`${API}/me/enrollment-payment-status`, () => new HttpResponse(null, { status: 404 })),
    );
    expect(await screen.findByText(/estado de cuenta/i, { selector: 'div,p,span,h2,h3' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: /reintentar/i })).toBeInTheDocument());
    expect(screen.queryByText('No tenés saldo pendiente.')).not.toBeInTheDocument();
  });

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
