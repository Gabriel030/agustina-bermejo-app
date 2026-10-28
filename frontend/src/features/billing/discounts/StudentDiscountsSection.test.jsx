import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { StudentDiscountsSection } from './StudentDiscountsSection';
import { API, money } from './discountFixtures';

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

vi.setConfig({ testTimeout: 20000 });

const ENROLLMENT = { kind: 'program', id: 4 };

function studentDiscount(overrides) {
  return {
    id: 101,
    type: 'SCHOLARSHIP',
    applicableInvoiceType: 'MONTHLY_INSTALLMENT',
    calculation: { type: 'PROPORTIONAL', ratePercent: 15 },
    priority: 1,
    fromPeriod: '2026-03',
    toPeriod: '2026-12',
    status: 'ACTIVE',
    grantedAt: '2026-02-20T10:00:00Z',
    reason: 'Situación económica',
    revokedAt: null,
    revocationReason: null,
    revokedFromPeriod: null,
    appliedInstallments: [
      { period: '2026-03', installmentNumber: 1, installmentsTotal: null },
      { period: '2026-04', installmentNumber: 2, installmentsTotal: null },
    ],
    lostPeriods: ['2026-08', '2026-09'],
    studentId: 9,
    definitionName: 'Beca',
    ...overrides,
  };
}

const DISCOUNTS = [
  studentDiscount(),
  studentDiscount({
    id: 102,
    type: 'COHORT_PROMOTION',
    fromPeriod: '2026-08',
    toPeriod: '2026-10',
    status: 'EXHAUSTED',
    lostPeriods: [],
    definitionName: 'Cohorte agosto 2026',
    calculation: { type: 'PROPORTIONAL', ratePercent: 20 },
    appliedInstallments: [
      { period: '2026-08', installmentNumber: 1, installmentsTotal: 3 },
      { period: '2026-09', installmentNumber: 2, installmentsTotal: 3 },
      { period: '2026-10', installmentNumber: 3, installmentsTotal: 3 },
    ],
  }),
  studentDiscount({
    id: 103,
    type: 'INDIVIDUAL',
    applicableInvoiceType: 'ENROLLMENT_FEE',
    calculation: { type: 'NOMINAL', amount: money('50000.00') },
    fromPeriod: '2027-03',
    toPeriod: '2027-03',
    appliedInstallments: [],
    lostPeriods: [],
    definitionName: null,
  }),
  studentDiscount({
    id: 104,
    type: 'INDIVIDUAL',
    status: 'REVOKED',
    calculation: { type: 'NOMINAL', amount: money('20000.00') },
    revokedAt: '2026-06-02T10:00:00Z',
    revokedFromPeriod: '2026-06',
    revocationReason: 'Dejó de cumplir el requisito',
    appliedInstallments: [{ period: '2026-03', installmentNumber: 1, installmentsTotal: null }],
    lostPeriods: [],
    definitionName: null,
  }),
];

function mockDiscounts(items) {
  server.use(http.get(`${API}/program-enrollments/4/student-discounts`, () => HttpResponse.json(items)));
}

function renderSection(roleBase = 'director') {
  return renderWithProviders(<StudentDiscountsSection studentId={9} enrollment={ENROLLMENT} roleBase={roleBase} />);
}

