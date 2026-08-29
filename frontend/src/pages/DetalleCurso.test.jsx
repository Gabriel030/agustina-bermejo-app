import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DetalleCurso } from '@/pages/DetalleCurso';

const API = 'http://localhost:8080';

function renderDetalle(id = '1') {
  return renderWithProviders(
    <Routes>
      <Route path="/catalogo/cursos/:id" element={<DetalleCurso />} />
    </Routes>,
    { route: `/catalogo/cursos/${id}` },
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

  it('el wizard de curso arranca directo en el paso de pago (sin cohorte)', async () => {
    server.use(
      http.post(`${API}/me/course-enrollments`, () =>
        HttpResponse.json({ id: 7, paymentStatus: 'PENDING_REVIEW' }, { status: 201 }),
      ),
    );
    const user = userEvent.setup();
    renderDetalle();

    await user.click(await screen.findByRole('button', { name: 'Inscribirme' }));
    const dialog = await screen.findByRole('dialog');

    // Sin paso de cohorte: el monto a pagar ya aparece directo.
    expect(await within(dialog).findByText('$ 8.000')).toBeInTheDocument();

    const file = new File(['contenido'], 'comprobante.pdf', { type: 'application/pdf' });
    const [fileInput] = dialog.querySelectorAll('input[type="file"]');
    await user.upload(fileInput, file);
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar inscripción' }));

    expect(await screen.findByText('Solicitud enviada')).toBeInTheDocument();
  });
});
