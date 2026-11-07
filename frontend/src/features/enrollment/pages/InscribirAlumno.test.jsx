import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installFixedClock } from '@/test/fixedClock';
import { InscribirAlumno } from './InscribirAlumno';
import { studentFixture } from '@/test/fixtures/students';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: ['PRECEPTOR'], nombre: 'Paula', apellido: 'Ríos' } }),
}));

installFixedClock();

const API = 'http://localhost:8080';

const STUDENT = studentFixture({
  id: 9,
  nombre: 'Valentina',
  apellido: 'Paz',
  dni: '50111222',
  username: '50111222',
  email: 'vale@demo.test',
  status: 'ACTIVE',
});

const PROGRAM = { id: 3, name: 'Tecnicatura en Programación', status: 'ACTIVE', totalYears: 3 };
const COURSE = { id: 5, name: 'Inglés B1', status: 'ACTIVE', registrationOpen: true, full: false, termId: 7, schedule: 'Mar y jue' };
const SUBJECT = { id: 11, courseId: 5, name: 'Módulo 1', sortOrder: 1, status: 'ACTIVE', weeklyWorkload: 4 };

const section = (id, code, overrides = {}) => ({
  id,
  code,
  name: `Turno ${code}`,
  courseSubjectId: 11,
  termId: 7,
  capacity: 15,
  description: '',
  maxAbsencePercent: 25,
  schedule: 'Mar 17:00',
  shift: 'EVENING',
  status: 'ACTIVE',
  ...overrides,
});

const occupancy = (sectionId, enrolled, capacity, extra = {}) => ({
  sectionId,
  kind: 'COURSE',
  name: `Turno ${sectionId}`,
  capacity,
  enrolled,
  available: capacity == null ? null : capacity - enrolled,
  waitlisted: 0,
  ...extra,
});

function useCourseOffer({ sections, occupancies }) {
  server.use(
    http.get(`${API}/courses`, () => HttpResponse.json([COURSE])),
    http.get(`${API}/courses/5/subjects`, () => HttpResponse.json([SUBJECT])),
    http.get(`${API}/course-subjects/11/sections`, () => HttpResponse.json(sections)),
    http.get(`${API}/academic-offering-cycles`, () =>
      HttpResponse.json([
        {
          id: 40,
          courseId: 5,
          courseName: 'Inglés B1',
          createdAt: '2026-10-01T10:00:00',
          createdByName: 'Dir',
          decidedAt: null,
          decidedByName: 'Dir',
          programId: 0,
          programName: '',
          rejectionReason: null,
          status: 'APPROVED',
          termId: 7,
          termName: '2026',
        },
      ]),
    ),
    http.get(`${API}/academic-offering-cycles/40/occupancy`, () => HttpResponse.json({ cycleId: 40, sections: occupancies })),
  );
}

function renderPage() {
  server.use(
    http.get(`${API}/students/9`, () => HttpResponse.json(STUDENT)),
    http.get(`${API}/programs`, () => HttpResponse.json([PROGRAM])),
    http.get(`${API}/programs/3/cohorts`, () =>
      HttpResponse.json([
        { id: 1, programId: 3, label: 'Marzo 2027', ordenEnAnio: 1, cursadaStart: '2027-03-01', admissionStart: '2026-10-01', admissionEnd: '2026-12-01', admissionOpen: true },
        { id: 2, programId: 3, label: 'Agosto 2026', ordenEnAnio: 2, cursadaStart: '2026-08-01', admissionStart: '2026-05-01', admissionEnd: '2026-07-01', admissionOpen: false },
      ]),
    ),
  );
  return renderWithProviders(
    <Routes>
      <Route path="/:rol/alumnos/:id/inscribir" element={<InscribirAlumno />} />
      <Route path="/:rol/alumnos/:id" element={<div>Legajo destino</div>} />
      <Route path="/:rol/alumnos/nuevo" element={<div>Alta destino</div>} />
    </Routes>,
    { route: '/preceptor/alumnos/9/inscribir' },
  );
}

