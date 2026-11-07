import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route, useLocation } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { createDeferred } from '@/test/deferred';
import { errorBody } from '@/test/fixtures/errors';
import { annualQuote, annualRequest } from '@/test/fixtures/annualUpfront';
import { annualTerms } from '@/test/fixtures/annualTerms';
import { AnnualUpfrontCard } from './AnnualUpfrontCard';
import { AnnualUpfrontCards } from './AnnualUpfrontCards';

const API = 'http://localhost:8080';

function Destination() {
  return <div>{useLocation().pathname}</div>;
}

function mount({ quote, requests = [], primary = false }) {
  server.use(
    http.get(`${API}/me/program-enrollments/3/annual-upfront/quote`, () => HttpResponse.json(quote)),
    http.get(`${API}/me/program-enrollments/3/annual-upfront-requests`, () => HttpResponse.json(requests)),
  );
  return renderWithProviders(
    <Routes>
      <Route path="/" element={<AnnualUpfrontCard enrollmentId={3} programName="Profesorado" primary={primary} />} />
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
    expect(screen.getByText('Aceptá los términos para pedir el año.')).toBeInTheDocument();
    await user.click(await screen.findByRole('checkbox'));
    await user.click(confirm);
    const { key, body } = await sent.promise;
    expect(key).toBeTruthy();
    expect(body).toEqual({ termsAccepted: true, termsVersion: 'v1' });
  });

  it('sendsTheCurrentTermsVersionAndLinksToItInANewTab', async () => {
    const user = userEvent.setup();
    const sent = createDeferred();
    mount({ quote: annualQuote() });
    server.use(
      http.get(`${API}/annual-upfront-terms/current`, () => HttpResponse.json(annualTerms({ versionNumber: 2, version: 'v2' }))),
      http.post(`${API}/me/program-enrollments/3/annual-upfront-requests`, async ({ request }) => {
        sent.resolve(await request.json());
        return HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 });
      }),
    );
    await user.click(await screen.findByRole('button', { name: 'Pedir el año completo' }));
    const link = await screen.findByRole('link', { name: /Leer los términos \(v2\)/ });
    expect(link).toHaveAttribute('href', '/terminos-pago-anual/2');
    expect(link).toHaveAttribute('target', '_blank');
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getAllByRole('button', { name: 'Pedir el año completo' }).at(-1));
    expect(await sent.promise).toEqual({ termsAccepted: true, termsVersion: 'v2' });
  });

  it('reloadsTermsAndAsksAgainWhenTheVersionIsOutdated', async () => {
    const user = userEvent.setup();
    const bodies = [];
    let published = false;
    mount({ quote: annualQuote() });
    server.use(
      http.get(`${API}/annual-upfront-terms/current`, () =>
        HttpResponse.json(published ? annualTerms({ versionNumber: 2, version: 'v2' }) : annualTerms()),
      ),
      http.post(`${API}/me/program-enrollments/3/annual-upfront-requests`, async ({ request }) => {
        bodies.push(await request.json());
        published = true;
        return HttpResponse.json(errorBody('ANNUAL_UPFRONT_TERMS_OUTDATED'), { status: 409 });
      }),
    );
    await user.click(await screen.findByRole('button', { name: 'Pedir el año completo' }));
    await user.click(await screen.findByRole('checkbox'));
    await user.click(screen.getAllByRole('button', { name: 'Pedir el año completo' }).at(-1));
    expect(await screen.findByRole('alert')).toHaveTextContent('Cambiaron los términos');
    expect(await screen.findByRole('link', { name: /Leer los términos \(v2\)/ })).toBeInTheDocument();
    expect(screen.getByRole('checkbox')).not.toBeChecked();
    expect(screen.getAllByRole('button', { name: 'Pedir el año completo' }).at(-1)).toBeDisabled();
    expect(bodies).toEqual([{ termsAccepted: true, termsVersion: 'v1' }]);
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
    await user.click(await screen.findByRole('checkbox'));
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

  it('showsOnlyServerTotalWithoutStruckThroughPrice', async () => {
    const { container } = mount({ quote: annualQuote() });
    await screen.findByText(/792\.000/);
    expect(container.querySelector('.line-through')).toBeNull();
    expect(screen.queryByText(/880\.000/)).not.toBeInTheDocument();
  });

  it('usesGradientCtaOnlyWhenCardIsPrimary', async () => {
    mount({ quote: annualQuote(), primary: true });
    expect((await screen.findByRole('button', { name: 'Pedir el año completo' })).className).toContain('bg-gradient-to-br');
  });

  it('usesOutlineCtaWhenCardIsNotPrimary', async () => {
    mount({ quote: annualQuote() });
    expect((await screen.findByRole('button', { name: 'Pedir el año completo' })).className).not.toContain('bg-gradient-to-br');
  });

  it('keepsIdempotencyKeyWhenDialogIsClosedAndReopenedAfterUnknownOutcome', async () => {
    const user = userEvent.setup();
    const keys = [];
    mount({ quote: annualQuote() });
    server.use(
      http.post(`${API}/me/program-enrollments/3/annual-upfront-requests`, ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        return HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 });
      }),
    );
    await user.click(await screen.findByRole('button', { name: 'Pedir el año completo' }));
    await user.click(await screen.findByRole('checkbox'));
    await user.click(screen.getAllByRole('button', { name: 'Pedir el año completo' }).at(-1));
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos confirmar');
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    await user.click(await screen.findByRole('button', { name: 'Pedir el año completo' }));
    await user.click(await screen.findByRole('checkbox'));
    await user.click(screen.getAllByRole('button', { name: 'Pedir el año completo' }).at(-1));
    await vi.waitFor(() => expect(keys).toHaveLength(2));
    expect(keys[1]).toBe(keys[0]);
  });

  it('showsNeutralFallbackForUnknownRequestStatus', async () => {
    mount({ quote: { eligible: false, reasonCode: 'ANNUAL_UPFRONT_PAYMENT_ALREADY_REQUESTED', reasonMessage: 'x' }, requests: [annualRequest({ status: 'SOMETHING_NEW' })] });
    expect(await screen.findByText('SOMETHING_NEW')).toBeInTheDocument();
    expect(screen.queryByText('Esperando el pago')).not.toBeInTheDocument();
  });

  it('mapsDiscardFailureByBackendCode', async () => {
    const user = userEvent.setup();
    mount({ quote: { eligible: false, reasonCode: 'ANNUAL_UPFRONT_PAYMENT_ALREADY_REQUESTED', reasonMessage: 'x' }, requests: [annualRequest()] });
    server.use(
      http.post(`${API}/me/annual-upfront-requests/7/discard`, () =>
        HttpResponse.json(errorBody('ILLEGAL_ANNUAL_UPFRONT_REQUEST_TRANSITION'), { status: 409 }),
      ),
    );
    await user.click(await screen.findByRole('button', { name: 'Descartar pedido' }));
    await user.click(screen.getAllByRole('button', { name: 'Descartar pedido' }).at(-1));
    expect(await screen.findByRole('alert')).toHaveTextContent('ya cambió de estado');
  });

  it('givesGradientOnlyToFirstActionableCardAndNoneWhenAdmissionIsActionable', async () => {
    const enrollments = [{ id: 3, programName: 'Uno' }, { id: 4, programName: 'Dos' }];
    server.use(
      http.get(`${API}/me/program-enrollments/3/annual-upfront/quote`, () => HttpResponse.json(annualQuote())),
      http.get(`${API}/me/program-enrollments/4/annual-upfront/quote`, () => HttpResponse.json(annualQuote())),
    );
    renderWithProviders(<AnnualUpfrontCards enrollments={enrollments} />);
    const buttons = await screen.findAllByRole('button', { name: 'Pedir el año completo' });
    expect(buttons).toHaveLength(2);
    await vi.waitFor(() => expect(buttons[0].className).toContain('bg-gradient-to-br'));
    expect(buttons[1].className).not.toContain('bg-gradient-to-br');
  });

  it('givesNoGradientWhenAnAdmissionInvoiceIsActionable', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 1, programName: 'X', status: 'APPROVED', admissionInvoice: { id: 10, total: { amount: '1.00', currency: 'ARS' }, dueDate: '2027-01-01', paymentStatus: 'PENDING' } }]),
      ),
      http.get(`${API}/me/program-enrollments/3/annual-upfront/quote`, () => HttpResponse.json(annualQuote())),
    );
    renderWithProviders(<AnnualUpfrontCards enrollments={[{ id: 3, programName: 'Uno' }]} />);
    const button = await screen.findByRole('button', { name: 'Pedir el año completo' });
    await vi.waitFor(() => expect(button.className).not.toContain('bg-gradient-to-br'));
  });
});
