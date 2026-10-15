import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoPagoDetalle } from '@/pages/AlumnoPagoDetalle';
import { discountFixture, invoiceFixture, money } from '@/test/fixtures/invoices';

const API = 'http://localhost:8080';

const invoice = (overrides) => invoiceFixture(overrides);

function billingStatus(enrollmentType, productName, invoices) {
  return {
    paymentStatus: 'UP_TO_DATE',
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
  server.use(http.get(`${API}/me/bank-transfer-receipts`, () => HttpResponse.json(receipts)));
}

describe('AlumnoPagoDetalle', () => {
  it('shows the installments of a course by asking the course endpoint', async () => {
    withReceipts([]);
    server.use(
      http.get(`${API}/me/course-enrollments/10/invoices`, () =>
        HttpResponse.json(billingStatus('COURSE', 'Primeros Auxilios y RCP', [
          invoice({ id: 5, period: '2026-03', paymentStatus: 'PAID', total: '11050.00', paid: '11050.00' }),
        ])),
      ),
      http.get(`${API}/me/program-enrollments/10/invoices`, () => new HttpResponse(null, { status: 403 })),
    );

    renderDetail('/alumno/pagos/curso/10');

    expect(await screen.findByText('Curso: Primeros Auxilios y RCP')).toBeInTheDocument();
    expect(screen.getByText('Cuota mensual · mar 2026')).toBeInTheDocument();
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
        HttpResponse.json(billingStatus('PROGRAM', 'Profesorado', [invoice({ paymentStatus: 'OVERDUE' })])),
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

  it('explains the scholarship discount of the installment in its detail', async () => {
    withReceipts([]);
    server.use(
      http.get(`${API}/me/program-enrollments/10/invoices`, () =>
        HttpResponse.json(billingStatus('PROGRAM', 'Profesorado', [
          invoice({ total: '24000.00', discount: discountFixture() }),
        ])),
      ),
    );
    const user = userEvent.setup();

    renderDetail('/alumno/pagos/carrera/10');

    await user.click(await screen.findByRole('button', { name: /Ver detalle de/ }));
    expect(screen.getByText('Beca 50 % (cuota 1 de 3)')).toBeInTheDocument();
    expect(screen.getByText('− $ 24.000,00')).toBeInTheDocument();
    expect(screen.getByText('Total')).toBeInTheDocument();
    expect(screen.queryByText('Recargo por mora')).not.toBeInTheDocument();
  });

  it('shows the payment date, the outstanding balance under the amount and the period inside the concept', async () => {
    withReceipts([]);
    server.use(
      http.get(`${API}/me/program-enrollments/10/invoices`, () =>
        HttpResponse.json(billingStatus('PROGRAM', 'Profesorado', [
          invoice({
            paymentStatus: 'PENDING',
            period: '2026-03',
            dueDate: '2026-03-10',
            total: '52000.00',
            credited: '32000.00',
            paymentDate: '2026-03-12T09:05:00',
            payments: [
              { paymentId: 1, method: 'CASH', amountApplied: money('12000.00'), paymentTotal: money('12000.00'), paidAt: '2026-03-12T09:05:00' },
              { paymentId: 2, method: 'CASH', amountApplied: money('20000.00'), paymentTotal: money('20000.00'), paidAt: '2026-03-11T10:00:00' },
            ],
          }),
        ])),
      ),
    );

    renderDetail('/alumno/pagos/carrera/10');

    expect(await screen.findByText('Cuota mensual · mar 2026')).toBeInTheDocument();
    expect(screen.getByText('10/03/2026')).toBeInTheDocument();
    expect(screen.getByText('12/03/2026')).toBeInTheDocument();
    expect(screen.getByText('2 pagos')).toBeInTheDocument();
    expect(screen.getByText('Saldo $ 20.000,00')).toBeInTheDocument();
  });

  it('expands the installment to show each payment with its card type and installments but not who registered it', async () => {
    withReceipts([]);
    server.use(
      http.get(`${API}/me/program-enrollments/10/invoices`, () =>
        HttpResponse.json(billingStatus('PROGRAM', 'Profesorado', [
          invoice({
            paymentStatus: 'PAID',
            paid: '48000.00',
            paymentDate: '2026-03-12T09:05:00',
            payments: [
              { paymentId: 1, method: 'MERCADO_PAGO', cardType: 'CREDIT', cardInstallments: 3, externalReference: '998877', amountApplied: { amount: '52000.00', currency: 'ARS' }, paymentTotal: { amount: '52000.00', currency: 'ARS' }, paidAt: '2026-03-12T09:05:00' },
            ],
          }),
        ])),
      ),
    );
    const user = userEvent.setup();

    renderDetail('/alumno/pagos/carrera/10');

    const toggle = await screen.findByRole('button', { name: /Ver detalle de/ });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await user.click(toggle);

    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('Mercado Pago · Tarjeta de crédito · 3 cuotas')).toBeInTheDocument();
    expect(screen.getByText(/Ref\. 998877/)).toBeInTheDocument();
    expect(screen.queryByText(/Registrado por/)).not.toBeInTheDocument();
  });

  it('does not make an installment without payments or receipts expandable', async () => {
    withReceipts([]);
    server.use(
      http.get(`${API}/me/program-enrollments/10/invoices`, () =>
        HttpResponse.json(billingStatus('PROGRAM', 'Profesorado', [invoice({ paymentStatus: 'PENDING', period: '2026-03' })])),
      ),
    );

    renderDetail('/alumno/pagos/carrera/10');

    await screen.findByText('Cuota mensual · mar 2026');
    expect(screen.queryByRole('button', { name: /Ver detalle de/ })).not.toBeInTheDocument();
  });
});
