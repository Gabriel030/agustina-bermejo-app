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
