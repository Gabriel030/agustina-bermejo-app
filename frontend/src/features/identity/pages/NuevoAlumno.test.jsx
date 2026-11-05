import { beforeEach, describe, expect, it, vi } from 'vitest';
import { errorBody } from '@/test/fixtures/errors';
import { axe } from '@/test/axe';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { RateLimitedError } from '@/api/client';
import { NuevoAlumno } from './NuevoAlumno';

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: ['PRECEPTOR'] } }),
}));

const API = 'http://localhost:8080';

vi.setConfig({ testTimeout: 20000 });

function prospectResponse(overrides = {}) {
  return {
    id: 64,
    personId: 90,
    nombre: 'Mostrador',
    apellido: 'Nueve',
    email: 'qa9@demo.test',
    dni: '',
    username: 'qa9@demo.test',
    emailVerified: false,
    onboardingStatus: 'PROSPECTO',
    status: 'ACTIVE',
    ...overrides,
  };
}

function mockOnboarding({ onCreate, onConfirm, onDraft } = {}) {
  server.use(
    http.get(`${API}/institutions`, () => HttpResponse.json([{ id: 1, name: 'ISAB' }])),
    http.post(`${API}/prospects/by-staff`, onCreate ?? (() => HttpResponse.json(prospectResponse(), { status: 201 }))),
    http.post(`${API}/prospects/64/documents`, ({ request }) =>
      HttpResponse.json({ id: 1, type: new URL(request.url).searchParams.get('type'), uploadedAt: '2026-11-06T10:00:00' }, { status: 201 }),
    ),
    http.put(`${API}/prospects/64`, onDraft ?? (() => new HttpResponse(null, { status: 204 }))),
    http.post(`${API}/prospects/64/confirm`, onConfirm ?? (() => HttpResponse.json(prospectResponse({ dni: '40111666', onboardingStatus: 'CONFIRMADO' })))),
  );
}

async function fillBasicData(user, email = 'qa9@demo.test') {
  await user.type(screen.getByPlaceholderText('Juan'), 'Mostrador');
  await user.type(screen.getByPlaceholderText('Pérez'), 'Nueve');
  await user.type(screen.getByPlaceholderText('juan.perez@email.com'), email);
}

async function fillPersonalData(user, dni = '40111666') {
  await user.type(await screen.findByPlaceholderText('12345678'), dni);
  await user.type(screen.getByPlaceholderText('Av. Corrientes'), 'Calle 1');
  await user.type(screen.getByPlaceholderText('Buenos Aires'), 'Córdoba');
}

async function goToPersonalDataStep(user) {
  await fillBasicData(user);
  await user.click(screen.getByRole('button', { name: 'Siguiente' }));
  await screen.findByRole('heading', { name: 'Documentos' });
  await user.click(screen.getByRole('button', { name: 'Siguiente' }));
  await screen.findByRole('heading', { name: 'Datos de confirmación' });
}

async function goToReviewStep(user) {
  await goToPersonalDataStep(user);
  await fillPersonalData(user);
  await user.click(screen.getByRole('button', { name: 'Siguiente' }));
  await screen.findByRole('heading', { name: 'Revisar y confirmar' });
}

