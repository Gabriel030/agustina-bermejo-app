import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { Home, Users } from 'lucide-react';
import { MobileMenu } from '@/components/MobileMenu';
import { ThemeProvider } from '@/context/ThemeContext';
import { axe } from '@/test/axe';

const logout = vi.fn();
let currentUser;

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: currentUser, logout }),
}));

const ITEMS = [
  { to: '/director/inicio', label: 'Inicio', icon: Home },
  { to: '/director/alumnos', label: 'Alumnos', icon: Users },
  { to: '/director/personal', label: 'Personal', icon: Users },
  { to: '/director/egresos', label: 'Egresos', icon: Users },
];

const GROUPS = [
  { id: 'main', label: null, items: [ITEMS[0]] },
  { id: 'people', label: 'Personas', items: [ITEMS[1]] },
  { id: 'staff', label: 'Equipo', items: [ITEMS[2]] },
];

const SECTIONED_GROUPS = [
  { id: 'main', label: null, items: [ITEMS[0]] },
  {
    id: 'academic',
    label: 'Académico',
    items: [ITEMS[1], ITEMS[2], ITEMS[3]],
    sections: [
      { id: 'one', label: 'Primera sección', items: [ITEMS[1], ITEMS[2]] },
      { id: 'two', label: 'Segunda sección', items: [ITEMS[3]] },
    ],
  },
];

function Location() {
  return <div data-testid="location">{useLocation().pathname}</div>;
}

async function openDrawer({ groups = GROUPS, route = '/director/alumnos' } = {}) {
  const user = userEvent.setup();
  const view = render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[route]}>
        <MobileMenu titulo="Director" items={ITEMS} groups={groups} />
        <Location />
      </MemoryRouter>
    </ThemeProvider>,
  );
  await user.click(screen.getByRole('button', { name: 'Abrir menú' }));
  return { user, ...view };
}

function dialog() {
  return screen.getByRole('dialog');
}

beforeEach(() => {
  localStorage.clear();
  logout.mockClear();
  currentUser = { id: 1, nombre: 'Ana', apellido: 'Perez', roles: ['DIRECTOR'] };
});