describe('StudentDiscountsSection', () => {
  beforeEach(() => {
    toastMock.success.mockClear();
    toastMock.error.mockClear();
  });

  it('lists the discounts with value, period, progress, lost months and status', async () => {
    mockDiscounts(DISCOUNTS);
    renderSection();

    const scholarship = (await screen.findByText('Beca', { selector: 'span' })).closest('tr');
    expect(within(scholarship).getByText('Cuota mensual')).toBeInTheDocument();
    expect(within(scholarship).getByText('15 %')).toBeInTheDocument();
    expect(within(scholarship).getByText('marzo 2026 – diciembre 2026')).toBeInTheDocument();
    expect(within(scholarship).getByText('2 de 10')).toBeInTheDocument();
    expect(within(scholarship).getByText('2 meses perdidos')).toBeInTheDocument();
    expect(within(scholarship).getByText('Vigente')).toBeInTheDocument();
    const promotion = screen.getByText('Cohorte agosto 2026').closest('tr');
    expect(within(promotion).getByText('3 de 3')).toBeInTheDocument();
    expect(within(promotion).getByText('Agotado')).toBeInTheDocument();
    const fee = screen.getAllByText('Descuento individual', { selector: 'span' })[0].closest('tr');
    expect(within(fee).getByText('Matrícula')).toBeInTheDocument();
    expect(within(fee).getByText('Sin emitir')).toBeInTheDocument();
    expect(screen.getByText('Revocado')).toBeInTheDocument();
  });

  it('expands a discount to show the installments, the lost months and who granted it', async () => {
    mockDiscounts(DISCOUNTS);
    const user = userEvent.setup();
    renderSection();

    const scholarship = (await screen.findByText('Beca', { selector: 'span' })).closest('tr');
    await user.click(within(scholarship).getByRole('button', { name: 'Ver detalle' }));

    expect(screen.getByText('marzo 2026 · cuota 1')).toBeInTheDocument();
    expect(screen.getByText('agosto 2026')).toBeInTheDocument();
    expect(screen.getByText('Ese mes ganó otro descuento y no se corre.')).toBeInTheDocument();
    expect(screen.getByText('Motivo: Situación económica')).toBeInTheDocument();
  });

  it('shows the revocation data of a revoked discount', async () => {
    mockDiscounts(DISCOUNTS);
    const user = userEvent.setup();
    renderSection();

    const revoked = (await screen.findByText('Revocado')).closest('tr');
    await user.click(within(revoked).getByRole('button', { name: 'Ver detalle' }));

    expect(screen.getByText(/Dejó de aplicarse desde junio 2026/)).toBeInTheDocument();
    expect(screen.getByText(/Dejó de cumplir el requisito/)).toBeInTheDocument();
  });

  it('filters by status with the column filter', async () => {
    mockDiscounts(DISCOUNTS);
    const user = userEvent.setup();
    renderSection();

    await screen.findByText('Cohorte agosto 2026');
    await user.click(screen.getByRole('button', { name: 'Filtrar por estado' }));
    await user.click(screen.getByRole('checkbox', { name: /Agotado/ }));

    expect(screen.queryByText('Cohorte agosto 2026')).not.toBeInTheDocument();
  });

  it('offers the revoke action only on active discounts', async () => {
    mockDiscounts(DISCOUNTS);
    renderSection();

    await screen.findByText('Cohorte agosto 2026');
    expect(screen.getAllByRole('button', { name: /^Revocar/ })).toHaveLength(2);
  });

  it('revokes directly as director with a required reason and reports the month it stops applying', async () => {
    mockDiscounts(DISCOUNTS);
    let body;
    server.use(
      http.post(`${API}/student-discounts/101/revocation`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(studentDiscount({ status: 'REVOKED', revokedFromPeriod: '2026-11' }), { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderSection('director');

    const scholarship = (await screen.findByText('Beca', { selector: 'span' })).closest('tr');
    await user.click(within(scholarship).getByRole('button', { name: 'Revocar beca' }));
    expect(screen.getByText(/Deja de aplicarse desde la próxima cuota a emitir/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Revocar descuento' }));
    expect(screen.getByText('El motivo es obligatorio.')).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText(/Qué lo motiva/), 'Dejó de cumplir el requisito.');
    await user.click(screen.getByRole('button', { name: 'Revocar descuento' }));

    await waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Descuento revocado desde noviembre 2026.'));
    expect(body).toEqual({ reason: 'Dejó de cumplir el requisito.' });
    await waitFor(() => expect(screen.queryByPlaceholderText(/Qué lo motiva/)).not.toBeInTheDocument());
  });

  it('proposes the revocation as administrative and links to their requests', async () => {
    mockDiscounts(DISCOUNTS);
    server.use(http.post(`${API}/student-discounts/101/revocation`, () => HttpResponse.json({ authorizationRequestId: 88 }, { status: 202 })));
    const user = userEvent.setup();
    renderSection('administrativo');

    const scholarship = (await screen.findByText('Beca', { selector: 'span' })).closest('tr');
    expect(screen.getByRole('link', { name: 'Proponer descuento' })).toBeInTheDocument();
    await user.click(within(scholarship).getByRole('button', { name: 'Proponer revocar beca' }));
    expect(screen.getByText(/Si nadie lo decide antes de fin de mes, la propuesta vence/)).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText(/Qué lo motiva/), 'Ya no corresponde.');
    await user.click(screen.getByRole('button', { name: 'Enviar al director' }));

    await waitFor(() => expect(toastMock.success).toHaveBeenCalled());
    const [message, options] = toastMock.success.mock.calls[0];
    expect(message).toBe('Pedido de revocación enviado al director.');
    expect(options.action.label).toBe('Ver mis pedidos');
  });

  it('keeps the dialog open when the server refuses the revocation', async () => {
    mockDiscounts(DISCOUNTS);
    server.use(
      http.post(`${API}/student-discounts/101/revocation`, () =>
        HttpResponse.json({ code: 'ILLEGAL_STUDENT_DISCOUNT_TRANSITION', violations: [] }, { status: 409 })),
    );
    const user = userEvent.setup();
    renderSection('director');

    const scholarship = (await screen.findByText('Beca', { selector: 'span' })).closest('tr');
    await user.click(within(scholarship).getByRole('button', { name: 'Revocar beca' }));
    await user.type(screen.getByPlaceholderText(/Qué lo motiva/), 'Motivo');
    await user.click(screen.getByRole('button', { name: 'Revocar descuento' }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith('No pudimos revocar el descuento. Probá de nuevo.'));
    expect(screen.getByPlaceholderText(/Qué lo motiva/)).toHaveValue('Motivo');
  });

  it('shows the empty, loading and error states', async () => {
    mockDiscounts([]);
    const empty = renderSection();
    expect(await screen.findByText('Sin descuentos')).toBeInTheDocument();
    empty.unmount();

    server.use(http.get(`${API}/program-enrollments/4/student-discounts`, () => new Promise(() => {})));
    const loading = renderSection();
    expect(screen.getByLabelText('Cargando')).toBeInTheDocument();
    loading.unmount();

    server.use(http.get(`${API}/program-enrollments/4/student-discounts`, () => new HttpResponse(null, { status: 500 })));
    renderSection();
    expect(await screen.findByText('No pudimos cargar los descuentos.')).toBeInTheDocument();
    mockDiscounts([]);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Sin descuentos')).toBeInTheDocument();
  });
});
