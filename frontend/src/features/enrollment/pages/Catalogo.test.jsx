import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { Catalogo } from '@/features/enrollment/pages/Catalogo';

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
    expect(screen.getAllByText('En revisión')).toHaveLength(2);
  });

  it('muestra en lista de espera una solicitud de curso aprobada cuyo cupo estaba lleno', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () => HttpResponse.json([])),
      http.get(`${API}/me/course-enrollment-applications`, () =>
        HttpResponse.json([
          { id: 3, courseName: 'Primeros Auxilios', paymentStatus: 'APPROVED', courseEnrollmentId: 25, courseEnrollmentStatus: 'WAITLISTED' },
        ]),
      ),
    );
    const user = userEvent.setup();
    renderCatalogo();

    await user.click(screen.getByRole('tab', { name: 'Mis solicitudes' }));

    expect(await screen.findByText('Primeros Auxilios')).toBeInTheDocument();
    expect(screen.getByText('En lista de espera')).toBeInTheDocument();
    expect(screen.queryByText('Aprobada')).not.toBeInTheDocument();
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

  it('only lists the programs and courses published in the catalog', async () => {
    const programs = [
      { id: 1, name: 'Carrera publicada', totalYears: 3, visibleInCatalog: true },
      { id: 2, name: 'Carrera oculta', totalYears: 3, visibleInCatalog: false },
    ];
    const courses = [
      { id: 1, name: 'Curso publicado', durationMonths: 3, visibleInCatalog: true },
      { id: 2, name: 'Curso oculto', durationMonths: 3, visibleInCatalog: false },
    ];
    const onlyPublished = (items, request) =>
      new URL(request.url).searchParams.get('visibleInCatalog') === 'true' ? items.filter((item) => item.visibleInCatalog) : items;
    server.use(
      http.get(`${API}/programs`, ({ request }) => HttpResponse.json(onlyPublished(programs, request))),
      http.get(`${API}/courses`, ({ request }) => HttpResponse.json(onlyPublished(courses, request))),
    );
    const user = userEvent.setup();
    renderCatalogo();

    expect(await screen.findByText('Carrera publicada')).toBeInTheDocument();
    expect(screen.queryByText('Carrera oculta')).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Cursos' }));

    expect(await screen.findByText('Curso publicado')).toBeInTheDocument();
    expect(screen.queryByText('Curso oculto')).not.toBeInTheDocument();
  });

  it('search ignores accents and case', async () => {
    server.use(
      http.get(`${API}/programs`, () =>
        HttpResponse.json([
          { id: 1, name: 'Profesorado de Educación Inicial', totalYears: 4 },
          { id: 2, name: 'Tecnicatura en Enfermería', totalYears: 3 },
        ]),
      ),
    );
    renderCatalogo('/catalogo?tab=carreras&search=EDUCACION');

    expect(await screen.findByText('Profesorado de Educación Inicial')).toBeInTheDocument();
    expect(screen.queryByText('Tecnicatura en Enfermería')).not.toBeInTheDocument();
  });

  it('falls back to the last page when the page in the URL is out of range', async () => {
    server.use(
      http.get(`${API}/programs`, () =>
        HttpResponse.json(Array.from({ length: 8 }, (_, i) => ({ id: i + 1, name: `Carrera ${i + 1}`, totalYears: 3 }))),
      ),
    );
    renderCatalogo('/catalogo?tab=carreras&page=99');

    expect(await screen.findByText('Carrera 7')).toBeInTheDocument();
    expect(screen.getByText('Página 2 de 2')).toBeInTheDocument();
  });

  it('falls back to the Carreras tab when the tab in the URL is unknown', async () => {
    server.use(http.get(`${API}/programs`, () => HttpResponse.json([{ id: 1, name: 'Diseño Gráfico', totalYears: 3 }])));
    renderCatalogo('/catalogo?tab=xxx');

    expect(await screen.findByText('Diseño Gráfico')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Carreras' })).toHaveAttribute('aria-selected', 'true');
  });

  it('shows the teaching mode on each course card', async () => {
    server.use(
      http.get(`${API}/courses`, () =>
        HttpResponse.json([{ id: 1, name: 'Primeros Auxilios', durationMonths: 3, teachingMode: 'IN_PERSON' }]),
      ),
    );
    renderCatalogo('/catalogo?tab=cursos');

    expect(await screen.findByText('3 meses · Presencial')).toBeInTheDocument();
  });

  it('does not claim an official title on program cards', async () => {
    server.use(http.get(`${API}/programs`, () => HttpResponse.json([{ id: 1, name: 'Diseño Gráfico', totalYears: 3 }])));
    renderCatalogo();

    expect(await screen.findByText('3 años')).toBeInTheDocument();
    expect(screen.queryByText(/Título oficial/)).not.toBeInTheDocument();
  });
});