describe('NuevoAlumno', () => {
  beforeEach(() => {
    toastMock.success.mockClear();
    toastMock.error.mockClear();
  });

  it('registers the student through the staff endpoint, never the public one', async () => {
    const calls = [];
    mockOnboarding({
      onCreate: () => {
        calls.push('by-staff');
        return HttpResponse.json(prospectResponse(), { status: 201 });
      },
    });
    server.use(http.post(`${API}/prospects`, () => { calls.push('public'); return HttpResponse.json(prospectResponse(), { status: 201 }); }));
    const user = userEvent.setup();
    renderWithProviders(<NuevoAlumno />);

    await fillBasicData(user);
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    await screen.findByRole('heading', { name: 'Documentos' });

    expect(calls).toEqual(['by-staff']);
  });

  it('going back to the basic data and forward again does not create a second prospect', async () => {
    let creations = 0;
    mockOnboarding({
      onCreate: () => {
        creations += 1;
        return HttpResponse.json(prospectResponse(), { status: 201 });
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

  it('shows the duplicated email under the email field and keeps the user on the first step', async () => {
    mockOnboarding({ onCreate: () => HttpResponse.json(errorBody('EMAIL_ALREADY_EXISTS'), { status: 409 }) });
    const user = userEvent.setup();
    renderWithProviders(<NuevoAlumno />);

    await fillBasicData(user);
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    const message = await screen.findByText('Ya existe una persona registrada con ese email.');
    expect(message).toHaveAttribute('role', 'alert');
    expect(screen.getByPlaceholderText('juan.perez@email.com')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.queryByRole('heading', { name: 'Documentos' })).not.toBeInTheDocument();
  });

  it('explains the rate limit in a banner instead of a field error', async () => {
    mockOnboarding({ onCreate: () => HttpResponse.json(errorBody('RATE_LIMITED', { retryable: true }), { status: 429, headers: { 'Retry-After': '20' } }) });
    const user = userEvent.setup();
    renderWithProviders(<NuevoAlumno />);

    await fillBasicData(user);
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByText(new RateLimitedError(20).message)).toBeInTheDocument();
  });

  it('lets the staff skip the documents and reports what is missing when confirming', async () => {
    mockOnboarding();
    const user = userEvent.setup();
    renderWithProviders(<NuevoAlumno />);

    await goToReviewStep(user);

    expect(screen.getByText('Falta: DNI frente, DNI dorso, Analítico secundario')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Confirmar alta' }));
    expect(await screen.findByRole('heading', { name: 'Alta confirmada' })).toBeInTheDocument();
    expect(screen.getByText('Falta: DNI frente, DNI dorso, Analítico secundario')).toBeInTheDocument();
  });

  it('shows the documentation as complete once every document was uploaded', async () => {
    mockOnboarding();
    const user = userEvent.setup();
    const { container } = renderWithProviders(<NuevoAlumno />);

    await fillBasicData(user);
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    await screen.findByRole('heading', { name: 'Documentos' });
    const file = new File(['%PDF'], 'doc.pdf', { type: 'application/pdf' });
    for (const input of container.querySelectorAll('input[type="file"]')) {
      await user.upload(input, file);
    }
    await screen.findByText('3/3 subidos');
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    await fillPersonalData(user);
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByText('Documentación completa')).toBeInTheDocument();
  });

  it('sends the user back to the document field when the DNI already exists and confirms after fixing it', async () => {
    let confirmAttempts = 0;
    mockOnboarding({
      onConfirm: () => {
        confirmAttempts += 1;
        return confirmAttempts === 1
          ? HttpResponse.json(errorBody('DNI_ALREADY_EXISTS'), { status: 409 })
          : HttpResponse.json(prospectResponse({ dni: '40111667', onboardingStatus: 'CONFIRMADO' }));
      },
    });
    const user = userEvent.setup();
    renderWithProviders(<NuevoAlumno />);
    await goToReviewStep(user);

    await user.click(screen.getByRole('button', { name: 'Confirmar alta' }));

    expect(await screen.findByText('Ya existe una persona registrada con ese documento.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Datos de confirmación' })).toBeInTheDocument();

    const document = screen.getByPlaceholderText('12345678');
    await user.clear(document);
    await user.type(document, '40111667');
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    await screen.findByRole('heading', { name: 'Revisar y confirmar' });
    await user.click(screen.getByRole('button', { name: 'Confirmar alta' }));

    expect(await screen.findByRole('heading', { name: 'Alta confirmada' })).toBeInTheDocument();
    expect(screen.getByText(/para que elija su contraseña/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ir al legajo' })).toBeInTheDocument();
  });

  it('starts a clean registration when the staff asks to register another student', async () => {
    mockOnboarding();
    const user = userEvent.setup();
    renderWithProviders(<NuevoAlumno />);
    await goToReviewStep(user);
    await user.click(screen.getByRole('button', { name: 'Confirmar alta' }));
    await screen.findByRole('heading', { name: 'Alta confirmada' });

    await user.click(screen.getByRole('button', { name: 'Dar de alta a otro' }));

    expect(await screen.findByRole('heading', { name: 'Datos básicos' })).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Juan')).toHaveValue('');
    expect(screen.getByPlaceholderText('Juan')).not.toHaveAttribute('readonly');
  });

  it('blocks the first step and shows the required messages in Spanish when everything is empty', async () => {
    let creations = 0;
    mockOnboarding({
      onCreate: () => {
        creations += 1;
        return HttpResponse.json(prospectResponse(), { status: 201 });
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

  it('omits the optional phone when empty and sends it when provided', async () => {
    const bodies = [];
    mockOnboarding({
      onCreate: async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(prospectResponse(), { status: 201 });
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
      { nombre: 'Mostrador', apellido: 'Nueve', email: 'qa9@demo.test', telefono: '', institutionId: 1 },
      { nombre: 'Mostrador', apellido: 'Nueve', email: 'qa9@demo.test', telefono: '1155556666', institutionId: 1 },
    ]);
  });

  it('blocks the personal data step until the required address and document fields are valid', async () => {
    let drafts = 0;
    mockOnboarding({
      onDraft: () => {
        drafts += 1;
        return new HttpResponse(null, { status: 204 });
      },
    });
    const user = userEvent.setup();
    renderWithProviders(<NuevoAlumno />);
    await goToPersonalDataStep(user);

    expect(screen.getByRole('combobox', { name: 'Tipo de documento' })).toHaveTextContent('DNI');

    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    expect(await screen.findAllByText('Requerido')).toHaveLength(3);

    await user.type(screen.getByPlaceholderText('12345678'), 'AB12');
    await user.type(screen.getByPlaceholderText('Av. Corrientes'), 'Calle 1');
    await user.type(screen.getByPlaceholderText('Buenos Aires'), 'Córdoba');
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByText('El DNI tiene 7 u 8 números, sin puntos ni espacios')).toBeInTheDocument();
    expect(drafts).toBe(0);
  });

  it('saves the draft and confirms with the default DNI type and null for the empty optional fields', async () => {
    let draft;
    let confirmed;
    mockOnboarding({
      onDraft: async ({ request }) => {
        draft = await request.json();
        return new HttpResponse(null, { status: 204 });
      },
      onConfirm: async ({ request }) => {
        confirmed = await request.json();
        return HttpResponse.json(prospectResponse({ dni: '40111666', onboardingStatus: 'CONFIRMADO' }));
      },
    });
    const user = userEvent.setup();
    renderWithProviders(<NuevoAlumno />);
    await goToReviewStep(user);
    await user.click(screen.getByRole('button', { name: 'Confirmar alta' }));
    await screen.findByRole('heading', { name: 'Alta confirmada' });

    const expected = {
      documentType: 'DNI',
      dni: '40111666',
      street: 'Calle 1',
      number: null,
      floor: null,
      city: 'Córdoba',
      state: null,
      postalCode: null,
      country: null,
    };
    expect(draft).toEqual(expected);
    expect(confirmed).toEqual(expected);
  });

  it('has no accessibility violations on the basic data and review steps', async () => {
    mockOnboarding();
    const user = userEvent.setup();
    const { container } = renderWithProviders(<NuevoAlumno />);
    expect(await axe(container)).toHaveNoViolations();

    await goToReviewStep(user);
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Revisar y confirmar' })).toBeInTheDocument());
    expect(await axe(container)).toHaveNoViolations();
  });
});
