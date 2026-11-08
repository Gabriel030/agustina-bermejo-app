import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { Route, Routes } from 'react-router-dom';
import { AlumnoPagarOnline } from './AlumnoPagarOnline';

const API = 'http://localhost:8080';

describe('AlumnoPagarOnline load error', () => {
  it('shows the retry state instead of redirecting as if card payment were disabled', async () => {
    server.use(http.get(`${API}/payment-config`, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })));
    renderWithProviders(
      <Routes>
        <Route path="/alumno/pagos/:invoiceId/online" element={<AlumnoPagarOnline />} />
        <Route path="/alumno/pagos/:invoiceId/seleccion" element={<p>selection page</p>} />
      </Routes>,
      { route: '/alumno/pagos/61/online' },
    );

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText('selection page')).not.toBeInTheDocument();
  });
});