describe('InscribirAlumno', () => {
  it('enrolls the student in a career with an open cohort and shows the success screen', async () => {
    let body;
    server.use(
      http.post(`${API}/students/9/program-enrollments`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(
          { id: 1, studentId: 9, programId: 3, programName: PROGRAM.name, status: 'ACTIVE', enrollmentDate: '2026-11-05T12:00:00', completionDate: '2027-12-01T00:00:00' },
          { status: 201 },
        );
      }),
    );
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Inscribir a Valentina Paz' })).toBeInTheDocument();
    const review = screen.getByRole('button', { name: 'Revisar inscripción' });
    expect(review).toBeDisabled();

    await user.click(await screen.findByRole('radio', { name: /Tecnicatura en Programación/ }));
    expect(await screen.findByRole('radio', { name: /Marzo 2027/ })).toBeInTheDocument();
    expect(screen.queryByRole('radio', { name: /Agosto 2026/ })).not.toBeInTheDocument();
    expect(review).toBeDisabled();

    await user.click(screen.getByRole('radio', { name: /Marzo 2027/ }));
    expect(review).toBeEnabled();
    await user.click(review);

    const table = await screen.findByRole('group', { name: 'Inscripción' });
    expect(within(table).getByText('Sin inscripción en esta carrera')).toBeInTheDocument();
    expect(within(table).getByText('+1 inscripción')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Confirmar inscripción' }));

    expect(await screen.findByText('Valentina Paz quedó inscripto/a')).toBeInTheDocument();
    expect(body).toEqual({ programId: 3, cohortId: 1 });
    expect(screen.getByText(/registrada por Paula Ríos/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Ir al legajo' }));
    expect(await screen.findByText('Legajo destino')).toBeInTheDocument();
  });

  it('goes to the new-student form from the success screen', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('radio', { name: /Tecnicatura/ }));
    await user.click(await screen.findByRole('radio', { name: /Marzo 2027/ }));
    await user.click(screen.getByRole('button', { name: 'Revisar inscripción' }));
    await user.click(await screen.findByRole('button', { name: 'Confirmar inscripción' }));
    await user.click(await screen.findByRole('button', { name: 'Dar de alta a otro alumno' }));
    expect(await screen.findByText('Alta destino')).toBeInTheDocument();
  });

  it('asks for a section per module, disables full sections and sends the overrides', async () => {
    useCourseOffer({
      sections: [section(21, 'A'), section(22, 'B'), section(23, 'C')],
      occupancies: [occupancy(21, 14, 15), occupancy(22, 15, 15), occupancy(23, 3, 15)],
    });
    let body;
    server.use(
      http.post(`${API}/students/9/course-enrollments`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(
          { id: 2, studentId: 9, courseId: 5, courseName: COURSE.name, status: 'ENROLLED', enrollmentDate: '2026-11-05T12:00:00', completionDate: '2027-12-01T00:00:00' },
          { status: 201 },
        );
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('tab', { name: 'Curso' }));
    await user.click(await screen.findByRole('radio', { name: /Inglés B1/ }));

    const full = await screen.findByRole('radio', { name: /Comisión B/ });
    expect(full).toBeDisabled();
    expect(screen.getByText('Lleno')).toBeInTheDocument();
    expect(screen.getByText('Últimos cupos')).toBeInTheDocument();
    const review = screen.getByRole('button', { name: 'Revisar inscripción' });
    expect(review).toBeDisabled();

    await user.click(screen.getByRole('radio', { name: /Comisión C/ }));
    expect(review).toBeEnabled();
    await user.click(review);

    const seats = await screen.findByRole('group', { name: 'Cupo · Módulo 1' });
    expect(within(seats).getByText('3/15')).toBeInTheDocument();
    expect(within(seats).getByText('4/15')).toBeInTheDocument();
    expect(within(seats).getByText('+1 ocupado, 11 libres')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Confirmar inscripción' }));

    expect(await screen.findByText('Valentina Paz quedó inscripto/a')).toBeInTheDocument();
    expect(body).toEqual({ courseId: 5, sectionOverrides: { 11: 23 } });
  });

  it('treats a null capacity as no seat limit and sends empty overrides for a single section', async () => {
    useCourseOffer({
      sections: [section(21, 'A', { capacity: 0 })],
      occupancies: [occupancy(21, 4, null)],
    });
    let body;
    server.use(
      http.post(`${API}/students/9/course-enrollments`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(
          { id: 2, studentId: 9, courseId: 5, courseName: COURSE.name, status: 'ENROLLED', enrollmentDate: '2026-11-05T12:00:00', completionDate: '2027-12-01T00:00:00' },
          { status: 201 },
        );
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('tab', { name: 'Curso' }));
    await user.click(await screen.findByRole('radio', { name: /Inglés B1/ }));
    const review = await screen.findByRole('button', { name: 'Revisar inscripción' });
    await vi.waitFor(() => expect(review).toBeEnabled());
    await user.click(review);

    const seats = await screen.findByRole('group', { name: 'Cupo · Módulo 1' });
    expect(within(seats).getAllByText('Sin límite de cupo').length).toBeGreaterThan(0);
    await user.click(screen.getByRole('button', { name: 'Confirmar inscripción' }));

    expect(await screen.findByText('Valentina Paz quedó inscripto/a')).toBeInTheDocument();
    expect(body).toEqual({ courseId: 5, sectionOverrides: {} });
  });

  it('shows the full-section banner and sends the user back to choose again on a 409', async () => {
    useCourseOffer({
      sections: [section(21, 'A'), section(22, 'B')],
      occupancies: [occupancy(21, 2, 15), occupancy(22, 3, 15)],
    });
    server.use(http.post(`${API}/students/9/course-enrollments`, () => new HttpResponse(null, { status: 409 })));
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('tab', { name: 'Curso' }));
    await user.click(await screen.findByRole('radio', { name: /Inglés B1/ }));
    await user.click(await screen.findByRole('radio', { name: /Comisión A/ }));
    await user.click(screen.getByRole('button', { name: 'Revisar inscripción' }));
    await user.click(await screen.findByRole('button', { name: 'Confirmar inscripción' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('La comisión se llenó mientras elegías');
    expect(screen.getByRole('button', { name: 'Revisar inscripción' })).toBeDisabled();
  });

  it('tells the staff member the student is on the waitlist when the server answers WAITLISTED', async () => {
    useCourseOffer({ sections: [section(21, 'A')], occupancies: [occupancy(21, 15, 15)] });
    server.use(
      http.post(`${API}/students/9/course-enrollments`, () =>
        HttpResponse.json(
          { id: 2, studentId: 9, courseId: 5, courseName: COURSE.name, status: 'WAITLISTED', enrollmentDate: '2026-11-05T12:00:00', completionDate: '2027-12-01T00:00:00' },
          { status: 201 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('tab', { name: 'Curso' }));
    await user.click(await screen.findByRole('radio', { name: /Inglés B1/ }));
    expect(await screen.findByText(/queda en lista de espera/)).toBeInTheDocument();
    const review = screen.getByRole('button', { name: 'Revisar inscripción' });
    await vi.waitFor(() => expect(review).toBeEnabled());
    await user.click(review);
    await user.click(await screen.findByRole('button', { name: 'Confirmar inscripción' }));

    expect(await screen.findByText('Valentina Paz quedó en lista de espera')).toBeInTheDocument();
  });

  it('shows a generic banner when the enrollment fails with a server error', async () => {
    server.use(http.post(`${API}/students/9/program-enrollments`, () => new HttpResponse(null, { status: 500 })));
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('radio', { name: /Tecnicatura/ }));
    await user.click(await screen.findByRole('radio', { name: /Marzo 2027/ }));
    await user.click(screen.getByRole('button', { name: 'Revisar inscripción' }));
    await user.click(await screen.findByRole('button', { name: 'Confirmar inscripción' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos registrar la inscripción');
  });

  it('shows the empty state when there are no courses open and offers to switch tab', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('tab', { name: 'Curso' }));
    expect(await screen.findByText('No hay cursos con inscripción abierta')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Ver carreras' }));
    expect(await screen.findByRole('radio', { name: /Tecnicatura/ })).toBeInTheDocument();
  });

  it('shows the load error with a retry when the offer cannot be loaded', async () => {
    let calls = 0;
    const user = userEvent.setup();
    renderPage();
    server.use(
      http.get(`${API}/programs`, () => {
        calls += 1;
        return calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json([PROGRAM]);
      }),
    );
    expect(await screen.findByText('No pudimos cargar las carreras')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('radio', { name: /Tecnicatura/ })).toBeInTheDocument();
  });
});
