import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { MyDiscountsTab } from './MyDiscountsTab';
import { API, money } from './discountFixtures';

function mine(overrides) {
  return {
    id: 1,
    type: 'SCHOLARSHIP',
    applicableInvoiceType: 'MONTHLY_INSTALLMENT',
    calculation: { type: 'PROPORTIONAL', ratePercent: 15 },
    fromPeriod: '2026-03',
    toPeriod: '2026-12',
    status: 'ACTIVE',
    appliedInstallments: [{ period: '2026-03', installmentNumber: 1, installmentsTotal: null }],
    lostPeriods: ['2026-08', '2026-09'],
    definitionName: 'Beca',
    enrollmentName: 'Enfermería',
    reason: null,
    grantedById: null,
    authorizationRequestId: null,
    revocationReason: null,
    endCause: null,
    endCauseText: null,
    revokedFromPeriod: null,
    ...overrides,
  };
}

function mockMine(items) {
  server.use(http.get(`${API}/me/student-discounts`, () => HttpResponse.json(items)));
}

describe('MyDiscountsTab', () => {
  it('groups the discounts in current and history with progress, lost months and the rule', async () => {
    mockMine([
      mine(),
      mine({ id: 2, type: 'COHORT_PROMOTION', status: 'EXHAUSTED', lostPeriods: [], definitionName: 'Cohorte agosto', fromPeriod: '2026-08', toPeriod: '2026-10', appliedInstallments: [1, 2, 3].map((n) => ({ period: `2026-0${7 + n}`, installmentNumber: n, installmentsTotal: 3 })) }),
      mine({ id: 3, type: 'INDIVIDUAL', applicableInvoiceType: 'ENROLLMENT_FEE', calculation: { type: 'NOMINAL', amount: money('50000.00') }, fromPeriod: '2027-03', toPeriod: '2027-03', appliedInstallments: [], lostPeriods: [], definitionName: null }),
    ]);
    renderWithProviders(<MyDiscountsTab />);

    const current = (await screen.findByRole('heading', { name: 'Vigentes' })).closest('section');
    const scholarship = within(current).getByRole('article', { name: /Beca/ });
    expect(within(scholarship).getByText('15 %')).toBeInTheDocument();
    expect(within(scholarship).getByRole('progressbar', { name: 'Cuotas con descuento: 1 de 10' })).toBeInTheDocument();
    expect(within(scholarship).getByText('2 meses perdidos: agosto 2026, septiembre 2026')).toBeInTheDocument();
    expect(within(current).getByText(/Se aplica cuando se emita la factura de matrícula/)).toBeInTheDocument();
    const history = screen.getByRole('heading', { name: 'Historial' }).closest('section');
    expect(within(history).getByText('Usado completo')).toBeInTheDocument();
    expect(screen.getByText('Si dos descuentos coinciden, se aplica el que más te ahorra.')).toBeInTheDocument();
  });

  it('explains the end of a discount in plain words and never shows the internal reason', async () => {
    mockMine([
      mine({
        status: 'REVOKED',
        endCause: 'INSTITUTION_DECISION',
        endCauseText: 'Decisión de la institución',
        revokedFromPeriod: '2026-06',
        lostPeriods: [],
        reason: 'Motivo interno que no debe verse',
        revocationReason: 'Revocación interna que no debe verse',
      }),
    ]);
    renderWithProviders(<MyDiscountsTab />);

    expect(await screen.findByText(/Decisión de la institución. Dejó de aplicarse desde junio 2026/)).toBeInTheDocument();
    expect(screen.getByText('Revocado')).toBeInTheDocument();
    expect(screen.queryByText(/no debe verse/)).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Vigentes' })).not.toBeInTheDocument();
  });

  it('shows the empty state', async () => {
    mockMine([]);
    renderWithProviders(<MyDiscountsTab />);

    expect(await screen.findByText('No tenés descuentos')).toBeInTheDocument();
  });

  it('shows skeleton cards while loading', () => {
    server.use(http.get(`${API}/me/student-discounts`, () => new Promise(() => {})));
    renderWithProviders(<MyDiscountsTab />);

    expect(screen.getByLabelText('Cargando')).toBeInTheDocument();
  });

  it('shows an error and retries', async () => {
    server.use(http.get(`${API}/me/student-discounts`, () => new HttpResponse(null, { status: 500 })));
    renderWithProviders(<MyDiscountsTab />);

    expect(await screen.findByText('No pudimos cargar tus descuentos')).toBeInTheDocument();
    mockMine([mine()]);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('heading', { name: 'Vigentes' })).toBeInTheDocument();
  });
});
