import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RolePicker } from '@/pages/RolePicker';
import { readAskAlways, readLastRole, writeAskAlways, writeLastRole } from '@/lib/activeRole';

let currentUser;
const logout = vi.fn();

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: currentUser, logout }),
}));

function renderPicker() {
  return render(
    <MemoryRouter initialEntries={['/elegir-rol']}>
      <Routes>
        <Route path="/elegir-rol" element={<RolePicker />} />
        <Route path="/:role/inicio" element={<div>Inicio de rol</div>} />
        <Route path="/catalogo" element={<div>Catalogo</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  localStorage.clear();
  logout.mockClear();
  currentUser = { id: 3, nombre: 'Lucia', roles: ['ALUMNO', 'DOCENTE'] };
});

describe('RolePicker', () => {
  it('offers the roles in the fixed order with the first one selected', () => {
    renderPicker();

    const options = screen.getAllByRole('radio');
    expect(options.map((option) => option.value)).toEqual(['DOCENTE', 'ALUMNO']);
    expect(options[0]).toBeChecked();
    expect(screen.getByRole('heading', { level: 1, name: 'Elegí cómo querés entrar' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Entrar como Docente' })).toBeInTheDocument();
  });

  it('enters with the chosen role and remembers it for next time', async () => {
    const user = userEvent.setup();
    renderPicker();

    await user.click(screen.getByRole('radio', { name: /Alumno/ }));
    await user.click(screen.getByRole('button', { name: 'Entrar como Alumno' }));

    expect(await screen.findByText('Inicio de rol')).toBeInTheDocument();
    expect(readLastRole(3)).toBe('ALUMNO');
    expect(readAskAlways(3)).toBe(false);
  });

  it('starts with the last used role selected', () => {
    writeLastRole(3, 'ALUMNO');

    renderPicker();

    expect(screen.getByRole('radio', { name: /Alumno/ })).toBeChecked();
  });

  it('saves the ask-every-time choice when the box is checked', async () => {
    const user = userEvent.setup();
    renderPicker();

    await user.click(screen.getByRole('checkbox', { name: 'Preguntarme el rol al entrar' }));
    await user.click(screen.getByRole('button', { name: 'Entrar como Docente' }));

    expect(readAskAlways(3)).toBe(true);
  });

  it('shows the box already checked when the person asked to be asked every time', () => {
    writeAskAlways(3, true);

    renderPicker();

    expect(screen.getByRole('checkbox', { name: 'Preguntarme el rol al entrar' })).toBeChecked();
  });

  it('lets the person leave when the account is not theirs', async () => {
    const user = userEvent.setup();
    renderPicker();

    await user.click(screen.getByRole('button', { name: 'No soy Lucia: cerrar sesión' }));

    expect(logout).toHaveBeenCalled();
  });

  it('skips the screen when the person has a single role', () => {
    currentUser = { id: 3, nombre: 'Lucia', roles: ['DOCENTE'] };

    renderPicker();

    expect(screen.getByText('Inicio de rol')).toBeInTheDocument();
  });

  it('sends a person without roles to the catalog', () => {
    currentUser = { id: 3, nombre: 'Lucia', roles: [] };

    renderPicker();

    expect(screen.getByText('Catalogo')).toBeInTheDocument();
  });
});
