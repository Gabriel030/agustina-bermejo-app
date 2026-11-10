import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { GestionPersonal } from './GestionPersonal';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: ['ADMINISTRATIVO'] } }),
}));

const API = 'http://localhost:8080';

describe('GestionPersonal load error', () => {
  it('shows the retry state instead of saying no staff matches', async () => {
    server.use(http.get(`${API}/staff`, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })));
    renderWithProviders(<GestionPersonal />);

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText('No se encontraron miembros del personal que coincidan.')).not.toBeInTheDocument();
  });
});
