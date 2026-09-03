import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { AppRouter } from '@/routes/AppRouter';

const API = 'http://localhost:8080';

/*
 * Integration test del flujo crítico de Épica 7 (Fase 2 + Fase 3) — registro →
 * catálogo → detalle de carrera → wizard de inscripción con pago → ver la
 * solicitud PENDING en la tab Mis solicitudes, con datos reales del backend
 * (mockeados vía MSW), mismo criterio que login.integration.test.jsx.
 */
function renderApp(initialPath = '/registrarse') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <AuthProvider>
          <AppRouter />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

async function registrarse(user) {
  await user.type(screen.getByPlaceholderText('Nombre'), 'Sofía');
  await user.type(screen.getByPlaceholderText('Apellido'), 'Ramírez');
  await user.type(screen.getByPlaceholderText('tu@email.com'), 'sofia@example.com');
  await user.type(screen.getByPlaceholderText('Contraseña'), 'Test1234');
  await user.type(screen.getByPlaceholderText('Repetir contraseña'), 'Test1234');
  await user.click(screen.getByRole('button', { name: /crear cuenta/i }));
}

describe('flujo de registro end-to-end', () => {
  it('registrarse sin rol asignado redirige a /catalogo, no a una pantalla de rol', async () => {
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json({ id: 9, nombre: 'Sofía', apellido: 'Ramírez', email: 'sofia@example.com', roles: [] }),
      ),
      http.get(`${API}/programs`, () => HttpResponse.json([{ id: 1, name: 'Diseño Gráfico', totalYears: 3 }])),
    );
    const user = userEvent.setup();
    renderApp();

    await registrarse(user);

    expect(await screen.findByText('Diseño Gráfico')).toBeInTheDocument();
    expect(screen.getAllByText('Catálogo').length).toBeGreaterThan(0);
  });

  it('registrarse → detalle de carrera → wizard con pago → ver la solicitud PENDING en Mis solicitudes', async () => {
    server.use(
      http.get(`${API}/me`, () =>
        HttpResponse.json({ id: 9, nombre: 'Sofía', apellido: 'Ramírez', email: 'sofia@example.com', roles: [] }),
      ),
      http.get(`${API}/programs`, () => HttpResponse.json([{ id: 1, name: 'Diseño Gráfico', totalYears: 3 }])),
      http.get(`${API}/programs/1`, () => HttpResponse.json({ id: 1, name: 'Diseño Gráfico', totalYears: 3 })),
      http.get(`${API}/programs/1/pricing`, () =>
        HttpResponse.json({ programId: 1, matriculaCohorte1: 50000, matriculaCohorte2: 25000, cuotaMensual: 15000 }),
      ),
      http.get(`${API}/programs/1/cohorts`, () =>
        HttpResponse.json([{ id: 1, programId: 1, label: 'Marzo 2026', ordenEnAnio: 1, cursadaStart: '2026-03-01' }]),
      ),
      http.get(`${API}/programs/1/subjects`, () => HttpResponse.json([])),
      http.put(`${API}/me`, () =>
        HttpResponse.json({ id: 9, nombre: 'Sofía', apellido: 'Ramírez', email: 'sofia@example.com', dni: '30111222', roles: [] }),
      ),
      http.post(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json({ id: 42, programId: 1, programName: 'Diseño Gráfico', status: 'PENDING' }, { status: 201 }),
      ),
      http.post(`${API}/me/program-enrollment-applications/:id/documents`, () => new HttpResponse(null, { status: 201 })),
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 42, programId: 1, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' }]),
      ),
      http.get(`${API}/me/course-enrollment-applications`, () => HttpResponse.json([])),
    );
    const user = userEvent.setup();
    renderApp();

    await registrarse(user);

    // Catálogo → click en la card → Detalle de carrera
    await user.click(await screen.findByText('Diseño Gráfico'));
    await user.click(await screen.findByRole('button', { name: 'Inscribirme' }));

    // Página de inscripción (no modal): cohorte → documentación → pago → confirmar
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await user.click(await screen.findByText('Marzo 2026'));

    // Email ya viene precargado de /me (Sofía se registró con email) — solo falta DNI/dirección.
    await user.type(screen.getByPlaceholderText('DNI sin puntos'), '30111222');
    const [, , , , calleInput, numeroInput, , ciudadInput] = screen.getAllByRole('textbox');
    await user.type(calleInput, 'Calle Falsa');
    await user.type(numeroInput, '123');
    await user.type(ciudadInput, 'Ciudad');
    const dniFrente = new File(['contenido'], 'dni-frente.jpg', { type: 'image/jpeg' });
    const dniDorso = new File(['contenido'], 'dni-dorso.jpg', { type: 'image/jpeg' });
    const analitico = new File(['contenido'], 'analitico.pdf', { type: 'application/pdf' });
    const inputsDocs = document.querySelectorAll('input[type="file"]');
    await user.upload(inputsDocs[0], dniFrente);
    await user.upload(inputsDocs[1], dniDorso);
    await user.upload(inputsDocs[2], analitico);
    await user.click(screen.getByRole('button', { name: 'Continuar →' }));

    const comprobante = new File(['contenido'], 'comprobante.pdf', { type: 'application/pdf' });
    const [comprobanteInput] = document.querySelectorAll('input[type="file"]');
    await user.upload(comprobanteInput, comprobante);
    await user.click(await screen.findByRole('button', { name: 'Confirmar solicitud' }));
    const modalConfirmar = await screen.findByRole('dialog');
    await user.click(within(modalConfirmar).getByRole('button', { name: 'Sí, confirmar' }));
    expect(await screen.findByText('Solicitud enviada')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Ir a mis solicitudes' }));

    // Revisar la tab Mis solicitudes
    await user.click(await screen.findByRole('tab', { name: 'Mis solicitudes' }));

    expect(await screen.findByText('Diseño Gráfico')).toBeInTheDocument();
    expect(screen.getByText('Pendiente')).toBeInTheDocument();
  });
});
