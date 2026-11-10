import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Home, Users } from 'lucide-react';
import { Sidebar } from '@/components/Sidebar';

const logout = vi.fn();

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Perez' }, logout }),
}));

const ITEMS = [
  { to: '/director/inicio', label: 'Inicio', icon: Home },
  { to: '/director/alumnos', label: 'Alumnos', icon: Users },
  { to: '/director/opciones', label: 'Opciones', icon: Users },
];

const GROUPS = [
  { id: 'main', label: null, items: [ITEMS[0]] },
  { id: 'people', label: 'Personas', items: [ITEMS[1]] },
];

function renderSidebar(props = {}) {
  return render(
    <MemoryRouter initialEntries={['/director/inicio']}>
      <Sidebar titulo="Director" items={ITEMS} {...props} />
    </MemoryRouter>,
  );
}

afterEach(() => {
  delete document.documentElement.dataset.design;
  logout.mockClear();
});

describe('Sidebar', () => {
  it('active item carries the navigation marker used by the sliding indicator', () => {
    renderSidebar();

    expect(screen.getByRole('link', { name: 'Inicio' })).toHaveClass('nav-item-activo');
    expect(screen.getByRole('link', { name: 'Alumnos' })).not.toHaveClass('nav-item-activo');
  });

  it('logout button is rendered and calls logout', async () => {
    renderSidebar();

    await userEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }));

    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('keeps the flat menu with design vl even when groups are given', () => {
    document.documentElement.dataset.design = 'vl';
    renderSidebar({ groups: GROUPS });

    expect(screen.queryByText('Personas')).not.toBeInTheDocument();
    expect(screen.queryAllByRole('list')).toHaveLength(0);
  });

  it('renders labelled groups as lists with design b', () => {
    document.documentElement.dataset.design = 'b';
    renderSidebar({ groups: GROUPS });

    const people = screen.getByRole('list', { name: 'Personas' });
    expect(within(people).getByRole('link', { name: 'Alumnos' })).toBeInTheDocument();
  });

  it('does not render the options entry with design b', () => {
    document.documentElement.dataset.design = 'b';
    renderSidebar({ groups: GROUPS });

    expect(screen.queryByRole('link', { name: 'Opciones' })).not.toBeInTheDocument();
  });
});
