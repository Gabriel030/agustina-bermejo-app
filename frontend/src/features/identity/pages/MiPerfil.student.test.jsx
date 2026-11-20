import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { MiPerfil } from '@/features/identity/pages/MiPerfil';

const API = 'http://localhost:8080';

const auth = vi.hoisted(() => ({ roles: ['ALUMNO'] }));
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ roles: auth.roles, logout: vi.fn() }),
}));

function mockProfileEndpoints() {
  server.use(
    http.get(`${API}/me/sessions`, () => HttpResponse.json([])),
    http.get(`${API}/me/notification-preferences`, () => HttpResponse.json({ preferences: {} })),
    http.get(`${API}/me/avatar`, () => new HttpResponse(null, { status: 404 })),
  );
}

describe('MiPerfil para el alumno', () => {
  it('links to the options screen instead of repeating security and notifications tabs', async () => {
    mockProfileEndpoints();
    renderWithProviders(<MiPerfil />, { route: '/perfil' });

    expect(await screen.findByRole('link', { name: /Ir a Opciones/ })).toHaveAttribute('href', '/alumno/opciones');
    expect(screen.getByRole('tab', { name: 'Mis datos' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Mis aceptaciones' })).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Seguridad' })).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Notificaciones' })).not.toBeInTheDocument();
  });
});
