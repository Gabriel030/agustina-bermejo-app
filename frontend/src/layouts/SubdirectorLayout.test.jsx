import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ThemeProvider } from '@/context/ThemeContext';
import { SubdirectorLayout } from '@/layouts/SubdirectorLayout';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: ['SUBDIRECTOR'] }, roles: ['SUBDIRECTOR'], logout: vi.fn() }),
}));

function renderLayout() {
  return renderWithProviders(<ThemeProvider><SubdirectorLayout /></ThemeProvider>, { route: '/subdirector/inicio' });
}

describe('SubdirectorLayout', () => {
  it('shows the staff entries with the permissions link at the end', async () => {
    renderLayout();

    await screen.findByRole('link', { name: 'Aprobaciones' });
    const bar = screen.getByRole('navigation', { name: 'Principal' });
    expect(within(bar).getAllByRole('link').map((link) => link.textContent)).toEqual(['Inicio', 'Aprobaciones', 'Roles y permisos']);
    expect(within(bar).getAllByRole('button').map((button) => button.textContent)).toEqual(['Personas', 'Inscripciones', 'Académico', 'Facturación']);
  });

  it('shows the same home entry as the administrative role', () => {
    renderLayout();

    expect(screen.getByRole('link', { name: 'Inicio' })).toHaveAttribute('href', '/subdirector/inicio');
  });

  it('lists the academic screens without the exception certificates', async () => {
    const user = userEvent.setup();
    renderLayout();

    await user.click(screen.getByRole('button', { name: 'Académico' }));
    const rows = screen.getAllByRole('button', { name: /\(\d+\)$/ });
    expect(rows.map((row) => row.textContent)).toEqual(['Oferta y períodos(3)', 'Cursada(3)', 'Egresos y certificados(5)']);
    await user.click(rows[2]);
    expect(screen.queryByRole('link', { name: 'Emitir por excepción' })).not.toBeInTheDocument();
  });
});
