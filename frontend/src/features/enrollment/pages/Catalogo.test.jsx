import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installFixedClock } from '@/test/fixedClock';
import { errorBody } from '@/test/fixtures/errors';
import { Catalogo } from '@/features/enrollment/pages/Catalogo';

const authState = vi.hoisted(() => ({ roles: ['ALUMNO'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ roles: authState.roles }),
}));

const API = 'http://localhost:8080';

installFixedClock();

const designProgram = { id: 1, name: 'Licenciatura en Diseño Gráfico', area: 'DESIGN', level: 'BACHELOR', totalYears: 4, teachingMode: 'IN_PERSON' };
const businessProgram = { id: 2, name: 'Licenciatura en Administración', area: 'BUSINESS_ADMINISTRATION', level: 'BACHELOR', totalYears: 4, teachingMode: 'IN_PERSON' };
const technicalProgram = { id: 3, name: 'Tecnicatura en Seguridad e Higiene', area: 'TECHNOLOGY', level: 'UNDERGRADUATE', totalYears: 3, teachingMode: 'IN_PERSON' };
const photographyCourse = { id: 7, name: 'Fotografía digital', area: 'DESIGN', durationMonths: 3, teachingMode: 'IN_PERSON' };

function renderCatalogo(route = '/catalogo') {
  return renderWithProviders(
    <Routes>
      <Route path="/catalogo" element={<Catalogo />} />
    </Routes>,
    { route },
  );
}

function respondWith(programs, courses = []) {
  const requests = { programs: [], courses: [] };
  server.use(
    http.get(`${API}/programs`, ({ request }) => {
      requests.programs.push(new URL(request.url).searchParams);
      return HttpResponse.json(programs);
    }),
    http.get(`${API}/courses`, ({ request }) => {
      requests.courses.push(new URL(request.url).searchParams);
      return HttpResponse.json(courses);
    }),
  );
  return requests;
}

function cohortsFor(byProgramId) {
  server.use(
    http.get(`${API}/programs/:id/cohorts`, ({ params }) => HttpResponse.json(byProgramId[params.id] ?? [])),
  );
}

