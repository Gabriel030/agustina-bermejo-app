import { afterEach, describe, expect, it } from 'vitest';
import { errorBody } from '@/test/fixtures/errors';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster, toast } from 'sonner';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { MiPerfil } from '@/features/identity/pages/MiPerfil';

const API = 'http://localhost:8080';

afterEach(async () => {
  toast.dismiss();
  await waitFor(() => expect(document.querySelector('[data-sonner-toast]')).toBeNull());
});

function renderProfile() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/perfil']}>
        <AuthProvider>
          <MiPerfil />
          <Toaster />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function mockProfileEndpoints() {
  server.use(
    http.get(`${API}/me/sessions`, () => HttpResponse.json([])),
    http.get(`${API}/me/notification-preferences`, () => HttpResponse.json({ preferences: {} })),
    http.get(`${API}/me/avatar`, () => new HttpResponse(null, { status: 404 })),
  );
}

function avatarInput() {
  return document.querySelector('input[type="file"]');
}

describe('MiPerfil — foto de perfil', () => {
  it('rejects a photo that is not JPG or PNG without uploading it', async () => {
    mockProfileEndpoints();
    let uploads = 0;
    server.use(
      http.post(`${API}/me/avatar`, () => {
        uploads += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup({ applyAccept: false });
    renderProfile();

    await screen.findByText('Foto de perfil');
    await user.upload(avatarInput(), new File(['gif'], 'foto.gif', { type: 'image/gif' }));

    expect(await screen.findByText('La foto tiene que ser JPG o PNG de hasta 5 MB.')).toBeInTheDocument();
    expect(uploads).toBe(0);
  });

  it('rejects a photo larger than 5 MB without uploading it', async () => {
    mockProfileEndpoints();
    let uploads = 0;
    server.use(
      http.post(`${API}/me/avatar`, () => {
        uploads += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderProfile();

    await screen.findByText('Foto de perfil');
    const bigPhoto = new File(['png'], 'foto.png', { type: 'image/png' });
    Object.defineProperty(bigPhoto, 'size', { value: 6 * 1024 * 1024 });
    await user.upload(avatarInput(), bigPhoto);

    expect(await screen.findByText('La foto tiene que ser JPG o PNG de hasta 5 MB.')).toBeInTheDocument();
    expect(uploads).toBe(0);
  });

  it('tells the user when the server rejects the photo', async () => {
    mockProfileEndpoints();
    server.use(
      http.post(`${API}/me/avatar`, () => HttpResponse.json(errorBody('UNSUPPORTED_FILE_TYPE'), { status: 400 })),
    );
    const user = userEvent.setup();
    renderProfile();

    await screen.findByText('Foto de perfil');
    await user.upload(avatarInput(), new File(['png'], 'foto.png', { type: 'image/png' }));

    expect(await screen.findByText('La foto tiene que ser JPG o PNG de hasta 5 MB.')).toBeInTheDocument();
  });
});

describe('MiPerfil — cambiar contraseña', () => {
  async function submitPasswordChange(user, { current = 'Actual123', next = 'Nueva1234' } = {}) {
    await user.click(await screen.findByRole('tab', { name: 'Seguridad' }));
    const [currentInput, nextInput, confirmInput] = document.querySelectorAll('input[type="password"]');
    await user.type(currentInput, current);
    await user.type(nextInput, next);
    await user.type(confirmInput, next);
    await user.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));
  }

  it('says the current password is wrong instead of a generic error', async () => {
    mockProfileEndpoints();
    server.use(
      http.put(`${API}/me/password`, () => HttpResponse.json(errorBody('INVALID_CURRENT_PASSWORD'), { status: 400 })),
    );
    const user = userEvent.setup();
    renderProfile();

    await submitPasswordChange(user);

    expect(await screen.findByText('La contraseña actual no es correcta.')).toBeInTheDocument();
  });

  it('explains the password policy when the new password does not meet it', async () => {
    mockProfileEndpoints();
    server.use(
      http.put(`${API}/me/password`, () => HttpResponse.json(errorBody('INVALID_PASSWORD_POLICY'), { status: 400 })),
    );
    const user = userEvent.setup();
    renderProfile();

    await submitPasswordChange(user, { next: 'nueva1234' });

    expect(
      await screen.findByText('La nueva contraseña tiene que tener al menos 8 caracteres, una mayúscula y un número.'),
    ).toBeInTheDocument();
  });

  it('shows the password policy before submitting', async () => {
    mockProfileEndpoints();
    const user = userEvent.setup();
    renderProfile();

    await user.click(await screen.findByRole('tab', { name: 'Seguridad' }));

    expect(screen.getByText('Mínimo 8 caracteres, con una mayúscula y un número.')).toBeInTheDocument();
  });

  it('warns that every session was closed and signs the user out after a successful change', async () => {
    mockProfileEndpoints();
    localStorage.setItem('refreshToken', 'refresh-actual');
    let loggedOut = false;
    server.use(
      http.put(`${API}/me/password`, () => new HttpResponse(null, { status: 204 })),
      http.post(`${API}/auth/logout`, () => {
        loggedOut = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderProfile();

    await submitPasswordChange(user);

    expect(
      await screen.findByText('Contraseña actualizada. Por seguridad cerramos todas tus sesiones: ingresá de nuevo con tu nueva contraseña.'),
    ).toBeInTheDocument();
    await waitFor(() => expect(loggedOut).toBe(true));
  });
});

const PROFILE = {
  id: 7,
  nombre: 'Valentina',
  apellido: 'Suárez',
  legajo: '1007',
  dni: '30123456',
  documentType: 'DNI',
  email: 'valentina@demo.test',
  telefono: '1155551234',
  sexo: 'FEMALE',
  street: 'Av. Rivadavia',
  number: '4520',
  floor: '',
  city: 'CABA',
  state: 'CABA',
  postalCode: '1406',
  country: 'Argentina',
  hasAvatar: false,
  roles: ['ALUMNO'],
};

function mockProfile(profile = PROFILE, onPut = () => HttpResponse.json(profile)) {
  mockProfileEndpoints();
  const bodies = [];
  server.use(
    http.get(`${API}/me`, () => HttpResponse.json(profile)),
    http.put(`${API}/me`, async ({ request }) => {
      bodies.push(await request.json());
      return onPut(bodies.at(-1));
    }),
  );
  return bodies;
}

async function replaceField(user, value, next) {
  const input = await screen.findByDisplayValue(value);
  await user.clear(input);
  await user.type(input, next);
}

describe('MiPerfil — cambio seguro de email y documento', () => {
  it('saves without asking for the password when no sensitive field changed', async () => {
    const bodies = mockProfile();
    const user = userEvent.setup();
    renderProfile();

    await replaceField(user, '1155551234', '1166667777');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await screen.findByText('Datos guardados.');
    expect(bodies[0].currentPassword).toBeUndefined();
    expect(screen.queryByText('Confirmá los cambios sensibles')).not.toBeInTheDocument();
  });

  it('shows Hoy and Nuevo and sends the current password when the email changes', async () => {
    const bodies = mockProfile();
    const user = userEvent.setup();
    renderProfile();

    await replaceField(user, 'valentina@demo.test', 'nuevo@demo.test');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(bodies).toHaveLength(0);
    expect(screen.getByRole('group', { name: 'Email' })).toHaveTextContent('Hoyvalentina@demo.testNuevonuevo@demo.test');
    await user.type(document.getElementById('current-password'), 'Test1234');
    await user.click(screen.getByRole('button', { name: 'Confirmar cambios' }));

    expect(await screen.findByText(/Te enviamos un enlace a nuevo@demo.test;/)).toBeInTheDocument();
    expect(bodies[0]).toMatchObject({ email: 'nuevo@demo.test', currentPassword: 'Test1234' });
    expect(screen.getByText('Pendiente: nuevo@demo.test')).toBeInTheDocument();
  });

  it('shows an inline error and keeps the step open when the current password is wrong', async () => {
    mockProfile(PROFILE, () => HttpResponse.json(errorBody('INVALID_CURRENT_PASSWORD'), { status: 400 }));
    const user = userEvent.setup();
    renderProfile();

    await replaceField(user, '30123456', '30999888');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));
    await user.type(document.getElementById('current-password'), 'mal');
    await user.click(screen.getByRole('button', { name: 'Confirmar cambios' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('La contraseña actual no es correcta.');
    expect(document.getElementById('current-password')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.queryByText(/Te enviamos un enlace/)).not.toBeInTheDocument();
  });

  it('does not ask for the password the first time the document is loaded', async () => {
    const bodies = mockProfile({ ...PROFILE, dni: '' });
    const user = userEvent.setup();
    renderProfile();

    await screen.findByDisplayValue('valentina@demo.test');
    const documentInput = screen.getAllByRole('textbox').find((input) => input.value === '');
    await user.type(documentInput, '30111222');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await screen.findByText('Datos guardados.');
    expect(bodies[0].currentPassword).toBeUndefined();
  });

  it('resends the link by saving again with the current password', async () => {
    const bodies = mockProfile();
    const user = userEvent.setup();
    renderProfile();

    await replaceField(user, 'valentina@demo.test', 'nuevo@demo.test');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));
    await user.type(document.getElementById('current-password'), 'Test1234');
    await user.click(screen.getByRole('button', { name: 'Confirmar cambios' }));
    await user.click(await screen.findByRole('button', { name: 'Reenviar enlace' }));
    await user.type(document.getElementById('resend-password'), 'Test1234');
    await user.click(screen.getByRole('button', { name: 'Reenviar' }));

    await waitFor(() => expect(bodies).toHaveLength(2));
    expect(bodies[1]).toMatchObject({ email: 'nuevo@demo.test', currentPassword: 'Test1234' });
  });
});

describe('MiPerfil — load failure', () => {
  it('does not mount the personal data form nor allow saving when the profile fails to load', async () => {
    let puts = 0;
    mockProfileEndpoints();
    server.use(
      http.get(`${API}/me`, () => new HttpResponse(null, { status: 500 })),
      http.put(`${API}/me`, () => {
        puts += 1;
        return HttpResponse.json(PROFILE);
      }),
    );
    renderProfile();

    await screen.findByText('No pudimos cargar tus datos');
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Guardar cambios' })).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue('')).not.toBeInTheDocument();
    expect(puts).toBe(0);
  });
});
