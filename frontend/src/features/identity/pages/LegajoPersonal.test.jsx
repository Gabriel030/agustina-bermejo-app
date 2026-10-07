import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { LegajoPersonal } from './LegajoPersonal';

const auth = vi.hoisted(() => ({ roles: ['ADMINISTRATIVO'] }));
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: auth.roles } }),
}));

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

const API = 'http://localhost:8080';

function record(role) {
  return {
    id: 89,
    nombre: 'Pedro',
    apellido: 'Paz',
    dni: '90300012',
    username: '90300012',
    email: 'pedro@demo.test',
    telefono: null,
    role,
    status: 'ACTIVE',
    domicilio: null,
  };
}

function mount() {
  return renderWithProviders(
    <Routes>
      <Route path="/:rol/personal/:id" element={<LegajoPersonal />} />
    </Routes>,
    { route: '/director/personal/89' },
  );
}

describe('LegajoPersonal', () => {
  beforeEach(() => {
    toastMock.success.mockClear();
    toastMock.error.mockClear();
  });

  it('does not offer the director actions on teachers that the backend forbids', async () => {
    auth.roles = ['DIRECTOR'];
    server.use(http.get(`${API}/staff/89`, () => HttpResponse.json(record('DOCENTE'))));
    mount();

    expect(await screen.findByRole('heading', { name: 'Paz, Pedro' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Editar datos' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Resetear contraseña' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Dar de baja' })).not.toBeInTheDocument();
  });

  it('lets the director manage administrative staff', async () => {
    auth.roles = ['DIRECTOR'];
    server.use(http.get(`${API}/staff/89`, () => HttpResponse.json(record('ADMINISTRATIVO'))));
    mount();

    expect(await screen.findByRole('button', { name: 'Editar datos' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Dar de baja' })).toBeInTheDocument();
  });

  it('explains an invalid email instead of leaving the edit dialog silently open', async () => {
    auth.roles = ['ADMINISTRATIVO'];
    server.use(
      http.get(`${API}/staff/89`, () => HttpResponse.json(record('PRECEPTOR'))),
      http.put(`${API}/staff/89`, () => new HttpResponse(null, { status: 400 })),
    );
    const user = userEvent.setup();
    mount();

    await user.click(await screen.findByRole('button', { name: 'Editar datos' }));
    await user.click(await screen.findByRole('button', { name: /Guardar/ }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith('Revisá los datos: el email no es válido.'));
  });

  it('confirms when the contact data was saved', async () => {
    auth.roles = ['ADMINISTRATIVO'];
    server.use(
      http.get(`${API}/staff/89`, () => HttpResponse.json(record('PRECEPTOR'))),
      http.put(`${API}/staff/89`, () => HttpResponse.json(record('PRECEPTOR'))),
    );
    const user = userEvent.setup();
    mount();

    await user.click(await screen.findByRole('button', { name: 'Editar datos' }));
    await user.click(await screen.findByRole('button', { name: /Guardar/ }));

    await waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Datos de contacto actualizados'));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
});
