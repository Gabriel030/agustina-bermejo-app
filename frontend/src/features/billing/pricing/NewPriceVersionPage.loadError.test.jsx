import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { Route, Routes } from 'react-router-dom';
import { NewPriceVersionPage } from './NewPriceVersionPage';

const API = 'http://localhost:8080';

vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ usuario: { roles: ['DIRECTOR'] } }) }));
vi.mock('@/features/billing/hooks/useOfferNames', () => ({
  useOfferNames: () => ({ isLoading: false, programs: [], courses: [], programName: (id) => `Carrera ${id}`, courseName: (id) => `Curso ${id}` }),
}));

describe('NewPriceVersionPage load error', () => {
  it('shows the retry state instead of saying the price was not found', async () => {
    server.use(
      http.get(`${API}/prices`, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })),
      http.get(`${API}/terms`, () => HttpResponse.json([])),
    );
    renderWithProviders(
      <Routes>
        <Route path="/director/precios/:priceId/nueva-version" element={<NewPriceVersionPage />} />
      </Routes>,
      { route: '/director/precios/1/nueva-version' },
    );

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText('No encontramos este precio.')).not.toBeInTheDocument();
  });
});
