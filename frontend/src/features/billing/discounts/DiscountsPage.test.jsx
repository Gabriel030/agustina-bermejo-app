import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DiscountsPage } from './DiscountsPage';
import { API, CATALOG, PROMOTIONS, mockOffers, promotion } from './discountFixtures';

const auth = vi.hoisted(() => ({ roles: ['DIRECTOR'] }));
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: auth.roles } }),
}));

vi.setConfig({ testTimeout: 20000 });

function mockDefinitions(entries) {
  server.use(http.get(`${API}/discount-definitions`, () => HttpResponse.json(entries)));
}

function lostBenefit(overrides) {
  return {
    promotionRedemptionId: 1,
    promotionDescription: 'Cohorte agosto 2026',
    studentName: 'Medina, Sofía',
    feeType: 'MONTHLY_INSTALLMENT',
    used: 1,
    scopeCount: 3,
    remaining: 2,
    cutOffAt: '2026-10',
    discountDefinitionId: 3,
    studentId: 77,
    lostBenefitCount: 2,
    ...overrides,
  };
}

function mockLostBenefits(items, total = items.length, onRequest) {
  server.use(
    http.get(`${API}/reports/billing/promotions/lost-benefits`, ({ request }) => {
      onRequest?.(new URL(request.url).searchParams);
      return HttpResponse.json(items, { headers: { 'X-Total-Count': String(total) } });
    }),
  );
}

