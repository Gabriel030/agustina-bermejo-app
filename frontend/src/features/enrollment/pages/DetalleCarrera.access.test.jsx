import { afterEach, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DetalleCarrera } from './DetalleCarrera';
import { profileFixture } from '@/test/fixtures/profile';

const API = 'http://localhost:8080';

function signInAs(roles) {
  localStorage.setItem('refreshToken', 'refresh-1');
  server.use(
    http.post(`${API}/auth/refresh`, () =>
      HttpResponse.json({ accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 900 }),
    ),
    http.get(`${API}/me`, () => HttpResponse.json(profileFixture({ roles }))),
  );
}

function renderDetail() {
  return renderWithProviders(
    <Routes>
      <Route path="/catalogo/carreras/:id" element={<DetalleCarrera />} />
    </Routes>,
    { route: '/catalogo/carreras/1', withAuth: true },
  );
}

afterEach(() => localStorage.clear());

describe('DetalleCarrera access by role and status', () => {
  it('planOmitsInactiveSubjects', async () => {
    let requestedStatus = null;
    server.use(
      http.get(`${API}/programs/1/subjects`, ({ request }) => {
        requestedStatus = new URL(request.url).searchParams.get('status');
        return HttpResponse.json([{ id: 1, subjectName: 'Álgebra', curricularYear: 1, credits: 8 }]);
      }),
    );
    renderDetail();

    expect(await screen.findByText('Álgebra')).toBeInTheDocument();
    expect(requestedStatus).toBe('ACTIVE');
  });

  it('electiveSubjectsAreLabeled', async () => {
    server.use(
      http.get(`${API}/programs/1/subjects`, () =>
        HttpResponse.json([
          { id: 1, subjectName: 'Álgebra', curricularYear: 1, credits: 8, category: 'MANDATORY' },
          { id: 2, subjectName: 'Taller de arte', curricularYear: 1, credits: 4, category: 'ELECTIVE' },
        ]),
      ),
    );
    renderDetail();

    expect(await screen.findByText('Taller de arte')).toBeInTheDocument();
    expect(screen.getAllByText('Optativa')).toHaveLength(1);
  });

  it('staffRoleSeesPreviewInsteadOfEnrollButton', async () => {
    signInAs(['DIRECTOR']);
    renderDetail();

    expect(await screen.findByText('Vista previa del catálogo')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Inscribirme' })).not.toBeInTheDocument();
  });

  it.each([
    ['inactive', { status: 'INACTIVE' }],
    ['hidden', { visibleInCatalog: false }],
  ])('staff%sProgramShowsHiddenNoticeWithFullDetail', async (_label, override) => {
    signInAs(['DIRECTOR']);
    server.use(
      http.get(`${API}/programs/1`, () =>
        HttpResponse.json({ id: 1, name: 'Carrera de prueba', totalYears: 3, status: 'ACTIVE', visibleInCatalog: true, ...override }),
      ),
    );
    renderDetail();

    expect(await screen.findByText('Programa no visible en el catálogo')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Carrera de prueba' })).toBeInTheDocument();
  });

  it('staffListedProgramShowsNoHiddenNotice', async () => {
    signInAs(['DIRECTOR']);
    renderDetail();

    expect(await screen.findByText('Vista previa del catálogo')).toBeInTheDocument();
    expect(screen.queryByText('Programa no visible en el catálogo')).not.toBeInTheDocument();
  });

  it('studentWithPendingApplicationSeesLinkToIt', async () => {
    signInAs(['ALUMNO']);
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 7, programId: 1, status: 'PENDING', programName: 'Carrera de prueba' }]),
      ),
    );
    renderDetail();

    expect(await screen.findByText('Ya tenés una solicitud pendiente para esta carrera.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver solicitud' })).toHaveAttribute('href', '/mis-solicitudes/7');
    expect(screen.queryByRole('button', { name: 'Inscribirme' })).not.toBeInTheDocument();
  });

  it('studentAlreadyEnrolledSeesLinkToMyPrograms', async () => {
    signInAs(['ALUMNO']);
    server.use(
      http.get(`${API}/me/program-enrollments`, () =>
        HttpResponse.json([
          { id: 3, studentId: 1, programId: 1, programName: 'Carrera de prueba', status: 'ENROLLED', enrollmentDate: '2026-03-02T10:00:00', completionDate: '2027-12-01T10:00:00' },
        ]),
      ),
    );
    renderDetail();

    expect(await screen.findByText('Ya cursás esta carrera.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a Mis carreras' })).toHaveAttribute('href', '/alumno/mis-carreras');
  });

  it('applicantWithoutRolesStillSeesEnrollButton', async () => {
    renderDetail();

    expect(await screen.findByRole('button', { name: 'Inscribirme' })).toBeInTheDocument();
  });

  it.each([
    ['inactive', { status: 'INACTIVE' }],
    ['hidden', { visibleInCatalog: false }],
  ])('%sProgramShowsUnavailableToStudents', async (_label, override) => {
    server.use(
      http.get(`${API}/programs/1`, () =>
        HttpResponse.json({ id: 1, name: 'Carrera de prueba', totalYears: 3, status: 'ACTIVE', visibleInCatalog: true, ...override }),
      ),
    );
    renderDetail();

    expect(await screen.findByText('No encontramos lo que buscabas')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Inscribirme' })).not.toBeInTheDocument();
    expect(screen.queryByText('Programa no visible en el catálogo')).not.toBeInTheDocument();
  });
});
