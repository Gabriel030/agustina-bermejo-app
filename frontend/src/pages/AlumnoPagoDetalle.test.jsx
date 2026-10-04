import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoPagoDetalle } from '@/pages/AlumnoPagoDetalle';

const API = 'http://localhost:8080';

function invoice(overrides) {
  return {
    id: 1,
    feeType: 'RECURRING_FEE',
    period: '2026-09',
    dueDate: '2026-09-10',
    baseAmount: { amount: '48000.00', currency: 'ARS' },
    amountDue: { amount: '48000.00', currency: 'ARS' },
    remainingBalance: { amount: '48000.00', currency: 'ARS' },
    status: 'PENDING',
    adjustments: [],
    paymentDate: null,
    paymentMethod: null,
    ...overrides,
  };
}

function billingStatus(enrollmentType, productName, invoices) {
  return {
    billingStanding: 'UP_TO_DATE',
    enrollmentType,
    enrollmentId: 10,
    productName,
    productId: 3,
    invoices,
  };
}

function renderDetail(route) {
  return renderWithProviders(
    <Routes>
      <Route path="/alumno/pagos/curso/:enrollmentId" element={<AlumnoPagoDetalle />} />
      <Route path="/alumno/pagos/carrera/:enrollmentId" element={<AlumnoPagoDetalle />} />
    </Routes>,
    { route },
  );
}

function withReceipts(receipts) {
  server.use(http.get(`${API}/me/payment-receipts`, () => HttpResponse.json(receipts)));
}

describe('AlumnoPagoDetalle', () => {
  it('shows the installments of a course by asking the course endpoint', async () => {
    withReceipts([]);
    server.use(
      http.get(`${API}/me/course-enrollments/10/invoices`, () =>
        HttpResponse.json(billingStatus('COURSE', 'Primeros Auxilios y RCP', [
          invoice({ id: 5, period: '2026-03', status: 'PAID', remainingBalance: { amount: '0.00', currency: 'ARS' }, amountDue: { amount: '11050.00', currency: 'ARS' }, baseAmount: { amount: '11050.00', currency: 'ARS' } }),
        ])),
      ),
      http.get(`${API}/me/program-enrollments/10/invoices`, () => new HttpResponse(null, { status: 403 })),
    );

    renderDetail('/alumno/pagos/curso/10');

    expect(await screen.findByText('Curso: Primeros Auxilios y RCP')).toBeInTheDocument();
    expect(screen.getByText('mar 2026')).toBeInTheDocument();
    expect(screen.queryByText('No hay cuotas para esta inscripción.')).not.toBeInTheDocument();
  });

  it('shows the program name as the title of a program detail', async () => {
    withReceipts([]);
    server.use(
      http.get(`${API}/me/program-enrollments/10/invoices`, () =>
        HttpResponse.json(billingStatus('PROGRAM', 'Profesorado de Educación Inicial', [invoice({})])),
      ),
    );

    renderDetail('/alumno/pagos/carrera/10');

    expect(await screen.findByText('Carrera: Profesorado de Educación Inicial')).toBeInTheDocument();
  });

  it('shows an error instead of an empty list when the installments cannot be loaded', async () => {
    withReceipts([]);
    server.use(
      http.get(`${API}/me/program-enrollments/99/invoices`, () => new HttpResponse(null, { status: 404 })),
    );

    renderDetail('/alumno/pagos/carrera/99');

    expect(await screen.findByText('No pudimos cargar las cuotas de esta inscripción.')).toBeInTheDocument();
    expect(screen.queryByText('No hay cuotas para esta inscripción.')).not.toBeInTheDocument();
  });

  it('marks an installment with a receipt under review as "En revisión" and hides the pay button', async () => {
    withReceipts([{ id: 300, invoiceId: 1, status: 'PENDING_REVIEW', uploadedAt: '2026-10-01T10:00:00' }]);
    server.use(
      http.get(`${API}/me/program-enrollments/10/invoices`, () =>
        HttpResponse.json(billingStatus('PROGRAM', 'Profesorado', [invoice({ status: 'OVERDUE' })])),
      ),
    );

    renderDetail('/alumno/pagos/carrera/10');

    expect(await screen.findByText('En revisión')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pagar' })).not.toBeInTheDocument();
  });

  it('tells the student that the receipt was rejected and why, and lets them pay again', async () => {
    withReceipts([
      {
        id: 301,
        invoiceId: 1,
        status: 'REJECTED',
        rejectionReason: 'El comprobante corresponde a otro período',
        uploadedAt: '2026-10-01T10:00:00',
      },
    ]);
    server.use(
      http.get(`${API}/me/program-enrollments/10/invoices`, () =>
        HttpResponse.json(billingStatus('PROGRAM', 'Profesorado', [invoice({})])),
      ),
    );

    renderDetail('/alumno/pagos/carrera/10');

    expect(await screen.findByText(/Comprobante rechazado: El comprobante corresponde a otro período/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pagar' })).toBeInTheDocument();
  });

  it('explains the late fee in the installment detail', async () => {
    withReceipts([]);
    server.use(
      http.get(`${API}/me/program-enrollments/10/invoices`, () =>
        HttpResponse.json(billingStatus('PROGRAM', 'Profesorado', [
          invoice({
            status: 'OVERDUE',
            amountDue: { amount: '52800.00', currency: 'ARS' },
            remainingBalance: { amount: '52800.00', currency: 'ARS' },
            adjustments: [{ type: 'SURCHARGE', resolvedAmount: { amount: '4800.00', currency: 'ARS' }, reason: 'Recargo por mora' }],
          }),
        ])),
      ),
    );
    const user = userEvent.setup();

    renderDetail('/alumno/pagos/carrera/10');

    await user.click(await screen.findByRole('button', { name: 'Detalle' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Importe de la cuota')).toBeInTheDocument();
    expect(within(dialog).getByText('Recargo por mora')).toBeInTheDocument();
    expect(within(dialog).getByText('+ $ 4.800,00')).toBeInTheDocument();
  });
});
