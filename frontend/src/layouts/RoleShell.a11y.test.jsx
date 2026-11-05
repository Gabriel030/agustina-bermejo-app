import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { ThemeProvider } from '@/context/ThemeContext';
import { DirectorLayout } from '@/layouts/DirectorLayout';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: ['DIRECTOR'] }, roles: ['DIRECTOR'], logout: vi.fn() }),
}));

describe('RoleShell accessibility', () => {
  it('roleShellHasNoAxeViolations', async () => {
    const { container } = renderWithProviders(
      <ThemeProvider>
        <DirectorLayout />
      </ThemeProvider>,
      { route: '/director/inicio' },
    );

    expect(await axe(container)).toHaveNoViolations();
  });

  it('roleShellExposesSkipLinkAndFocusableMain', () => {
    renderWithProviders(
      <ThemeProvider>
        <DirectorLayout />
      </ThemeProvider>,
      { route: '/director/inicio' },
    );

    expect(screen.getByRole('link', { name: 'Saltar al contenido' })).toHaveAttribute('href', '#main-content');
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main-content');
  });
});
