import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route, useLocation } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { createDeferred } from '@/test/deferred';
import { errorBody } from '@/test/fixtures/errors';
import { advanceView, obligationWith } from '@/test/fixtures/advanceEnrollmentFee';
import { AdvanceEnrollmentFeePage } from './AdvanceEnrollmentFeePage';

const API = 'http://localhost:8080';
const PATH = '/alumno/pagos/carrera/3/matricula-anticipada';

function Destination() {
  return <div>{useLocation().pathname}</div>;
}

function mount(advance = advanceView()) {
  server.use(
    http.get(`${API}/me/program-enrollments/3/enrollment-fee-status`, () => HttpResponse.json(obligationWith(advance))),
    http.get(`${API}/me/program-enrollments`, () => HttpResponse.json([{ id: 3, programId: 1, programName: 'Tecnicatura en Enfermería', status: 'ENROLLED', enrollmentDate: '2026-03-01T10:00:00', studentId: 9 }])),
  );
  return renderWithProviders(
    <Routes>
      <Route path="/alumno/pagos/carrera/:enrollmentId/matricula-anticipada" element={<AdvanceEnrollmentFeePage />} />
      <Route path="*" element={<Destination />} />
    </Routes>,
    { route: PATH },
  );
}

describe('AdvanceEnrollmentFeePage', () => {
  it('summarizesPriceDueDateAndNoRefundNoticeBeforeGeneratingAnything', async () => {
    mount();
    expect(await screen.findByText('Resumen')).toBeInTheDocument();
    expect(screen.getByText('Tecnicatura en Enfermería')).toBeInTheDocument();
    expect(screen.getAllByText(/45\.000/).length).toBeGreaterThan(0);
    expect(screen.getByText('12/02/2027')).toBeInTheDocument();
    expect(screen.getByText(/no se devuelve ni queda a favor/)).toBeInTheDocument();
    expect(screen.getByText('Todavía no pagás nada.', { selector: 'small' })).toBeInTheDocument();
  });

  it('generatesTheInvoiceAndShowsTheInvoiceStepWithPayNow', async () => {
    const user = userEvent.setup();
    const posted = createDeferred();
    mount();
    server.use(
      http.post(`${API}/me/program-enrollments/3/advance-enrollment-fee`, () => {
        posted.resolve(true);
        return HttpResponse.json({ invoiceId: 77, created: true });
      }),
    );
    await user.click(await screen.findByRole('button', { name: /Continuar al pago/ }));
    await posted.promise;
    expect(await screen.findByText('Factura generada')).toBeInTheDocument();
    expect(screen.getByText('Pendiente')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Pagar ahora' }));
    expect(await screen.findByText('/alumno/pagos/77/seleccion')).toBeInTheDocument();
  });

  it('keepsTheInvoiceStepWhenTheServerReportsItAlreadyExisted', async () => {
    const user = userEvent.setup();
    mount();
    server.use(http.post(`${API}/me/program-enrollments/3/advance-enrollment-fee`, () => HttpResponse.json({ invoiceId: 77, created: false })));
    await user.click(await screen.findByRole('button', { name: /Continuar al pago/ }));
    expect(await screen.findByText('Factura generada')).toBeInTheDocument();
  });

  it('showsTheViolationMessageAndStaysOnConfirmWhenTheServerDenies', async () => {
    const user = userEvent.setup();
    mount();
    server.use(
      http.post(`${API}/me/program-enrollments/3/advance-enrollment-fee`, () =>
        HttpResponse.json(errorBody('ADVANCE_FEE_OVERDUE_DEBT', { violations: [{ code: 'ADVANCE_FEE_OVERDUE_DEBT', message: 'x' }] }), { status: 409 }),
      ),
    );
    await user.click(await screen.findByRole('button', { name: /Continuar al pago/ }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Hay cuotas vencidas: regularizalas antes de generar la matrícula.');
    expect(screen.queryByText('Factura generada')).not.toBeInTheDocument();
  });

  it('paysLaterByReturningToTheInstallments', async () => {
    const user = userEvent.setup();
    mount();
    server.use(http.post(`${API}/me/program-enrollments/3/advance-enrollment-fee`, () => HttpResponse.json({ invoiceId: 77, created: true })));
    await user.click(await screen.findByRole('button', { name: /Continuar al pago/ }));
    await user.click(await screen.findByRole('button', { name: 'Pagar más tarde' }));
    expect(await screen.findByText('/alumno/pagos/carrera/3')).toBeInTheDocument();
  });

  it('explainsUnavailabilityWhenOpenedWithoutAnOffer', async () => {
    mount(advanceView({ available: false, reasonCode: 'ADVANCE_FEE_WINDOW_NOT_OPEN' }));
    expect(await screen.findByText('La matrícula del año siguiente no está disponible por ahora.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Continuar al pago/ })).not.toBeInTheDocument();
  });
});
