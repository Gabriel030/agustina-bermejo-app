import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ThemeProvider } from '@/context/ThemeContext';
import { AdministrativoLayout } from '@/layouts/AdministrativoLayout';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: ['ADMINISTRATIVO'] }, roles: ['ADMINISTRATIVO'], logout: vi.fn() }),
}));

describe('AdministrativoLayout', () => {
  it('offers the terms screen in the menu', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ThemeProvider><AdministrativoLayout /></ThemeProvider>, { route: '/administrativo/inicio' });

    await user.click(screen.getByRole('button', { name: 'Académico' }));
    await user.click(screen.getByRole('button', { name: /Oferta y períodos/ }));
    expect(screen.getByRole('link', { name: 'Períodos' })).toHaveAttribute('href', '/administrativo/periodos');
  });

  it('shows the six staff entries in order in the top bar', async () => {
    renderWithProviders(<ThemeProvider><AdministrativoLayout /></ThemeProvider>, { route: '/administrativo/inicio' });

    await screen.findByRole('link', { name: 'Aprobaciones' });
    const bar = screen.getByRole('navigation', { name: 'Principal' });
    expect(within(bar).getAllByRole('link').map((link) => link.textContent)).toEqual(['Inicio', 'Aprobaciones']);
    expect(within(bar).getAllByRole('button').map((button) => button.textContent)).toEqual(['Personas', 'Inscripciones', 'Académico', 'Facturación']);
  });

  it('shows the home entry of the administrative role', () => {
    renderWithProviders(<ThemeProvider><AdministrativoLayout /></ThemeProvider>, { route: '/administrativo/inicio' });

    expect(screen.getByRole('link', { name: 'Inicio' })).toHaveAttribute('href', '/administrativo/inicio');
  });
});