describe('MobileMenu levels', () => {
  it('starts at the root with direct links and group rows', async () => {
    await openDrawer();

    expect(within(dialog()).getByRole('link', { name: 'Inicio' })).toBeInTheDocument();
    expect(within(dialog()).getByRole('button', { name: /Personas/ })).toBeInTheDocument();
    expect(within(dialog()).queryByRole('link', { name: /Alumnos/ })).not.toBeInTheDocument();
    expect(dialog()).toHaveAccessibleName('Menú de Director');
  });

  it('enters a group, shows its items and renames the dialog after the level', async () => {
    const { user } = await openDrawer();

    await user.click(screen.getByRole('button', { name: /Equipo/ }));

    expect(screen.getByRole('link', { name: 'Personal' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Inicio' })).not.toBeInTheDocument();
    expect(dialog()).toHaveAccessibleName('Equipo');
  });

  it('goes back one level with Volver and returns focus to the row it came from', async () => {
    const { user } = await openDrawer();
    await user.click(screen.getByRole('button', { name: /Equipo/ }));

    await user.click(screen.getByRole('button', { name: 'Volver' }));

    expect(screen.getByRole('link', { name: 'Inicio' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Personal' })).not.toBeInTheDocument();
    expect(dialog()).toHaveAccessibleName('Menú de Director');
    expect(screen.getByRole('button', { name: /Equipo/ })).toHaveFocus();
  });

  it('moves focus to Volver when entering a level', async () => {
    const { user } = await openDrawer();

    await user.click(screen.getByRole('button', { name: /Equipo/ }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Volver' })).toHaveFocus());
  });

  it('walks root, group, section and destination, then navigates and closes', async () => {
    const { user } = await openDrawer({ groups: SECTIONED_GROUPS });

    await user.click(screen.getByRole('button', { name: /Académico/ }));
    expect(screen.getByRole('button', { name: /Primera sección.*2 pantallas/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Segunda sección.*1 pantalla$/ })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Segunda sección/ }));
    expect(dialog()).toHaveAccessibleName('Segunda sección');
    await user.click(screen.getByRole('link', { name: 'Egresos' }));

    expect(screen.getByTestId('location')).toHaveTextContent('/director/egresos');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('climbs from the destinations back to the sections and then to the root', async () => {
    const { user } = await openDrawer({ groups: SECTIONED_GROUPS });
    await user.click(screen.getByRole('button', { name: /Académico/ }));
    await user.click(screen.getByRole('button', { name: /Primera sección/ }));

    await user.click(screen.getByRole('button', { name: 'Volver' }));
    expect(screen.getByRole('button', { name: /Segunda sección/ })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Volver' }));
    expect(screen.getByRole('link', { name: 'Inicio' })).toBeInTheDocument();
  });

  it('starts again at the root after closing from a deeper level', async () => {
    const { user } = await openDrawer();
    await user.click(screen.getByRole('button', { name: /Equipo/ }));

    await user.keyboard('{Escape}');
    await user.click(screen.getByRole('button', { name: 'Abrir menú' }));

    expect(screen.getByRole('link', { name: 'Inicio' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Volver' })).not.toBeInTheDocument();
  });

  it('closes the whole drawer with Escape from a deep level and returns focus to the trigger', async () => {
    const { user } = await openDrawer({ groups: SECTIONED_GROUPS });
    await user.click(screen.getByRole('button', { name: /Académico/ }));
    await user.click(screen.getByRole('button', { name: /Primera sección/ }));

    await user.keyboard('{Escape}');

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Abrir menú' })).toHaveFocus());
  });

  it('marks the group and the destination that contain the current page', async () => {
    const { user } = await openDrawer();

    const peopleRow = screen.getByRole('button', { name: /Personas/ });
    expect(peopleRow).toHaveTextContent('· actual');
    expect(screen.getByRole('button', { name: /Equipo/ })).not.toHaveTextContent('· actual');

    await user.click(peopleRow);
    const current = screen.getByRole('link', { name: /Alumnos/ });
    expect(current).toHaveAttribute('aria-current', 'page');
    expect(current).toHaveTextContent('· actual');
  });

  it('marks a direct root link as current', async () => {
    await openDrawer({ route: '/director/inicio' });

    expect(screen.getByRole('link', { name: /Inicio/ })).toHaveTextContent('· actual');
  });

  it('lists every item as a direct row when the menu has no groups', async () => {
    await openDrawer({ groups: null });

    expect(screen.getByRole('link', { name: 'Personal' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Personas/ })).not.toBeInTheDocument();
  });

  it('closes when the overlay is pressed', async () => {
    const { user } = await openDrawer();

    await user.click(document.querySelector('[data-state="open"].fixed.inset-0'));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});

describe('MobileMenu footer', () => {
  it('keeps Cerrar sesión at the root and runs the logout action', async () => {
    const { user } = await openDrawer();

    await user.click(screen.getByRole('button', { name: 'Cerrar sesión' }));

    expect(logout).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('hides the footer inside a level and shows it again at the root', async () => {
    const { user } = await openDrawer();
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Equipo/ }));
    expect(screen.queryByRole('button', { name: 'Cerrar sesión' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Volver' }));
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument();
  });

  it('does not offer role switching with a single role', async () => {
    await openDrawer();

    expect(screen.queryByRole('button', { name: /Cambiar de rol/ })).not.toBeInTheDocument();
  });

  it('opens a role level from the footer with the active role marked', async () => {
    currentUser = { id: 1, nombre: 'Ana', apellido: 'Perez', roles: ['DIRECTOR', 'DOCENTE'] };
    const { user } = await openDrawer();

    await user.click(screen.getByRole('button', { name: /Cambiar de rol/ }));

    expect(dialog()).toHaveAccessibleName('Cambiar de rol');
    expect(screen.getByRole('link', { name: /Director/ })).toHaveAttribute('aria-current', 'true');
    expect(screen.getByRole('link', { name: 'Docente' }).getAttribute('href')).toBe('/docente/inicio');
  });

  it('does not put the theme toggle in the drawer', async () => {
    await openDrawer();

    expect(within(dialog()).queryByRole('button', { name: /Tema/ })).not.toBeInTheDocument();
  });
});

describe('MobileMenu accessibility', () => {
  it('has no axe violations at the root, a group and a section', async () => {
    const { user } = await openDrawer({ groups: SECTIONED_GROUPS });
    expect(await axe(dialog())).toHaveNoViolations();

    await user.click(screen.getByRole('button', { name: /Académico/ }));
    expect(await axe(dialog())).toHaveNoViolations();

    await user.click(screen.getByRole('button', { name: /Primera sección/ }));
    expect(await axe(dialog())).toHaveNoViolations();
  });

  it('has no axe violations in the role level', async () => {
    currentUser = { id: 1, nombre: 'Ana', apellido: 'Perez', roles: ['DIRECTOR', 'DOCENTE'] };
    const { user } = await openDrawer();
    await user.click(screen.getByRole('button', { name: /Cambiar de rol/ }));

    expect(await axe(dialog())).toHaveNoViolations();
  });
});
