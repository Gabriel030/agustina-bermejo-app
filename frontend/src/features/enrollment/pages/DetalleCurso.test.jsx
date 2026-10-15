import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DetalleCurso } from '@/features/enrollment/pages/DetalleCurso';
import { InscripcionCurso } from '@/features/enrollment/pages/InscripcionCurso';

const API = 'http://localhost:8080';

function renderDetalle(id = '1') {
  return renderWithProviders(
    <Routes>
      <Route path="/catalogo/cursos/:id" element={<DetalleCurso />} />
    </Routes>,
    { route: `/catalogo/cursos/${id}` },
  );
}

function renderConWizard(id = '1') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/catalogo/cursos/${id}`]}>
        <AuthProvider>
          <Routes>
            <Route path="/catalogo/cursos/:id" element={<DetalleCurso />} />
            <Route path="/catalogo/cursos/:id/inscribirme" element={<InscripcionCurso />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('DetalleCurso', () => {
  it('muestra nombre, cuota mensual y temario', async () => {
    server.use(
      http.get(`${API}/courses/1/subjects`, () => HttpResponse.json([{ id: 1, name: 'Módulo 1 — Fundamentos' }])),
    );
    renderDetalle();

    expect(await screen.findByText('Curso de prueba')).toBeInTheDocument();
    expect(screen.getByText('$ 8.000,00')).toBeInTheDocument();
    expect(screen.getByText('Módulo 1 — Fundamentos')).toBeInTheDocument();
  });

  // Auditoría 2026-09-04 (calidad de tests): el handler devolvía siempre 201 sin mirar si
  // realmente llegó un comprobante adjunto — un bug que dejara el FormData sin el archivo
  // (ej. el estado del wizard no capturó el archivo subido) hubiera pasado inadvertido. Se
  // espía fetch en vez de leer request.formData() dentro del handler de MSW: en este entorno
  // (jsdom + MSW node) leer el body de una request cuyo FormData incluye un File real cuelga la
  // promesa para siempre (ver el comentario equivalente en registro.integration.test.jsx).
  it('el wizard de curso arranca en documentación (sin cohorte) y no pide documentos', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    server.use(
      http.post(`${API}/me/course-enrollments`, () =>
        HttpResponse.json({ id: 7, paymentStatus: 'PENDING_REVIEW' }, { status: 201 }),
      ),
    );
    const user = userEvent.setup();
    renderConWizard();

    await user.click(await screen.findByRole('button', { name: 'Inscribirme' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByText('Cohorte')).not.toBeInTheDocument();

    await user.type(screen.getByPlaceholderText('tu@email.com'), 'alumno@example.com');
    await user.type(screen.getByLabelText('Número de documento'), '30111222');
    const [, , , , calleInput, numeroInput, , ciudadInput] = screen.getAllByRole('textbox');
    await user.type(calleInput, 'Calle Falsa');
    await user.type(numeroInput, '123');
    await user.type(ciudadInput, 'Ciudad');
    await user.click(screen.getByRole('button', { name: 'Continuar →' }));

    // Sin paso de documentos: se va directo al monto a pagar.
    expect(await screen.findByText('$ 8.000,00')).toBeInTheDocument();

    const file = new File(['contenido'], 'comprobante.pdf', { type: 'application/pdf' });
    const [fileInput] = document.querySelectorAll('input[type="file"]');
    await user.upload(fileInput, file);
    await user.click(await screen.findByRole('button', { name: 'Confirmar solicitud' }));

    const modal = await screen.findByRole('dialog');
    await user.click(within(modal).getByRole('button', { name: 'Sí, confirmar' }));

    expect(await screen.findByText('Solicitud enviada')).toBeInTheDocument();

    const [, inscribirmeOpts] = fetchSpy.mock.calls.find(
      ([url, opts]) => String(url).endsWith('/me/course-enrollments') && opts?.method === 'POST',
    );
    expect(inscribirmeOpts.body.get('courseId')).toBe('1');
    expect(inscribirmeOpts.body.get('comprobante').name).toBe('comprobante.pdf');
  });

  it('si el curso tiene la inscripción cerrada lo dice en vez de un error genérico', async () => {
    server.use(
      http.post(`${API}/me/course-enrollments`, () =>
        HttpResponse.json({ code: 'ADMISSION_WINDOW_CLOSED' }, { status: 400 }),
      ),
    );
    const user = userEvent.setup();
    renderConWizard();

    await user.click(await screen.findByRole('button', { name: 'Inscribirme' }));
    await user.type(screen.getByPlaceholderText('tu@email.com'), 'alumno@example.com');
    await user.type(screen.getByLabelText('Número de documento'), '30111222');
    const [, , , , calleInput, numeroInput, , ciudadInput] = screen.getAllByRole('textbox');
    await user.type(calleInput, 'Calle Falsa');
    await user.type(numeroInput, '123');
    await user.type(ciudadInput, 'Ciudad');
    await user.click(screen.getByRole('button', { name: 'Continuar →' }));
    const [fileInput] = await waitForFileInput();
    await user.upload(fileInput, new File(['contenido'], 'comprobante.pdf', { type: 'application/pdf' }));
    await user.click(await screen.findByRole('button', { name: 'Confirmar solicitud' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Sí, confirmar' }));

    expect(await screen.findByText('La inscripción a este curso está cerrada.')).toBeInTheDocument();
  });

  it('disables Inscribirme and explains why when the registration window is closed', async () => {
    server.use(
      http.get(`${API}/courses/1`, () =>
        HttpResponse.json({ id: 1, name: 'Curso cerrado', durationMonths: 3, registrationOpen: false, full: false }),
      ),
    );
    renderDetalle();

    expect(await screen.findByRole('button', { name: 'Inscribirme' })).toBeDisabled();
    expect(screen.getByText('La inscripción a este curso está cerrada.')).toBeInTheDocument();
  });

  it('warns that you will be waitlisted when the course is full', async () => {
    server.use(
      http.get(`${API}/courses/1`, () =>
        HttpResponse.json({ id: 1, name: 'Curso lleno', durationMonths: 3, registrationOpen: true, full: true }),
      ),
    );
    renderDetalle();

    expect(await screen.findByRole('button', { name: 'Inscribirme' })).toBeEnabled();
    expect(screen.getByText('El cupo está completo: si te inscribís, quedás en lista de espera.')).toBeInTheDocument();
  });

  it('shows the teaching mode of the course', async () => {
    server.use(
      http.get(`${API}/courses/1`, () =>
        HttpResponse.json({ id: 1, name: 'Inglés', durationMonths: 10, teachingMode: 'ONLINE', registrationOpen: true, full: false }),
      ),
    );
    renderDetalle();

    expect(await screen.findByText('10 meses · Virtual')).toBeInTheDocument();
  });

  it('says the course does not exist and offers a way back when the API answers 404', async () => {
    server.use(http.get(`${API}/courses/9999`, () => new HttpResponse(null, { status: 404 })));
    renderDetalle('9999');

    expect(await screen.findByText('Este curso no existe o ya no está disponible.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '← Catálogo' })).toBeInTheDocument();
  });

  it('the payment step shows the bank account the applicant has to transfer to', async () => {
    server.use(
      http.get(`${API}/payment-config`, () =>
        HttpResponse.json({
          bankAlias: 'isab.pagos.cba',
          bankCbu: '0070123430004567890123',
          accountHolder: 'Instituto Superior Agustina Bermejo',
          mpEnabled: false,
        }),
      ),
    );
    const user = userEvent.setup();
    renderConWizard();

    await user.click(await screen.findByRole('button', { name: 'Inscribirme' }));
    await user.type(screen.getByPlaceholderText('tu@email.com'), 'alumno@example.com');
    await user.type(screen.getByLabelText('Número de documento'), '30111222');
    const [, , , , calleInput, numeroInput, , ciudadInput] = screen.getAllByRole('textbox');
    await user.type(calleInput, 'Calle Falsa');
    await user.type(numeroInput, '123');
    await user.type(ciudadInput, 'Ciudad');
    await user.click(screen.getByRole('button', { name: 'Continuar →' }));

    expect(await screen.findByText('0070123430004567890123')).toBeInTheDocument();
    expect(screen.getByText('isab.pagos.cba')).toBeInTheDocument();
    expect(screen.getByText('Instituto Superior Agustina Bermejo')).toBeInTheDocument();
  });
});

async function waitForFileInput() {
  await screen.findByText('$ 8.000,00');
  return document.querySelectorAll('input[type="file"]');
}
