import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ContactListTab } from './ContactListTab';

const API = 'http://localhost:8080';

vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ usuario: { roles: ['ADMINISTRATIVO'] } }) }));

function entry(overrides) {
  return {
    studentId: 1,
    studentName: 'Ledesma, Florencia',
    dni: '40.111.222',
    entryReason: 'DAYS_OVERDUE',
    newlyListed: false,
    oldestDaysOverdue: 65,
    overdueInstallments: 2,
    overdueAmounts: [{ amount: '120000.00', currency: 'ARS' }],
    lastReminderDate: '2026-10-20',
    ...overrides,
  };
}

const items = [
  entry({}),
  entry({
    studentId: 2,
    studentName: 'Benítez, Joaquín',
    dni: '38.555.666',
    newlyListed: true,
    entryReason: 'OVERDUE_INSTALLMENTS',
    oldestDaysOverdue: 12,
    overdueInstallments: 3,
    overdueAmounts: [
      { amount: '90000.00', currency: 'ARS' },
      { amount: '150.00', currency: 'USD' },
    ],
    lastReminderDate: null,
  }),
];

function mockList(handler) {
  server.use(
    http.get(`${API}/collection-reminders/contact-list`, ({ request }) => handler(new URL(request.url).searchParams)),
    http.get(`${API}/billing-configuration/COLLECTION_REMINDERS/topics/COLLECTION_REMINDERS/versions`, () =>
      HttpResponse.json([{ id: 1, status: 'EFFECTIVE', effectiveFrom: '2026-01-01', values: { contactListDaysOverdue: 30, contactListOverdueInstallments: 2 } }]),
    ),
  );
}

describe('ContactListTab', () => {
  it('lists each student with one debt line per currency and flags the newly listed ones', async () => {
    mockList(() => HttpResponse.json({ items, page: 0, size: 100, totalElements: 2 }));
    renderWithProviders(<ContactListTab accountBase="/administrativo" />);

    const row = (await screen.findByText('Benítez, Joaquín')).closest('tr');
    expect(within(row).getByText('Nuevo')).toBeInTheDocument();
    expect(within(row).getByText(/90\.000/)).toBeInTheDocument();
    expect(within(row).getByText(/150/)).toBeInTheDocument();
    expect(within(row).getByText('Nunca')).toBeInTheDocument();
    expect(within(row).getByRole('link', { name: 'Cuenta corriente' })).toHaveAttribute('href', '/administrativo/facturacion?alumno=2');
    expect(await screen.findByText(/30 días de atraso o 2 cuotas vencidas/)).toBeInTheDocument();
  });

  it('asks the server for only the newly listed students when the switch is on', async () => {
    const requests = [];
    mockList((params) => {
      requests.push(params.get('onlyNewlyListed'));
      return HttpResponse.json({ items, page: 0, size: 100, totalElements: 2 });
    });
    const user = userEvent.setup();
    renderWithProviders(<ContactListTab accountBase="/administrativo" />);
    await screen.findByText('Benítez, Joaquín');

    await user.click(screen.getByRole('switch', { name: 'Solo los nuevos' }));

    await waitFor(() => expect(requests).toContain('true'));
  });

  it('filters by the search term over the loaded page', async () => {
    mockList(() => HttpResponse.json({ items, page: 0, size: 100, totalElements: 2 }));
    const user = userEvent.setup();
    renderWithProviders(<ContactListTab accountBase="/administrativo" />);
    await screen.findByText('Benítez, Joaquín');

    await user.type(screen.getByPlaceholderText('Buscar por nombre o DNI'), '40111');

    expect(screen.queryByText('Benítez, Joaquín')).not.toBeInTheDocument();
    expect(screen.getByText('Ledesma, Florencia')).toBeInTheDocument();
  });

  it('filters by the days-overdue bucket from the column filter', async () => {
    mockList(() => HttpResponse.json({ items, page: 0, size: 100, totalElements: 2 }));
    const user = userEvent.setup();
    renderWithProviders(<ContactListTab accountBase="/administrativo" />);
    await screen.findByText('Benítez, Joaquín');

    await user.click(screen.getByRole('button', { name: 'Filtrar por días de atraso' }));
    await user.click(screen.getByRole('checkbox', { name: /60 días o más/ }));

    expect(screen.queryByText('Ledesma, Florencia')).not.toBeInTheDocument();
    expect(screen.getByText('Benítez, Joaquín')).toBeInTheDocument();
  });

  it('tells apart an empty list from a list emptied by filters', async () => {
    mockList((params) =>
      HttpResponse.json({ items: params.get('onlyNewlyListed') ? [] : [], page: 0, size: 100, totalElements: 0 }),
    );
    const user = userEvent.setup();
    renderWithProviders(<ContactListTab accountBase="/administrativo" />);

    expect(await screen.findByText('Nadie para contactar hoy')).toBeInTheDocument();
    await user.click(screen.getByRole('switch', { name: 'Solo los nuevos' }));
    expect(await screen.findByText('Ningún resultado con estos filtros')).toBeInTheDocument();
  });

  it('shows a retry when the list cannot be loaded', async () => {
    mockList(() => new HttpResponse(null, { status: 500 }));
    renderWithProviders(<ContactListTab accountBase="/administrativo" />);

    expect(await screen.findByText('No pudimos cargar la lista de alumnos para contactar.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
