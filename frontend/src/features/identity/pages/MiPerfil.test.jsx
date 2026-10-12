import { afterEach, describe, expect, it } from 'vitest';
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
      http.post(`${API}/me/avatar`, () => HttpResponse.json({ code: 'UNSUPPORTED_FILE_TYPE' }, { status: 400 })),
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
      http.put(`${API}/me/password`, () => HttpResponse.json({ code: 'INVALID_CURRENT_PASSWORD' }, { status: 400 })),
    );
    const user = userEvent.setup();
    renderProfile();

    await submitPasswordChange(user);

    expect(await screen.findByText('La contraseña actual no es correcta.')).toBeInTheDocument();
  });

  it('explains the password policy when the new password does not meet it', async () => {
    mockProfileEndpoints();
    server.use(
      http.put(`${API}/me/password`, () => HttpResponse.json({ code: 'INVALID_PASSWORD_POLICY' }, { status: 400 })),
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
