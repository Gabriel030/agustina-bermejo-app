import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ThemeProvider } from '@/context/ThemeContext';
import { DirectorLayout } from '@/layouts/DirectorLayout';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: ['DIRECTOR'] }, roles: ['DIRECTOR'], logout: vi.fn() }),
}));

describe('DirectorLayout', () => {
  it('offers the billing screen in the menu', () => {
    renderWithProviders(<ThemeProvider><DirectorLayout /></ThemeProvider>, { route: '/director/inicio' });

    const links = screen.getAllByRole('link', { name: 'Facturación' });
    expect(links[0]).toHaveAttribute('href', '/director/facturacion');
  });
});
