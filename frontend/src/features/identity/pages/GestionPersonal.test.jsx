import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { GestionPersonal } from './GestionPersonal';

const auth = vi.hoisted(() => ({ roles: ['ADMINISTRATIVO'] }));
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: auth.roles } }),
}));

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

const API = 'http://localhost:8080';

vi.setConfig({ testTimeout: 20000 });

async function openCreateModal(user) {
  await user.click(await screen.findByRole('button', { name: 'Nuevo personal' }));
  return screen.findByRole('dialog');
}

async function fillRequiredFields(user, role) {
  await user.type(screen.getByPlaceholderText('Ej: María'), 'Mara');
  await user.type(screen.getByPlaceholderText('Ej: García'), 'Gil');
  await user.type(screen.getByPlaceholderText('correo@ejemplo.com'), 'mara@demo.test');
  await user.type(screen.getByPlaceholderText('12345678'), '90300011');
  await user.click(screen.getByRole('combobox', { name: 'Rol' }));
  await user.click(await screen.findByRole('option', { name: role }));
}

describe('GestionPersonal', () => {
  beforeEach(() => {
    auth.roles = ['ADMINISTRATIVO'];
    toastMock.success.mockClear();
    toastMock.error.mockClear();
    server.use(http.get(`${API}/staff`, () => HttpResponse.json([])));
  });

  it('offers the director only the roles the backend lets them create', async () => {
    auth.roles = ['DIRECTOR'];
    const user = userEvent.setup();
    renderWithProviders(<GestionPersonal />);

    await openCreateModal(user);
    await user.click(screen.getByRole('combobox', { name: 'Rol' }));

    expect(await screen.findByRole('option', { name: 'Administrativo' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Docente' })).not.toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Preceptor' })).not.toBeInTheDocument();
  });

  it('tells the user when the DNI or email already exists and keeps the form open', async () => {
    server.use(http.post(`${API}/staff`, () => HttpResponse.json({ code: 'DNI_ALREADY_EXISTS' }, { status: 400 })));
    const user = userEvent.setup();
    renderWithProviders(<GestionPersonal />);

    await openCreateModal(user);
    await fillRequiredFields(user, 'Docente');
    await user.click(screen.getByRole('button', { name: 'Crear personal' }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith('Ya existe una persona con ese DNI o ese email.'));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('confirms the creation and starts the next one with an empty form', async () => {
    server.use(
      http.post(`${API}/staff`, () =>
        HttpResponse.json({ id: 88, nombre: 'Mara', apellido: 'Gil', role: 'DOCENTE' }, { status: 201 }),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(<GestionPersonal />);

    await openCreateModal(user);
    await fillRequiredFields(user, 'Docente');
    await user.click(screen.getByRole('button', { name: 'Crear personal' }));

    await waitFor(() => expect(toastMock.success).toHaveBeenCalled());
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    await openCreateModal(user);
    expect(screen.getByPlaceholderText('Ej: María')).toHaveValue('');
  });
});
