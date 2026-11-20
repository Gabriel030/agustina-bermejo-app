import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { Home } from 'lucide-react';
import { toast } from 'sonner';
import { RoleSwitcher } from '@/components/RoleSwitcher';
import { RoleSync } from '@/components/RoleSync';
import { Sidebar } from '@/components/Sidebar';
import { MobileMenu } from '@/components/MobileMenu';
import { useUnsavedChangesGuard } from '@/features/academic-structure/components/UnsavedChangesGuard';
import { readLastRole, writeLastRole } from '@/lib/activeRole';

let currentUser;

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: currentUser, logout: vi.fn() }),
}));

vi.mock('@/context/ThemeContext', () => ({
  useTheme: () => ({ tema: 'light', alternarTema: vi.fn() }),
}));

vi.mock('sonner', () => ({ toast: vi.fn() }));

const ITEMS = [{ to: '/docente/inicio', label: 'Inicio', icon: Home }];

function Location() {
  return <div data-testid="location">{useLocation().pathname}</div>;
}

function DirtyScreen() {
  const { guardDialog } = useUnsavedChangesGuard(true);
  return (
    <>
      <RoleSwitcher variant="inline" />
      {guardDialog}
    </>
  );
}

function renderAt(path, ui) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <RoleSync />
      {ui}
      <Location />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  localStorage.clear();
  vi.mocked(toast).mockClear();
  currentUser = { id: 5, nombre: 'Ana', apellido: 'Perez', roles: ['DOCENTE', 'PRECEPTOR'] };
});

describe('RoleSwitcher on desktop', () => {
  it('shows the active role under the name as a button with a menu of real links', async () => {
    const user = userEvent.setup();
    renderAt('/docente/inicio', <Sidebar titulo="Docente" items={ITEMS} />);

    await user.click(screen.getByRole('button', { name: 'Rol activo: Docente. Cambiar de rol' }));

    const preceptor = screen.getByRole('menuitem', { name: 'Preceptor' });
    expect(preceptor.getAttribute('href')).toBe('/preceptor/inicio');
    expect(screen.getByRole('menuitem', { name: 'Docente' })).toHaveAttribute('aria-current', 'true');
    expect(screen.getByRole('menuitem', { name: 'Preceptor' })).not.toHaveAttribute('aria-current');
  });

  it('shows the role only once, in the button, instead of the layout title', () => {
    renderAt('/docente/inicio', <Sidebar titulo="Docente" items={ITEMS} />);

    expect(screen.getAllByText('Docente')).toHaveLength(1);
  });

  it('goes to the start of the new role and warns who the person is now', async () => {
    const user = userEvent.setup();
    renderAt(
      '/docente/inicio',
      <Routes>
        <Route path="*" element={<Sidebar titulo="Docente" items={ITEMS} />} />
      </Routes>,
    );
    writeLastRole(5, 'DOCENTE');

    await user.click(screen.getByRole('button', { name: 'Rol activo: Docente. Cambiar de rol' }));
    await user.click(screen.getByRole('menuitem', { name: 'Preceptor' }));

    expect(screen.getByTestId('location')).toHaveTextContent('/preceptor/inicio');
    expect(readLastRole(5)).toBe('PRECEPTOR');
    expect(toast).toHaveBeenCalledWith('Ahora estás como Preceptor');
  });

  it('shows nothing extra and keeps the layout title with a single role', () => {
    currentUser = { id: 5, nombre: 'Ana', apellido: 'Perez', roles: ['DOCENTE'] };

    renderAt('/docente/inicio', <Sidebar titulo="Docente" items={ITEMS} />);

    expect(screen.queryByRole('button', { name: /Cambiar de rol/ })).not.toBeInTheDocument();
    expect(screen.getByText('Docente')).toBeInTheDocument();
  });
});

