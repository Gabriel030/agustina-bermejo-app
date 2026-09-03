import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DetalleCurso } from '@/pages/DetalleCurso';
import { InscripcionCurso } from '@/pages/InscripcionCurso';

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
    expect(screen.getByText('$ 8.000')).toBeInTheDocument();
    expect(screen.getByText('Módulo 1 — Fundamentos')).toBeInTheDocument();
  });

  it('el wizard de curso arranca en documentación (sin cohorte) y no pide documentos', async () => {
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
    await user.type(screen.getByPlaceholderText('DNI sin puntos'), '30111222');
    const [, , , , calleInput, numeroInput, , ciudadInput] = screen.getAllByRole('textbox');
    await user.type(calleInput, 'Calle Falsa');
    await user.type(numeroInput, '123');
    await user.type(ciudadInput, 'Ciudad');
    await user.click(screen.getByRole('button', { name: 'Continuar →' }));

    // Sin paso de documentos: se va directo al monto a pagar.
    expect(await screen.findByText('$ 8.000')).toBeInTheDocument();

    const file = new File(['contenido'], 'comprobante.pdf', { type: 'application/pdf' });
    const [fileInput] = document.querySelectorAll('input[type="file"]');
    await user.upload(fileInput, file);
    await user.click(await screen.findByRole('button', { name: 'Confirmar solicitud' }));

    const modal = await screen.findByRole('dialog');
    await user.click(within(modal).getByRole('button', { name: 'Sí, confirmar' }));

    expect(await screen.findByText('Solicitud enviada')).toBeInTheDocument();
  });
});
