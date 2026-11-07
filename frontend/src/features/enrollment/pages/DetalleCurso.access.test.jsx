import { afterEach, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DetalleCurso } from './DetalleCurso';

const API = 'http://localhost:8080';

function signInAs(roles) {
  localStorage.setItem('refreshToken', 'refresh-1');
  server.use(
    http.post(`${API}/auth/refresh`, () =>
      HttpResponse.json({ accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 900 }),
    ),
    http.get(`${API}/me`, () => HttpResponse.json({ id: 1, username: 'user', roles })),
  );
}

function renderDetail() {
  return renderWithProviders(
    <Routes>
      <Route path="/catalogo/cursos/:id" element={<DetalleCurso />} />
    </Routes>,
    { route: '/catalogo/cursos/1', withAuth: true },
  );
}

afterEach(() => localStorage.clear());

describe('DetalleCurso access by role and status', () => {
  it('syllabusRequestsOnlyActiveModules', async () => {
    let requestedStatus = null;
    server.use(
      http.get(`${API}/courses/1/subjects`, ({ request }) => {
        requestedStatus = new URL(request.url).searchParams.get('status');
        return HttpResponse.json([{ id: 1, courseId: 1, name: 'Módulo 1', sortOrder: 1, status: 'ACTIVE', weeklyWorkload: 1 }]);
      }),
    );
    renderDetail();

    expect(await screen.findByText('Módulo 1')).toBeInTheDocument();
    expect(requestedStatus).toBe('ACTIVE');
  });

  it('staffRoleSeesPreviewInsteadOfEnrollButton', async () => {
    signInAs(['PRECEPTOR']);
    renderDetail();

    expect(await screen.findByText('Vista previa del catálogo')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Inscribirme' })).not.toBeInTheDocument();
  });

  it.each([
    ['inactive', { status: 'INACTIVE' }],
    ['hidden', { visibleInCatalog: false }],
  ])('staff%sCourseShowsHiddenNoticeWithFullDetail', async (_label, override) => {
    signInAs(['PRECEPTOR']);
    server.use(
      http.get(`${API}/courses/1`, () =>
        HttpResponse.json({ id: 1, name: 'Curso de prueba', status: 'ACTIVE', visibleInCatalog: true, registrationOpen: true, ...override }),
      ),
    );
    renderDetail();

    expect(await screen.findByText('Curso no visible en el catálogo')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Curso de prueba' })).toBeInTheDocument();
  });

  it('studentAlreadyEnrolledSeesLinkToMyCourses', async () => {
    signInAs(['ALUMNO']);
    server.use(
      http.get(`${API}/me/course-enrollments`, () =>
        HttpResponse.json([{ id: 4, courseId: 1, status: 'ENROLLED' }]),
      ),
    );
    renderDetail();

    expect(await screen.findByText('Ya estás inscripto en este curso.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a Mis cursos' })).toHaveAttribute('href', '/alumno/mis-cursos');
  });

  it('studentOnWaitlistSeesWaitlistNotice', async () => {
    signInAs(['ALUMNO']);
    server.use(
      http.get(`${API}/me/course-enrollments`, () =>
        HttpResponse.json([{ id: 4, courseId: 1, status: 'WAITLISTED' }]),
      ),
    );
    renderDetail();

    expect(await screen.findByText('Estás en la lista de espera de este curso.')).toBeInTheDocument();
  });

  it('inactiveCourseShowsUnavailableToStudents', async () => {
    server.use(
      http.get(`${API}/courses/1`, () =>
        HttpResponse.json({ id: 1, name: 'Curso de prueba', status: 'INACTIVE', visibleInCatalog: true, registrationOpen: true }),
      ),
    );
    renderDetail();

    expect(await screen.findByText('No encontramos lo que buscabas')).toBeInTheDocument();
    expect(screen.queryByText('Curso no visible en el catálogo')).not.toBeInTheDocument();
  });
});
