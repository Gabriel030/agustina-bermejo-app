import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ThemeProvider } from '@/context/ThemeContext';
import { DocenteLayout } from '@/layouts/DocenteLayout';
import { PreceptorLayout } from '@/layouts/PreceptorLayout';
import { PlatformLayout } from '@/layouts/PlatformLayout';

const authState = { roles: ['DOCENTE'] };

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({
    usuario: { id: 7, nombre: 'Ana', apellido: 'Pérez', roles: authState.roles },
    roles: authState.roles,
    logout: vi.fn(),
  }),
}));

function renderLayout(Layout, role, route) {
  authState.roles = [role];
  return renderWithProviders(
    <ThemeProvider>
      <Layout />
    </ThemeProvider>,
    { route },
  );
}

function barLinks() {
  const bar = screen.getByRole('navigation', { name: 'Principal' });
  expect(within(bar).queryAllByRole('button')).toEqual([]);
  return within(bar).getAllByRole('link').map((link) => link.textContent);
}

describe('short role layouts', () => {
  it('shows the teacher entries without the unavailable home as plain links in the top bar', () => {
    renderLayout(DocenteLayout, 'DOCENTE', '/docente/inicio');

    expect(barLinks()).toEqual(['Mis comisiones', 'Tomar asistencia', 'Mesas de examen']);
  });

  it('shows the preceptor entries as plain links in the top bar', () => {
    renderLayout(PreceptorLayout, 'PRECEPTOR', '/preceptor/inicio');

    const labels = barLinks();
    expect(labels).toEqual(expect.arrayContaining(['Alumnos', 'Mesas de examen']));
    expect(new Set(labels).size).toBe(labels.length);
  });

  it('shows the platform admin entries as plain links in the top bar without home', () => {
    renderLayout(PlatformLayout, 'PLATFORM_ADMIN', '/platform_admin/modulos');

    expect(barLinks()).toEqual(['Módulos', 'Primer director', 'Auditoría', 'Backups']);
  });

  it.each([
    [DocenteLayout, 'DOCENTE', '/docente/inicio', 'lg:flex', 'lg:hidden'],
    [PlatformLayout, 'PLATFORM_ADMIN', '/platform_admin/modulos', 'lg:flex', 'lg:hidden'],
    [PreceptorLayout, 'PRECEPTOR', '/preceptor/inicio', 'min-[1220px]:flex', 'min-[1220px]:hidden'],
  ])('renders one top bar with the drawer header hidden from the same width', (Layout, role, route, barClass, drawerClass) => {
    renderLayout(Layout, role, route);

    expect(screen.getAllByRole('navigation', { name: 'Principal' })).toHaveLength(1);
    expect(screen.getByRole('navigation', { name: 'Principal' }).parentElement).toHaveClass(barClass);
    const trigger = screen.getByRole('button', { name: 'Abrir menú' });
    expect(trigger).toHaveClass(drawerClass);
    expect(trigger.closest('.pt-4.px-4')).toHaveClass(drawerClass);
  });

  it('marks the active route as current', () => {
    renderLayout(DocenteLayout, 'DOCENTE', '/docente/mis-comisiones');

    const bar = screen.getByRole('navigation', { name: 'Principal' });
    expect(within(bar).getByRole('link', { name: 'Mis comisiones' })).toHaveAttribute('aria-current', 'page');
  });
});
