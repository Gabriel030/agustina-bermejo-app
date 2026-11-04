import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ThemeProvider } from '@/context/ThemeContext';
import { AdministrativoLayout } from '@/layouts/AdministrativoLayout';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: ['ADMINISTRATIVO'] }, roles: ['ADMINISTRATIVO'], logout: vi.fn() }),
}));

describe('AdministrativoLayout', () => {
  it('offers the terms screen in the menu', () => {
    renderWithProviders(<ThemeProvider><AdministrativoLayout /></ThemeProvider>, { route: '/administrativo/inicio' });

    const links = screen.getAllByRole('link', { name: 'Períodos' });
    expect(links[0]).toHaveAttribute('href', '/administrativo/periodos');
  });
});
