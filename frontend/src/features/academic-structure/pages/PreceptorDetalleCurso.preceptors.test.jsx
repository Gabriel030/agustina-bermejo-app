import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PreceptorDetalleCurso } from './PreceptorDetalleCurso';

const API = 'http://localhost:8080';

function mockCourseOffer(sectionExtra = {}) {
  server.use(
    http.get(`${API}/courses/10`, () => HttpResponse.json({ id: 10, name: 'Primeros auxilios', durationMonths: 4 })),
    http.get(`${API}/academic-offering-cycles`, () =>
      HttpResponse.json([
        { id: 22, status: 'DRAFT', termId: 7, termName: 'Ciclo lectivo 2027', courseId: 10, createdByName: 'Preceptor Demo' },
      ]),
    ),
    http.get(`${API}/academic-offering-cycles/22/sections`, () =>
      HttpResponse.json([
        { id: 61, name: 'Comisión A', subjectName: 'Primeros auxilios', status: 'ACTIVE', capacity: 20, instructors: [], slots: [], pendingApproval: false, ...sectionExtra },
      ]),
    ),
  );
}

function mockReplaceCourseSection(requests, status = 200) {
  server.use(
    http.put(`${API}/preceptor-assignments/course-sections/:id`, async ({ request, params }) => {
      requests.push({ id: params.id, body: await request.json() });
      if (status !== 200) return HttpResponse.json(errorBody('INTERNAL_ERROR'), { status });
      return HttpResponse.json([]);
    }),
  );
}

function renderDetail(role) {
  return renderWithProviders(
    <Routes>
      <Route path="/:role/oferta-academica/cursos/:cursoId" element={<PreceptorDetalleCurso />} />
    </Routes>,
    { route: `/${role}/oferta-academica/cursos/10` },
  );
}

describe('course section preceptors editing', () => {
  it('shows the course section without preceptor and offers to assign one to the director', async () => {
    mockCourseOffer();
    renderDetail('director');

    expect(await screen.findByText('Sin preceptor')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Asignar preceptor' })).toBeEnabled();
  });

  it('does not offer the editor to the preceptor role', async () => {
    mockCourseOffer();
    renderDetail('preceptor');

    expect(await screen.findByText('Comisión A')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Asignar preceptor' })).not.toBeInTheDocument();
  });

  it('saves the chosen preceptors through the course-sections endpoint', async () => {
    const requests = [];
    mockCourseOffer();
    mockReplaceCourseSection(requests);
    const user = userEvent.setup();
    renderDetail('director');

    await user.click(await screen.findByRole('button', { name: 'Asignar preceptor' }));
    await user.click(screen.getByRole('button', { name: 'Buscar preceptor' }));
    await user.click(await screen.findByRole('option', { name: /Marcela Quiroga/ }));
    await user.click(screen.getByRole('button', { name: 'Listo' }));
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(requests).toEqual([{ id: '61', body: { personIds: [31] } }]));
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Guardar' })).not.toBeInTheDocument());
  });

  it('keeps the changes and offers a retry when saving fails', async () => {
    const requests = [];
    mockCourseOffer({ preceptors: [{ assignmentId: 5, personId: 31, personName: 'Marcela Quiroga', pendingRequestCount: 0 }] });
    mockReplaceCourseSection(requests, 500);
    const user = userEvent.setup();
    renderDetail('director');

    await user.click(await screen.findByRole('button', { name: 'Preceptores' }));
    await user.click(screen.getByRole('button', { name: /Quitar/ }));
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByText('No pudimos guardar.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
