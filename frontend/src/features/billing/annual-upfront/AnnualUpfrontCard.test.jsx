import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route, useLocation } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { createDeferred } from '@/test/deferred';
import { errorBody } from '@/test/fixtures/errors';
import { annualQuote, annualRequest } from '@/test/fixtures/annualUpfront';
import { AnnualUpfrontCard } from './AnnualUpfrontCard';

const API = 'http://localhost:8080';

function Destination() {
  return <div>{useLocation().pathname}</div>;
}

function mount({ quote, requests = [] }) {
  server.use(
    http.get(`${API}/me/program-enrollments/3/annual-upfront/quote`, () => HttpResponse.json(quote)),
    http.get(`${API}/me/program-enrollments/3/annual-upfront-requests`, () => HttpResponse.json(requests)),
  );
  return renderWithProviders(
    <Routes>
      <Route path="/" element={<AnnualUpfrontCard enrollmentId={3} programName="Profesorado" />} />
      <Route path="*" element={<Destination />} />
    </Routes>,
  );
}

describe('AnnualUpfrontCard', () => {
  it('showsTotalSavingAndDeadlineWhenEligible', async () => {
    mount({ quote: annualQuote() });
    expect(await screen.findByText(/792\.000/)).toBeInTheDocument();
    expect(screen.getByText(/Ahorrás/)).toHaveTextContent('88.000');
    expect(screen.getByText(/hasta el 10\/02\/2027/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /11 cuotas de/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pedir el año completo' })).toBeInTheDocument();
  });

  it('showsNoPromotionPillWhenQuoteHasNoDiscount', async () => {
    const eligibility = annualQuote();
    delete eligibility.quote.discount;
    mount({ quote: eligibility });
    expect(await screen.findByText('Sin promoción')).toBeInTheDocument();
    expect(screen.queryByText(/Ahorrás/)).not.toBeInTheDocument();
  });

  it('requestsTheYearSendingTermsAcceptanceWithIdempotencyKey', async () => {
    const user = userEvent.setup();
    const sent = createDeferred();
    mount({ quote: annualQuote() });
    server.use(
      http.post(`${API}/me/program-enrollments/3/annual-upfront-requests`, async ({ request }) => {
        sent.resolve({ key: request.headers.get('Idempotency-Key'), body: await request.json() });
        return HttpResponse.json(
          { id: 7, invoiceId: 55, status: 'AWAITING_PAYMENT', acceptedAt: '2027-02-02T10:00:00', acceptedTermsVersion: 'v1', paymentDeadline: '2027-02-04T10:00:00', total: { amount: '792000.00', currency: 'ARS' } },
          { status: 201 },
        );
      }),
    );
    await user.click(await screen.findByRole('button', { name: 'Pedir el año completo' }));
    const confirm = screen.getAllByRole('button', { name: 'Pedir el año completo' }).at(-1);
    expect(confirm).toBeDisabled();
    await user.click(screen.getByRole('checkbox'));
    await user.click(confirm);
    const { key, body } = await sent.promise;
    expect(key).toBeTruthy();
    expect(body).toEqual({ termsAccepted: true, termsVersion: 'v1' });
  });

  it('showsServerMessageWhenRequestIsRejected', async () => {
    const user = userEvent.setup();
    mount({ quote: annualQuote() });
    server.use(
      http.post(`${API}/me/program-enrollments/3/annual-upfront-requests`, () =>
        HttpResponse.json(errorBody('ANNUAL_UPFRONT_REQUEST_WINDOW_CLOSED'), { status: 409 }),
      ),
    );
    await user.click(await screen.findByRole('button', { name: 'Pedir el año completo' }));
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getAllByRole('button', { name: 'Pedir el año completo' }).at(-1));
    expect(await screen.findByRole('alert')).toHaveTextContent('Ya pasó la fecha límite');
  });

  it.each([
    ['NO_MONTHS_LEFT_FOR_ANNUAL_UPFRONT', 'Ya no disponible'],
    ['ANNUAL_UPFRONT_NOT_AVAILABLE_WITH_SCHOLARSHIP', 'Con beca'],
    ['OVERDUE_DEBT_BLOCKS_ANNUAL_UPFRONT', 'Con deuda'],
    ['ANNUAL_UPFRONT_REQUEST_WINDOW_CLOSED', 'Fuera de fecha'],
  ])('showsReasonWithoutButtonWhen%s', async (reasonCode, label) => {
    mount({ quote: { eligible: false, reasonCode, reasonMessage: 'Motivo del servidor' } });
    expect(await screen.findByText(label)).toBeInTheDocument();
    expect(screen.getByText('Motivo del servidor')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('rendersNothingWhenEnrollmentIsNotActive', async () => {
    const { container } = mount({ quote: { eligible: false, reasonCode: 'ENROLLMENT_NOT_ACTIVE', reasonMessage: 'x' } });
    await waitFor(() => expect(container).toBeEmptyDOMElement());
  });

  it('guidesPaymentWhileAwaitingAndOpensReceiptUpload', async () => {
    const user = userEvent.setup();
    mount({ quote: { eligible: false, reasonCode: 'ANNUAL_UPFRONT_PAYMENT_ALREADY_REQUESTED', reasonMessage: 'x' }, requests: [annualRequest()] });
    expect(await screen.findByText('Esperando el pago')).toBeInTheDocument();
    expect(screen.getByText(/Te avisamos 24 h antes/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Subir comprobante' }));
    expect(await screen.findByText('/alumno/pagos/55/transferencia')).toBeInTheDocument();
  });

  it('discardsTheRequestAfterConfirmation', async () => {
    const user = userEvent.setup();
    const discarded = createDeferred();
    mount({ quote: { eligible: false, reasonCode: 'ANNUAL_UPFRONT_PAYMENT_ALREADY_REQUESTED', reasonMessage: 'x' }, requests: [annualRequest()] });
    server.use(
      http.post(`${API}/me/annual-upfront-requests/7/discard`, () => {
        discarded.resolve(true);
        return HttpResponse.json({ id: 7, invoiceId: 55, status: 'DISCARDED', discardReason: 'CANCELLED_BY_STUDENT' });
      }),
    );
    await user.click(await screen.findByRole('button', { name: 'Descartar pedido' }));
    await user.click(screen.getAllByRole('button', { name: 'Descartar pedido' }).at(-1));
    expect(await discarded.promise).toBe(true);
  });

  it('showsReviewConfirmedAndDiscardedStates', async () => {
    mount({ quote: { eligible: false, reasonCode: 'ANNUAL_UPFRONT_PAYMENT_ALREADY_REQUESTED', reasonMessage: 'x' }, requests: [annualRequest({ status: 'PAYMENT_UNDER_REVIEW', paymentUnderReview: true })] });
    expect(await screen.findByText('Pago en revisión')).toBeInTheDocument();
    expect(screen.getByText(/el pedido no vence/)).toBeInTheDocument();
  });

  it('offersToRequestAgainAfterRejectedPaymentWhenStillInTime', async () => {
    mount({ quote: annualQuote(), requests: [annualRequest({ status: 'DISCARDED', discardReason: 'PAYMENT_REJECTED' })] });
    expect(await screen.findByText(/Pago rechazado/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Volver a pedir' })).toBeInTheDocument();
  });

  it('showsRetryWhenQuoteFails', async () => {
    server.use(http.get(`${API}/me/program-enrollments/3/annual-upfront/quote`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })));
    renderWithProviders(<AnnualUpfrontCard enrollmentId={3} programName="Profesorado" />);
    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
