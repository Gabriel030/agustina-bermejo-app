import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { errorBody } from '@/test/fixtures/errors';
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

  it('asks for a Hoy/Nuevo confirmation before changing the email and then shows the pending notice', async () => {
    auth.roles = ['ADMINISTRATIVO'];
    let sent = null;
    server.use(
      http.get(`${API}/staff/89`, () => HttpResponse.json(record('PRECEPTOR'))),
      http.put(`${API}/staff/89`, async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(record('PRECEPTOR'));
      }),
    );
    const user = userEvent.setup();
    mount();

    await user.click(await screen.findByRole('button', { name: 'Editar datos' }));
    const email = within(await screen.findByRole('dialog')).getByDisplayValue('pedro@demo.test');
    await user.clear(email);
    await user.type(email, 'nuevo@demo.test');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(sent).toBeNull();
    expect(screen.getByRole('group', { name: 'Email' })).toHaveTextContent('Hoypedro@demo.testNuevonuevo@demo.test');
    await user.click(screen.getByRole('button', { name: 'Confirmar y enviar enlace' }));

    expect(await screen.findByText('Cambio de email enviado a confirmar')).toBeInTheDocument();
    expect(sent.email).toBe('nuevo@demo.test');
    expect(screen.getByRole('status')).toHaveTextContent('nuevo@demo.test');
  });

  it('does not add a confirmation step when the email did not change', async () => {
    auth.roles = ['ADMINISTRATIVO'];
    server.use(
      http.get(`${API}/staff/89`, () => HttpResponse.json(record('PRECEPTOR'))),
      http.put(`${API}/staff/89`, () => HttpResponse.json(record('PRECEPTOR'))),
    );
    const user = userEvent.setup();
    mount();

    await user.click(await screen.findByRole('button', { name: 'Editar datos' }));
    await user.click(await screen.findByRole('button', { name: 'Guardar cambios' }));

    await waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Datos de contacto actualizados'));
    expect(screen.queryByText('Cambio de email enviado a confirmar')).not.toBeInTheDocument();
  });

  it('shows the 24 h block when the backend refuses the password reset', async () => {
    auth.roles = ['ADMINISTRATIVO'];
    server.use(
      http.get(`${API}/staff/89`, () => HttpResponse.json(record('PRECEPTOR'))),
      http.post(`${API}/staff/89/reset-password`, () =>
        HttpResponse.json(errorBody('STAFF_PASSWORD_RESET_BLOCKED'), { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    mount();

    await user.click(await screen.findByRole('button', { name: 'Resetear contraseña' }));
    await user.click(await screen.findByRole('button', { name: 'Enviar link' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('pasadas 24 horas');
    expect(toastMock.error).not.toHaveBeenCalled();
  });
});
