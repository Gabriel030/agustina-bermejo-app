import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { NewPriceVersionPage } from './NewPriceVersionPage';
import { PricesPage } from './PricesPage';

const API = 'http://localhost:8080';

const auth = vi.hoisted(() => ({ roles: ['DIRECTOR'] }));
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ usuario: { roles: auth.roles } }) }));
vi.mock('@/features/billing/hooks/useOfferNames', () => ({
  useOfferNames: () => ({
    isLoading: false,
    programs: [],
    courses: [],
    programName: (id) => `Carrera ${id}`,
    courseName: (id) => `Curso ${id}`,
  }),
}));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: { success: vi.fn(), error: vi.fn() } }));

vi.setConfig({ testTimeout: 20000 });

const money = (amount) => ({ amount, currency: 'ARS' });
const version = (id, amount, extra = {}) => ({
  id,
  priceId: 1,
  status: 'EFFECTIVE',
  effectiveFrom: '2026-01-01',
  baseAmount: money(amount),
  marketingDiscountType: 'PERCENTAGE',
  marketingDiscountPercent: 0,
  marketingDiscountFixedAmount: money('0.00'),
  marketingDiscountAmount: money('0.00'),
  finalAmount: money(amount),
  changeReason: 'Alta',
  directPublication: true,
  proposedById: 1,
  decidedById: 1,
  cancelledById: 1,
  cancellationReason: '',
  ...extra,
});

const withPrice = {
  priceId: 1,
  programId: 10,
  courseId: null,
  feeType: 'ENROLLMENT_FEE',
  description: 'Matrícula',
  currency: 'ARS',
  currentVersion: version(11, '45000.00'),
  nextScheduledVersion: version(12, '52000.00', { status: 'SCHEDULED', effectiveFrom: '2027-01-01', appliesToLectiveYear: 2027 }),
  openProposal: version(13, '48000.00', { status: 'PROPOSED', effectiveFrom: '2026-12-01' }),
};
const otherPrice = { ...withPrice, priceId: 2, programId: 20, feeType: 'MONTHLY_INSTALLMENT', description: 'Cuota' };

function useBackend(prices) {
  server.use(
    http.get(`${API}/prices`, () => HttpResponse.json(prices)),
    http.get(`${API}/terms`, () => HttpResponse.json([{ id: 1, year: 2027, type: 'ANNUAL', status: 'ACTIVE', description: 'Año 2027', startDate: '2027-02-01T00:00:00Z', endDate: '2027-12-15T00:00:00Z', enrollmentStart: '2027-02-01T00:00:00Z', enrollmentEnd: '2027-02-28T00:00:00Z', enrollmentOpen: true }])),
    http.post(`${API}/prices/1/versions/impact`, () =>
      HttpResponse.json({ effectiveFrom: '2026-11-08', reachesActiveEnrollments: true, affectedStudents: 3, currentFinalAmount: money('45000.00'), proposedFinalAmount: money('52000.00'), currentMonthlyIncome: money('135000.00'), proposedMonthlyIncome: money('156000.00'), monthlyIncomeDifference: money('21000.00'), monthlyMarketingDiscountCost: money('0.00') }),
    ),
  );
}

describe('PricesPage', () => {
  it('groups concepts by offer and shows the advance line', async () => {
    useBackend([withPrice, otherPrice]);
    renderWithProviders(<PricesPage />);
    expect(await screen.findByText('Carrera 10')).toBeInTheDocument();
    expect(screen.getAllByText(/Anticipada 2027/).length).toBeGreaterThan(0);
    expect(screen.getAllByText('Propuesta pendiente').length).toBeGreaterThan(0);
  });

  it('filters offers with the search box', async () => {
    useBackend([withPrice, otherPrice]);
    renderWithProviders(<PricesPage />);
    await screen.findByText('Carrera 10');
    await userEvent.type(screen.getByPlaceholderText('Buscar por carrera o curso'), '20');
    expect(screen.queryByText('Carrera 10')).not.toBeInTheDocument();
    expect(screen.getByText('Carrera 20')).toBeInTheDocument();
  });

  it('keeps a pressed card as the active filter and clears it', async () => {
    useBackend([withPrice, otherPrice]);
    renderWithProviders(<PricesPage />);
    await screen.findByText('Carrera 10');
    await userEvent.click(screen.getByRole('button', { name: /Propuestas pendientes/ }));
    expect(screen.getByRole('button', { name: /Quitar filtro/ })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Quitar filtro/ }));
    expect(screen.queryByRole('button', { name: /Quitar filtro/ })).not.toBeInTheDocument();
  });
})

describe('NewPriceVersionPage', () => {
  function renderForm() {
    return renderWithProviders(
      <Routes>
        <Route path="/director/precios/:priceId/nueva-version" element={<NewPriceVersionPage />} />
        <Route path="/director/precios/:priceId" element={<p>Detalle</p>} />
      </Routes>,
      { route: '/director/precios/1/nueva-version' },
    );
  }

  it('sends the chosen lective year and publishes directly as director', async () => {
    useBackend([withPrice]);
    let published;
    server.use(
      http.post(`${API}/prices/1/versions/validation`, () => HttpResponse.json({ violations: [] })),
      http.post(`${API}/prices/1/versions`, async ({ request }) => {
        published = await request.json();
        return HttpResponse.json(version(99, '52000.00', { status: 'SCHEDULED' }));
      }),
    );
    renderForm();
    await userEvent.type(await screen.findByLabelText('Precio de lista'), '52000');
    await userEvent.click(screen.getByLabelText('Porcentaje'));
    await userEvent.type(screen.getByLabelText('Porcentaje de descuento'), '10');
    await userEvent.click(screen.getByLabelText('Año lectivo al que aplica'));
    expect(await screen.findByRole('option', { name: /2028/ })).toHaveAttribute('aria-disabled', 'true');
    await userEvent.click(screen.getByRole('option', { name: '2027' }));
    await userEvent.type(screen.getByLabelText('Motivo (obligatorio)'), 'Aumento anual');
    expect(await screen.findByText(/Quien adelante la matrícula 2027/)).toBeInTheDocument();
    const publish = screen.getByRole('button', { name: 'Publicar cambio' });
    await waitFor(() => expect(publish).toBeEnabled());
    await userEvent.click(publish);
    await waitFor(() => expect(published).toBeDefined());
    expect(published.appliesToLectiveYear).toBe(2027);
    expect(published.marketingDiscount).toEqual({ type: 'PERCENTAGE', percent: 10 });
    expect(published.amount).toEqual({ amount: '52000.00', currency: 'ARS' });
    expect(await screen.findByText('Detalle')).toBeInTheDocument();
  });

  it('blocks the submit while the validation reports a violation', async () => {
    useBackend([withPrice]);
    server.use(
      http.post(`${API}/prices/1/versions/validation`, () =>
        HttpResponse.json({ violations: [{ code: 'ADVANCE_PRICE_STARTS_AFTER_REGULAR_EMISSION', message: 'La vigencia es posterior a la emisión regular.' }] }),
      ),
    );
    renderForm();
    await userEvent.type(await screen.findByLabelText('Precio de lista'), '52000');
    await userEvent.click(screen.getByLabelText('Porcentaje'));
    await userEvent.type(screen.getByLabelText('Porcentaje de descuento'), '10');
    await userEvent.type(screen.getByLabelText('Motivo (obligatorio)'), 'Aumento');
    expect(await screen.findByText('La vigencia es posterior a la emisión regular.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publicar cambio' })).toBeDisabled();
  });
});
