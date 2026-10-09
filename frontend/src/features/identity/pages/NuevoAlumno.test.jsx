import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { NuevoAlumno } from './NuevoAlumno';

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

const API = 'http://localhost:8080';

vi.setConfig({ testTimeout: 20000 });

function mockOnboarding({ onCreate, onConfirm } = {}) {
  server.use(
    http.get(`${API}/institutions`, () => HttpResponse.json([{ id: 1, name: 'ISAB' }])),
    http.post(`${API}/prospects`, onCreate ?? (() => HttpResponse.json({ id: 64 }, { status: 201 }))),
    http.post(`${API}/prospects/64/documents`, ({ request }) =>
      HttpResponse.json({ id: 1, type: new URL(request.url).searchParams.get('type') }, { status: 201 }),
    ),
    http.put(`${API}/prospects/64`, () => new HttpResponse(null, { status: 204 })),
    http.post(`${API}/prospects/64/confirm`, onConfirm ?? (() => HttpResponse.json({ id: 64 }))),
  );
}

async function fillBasicData(user) {
  await user.type(screen.getByPlaceholderText('Juan'), 'Mostrador');
  await user.type(screen.getByPlaceholderText('Pérez'), 'Nueve');
  await user.type(screen.getByPlaceholderText('juan.perez@email.com'), 'qa9@demo.test');
}

async function uploadAllDocuments(user, container) {
  const file = new File(['%PDF'], 'doc.pdf', { type: 'application/pdf' });
  for (const input of container.querySelectorAll('input[type="file"]')) {
    await user.upload(input, file);
  }
  await screen.findByText('3/3 subidos');
}

describe('NuevoAlumno', () => {
  beforeEach(() => {
    toastMock.success.mockClear();
    toastMock.error.mockClear();
  });

  it('going back to the basic data and forward again does not create a second prospect', async () => {
    let creations = 0;
    mockOnboarding({
      onCreate: () => {
        creations += 1;
        return HttpResponse.json({ id: 64 }, { status: 201 });
      },
    });
    const user = userEvent.setup();
    renderWithProviders(<NuevoAlumno />);

    await fillBasicData(user);
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    await screen.findByRole('heading', { name: 'Documentos' });

    await user.click(screen.getByRole('button', { name: 'Atrás' }));
    expect(screen.getByPlaceholderText('Juan')).toHaveAttribute('readonly');
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByRole('heading', { name: 'Documentos' })).toBeInTheDocument();
    expect(creations).toBe(1);
  });

  it('explains that the email is already registered', async () => {
    mockOnboarding({ onCreate: () => new HttpResponse(null, { status: 409 }) });
    const user = userEvent.setup();
    renderWithProviders(<NuevoAlumno />);

    await fillBasicData(user);
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith('Ya existe una persona registrada con ese email.'));
  });

  it('hides the missing documents warning once every document was uploaded', async () => {
    mockOnboarding();
    const user = userEvent.setup();
    const { container } = renderWithProviders(<NuevoAlumno />);

    await fillBasicData(user);
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    await screen.findByRole('heading', { name: 'Documentos' });
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    expect(screen.getByText('Tenés que subir todos los documentos para continuar.')).toBeInTheDocument();

    await uploadAllDocuments(user, container);

    expect(screen.queryByText('Tenés que subir todos los documentos para continuar.')).not.toBeInTheDocument();
  });

  it('explains a duplicate DNI and, once confirmed, describes the real state of the student', async () => {
    let confirmAttempts = 0;
    mockOnboarding({
      onConfirm: () => {
        confirmAttempts += 1;
        return confirmAttempts === 1
          ? HttpResponse.json({ code: 'DNI_ALREADY_EXISTS' }, { status: 400 })
          : HttpResponse.json({ id: 64 });
      },
    });
    const user = userEvent.setup();
    const { container } = renderWithProviders(<NuevoAlumno />);

    await fillBasicData(user);
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    await screen.findByRole('heading', { name: 'Documentos' });
    await uploadAllDocuments(user, container);
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    await user.type(await screen.findByPlaceholderText('12345678'), '40111666');
    await user.type(screen.getByPlaceholderText('Av. Corrientes'), 'Calle 1');
    await user.type(screen.getByPlaceholderText('Buenos Aires'), 'Córdoba');
    await user.click(screen.getByRole('button', { name: 'Confirmar alta' }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith('Ya existe una persona con ese documento.'));

    await user.click(screen.getByRole('button', { name: 'Confirmar alta' }));

    expect(await screen.findByText(/para que elija su contraseña/)).toBeInTheDocument();
    expect(screen.getByText('Alumno confirmado')).toBeInTheDocument();
    expect(screen.queryByText(/queda como prospecto/)).not.toBeInTheDocument();
    expect(screen.queryByText(/reenviar la verificación/)).not.toBeInTheDocument();
  });
});
