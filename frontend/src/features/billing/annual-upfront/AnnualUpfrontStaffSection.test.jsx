import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { createDeferred } from '@/test/deferred';
import { errorBody } from '@/test/fixtures/errors';
import { annualQuote, annualRequest } from '@/test/fixtures/annualUpfront';
import { AnnualUpfrontStaffSection } from './AnnualUpfrontStaffSection';

const API = 'http://localhost:8080';
const auth = vi.hoisted(() => ({ roles: ['ADMINISTRATIVO'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { id: 9, roles: auth.roles }, roles: auth.roles }),
}));

function mount(requests, quote = { eligible: false, reasonCode: 'ANNUAL_UPFRONT_PAYMENT_ALREADY_REQUESTED', reasonMessage: 'x' }) {
  server.use(
    http.get(`${API}/program-enrollments/3/annual-upfront-requests`, () => HttpResponse.json(requests)),
    http.get(`${API}/program-enrollments/3/annual-upfront/quote`, () => HttpResponse.json(quote)),
  );
  return renderWithProviders(<AnnualUpfrontStaffSection enrollmentId={3} studentId={12} />);
}

describe('AnnualUpfrontStaffSection', () => {
  beforeEach(() => {
    auth.roles = ['ADMINISTRATIVO'];
  });

  it('showsRequestStateTotalAndDeadline', async () => {
    mount([annualRequest()]);
    expect(await screen.findByText('Esperando el pago')).toBeInTheDocument();
    expect(screen.getByText(/792\.000/)).toBeInTheDocument();
    expect(screen.getByText('Plazo de pago')).toBeInTheDocument();
  });

  it('collectsCashForTheExactTotalWithIdempotencyKeyAndReceipt', async () => {
    const user = userEvent.setup();
    const sent = createDeferred();
    mount([annualRequest()]);
    server.use(
      http.post(`${API}/students/12/annual-upfront-requests/7/cash-payment`, async ({ request }) => {
        sent.resolve({ key: request.headers.get('Idempotency-Key'), body: await request.json() });
        return HttpResponse.json(
          { requestId: 7, invoiceId: 55, paymentId: 1, studentCurrentAccountId: 2, paidOn: '2027-02-03', amount: { amount: '792000.00', currency: 'ARS' } },
          { status: 201 },
        );
      }),
    );
    await user.click(await screen.findByRole('button', { name: /Cobrar en efectivo/ }));
    await user.type(screen.getByLabelText(/Número de recibo/), 'R-1');
    await user.click(screen.getByRole('button', { name: 'Cobrar y confirmar el año' }));
    const { key, body } = await sent.promise;
    expect(key).toBeTruthy();
    expect(body).toEqual({ amount: { amount: '792000.00', currency: 'ARS' }, receiptNumber: 'R-1' });
  });

  it('reusesTheSameKeyWhenRetryingAfterUnknownOutcome', async () => {
    const user = userEvent.setup();
    const keys = [];
    mount([annualRequest()]);
    server.use(
      http.post(`${API}/students/12/annual-upfront-requests/7/cash-payment`, ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        return keys.length === 1
          ? HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })
          : HttpResponse.json({ requestId: 7, invoiceId: 55, paymentId: 1, studentCurrentAccountId: 2, paidOn: '2027-02-03', amount: { amount: '792000.00', currency: 'ARS' } }, { status: 201 });
      }),
    );
    await user.click(await screen.findByRole('button', { name: /Cobrar en efectivo/ }));
    await user.click(screen.getByRole('button', { name: 'Cobrar y confirmar el año' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos confirmar');
    await user.click(screen.getByRole('button', { name: 'Reintentar cobro' }));
    await vi.waitFor(() => expect(keys).toHaveLength(2));
    expect(keys[1]).toBe(keys[0]);
  });

  it('showsServerReasonWhenCashPaymentIsRefused', async () => {
    const user = userEvent.setup();
    mount([annualRequest()]);
    server.use(
      http.post(`${API}/students/12/annual-upfront-requests/7/cash-payment`, () =>
        HttpResponse.json(errorBody('ANNUAL_UPFRONT_PAYMENT_EXPIRED'), { status: 409 }),
      ),
    );
    await user.click(await screen.findByRole('button', { name: /Cobrar en efectivo/ }));
    await user.click(screen.getByRole('button', { name: 'Cobrar y confirmar el año' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('El plazo del pedido venció');
  });

  it('hidesCashButtonWhenPaymentIsUnderReview', async () => {
    mount([annualRequest({ status: 'PAYMENT_UNDER_REVIEW', paymentUnderReview: true })]);
    expect(await screen.findByText('Pago en revisión')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Cobrar en efectivo/ })).not.toBeInTheDocument();
    expect(screen.getByText(/bandeja de comprobantes/)).toBeInTheDocument();
  });

  it('hidesCashButtonForRolesThatCannotCollect', async () => {
    auth.roles = ['PRECEPTOR'];
    mount([annualRequest()]);
    expect(await screen.findByText('Esperando el pago')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Cobrar en efectivo/ })).not.toBeInTheDocument();
  });

  it('showsEligibilityWhenThereIsNoRequest', async () => {
    mount([], annualQuote());
    expect(await screen.findByText('Puede pedirlo')).toBeInTheDocument();
  });
});
