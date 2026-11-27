import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { axe } from '@/test/axe';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PadronAlumnos } from './PadronAlumnos';

const authMock = vi.hoisted(() => ({ roles: ['DIRECTOR'] }));
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: authMock.roles } }),
}));

const API = 'http://localhost:8080';

function prospectsPage(items, overrides = {}) {
  return { items, page: 0, size: 25, totalElements: items.length, ...overrides };
}

function prospect(overrides = {}) {
  return {
    id: 5,
    personId: 9,
    nombre: 'Ana',
    apellido: 'Paz',
    email: 'ana@demo.test',
    telefono: '1122334455',
    emailVerified: false,
    registeredAt: '2026-11-05T10:00:00',
    missingDocuments: ['ANALITICO_SECUNDARIO'],
    ...overrides,
  };
}

async function openProspectsTab(user) {
  await user.click(screen.getByRole('tab', { name: 'Prospectos' }));
}

describe('PadronAlumnos prospects tab', () => {
  it('lists the missing documents of each prospect', async () => {
    server.use(
      http.get(`${API}/students`, () => HttpResponse.json({ items: [], page: 0, size: 25, totalElements: 0 })),
      http.get(`${API}/prospects`, () =>
        HttpResponse.json(prospectsPage([prospect(), prospect({ id: 6, nombre: 'Beto', apellido: 'Gil', email: 'beto@demo.test', missingDocuments: [] })])),
      ),
    );
    const user = userEvent.setup();
    const { container } = renderWithProviders(<PadronAlumnos />);
    await openProspectsTab(user);

    expect(await screen.findByText('Falta: Analítico secundario')).toBeInTheDocument();
    expect(screen.getByText('Documentación completa')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('sends the search text and the page to the server and shows the results', async () => {
    const requests = [];
    server.use(
      http.get(`${API}/students`, () => HttpResponse.json({ items: [], page: 0, size: 25, totalElements: 0 })),
      http.get(`${API}/prospects`, ({ request }) => {
        const params = new URL(request.url).searchParams;
        requests.push({ search: params.get('search'), page: params.get('page'), size: params.get('size') });
        return params.get('search') === 'gil'
          ? HttpResponse.json(prospectsPage([prospect({ id: 6, nombre: 'Beto', apellido: 'Gil', email: 'beto@demo.test' })]))
          : HttpResponse.json(prospectsPage([prospect()], { totalElements: 60 }));
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<PadronAlumnos />);
    await openProspectsTab(user);
    await screen.findByText('Paz, Ana');
    expect(requests[0]).toEqual({ search: null, page: '0', size: '25' });

    await user.click(await screen.findByRole('button', { name: /siguiente/i }));
    await waitFor(() => expect(requests).toContainEqual({ search: null, page: '1', size: '25' }));

    await user.type(screen.getByPlaceholderText(/Buscar/), 'gil');

    expect(await screen.findByText('Gil, Beto')).toBeInTheDocument();
    expect(screen.queryByText('Paz, Ana')).not.toBeInTheDocument();
    expect(requests).toContainEqual({ search: 'gil', page: '0', size: '25' });
  });

  it('shows the search empty state when no prospect matches', async () => {
    server.use(
      http.get(`${API}/students`, () => HttpResponse.json({ items: [], page: 0, size: 25, totalElements: 0 })),
      http.get(`${API}/prospects`, () => HttpResponse.json(prospectsPage([]))),
    );
    const user = userEvent.setup();
    renderWithProviders(<PadronAlumnos />);
    await openProspectsTab(user);
    await user.type(screen.getByPlaceholderText(/Buscar/), 'zzz');

    expect(await screen.findByText('Ningún prospecto coincide con la búsqueda')).toBeInTheDocument();
  });

  it('shows the empty state when there are no prospects', async () => {
    server.use(
      http.get(`${API}/students`, () => HttpResponse.json({ items: [], page: 0, size: 25, totalElements: 0 })),
      http.get(`${API}/prospects`, () => HttpResponse.json(prospectsPage([]))),
    );
    const user = userEvent.setup();
    renderWithProviders(<PadronAlumnos />);
    await openProspectsTab(user);

    expect(await screen.findByText('No hay prospectos')).toBeInTheDocument();
  });

  it('shows a retryable error when the prospects cannot be loaded', async () => {
    let attempts = 0;
    server.use(
      http.get(`${API}/students`, () => HttpResponse.json({ items: [], page: 0, size: 25, totalElements: 0 })),
      http.get(`${API}/prospects`, () => {
        attempts += 1;
        return attempts === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(prospectsPage([prospect()]));
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<PadronAlumnos />);
    await openProspectsTab(user);

    expect(await screen.findByText('No pudimos cargar los prospectos')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Paz, Ana')).toBeInTheDocument();
  });
});

function student(overrides = {}) {
  return {
    id: 1,
    personId: 11,
    nombre: 'Lola',
    apellido: 'Mena',
    email: 'lola@demo.test',
    dni: '30111222',
    username: 'lola',
    status: 'ACTIVE',
    onboardingStatus: 'CONFIRMADO',
    attendanceRisk: 'NONE',
    emailVerified: true,
    missingRequiredDocuments: [],
    ...overrides,
  };
}

function serveStudents(items) {
  server.use(http.get(`${API}/students`, () => HttpResponse.json(prospectsPage(items))));
}

const SIGNAL_TEXTS = ['Asistencia en riesgo', 'Libre por inasistencias', 'Email sin verificar', 'Documentación pendiente'];

describe('PadronAlumnos active student signals', () => {
  it.each([
    ['near limit attendance', { attendanceRisk: 'NEAR_LIMIT' }, 'Asistencia en riesgo'],
    ['free attendance', { attendanceRisk: 'FREE' }, 'Libre por inasistencias'],
    ['unverified email', { emailVerified: false }, 'Email sin verificar'],
    ['missing required documents', { missingRequiredDocuments: ['DNI_FRENTE'] }, 'Documentación pendiente'],
  ])('shows only the signal for %s', async (_name, overrides, expected) => {
    authMock.roles = ['PRECEPTOR'];
    serveStudents([student(overrides)]);
    renderWithProviders(<PadronAlumnos />);

    expect(await screen.findByText(expected)).toBeInTheDocument();
    SIGNAL_TEXTS.filter((text) => text !== expected).forEach((text) => {
      expect(screen.queryByText(text)).not.toBeInTheDocument();
    });
  });

  it('combines the signals up to three pills and exposes the missing documents text', async () => {
    authMock.roles = ['PRECEPTOR'];
    serveStudents([
      student({ attendanceRisk: 'NEAR_LIMIT', emailVerified: false, missingRequiredDocuments: ['ANALITICO_SECUNDARIO'] }),
    ]);
    renderWithProviders(<PadronAlumnos />);

    expect(await screen.findByText('Asistencia en riesgo')).toBeInTheDocument();
    expect(screen.getByText('Email sin verificar')).toBeInTheDocument();
    expect(screen.getByText('Documentación pendiente')).toBeInTheDocument();
    expect(screen.getByText(/Falta: Analítico secundario/)).toBeInTheDocument();
  });

  it('replaces the near limit pill with the free one when attendance is lost', async () => {
    authMock.roles = ['PRECEPTOR'];
    serveStudents([student({ attendanceRisk: 'FREE', emailVerified: false, missingRequiredDocuments: ['DNI_FRENTE'] })]);
    renderWithProviders(<PadronAlumnos />);

    expect(await screen.findByText('Libre por inasistencias')).toBeInTheDocument();
    expect(screen.queryByText('Asistencia en riesgo')).not.toBeInTheDocument();
  });

  it('keeps the row without pills when the student has no signals', async () => {
    authMock.roles = ['PRECEPTOR'];
    serveStudents([student()]);
    renderWithProviders(<PadronAlumnos />);

    await screen.findByText('Mena, Lola');
    SIGNAL_TEXTS.forEach((text) => expect(screen.queryByText(text)).not.toBeInTheDocument());
  });

  it('shows a retryable error when the students cannot be loaded', async () => {
    authMock.roles = ['PRECEPTOR'];
    let attempts = 0;
    server.use(
      http.get(`${API}/students`, () => {
        attempts += 1;
        return attempts === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(prospectsPage([student()]));
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<PadronAlumnos />);

    expect(await screen.findByText('No pudimos cargar los alumnos')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Mena, Lola')).toBeInTheDocument();
  });
});

describe('PadronAlumnos withdrawn students', () => {
  const withdrawn = { status: 'DELETED', withdrawnAt: '2026-10-05T09:30:00', attendanceRisk: 'FREE', emailVerified: false };

  it('shows the withdrawal date and the reason when the response carries it', async () => {
    authMock.roles = ['DIRECTOR'];
    serveStudents([student({ ...withdrawn, withdrawalReason: 'Cambio de ciudad' })]);
    const user = userEvent.setup();
    renderWithProviders(<PadronAlumnos />);
    await user.click(screen.getByRole('tab', { name: 'Bajas' }));

    expect(await screen.findByText('Baja el 05/10/2026')).toBeInTheDocument();
    expect(screen.getByText('Motivo: Cambio de ciudad')).toBeInTheDocument();
    expect(screen.queryByText('Libre por inasistencias')).not.toBeInTheDocument();
  });

  it('shows only the date when the response has no reason', async () => {
    authMock.roles = ['PRECEPTOR'];
    serveStudents([student(withdrawn)]);
    const user = userEvent.setup();
    renderWithProviders(<PadronAlumnos />);
    await user.click(screen.getByRole('tab', { name: 'Bajas' }));

    expect(await screen.findByText('Baja el 05/10/2026')).toBeInTheDocument();
    expect(screen.queryByText(/Motivo:/)).not.toBeInTheDocument();
  });
});

describe('PadronAlumnos new student button', () => {
  it.each([['DIRECTOR'], ['PRECEPTOR'], ['ADMINISTRATIVO']])('is visible for %s', async (role) => {
    authMock.roles = [role];
    server.use(http.get(`${API}/students`, () => HttpResponse.json({ items: [], page: 0, size: 25, totalElements: 0 })));
    renderWithProviders(<PadronAlumnos />);

    expect(await screen.findByRole('button', { name: 'Nuevo alumno' })).toBeInTheDocument();
  });

  it('is hidden for a teacher', async () => {
    authMock.roles = ['DOCENTE'];
    server.use(http.get(`${API}/students`, () => HttpResponse.json({ items: [], page: 0, size: 25, totalElements: 0 })));
    renderWithProviders(<PadronAlumnos />);

    await screen.findByText('Padrón de alumnos');
    expect(screen.queryByRole('button', { name: 'Nuevo alumno' })).not.toBeInTheDocument();
  });
});