describe('RoleSwitcher in the drawer', () => {
  it('shows the section open by default with the active role marked', async () => {
    const user = userEvent.setup();
    renderAt('/docente/inicio', <MobileMenu titulo="Docente" items={ITEMS} />);

    await user.click(screen.getByRole('button', { name: 'Abrir menú' }));

    const toggle = screen.getByRole('button', { name: /Rol activo: Docente/ });
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('link', { name: 'Docente' })).toHaveAttribute('aria-current', 'true');
    expect(screen.getByRole('link', { name: 'Preceptor' }).getAttribute('href')).toBe('/preceptor/inicio');
  });

  it('collapses the section and closes the drawer after choosing a role', async () => {
    const user = userEvent.setup();
    renderAt('/docente/inicio', <MobileMenu titulo="Docente" items={ITEMS} />);
    await user.click(screen.getByRole('button', { name: 'Abrir menú' }));

    await user.click(screen.getByRole('button', { name: /Rol activo: Docente/ }));
    expect(screen.queryByRole('link', { name: 'Preceptor' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Rol activo: Docente/ }));
    await user.click(screen.getByRole('link', { name: 'Preceptor' }));

    expect(screen.getByTestId('location')).toHaveTextContent('/preceptor/inicio');
    expect(screen.queryByRole('link', { name: 'Preceptor' })).not.toBeInTheDocument();
  });

  it('renders no role section with a single role', async () => {
    const user = userEvent.setup();
    currentUser = { id: 5, nombre: 'Ana', apellido: 'Perez', roles: ['DOCENTE'] };
    renderAt('/docente/inicio', <MobileMenu titulo="Docente" items={ITEMS} />);

    await user.click(screen.getByRole('button', { name: 'Abrir menú' }));

    expect(screen.queryByRole('button', { name: /Rol activo/ })).not.toBeInTheDocument();
  });
});

describe('RoleSwitcher with unsaved changes', () => {
  it('asks for confirmation with the shared dialog and stays put if the person cancels', async () => {
    const user = userEvent.setup();
    renderAt('/docente/inicio', <DirtyScreen />);

    await user.click(screen.getByRole('link', { name: 'Preceptor' }));

    expect(screen.getByRole('alertdialog')).toHaveTextContent('¿Salir sin guardar?');
    expect(screen.getByTestId('location')).toHaveTextContent('/docente/inicio');

    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/docente/inicio');
  });

  it('switches role once the person confirms leaving without saving', async () => {
    const user = userEvent.setup();
    renderAt('/docente/inicio', <DirtyScreen />);

    await user.click(screen.getByRole('link', { name: 'Preceptor' }));
    await user.click(screen.getByRole('button', { name: 'Salir sin guardar' }));

    expect(screen.getByTestId('location')).toHaveTextContent('/preceptor/inicio');
  });

  it('also guards a plain link to a route of another role', async () => {
    const user = userEvent.setup();
    function Screen() {
      const { guardDialog } = useUnsavedChangesGuard(true);
      return (
        <>
          <a href="/preceptor/asistencia">Ir a asistencia de preceptor</a>
          {guardDialog}
        </>
      );
    }
    renderAt('/docente/inicio', <Screen />);

    await user.click(screen.getByRole('link', { name: 'Ir a asistencia de preceptor' }));

    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
  });
});

describe('RoleSync', () => {
  it('does not warn the first time a role is stored', () => {
    renderAt('/docente/inicio', <div />);

    expect(readLastRole(5)).toBe('DOCENTE');
    expect(toast).not.toHaveBeenCalled();
  });

  it('switches the active role and warns when the URL belongs to another role of the person', () => {
    writeLastRole(5, 'DOCENTE');

    renderAt('/preceptor/asistencia', <div />);

    expect(readLastRole(5)).toBe('PRECEPTOR');
    expect(toast).toHaveBeenCalledWith('Ahora estás como Preceptor');
  });

  it('keeps the active role on shared screens', () => {
    writeLastRole(5, 'DOCENTE');

    renderAt('/perfil', <div />);

    expect(readLastRole(5)).toBe('DOCENTE');
    expect(toast).not.toHaveBeenCalled();
  });

  it('does not warn a person with a single role', () => {
    currentUser = { id: 5, roles: ['DOCENTE'] };
    writeLastRole(5, 'ALUMNO');

    renderAt('/docente/inicio', <div />);

    expect(toast).not.toHaveBeenCalled();
  });
});