describe('DiscountsPage', () => {
  beforeEach(() => {
    auth.roles = ['DIRECTOR'];
    mockOffers();
  });

  it('lists scholarships and annual upfront discounts with a status and links to the detail', async () => {
    mockDefinitions([...CATALOG, ...PROMOTIONS]);
    renderWithProviders(<DiscountsPage />);

    const link = await screen.findByRole('link', { name: 'Pago anual anticipado 2027' });
    expect(link).toHaveAttribute('href', '/director/descuentos/2');
    const row = link.closest('tr');
    expect(within(row).getByText('15 %')).toBeInTheDocument();
    expect(within(row).getByText('Próxima')).toBeInTheDocument();
    expect(screen.getByText('Cerrada')).toBeInTheDocument();
    expect(screen.queryByText('Cohorte agosto 2026')).not.toBeInTheDocument();
  });

  it('offers the director a direct create button and the administrative a proposal', async () => {
    mockDefinitions(CATALOG);
    const view = renderWithProviders(<DiscountsPage />);

    expect(await screen.findByRole('link', { name: 'Nuevo descuento' })).toHaveAttribute('href', '/director/descuentos/nuevo');
    view.unmount();

    auth.roles = ['ADMINISTRATIVO'];
    renderWithProviders(<DiscountsPage />);
    expect(await screen.findByRole('link', { name: 'Proponer descuento' })).toHaveAttribute('href', '/administrativo/descuentos/nuevo');
  });

  it('filters the catalog by status with the Excel style column filter', async () => {
    mockDefinitions(CATALOG);
    const user = userEvent.setup();
    renderWithProviders(<DiscountsPage />);

    await screen.findByRole('link', { name: 'Beca vieja' });
    await user.click(screen.getByRole('button', { name: 'Filtrar por estado' }));
    await user.click(screen.getByRole('checkbox', { name: /Cerrada/ }));

    expect(screen.queryByRole('link', { name: 'Beca vieja' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Pago anual anticipado 2027' })).toBeInTheDocument();
  });

  it('shows skeleton rows while loading', () => {
    server.use(http.get(`${API}/discount-definitions`, () => new Promise(() => {})));
    renderWithProviders(<DiscountsPage />);

    expect(screen.getByLabelText('Cargando')).toBeInTheDocument();
  });

  it('shows an empty state with a single create call to action', async () => {
    mockDefinitions([]);
    renderWithProviders(<DiscountsPage />);

    expect(await screen.findByText('Todavía no hay descuentos')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Nuevo descuento' })).toHaveLength(1);
  });

  it('shows an error with retry', async () => {
    server.use(http.get(`${API}/discount-definitions`, () => new HttpResponse(null, { status: 500 })));
    const user = userEvent.setup();
    renderWithProviders(<DiscountsPage />);

    expect(await screen.findByText('No pudimos cargar los datos.')).toBeInTheDocument();
    mockDefinitions(CATALOG);
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('link', { name: 'Beca vieja' })).toBeInTheDocument();
  });

  it('shows the derived promotion status, scope names, quota and the enrollment window', async () => {
    mockDefinitions([...CATALOG, ...PROMOTIONS]);
    const user = userEvent.setup();
    renderWithProviders(<DiscountsPage />, { route: '/director/descuentos?tab=promotions' });
    mockLostBenefits([]);

    const link = await screen.findByRole('link', { name: 'Cohorte agosto 2026' });
    const row = link.closest('tr');
    expect(within(row).getByText('Enfermería')).toBeInTheDocument();
    expect(within(row).getByText('20 % · 3 cuotas')).toBeInTheDocument();
    expect(within(row).getByText('18 de 20')).toBeInTheDocument();
    expect(within(row).getByText('Abierta')).toBeInTheDocument();
    expect(within(row).getByText('01/08/2026 – 30/09/2026')).toBeInTheDocument();
    const fixedRow = screen.getByRole('link', { name: 'Matrícula ingresantes 2027' }).closest('tr');
    expect(within(fixedRow).getByText('Primeros Auxilios')).toBeInTheDocument();
    expect(within(fixedRow).getByText(/50\.000,00 fijo/)).toBeInTheDocument();
    expect(within(fixedRow).getByText('Próxima')).toBeInTheDocument();
    expect(screen.getByText('Cupo completo')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Filtrar por estado' }));
    await user.click(screen.getByRole('checkbox', { name: /Cupo completo/ }));
    expect(screen.queryByRole('link', { name: 'Reinscripción 2025' })).not.toBeInTheDocument();
  });

  it('hides the lost benefits block when there are no promotions', async () => {
    mockDefinitions(CATALOG);
    renderWithProviders(<DiscountsPage />, { route: '/director/descuentos?tab=promotions' });

    expect(await screen.findByText('Todavía no hay promociones')).toBeInTheDocument();
    expect(screen.queryByText('Beneficios perdidos')).not.toBeInTheDocument();
  });

  it('sends the filters and the page to the server and reads the total from the header', async () => {
    mockDefinitions([promotion(), promotion({ id: 5, name: 'Reinscripción 2025' })]);
    const requests = [];
    mockLostBenefits([lostBenefit()], 25, (params) => requests.push(Object.fromEntries(params)));
    const user = userEvent.setup();
    renderWithProviders(<DiscountsPage />, { route: '/director/descuentos?tab=promotions' });

    const student = await screen.findByRole('link', { name: 'Medina, Sofía' });
    expect(student).toHaveAttribute('href', '/director/alumnos/77?view=financiero');
    expect(screen.getByText('1 de 3')).toBeInTheDocument();
    expect(screen.getByText('octubre 2026')).toBeInTheDocument();
    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument();
    expect(requests.at(-1)).toEqual({ page: '0', size: '10' });

    await user.click(screen.getByRole('button', { name: 'Siguiente →' }));
    await waitFor(() => expect(requests.at(-1)).toEqual({ page: '1', size: '10' }));

    await user.click(screen.getByRole('combobox', { name: 'Promoción' }));
    await user.click(await screen.findByRole('option', { name: 'Reinscripción 2025' }));
    await waitFor(() => expect(requests.at(-1)).toEqual({ discountDefinitionId: '5', page: '0', size: '10' }));

    await user.click(screen.getByRole('combobox', { name: 'Cargo' }));
    await user.click(await screen.findByRole('option', { name: 'Matrícula' }));
    await waitFor(() => expect(requests.at(-1)).toEqual({ discountDefinitionId: '5', feeType: 'ENROLLMENT_FEE', page: '0', size: '10' }));
  });

  it('explains when no lost benefit matches the filters', async () => {
    mockDefinitions([promotion()]);
    mockLostBenefits([]);
    renderWithProviders(<DiscountsPage />, { route: '/director/descuentos?tab=promotions' });

    expect(await screen.findByText('No hay beneficios perdidos')).toBeInTheDocument();
  });

  it('shows the pending requests notice with the link to approvals for each role', async () => {
    mockDefinitions(CATALOG);
    mockOffers({ pendingRequests: 2 });
    const view = renderWithProviders(<DiscountsPage />);

    expect(await screen.findByText('2 pedidos esperan tu decisión')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver en Aprobaciones' })).toHaveAttribute('href', '/director/aprobaciones/facturacion');
    view.unmount();

    auth.roles = ['ADMINISTRATIVO'];
    renderWithProviders(<DiscountsPage />);
    expect(await screen.findByText('Tenés 2 pedidos esperando al director')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver mis pedidos' })).toHaveAttribute('href', '/administrativo/aprobaciones');
  });

  it('audits the discounts granted to every student with server side filters and pages', async () => {
    mockDefinitions(CATALOG);
    const requests = [];
    server.use(
      http.get(`${API}/student-discounts`, ({ request }) => {
        requests.push(Object.fromEntries(new URL(request.url).searchParams));
        return HttpResponse.json({
          items: [
            {
              id: 1, type: 'SCHOLARSHIP', applicableInvoiceType: 'MONTHLY_INSTALLMENT', calculation: { type: 'PROPORTIONAL', ratePercent: 15 },
              fromPeriod: '2026-03', toPeriod: '2026-12', status: 'ACTIVE', studentId: 77, studentName: 'Medina, Sofía', enrollmentName: 'Enfermería', definitionName: 'Beca',
            },
          ],
          page: 0, size: 20, totalElements: 45,
        });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<DiscountsPage />, { route: '/director/descuentos?tab=granted' });

    const student = await screen.findByRole('link', { name: 'Medina, Sofía' });
    expect(student).toHaveAttribute('href', '/director/alumnos/77?view=financiero');
    expect(screen.getByText('marzo 2026 – diciembre 2026')).toBeInTheDocument();
    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument();
    expect(requests.at(-1)).toEqual({ page: '0', size: '20' });

    await user.click(screen.getByRole('combobox', { name: 'Estado' }));
    await user.click(await screen.findByRole('option', { name: 'Revocado' }));
    await waitFor(() => expect(requests.at(-1)).toEqual({ status: 'REVOKED', page: '0', size: '20' }));

    await user.click(screen.getByRole('combobox', { name: 'Tipo' }));
    await user.click(await screen.findByRole('option', { name: 'Beca' }));
    await waitFor(() => expect(requests.at(-1)).toEqual({ status: 'REVOKED', type: 'SCHOLARSHIP', page: '0', size: '20' }));
  });

  it('explains when no granted discount exists', async () => {
    mockDefinitions(CATALOG);
    server.use(http.get(`${API}/student-discounts`, () => HttpResponse.json({ items: [], page: 0, size: 20, totalElements: 0 })));
    renderWithProviders(<DiscountsPage />, { route: '/director/descuentos?tab=granted' });

    expect(await screen.findByText('Todavía no se otorgó ningún descuento')).toBeInTheDocument();
  });
});
