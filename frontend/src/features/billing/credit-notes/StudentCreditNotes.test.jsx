import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { creditBalanceFixture, creditNoteFixture, money } from '@/test/fixtures/invoices';
import { CreditBalanceCard } from './CreditBalanceCard';
import { MyCreditNotesPage } from './MyCreditNotesPage';

const API = 'http://localhost:8080';

function studentNote(overrides = {}) {
  return creditNoteFixture({ justification: null, requestedByName: null, authorizedByName: null, authorizedAt: null, ...overrides });
}

describe('Saldo a favor del alumno', () => {
  it('shows the available credit with a link to the credit notes when there is any', async () => {
    server.use(http.get(`${API}/me/credit-balance`, () => HttpResponse.json(creditBalanceFixture('6000.00'))));
    renderWithProviders(<CreditBalanceCard />);

    expect(await screen.findByText('$ 6.000,00')).toBeInTheDocument();
    expect(screen.getByText('Saldo a favor')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver mis notas de crédito' })).toHaveAttribute('href', '/alumno/pagos/notas-de-credito');
  });

  it('does not show the card without credit and keeps a quiet link to the credit notes', async () => {
    server.use(http.get(`${API}/me/credit-balance`, () => HttpResponse.json(creditBalanceFixture('0.00'))));
    renderWithProviders(<CreditBalanceCard />);

    expect(await screen.findByRole('link', { name: 'Mis notas de crédito' })).toBeInTheDocument();
    expect(screen.queryByText('Saldo a favor')).not.toBeInTheDocument();
  });

  it('does not count the credit reserved by pending refunds as available', async () => {
    server.use(
      http.get(`${API}/me/credit-balance`, () =>
        HttpResponse.json({ ...creditBalanceFixture('0.00'), unallocatedCredit: money('6000.00'), reservedByPendingRefunds: money('6000.00') }),
      ),
    );
    renderWithProviders(<CreditBalanceCard />);

    await screen.findByRole('link', { name: 'Mis notas de crédito' });
    expect(screen.queryByText('Saldo a favor')).not.toBeInTheDocument();
  });
});

describe('Mis notas de crédito', () => {
  it('explains each note in plain language without the internal justification or staff names', async () => {
    server.use(
      http.get(`${API}/me/credit-notes`, () =>
        HttpResponse.json([
          studentNote({ id: 1, creditNoteNumber: 'NC-0000098' }),
          studentNote({ id: 2, creditNoteNumber: 'NC-0000099', reason: 'CANCELLATION', freeCreditAmount: money('0.00'), offsetAmount: money('48000.00'), totalAmount: money('48000.00') }),
          studentNote({ id: 3, creditNoteNumber: 'NC-0000100', reason: 'LATE_FEE_ANNULMENT', freeCreditAmount: money('0.00'), offsetAmount: money('2400.00'), totalAmount: money('2400.00') }),
        ]),
      ),
    );
    renderWithProviders(<MyCreditNotesPage />);

    expect(await screen.findByText('Corrección de la facturación')).toBeInTheDocument();
    expect(screen.getByText('Cuota anulada')).toBeInTheDocument();
    expect(screen.getByText('Se anuló un recargo')).toBeInTheDocument();
    expect(screen.getByText('A tu favor')).toBeInTheDocument();
    expect(screen.getByText('Sin saldo a pagar')).toBeInTheDocument();
    expect(screen.getByText('Descontado de la cuota')).toBeInTheDocument();
    expect(screen.queryByText(/seguro escolar/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Marcela/)).not.toBeInTheDocument();
    expect(screen.queryByText('Justificación')).not.toBeInTheDocument();
  });

  it('shows the amount of each note and its number', async () => {
    server.use(http.get(`${API}/me/credit-notes`, () => HttpResponse.json([studentNote()])));
    renderWithProviders(<MyCreditNotesPage />);

    const row = (await screen.findByText('NC-0000098')).closest('tr');
    expect(within(row).getByText('$ 6.000,00')).toBeInTheDocument();
    expect(within(row).getByText('F-00000061')).toBeInTheDocument();
  });

  it('shows an empty state when there are no notes', async () => {
    server.use(http.get(`${API}/me/credit-notes`, () => HttpResponse.json([])));
    renderWithProviders(<MyCreditNotesPage />);

    expect(await screen.findByText('Sin notas de crédito')).toBeInTheDocument();
  });

  it('offers a retry when the notes cannot be loaded', async () => {
    server.use(http.get(`${API}/me/credit-notes`, () => new HttpResponse(null, { status: 500 })));
    renderWithProviders(<MyCreditNotesPage />);

    expect(await screen.findByText('No pudimos cargar tus notas de crédito.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
