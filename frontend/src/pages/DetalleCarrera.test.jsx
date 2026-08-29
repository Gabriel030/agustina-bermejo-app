import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DetalleCarrera } from '@/pages/DetalleCarrera';

const API = 'http://localhost:8080';

function renderDetalle(id = '1') {
  return renderWithProviders(
    <Routes>
      <Route path="/catalogo/carreras/:id" element={<DetalleCarrera />} />
    </Routes>,
    { route: `/catalogo/carreras/${id}` },
  );
}

describe('DetalleCarrera', () => {
  it('muestra nombre, precios de las 2 cohortes y el plan de estudio', async () => {
    server.use(
      http.get(`${API}/programs/1/subjects`, () =>
        HttpResponse.json([{ id: 1, subjectName: 'Álgebra', curricularYear: 1, credits: 8 }]),
      ),
    );
    renderDetalle();

    expect(await screen.findByText('Carrera de prueba')).toBeInTheDocument();
    expect(screen.getByText('$ 50.000')).toBeInTheDocument();
    expect(screen.getByText('$ 25.000')).toBeInTheDocument();
    expect(screen.getByText('Álgebra')).toBeInTheDocument();
  });

  it('el flujo completo de inscripción pide cohorte, comprobante y confirma', async () => {
    // No se inspecciona el FormData recibido (request.formData() del lado del
    // handler queda colgado en este entorno jsdom/MSW) — se verifica el
    // resultado visible (pasos, confirmación), no el body crudo de la request.
    server.use(
      http.post(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json({ id: 5, status: 'PENDING' }, { status: 201 }),
      ),
    );
    const user = userEvent.setup();
    renderDetalle();

    await user.click(await screen.findByRole('button', { name: 'Inscribirme' }));
    const dialog = await screen.findByRole('dialog');

    // Paso cohorte
    await user.click(await within(dialog).findByText('Marzo 2026'));

    // Paso pago
    expect(await within(dialog).findByText('$ 50.000')).toBeInTheDocument();
    const file = new File(['contenido'], 'comprobante.pdf', { type: 'application/pdf' });
    const [fileInput] = dialog.querySelectorAll('input[type="file"]');
    await user.upload(fileInput, file);
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar inscripción' }));

    expect(await screen.findByText('Solicitud enviada')).toBeInTheDocument();
  });
});
