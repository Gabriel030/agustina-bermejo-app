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

async function fillBasicData(user, email = 'qa9@demo.test') {
  await user.type(screen.getByPlaceholderText('Juan'), 'Mostrador');
  await user.type(screen.getByPlaceholderText('Pérez'), 'Nueve');
  await user.type(screen.getByPlaceholderText('juan.perez@email.com'), email);
}

async function goToConfirmationStep(user, container) {
  await fillBasicData(user);
  await user.click(screen.getByRole('button', { name: 'Siguiente' }));
  await screen.findByRole('heading', { name: 'Documentos' });
  await uploadAllDocuments(user, container);
  await user.click(screen.getByRole('button', { name: 'Siguiente' }));
  await screen.findByRole('heading', { name: 'Datos de confirmación' });
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

  it('blocks the first step and shows the required messages in Spanish when everything is empty', async () => {
    let creations = 0;
    mockOnboarding({
      onCreate: () => {
        creations += 1;
        return HttpResponse.json({ id: 64 }, { status: 201 });
      },
    });
    const user = userEvent.setup();
    renderWithProviders(<NuevoAlumno />);

    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findAllByText('Requerido')).toHaveLength(2);
    expect(screen.getByText('Email inválido')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Documentos' })).not.toBeInTheDocument();
    expect(creations).toBe(0);
  });

  it('rejects a malformed email and lets the user continue once it is corrected', async () => {
    let creations = 0;
    mockOnboarding({
      onCreate: () => {
        creations += 1;
        return HttpResponse.json({ id: 64 }, { status: 201 });
      },
    });
    const user = userEvent.setup();
    renderWithProviders(<NuevoAlumno />);

    await fillBasicData(user, 'juan@');
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByText('Email inválido')).toBeInTheDocument();
    expect(creations).toBe(0);

    const email = screen.getByPlaceholderText('juan.perez@email.com');
    await user.clear(email);
    await user.type(email, 'juan@demo.test');
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByRole('heading', { name: 'Documentos' })).toBeInTheDocument();
    expect(creations).toBe(1);
  });

  it('omits the optional phone when empty and sends it when provided', async () => {
    const bodies = [];
    mockOnboarding({
      onCreate: async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ id: 64 }, { status: 201 });
      },
    });
    const user = userEvent.setup();
    const first = renderWithProviders(<NuevoAlumno />);

    await fillBasicData(user);
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    await screen.findByRole('heading', { name: 'Documentos' });
    first.unmount();

    renderWithProviders(<NuevoAlumno />);
    await fillBasicData(user);
    await user.type(screen.getByPlaceholderText('1122334455'), '1155556666');
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    await screen.findByRole('heading', { name: 'Documentos' });

    expect(bodies).toEqual([
      { nombre: 'Mostrador', apellido: 'Nueve', email: 'qa9@demo.test', institutionId: 1 },
      { nombre: 'Mostrador', apellido: 'Nueve', email: 'qa9@demo.test', telefono: '1155556666', institutionId: 1 },
    ]);
  });

  it('blocks the confirmation step until the required address and document fields are valid', async () => {
    let confirmations = 0;
    mockOnboarding({
      onConfirm: () => {
        confirmations += 1;
        return HttpResponse.json({ id: 64 });
      },
    });
    const user = userEvent.setup();
    const { container } = renderWithProviders(<NuevoAlumno />);
    await goToConfirmationStep(user, container);

    expect(screen.getByRole('combobox', { name: 'Tipo de documento' })).toHaveTextContent('DNI');

    await user.click(screen.getByRole('button', { name: 'Confirmar alta' }));
    expect(await screen.findAllByText('Requerido')).toHaveLength(3);
    expect(screen.queryByText(/Invalid input/)).not.toBeInTheDocument();

    await user.type(screen.getByPlaceholderText('12345678'), 'AB12');
    await user.type(screen.getByPlaceholderText('Av. Corrientes'), 'Calle 1');
    await user.type(screen.getByPlaceholderText('Buenos Aires'), 'Córdoba');
    await user.click(screen.getByRole('button', { name: 'Confirmar alta' }));

    expect(await screen.findByText('El DNI tiene 7 u 8 números, sin puntos ni espacios')).toBeInTheDocument();
    expect(screen.queryByText('Requerido')).not.toBeInTheDocument();
    expect(confirmations).toBe(0);
  });

  it('confirms the student with the default DNI type and null for the empty optional fields', async () => {
    let draft;
    mockOnboarding();
    server.use(
      http.put(`${API}/prospects/64`, async ({ request }) => {
        draft = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    const { container } = renderWithProviders(<NuevoAlumno />);
    await goToConfirmationStep(user, container);

    await user.type(screen.getByPlaceholderText('12345678'), '40111666');
    await user.type(screen.getByPlaceholderText('Av. Corrientes'), 'Calle 1');
    await user.type(screen.getByPlaceholderText('Buenos Aires'), 'Córdoba');
    await user.click(screen.getByRole('button', { name: 'Confirmar alta' }));

    expect(await screen.findByText('Alumno confirmado')).toBeInTheDocument();
    expect(draft).toEqual({
      documentType: 'DNI',
      dni: '40111666',
      street: 'Calle 1',
      number: null,
      floor: null,
      city: 'Córdoba',
      state: null,
      postalCode: null,
      country: null,
    });
  });
});
