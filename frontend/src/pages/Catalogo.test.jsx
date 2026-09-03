import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { Catalogo } from '@/pages/Catalogo';

const API = 'http://localhost:8080';

function renderCatalogo(route = '/catalogo') {
  return renderWithProviders(
    <Routes>
      <Route path="/catalogo" element={<Catalogo />} />
    </Routes>,
    { route },
  );
}

describe('Catalogo', () => {
  it('arranca en la tab Carreras y lista las carreras activas', async () => {
    server.use(http.get(`${API}/programs`, () => HttpResponse.json([{ id: 1, name: 'Diseño Gráfico', totalYears: 3 }])));
    renderCatalogo();

    expect(await screen.findByText('Diseño Gráfico')).toBeInTheDocument();
  });

  it('cambia a la tab Cursos y lista los cursos activos', async () => {
    server.use(http.get(`${API}/courses`, () => HttpResponse.json([{ id: 1, name: 'Illustrator desde cero', durationMonths: 3 }])));
    const user = userEvent.setup();
    renderCatalogo();

    await user.click(screen.getByRole('tab', { name: 'Cursos' }));

    expect(await screen.findByText('Illustrator desde cero')).toBeInTheDocument();
  });

  it('cambia a la tab Mis solicitudes y combina carrera + curso en una sola bandeja', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 1, programName: 'Diseño Gráfico', status: 'PENDING' }]),
      ),
      http.get(`${API}/me/course-enrollment-applications`, () =>
        HttpResponse.json([{ id: 2, courseName: 'Illustrator desde cero', paymentStatus: 'PENDING_REVIEW' }]),
      ),
    );
    const user = userEvent.setup();
    renderCatalogo();

    await user.click(screen.getByRole('tab', { name: 'Mis solicitudes' }));

    expect(await screen.findByText('Diseño Gráfico')).toBeInTheDocument();
    expect(screen.getByText('Illustrator desde cero')).toBeInTheDocument();
    expect(screen.getByText('Pendiente')).toBeInTheDocument();
    expect(screen.getByText('En revisión')).toBeInTheDocument();
  });

  it('el buscador filtra las carreras por nombre', async () => {
    server.use(
      http.get(`${API}/programs`, () =>
        HttpResponse.json([
          { id: 1, name: 'Diseño Gráfico', totalYears: 3 },
          { id: 2, name: 'Administración de Empresas', totalYears: 4 },
        ]),
      ),
    );
    const user = userEvent.setup();
    renderCatalogo();

    await screen.findByText('Diseño Gráfico');
    await user.type(screen.getByPlaceholderText('Buscar carrera…'), 'Diseño');

    expect(screen.getByText('Diseño Gráfico')).toBeInTheDocument();
    expect(screen.queryByText('Administración de Empresas')).not.toBeInTheDocument();
  });
});