describe('Catalogo', () => {
  it('groups careers and courses by area on the landing page', async () => {
    respondWith([designProgram, businessProgram, technicalProgram], [photographyCourse]);
    renderCatalogo();

    const designSection = await screen.findByRole('region', { name: 'Diseño' });
    expect(within(designSection).getByText('Licenciatura en Diseño Gráfico')).toBeInTheDocument();
    expect(within(designSection).getByText('Fotografía digital')).toBeInTheDocument();
    expect(within(designSection).queryByText('Licenciatura en Administración')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Oferta académica' })).toBeInTheDocument();
    expect(screen.getByText('Elegí una carrera o un curso para ver el detalle e inscribirte.')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Ruta de navegación' })).not.toBeInTheDocument();
  });

  it('puts the type and the area on every card and never a price', async () => {
    respondWith([technicalProgram], [photographyCourse]);
    renderCatalogo();

    const technicalCard = (await screen.findByText('Tecnicatura en Seguridad e Higiene')).closest('li');
    expect(within(technicalCard).getByText('Pregrado')).toBeInTheDocument();
    expect(within(technicalCard).getByText('Tecnología')).toBeInTheDocument();
    expect(within(technicalCard).getByText('3 años')).toBeInTheDocument();
    expect(within(technicalCard).getByText('Presencial')).toBeInTheDocument();
    const courseCard = screen.getByText('Fotografía digital').closest('li');
    expect(within(courseCard).getByText('Curso')).toBeInTheDocument();
    expect(within(courseCard).getByText('Diseño')).toBeInTheDocument();
    expect(within(courseCard).getByText('3 meses')).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/\$/);
  });

  it('links each card to the existing detail page', async () => {
    respondWith([designProgram], [photographyCourse]);
    renderCatalogo();

    expect(await screen.findByRole('link', { name: 'Ver detalle de Licenciatura en Diseño Gráfico' })).toHaveAttribute('href', '/catalogo/carreras/1');
    expect(screen.getByRole('link', { name: 'Ver detalle de Fotografía digital' })).toHaveAttribute('href', '/catalogo/cursos/7');
  });

  it('sends the area from the URL to the backend and shows breadcrumbs with a title', async () => {
    const requests = respondWith([designProgram], [photographyCourse]);
    renderCatalogo('/catalogo?area=DESIGN');

    expect(await screen.findByText('Licenciatura en Diseño Gráfico')).toBeInTheDocument();
    expect(requests.programs[0].get('area')).toBe('DESIGN');
    expect(requests.courses[0].get('area')).toBe('DESIGN');
    expect(screen.getByRole('heading', { level: 1, name: 'Oferta de Diseño' })).toBeInTheDocument();
    const crumbs = screen.getByRole('navigation', { name: 'Ruta de navegación' });
    expect(within(crumbs).getByRole('link', { name: 'Oferta académica' })).toHaveAttribute('href', '/catalogo');
    expect(within(crumbs).getByText('Diseño')).not.toHaveAttribute('href');
    expect(screen.queryByRole('region', { name: 'Diseño' })).not.toBeInTheDocument();
  });

  it('links the area crumb back to the area when a level is also selected', async () => {
    const requests = respondWith([designProgram]);
    renderCatalogo('/catalogo?area=DESIGN&level=BACHELOR');

    expect(await screen.findByText('Licenciatura en Diseño Gráfico')).toBeInTheDocument();
    expect(requests.programs[0].get('level')).toBe('BACHELOR');
    expect(requests.courses).toHaveLength(0);
    expect(screen.getByRole('heading', { level: 1, name: 'De grado · Diseño' })).toBeInTheDocument();
    const crumbs = screen.getByRole('navigation', { name: 'Ruta de navegación' });
    expect(within(crumbs).getByRole('link', { name: 'Diseño' })).toHaveAttribute('href', '/catalogo?area=DESIGN');
    expect(within(crumbs).getByText('De grado')).not.toHaveAttribute('href');
  });

  it('lists only courses when the level is course', async () => {
    const requests = respondWith([designProgram], [photographyCourse]);
    renderCatalogo('/catalogo?level=COURSE');

    expect(await screen.findByText('Fotografía digital')).toBeInTheDocument();
    expect(requests.programs).toHaveLength(0);
    expect(screen.queryByText('Licenciatura en Diseño Gráfico')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Curso' })).toBeInTheDocument();
  });

  it('ignores an unknown area or level in the URL', async () => {
    const requests = respondWith([designProgram, businessProgram]);
    renderCatalogo('/catalogo?area=NOPE&level=XXX');

    expect(await screen.findByText('Licenciatura en Administración')).toBeInTheDocument();
    expect(requests.programs[0].has('area')).toBe(false);
    expect(requests.programs[0].has('level')).toBe(false);
    expect(screen.getByRole('heading', { level: 1, name: 'Oferta académica' })).toBeInTheDocument();
  });

  it('filters by the search text ignoring accents and shows it in the breadcrumbs', async () => {
    respondWith([designProgram, businessProgram], [photographyCourse]);
    renderCatalogo('/catalogo?q=DISENO');

    expect(await screen.findByText('Licenciatura en Diseño Gráfico')).toBeInTheDocument();
    expect(screen.queryByText('Licenciatura en Administración')).not.toBeInTheDocument();
    expect(screen.queryByText('Fotografía digital')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Resultados para «DISENO»' })).toBeInTheDocument();
    expect(within(screen.getByRole('navigation', { name: 'Ruta de navegación' })).getByRole('link', { name: 'Oferta académica' })).toBeInTheDocument();
  });

  it('shows no filter chips with a remove button', async () => {
    respondWith([designProgram]);
    renderCatalogo('/catalogo?area=DESIGN&level=BACHELOR&q=diseno');

    await screen.findByText('Licenciatura en Diseño Gráfico');
    expect(screen.queryByRole('button', { name: /quitar|eliminar|✕|×/i })).not.toBeInTheDocument();
  });

  it('shows the closing date, the open badge or the dates hint depending on the cohorts', async () => {
    respondWith([designProgram, businessProgram, technicalProgram], [photographyCourse]);
    cohortsFor({
      1: [{ id: 11, programId: 1, label: 'Marzo 2027', admissionOpen: true, admissionStart: '2026-10-01', admissionEnd: '2026-11-20' }],
      2: [
        { id: 21, programId: 2, label: 'Marzo 2027', admissionOpen: true, admissionEnd: '2027-02-01' },
        { id: 22, programId: 2, label: 'Agosto 2027', admissionOpen: false, admissionEnd: '2027-07-01' },
      ],
      3: [{ id: 31, programId: 3, label: 'Marzo 2027', admissionOpen: false, admissionEnd: '2026-11-10' }],
    });
    renderCatalogo();

    const designCard = (await screen.findByText('Licenciatura en Diseño Gráfico')).closest('li');
    expect(await within(designCard).findByText('Cierra 20/11')).toBeInTheDocument();
    const businessCard = screen.getByText('Licenciatura en Administración').closest('li');
    expect(await within(businessCard).findByText('Inscripciones abiertas')).toBeInTheDocument();
    const technicalCard = screen.getByText('Tecnicatura en Seguridad e Higiene').closest('li');
    expect(await within(technicalCard).findByText('Consultá fechas')).toBeInTheDocument();
    const courseCard = screen.getByText('Fotografía digital').closest('li');
    expect(within(courseCard).getByText('Consultá fechas')).toBeInTheDocument();
  });

  it('switches from closing soon to open exactly after 30 days', async () => {
    respondWith([designProgram, businessProgram]);
    cohortsFor({
      1: [{ id: 11, programId: 1, label: 'A', admissionOpen: true, admissionEnd: '2026-12-05' }],
      2: [{ id: 21, programId: 2, label: 'B', admissionOpen: true, admissionEnd: '2026-12-06' }],
    });
    renderCatalogo();

    const designCard = (await screen.findByText('Licenciatura en Diseño Gráfico')).closest('li');
    expect(await within(designCard).findByText('Cierra 05/12')).toBeInTheDocument();
    const businessCard = screen.getByText('Licenciatura en Administración').closest('li');
    expect(await within(businessCard).findByText('Inscripciones abiertas')).toBeInTheDocument();
  });

  it('falls back to the dates hint when the cohorts of a career fail to load', async () => {
    respondWith([designProgram]);
    server.use(http.get(`${API}/programs/:id/cohorts`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })));
    renderCatalogo();

    const designCard = (await screen.findByText('Licenciatura en Diseño Gráfico')).closest('li');
    expect(within(designCard).queryByText('Inscripciones abiertas')).not.toBeInTheDocument();
  });

  it('shows the loading state while the offering loads', async () => {
    server.use(http.get(`${API}/programs`, () => new Promise(() => {})));
    renderCatalogo();

    expect(await screen.findByLabelText('Cargando la oferta')).toHaveAttribute('aria-busy', 'true');
  });

  it('shows the empty state when nothing is published', async () => {
    respondWith([], []);
    renderCatalogo();

    expect(await screen.findByText('Todavía no hay oferta publicada')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Ver toda la oferta' })).not.toBeInTheDocument();
  });

  it('shows no results with a link to the whole offering when a filter matches nothing', async () => {
    respondWith([designProgram]);
    renderCatalogo('/catalogo?q=astronomia');

    expect(await screen.findByText('No encontramos resultados')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver toda la oferta' })).toHaveAttribute('href', '/catalogo');
  });

  it('shows an error with a retry that loads the offering', async () => {
    let failing = true;
    server.use(
      http.get(`${API}/programs`, () => (failing ? HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 }) : HttpResponse.json([designProgram]))),
    );
    const user = userEvent.setup();
    renderCatalogo();

    expect(await screen.findByText('No pudimos cargar la oferta')).toBeInTheDocument();
    failing = false;
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Licenciatura en Diseño Gráfico')).toBeInTheDocument();
  });

  describe('next informative session', () => {
    const upcoming = {
      id: 5,
      title: 'Diseño: salida laboral',
      modality: 'ONLINE',
      status: 'SCHEDULED',
      scheduledAt: '2026-11-19T21:00:00Z',
      registeredByMe: false,
      registrationCount: 0,
    };
    const later = { ...upcoming, id: 6, title: 'Charla general', scheduledAt: '2026-12-10T21:00:00Z' };
    const stripName = { name: 'Próxima reunión informativa' };

    beforeEach(() => {
      authState.roles = ['ALUMNO'];
    });

    function serveSessions(resolver) {
      const served = { calls: 0 };
      server.use(
        http.get(`${API}/informative-sessions`, () => {
          served.calls += 1;
          return resolver();
        }),
      );
      return served;
    }

    async function afterSessionsServed(served) {
      await waitFor(() => expect(served.calls).toBeGreaterThan(0));
      await act(async () => {
        await new Promise((resolve) => setTimeout(resolve, 0));
      });
    }

    it('shows the soonest scheduled session on the landing page only', async () => {
      respondWith([designProgram]);
      serveSessions(() => HttpResponse.json([later, upcoming]));
      renderCatalogo();

      const strip = await screen.findByRole('complementary', stripName);
      expect(within(strip).getByText('Diseño: salida laboral')).toBeInTheDocument();
      expect(within(strip).getByText('Jueves 19 de noviembre · 18:00 h · Online')).toBeInTheDocument();
      expect(within(strip).queryByText('Charla general')).not.toBeInTheDocument();
    });

    it('links to the student informative sessions screen', async () => {
      respondWith([designProgram]);
      serveSessions(() => HttpResponse.json([upcoming]));
      renderCatalogo();

      const strip = await screen.findByRole('complementary', stripName);
      expect(within(strip).getByRole('link', { name: 'Ver reuniones' })).toHaveAttribute('href', '/alumno/reuniones-informativas');
    });

    it.each([['an applicant without roles', []], ['a staff member', ['DIRECTOR']]])('is not requested nor shown to %s', async (_label, roles) => {
      authState.roles = roles;
      respondWith([designProgram]);
      const served = serveSessions(() => HttpResponse.json([upcoming]));
      renderCatalogo();

      await screen.findByText('Licenciatura en Diseño Gráfico');
      expect(served.calls).toBe(0);
      expect(screen.queryByRole('complementary', stripName)).not.toBeInTheDocument();
    });

    it('is not even requested when a filter is applied', async () => {
      respondWith([designProgram]);
      const served = serveSessions(() => HttpResponse.json([upcoming]));
      renderCatalogo('/catalogo?area=DESIGN');

      await screen.findByText('Licenciatura en Diseño Gráfico');
      expect(served.calls).toBe(0);
      expect(screen.queryByRole('complementary', stripName)).not.toBeInTheDocument();
    });

    it('is hidden when the endpoint fails', async () => {
      respondWith([designProgram]);
      const served = serveSessions(() => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 }));
      renderCatalogo();

      await screen.findByText('Licenciatura en Diseño Gráfico');
      await afterSessionsServed(served);
      expect(screen.queryByRole('complementary', stripName)).not.toBeInTheDocument();
    });

    it('is hidden when there are no sessions', async () => {
      respondWith([designProgram]);
      const served = serveSessions(() => HttpResponse.json([]));
      renderCatalogo();

      await screen.findByText('Licenciatura en Diseño Gráfico');
      await afterSessionsServed(served);
      expect(screen.queryByRole('complementary', stripName)).not.toBeInTheDocument();
    });

    it('ignores cancelled and past sessions', async () => {
      respondWith([designProgram]);
      const served = serveSessions(() =>
        HttpResponse.json([
          { ...upcoming, status: 'CANCELLED' },
          { ...later, scheduledAt: '2026-11-01T21:00:00Z' },
        ]),
      );
      renderCatalogo();

      await screen.findByText('Licenciatura en Diseño Gráfico');
      await afterSessionsServed(served);
      expect(screen.queryByRole('complementary', stripName)).not.toBeInTheDocument();
    });
  });


  describe('my applications', () => {
    it('opens from the URL without inner tabs and combines careers and courses in one list', async () => {
      server.use(
        http.get(`${API}/me/program-enrollment-applications`, () =>
          HttpResponse.json([{ id: 1, programName: 'Diseño Gráfico', status: 'PENDING' }]),
        ),
        http.get(`${API}/me/course-enrollment-applications`, () =>
          HttpResponse.json([{ id: 2, courseName: 'Illustrator desde cero', paymentStatus: 'PENDING_REVIEW' }]),
        ),
      );
      renderCatalogo('/catalogo?tab=mis-solicitudes');

      expect(await screen.findByText('Diseño Gráfico')).toBeInTheDocument();
      expect(screen.getByText('Illustrator desde cero')).toBeInTheDocument();
      expect(screen.getAllByText('En revisión')).toHaveLength(2);
      expect(screen.queryByRole('tab')).not.toBeInTheDocument();
      expect(screen.getByRole('heading', { level: 1, name: 'Mis solicitudes' })).toBeInTheDocument();
    });

    it('shows a waitlisted course application as on the waitlist', async () => {
      server.use(
        http.get(`${API}/me/program-enrollment-applications`, () => HttpResponse.json([])),
        http.get(`${API}/me/course-enrollment-applications`, () =>
          HttpResponse.json([
            { id: 3, courseName: 'Primeros Auxilios', paymentStatus: 'APPROVED', courseEnrollmentId: 25, courseEnrollmentStatus: 'WAITLISTED' },
          ]),
        ),
      );
      renderCatalogo('/catalogo?tab=mis-solicitudes');

      expect(await screen.findByText('Primeros Auxilios')).toBeInTheDocument();
      expect(screen.getByText('En lista de espera')).toBeInTheDocument();
      expect(screen.queryByText('Aprobada')).not.toBeInTheDocument();
    });

    it('is reachable from the offering page', async () => {
      respondWith([designProgram]);
      renderCatalogo();

      expect(await screen.findByRole('link', { name: 'Mis solicitudes' })).toHaveAttribute('href', '/catalogo?tab=mis-solicitudes');
    });
  });
});
