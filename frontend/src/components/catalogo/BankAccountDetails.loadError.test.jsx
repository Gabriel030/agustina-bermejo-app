import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { BankAccountDetails } from './BankAccountDetails';

const API = 'http://localhost:8080';

describe('BankAccountDetails load error', () => {
  it('shows the retry action instead of telling the user to ask the institute for the account', async () => {
    server.use(http.get(`${API}/payment-config`, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })));
    renderWithProviders(<BankAccountDetails />);

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText(/Pedí los datos de la cuenta/)).not.toBeInTheDocument();
  });
});
