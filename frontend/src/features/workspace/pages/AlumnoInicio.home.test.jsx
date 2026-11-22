import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installFixedClock } from '@/test/fixedClock';
import { calendarHandler, calendarResponse, classEvent } from '@/test/fixtures/studentCalendar';
import { programEnrollment, sectionEnrollment, studentHomeHandlers } from '@/test/fixtures/studentHome';
import { AlumnoInicio } from '@/features/workspace/pages/AlumnoInicio';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Lucía', apellido: 'Fernández' } }),
}));

const API = 'http://localhost:8080';

installFixedClock({ now: '2026-11-21T12:00:00-03:00' });

function mountHome(...handlers) {
  server.use(http.get(`${API}/me/announcements`, () => HttpResponse.json([])), ...handlers);
  return renderWithProviders(
    <Routes>
      <Route path="/alumno/inicio" element={<AlumnoInicio />} />
    </Routes>,
    { route: '/alumno/inicio' },
  );
}

describe('AlumnoInicio — identidad y KPIs', () => {
  it('showsTheStudentIdentityWithFileNumberProgramAndRegularity', async () => {
    mountHome(...studentHomeHandlers());
    expect(await screen.findByRole('heading', { level: 1, name: 'Lucía Fernández' })).toBeInTheDocument();
    expect(await screen.findByText('Legajo N° 1042 · Tecnicatura en Desarrollo de Software')).toBeInTheDocument();
    expect(await screen.findByText('Alumno regular')).toBeInTheDocument();
  });

  it('showsActiveSubjectsAndCreditsFromTheRealProgress', async () => {
    mountHome(
      ...studentHomeHandlers({
        sectionEnrollments: [
          sectionEnrollment({ id: 1, status: 'IN_PROGRESS' }),
          sectionEnrollment({ id: 2, subjectName: 'Sistemas Operativos', status: 'ENROLLED' }),
          sectionEnrollment({ id: 3, subjectName: 'Programación I', status: 'APPROVED' }),
        ],
      }),
    );
    const label = await screen.findByText('Materias en curso', { selector: 'span' });
    expect(within(label.parentElement.parentElement).getByText('2')).toBeInTheDocument();
    expect(await screen.findByRole('progressbar', { name: 'Créditos acumulados' })).toHaveAttribute('aria-valuenow', '50');
    expect(screen.getByText('/ 60')).toBeInTheDocument();
  });

  it('doesNotShowAnAttendanceKpiBecauseThereIsNoGlobalSource', async () => {
    mountHome(...studentHomeHandlers());
    await screen.findByText('Créditos');
    expect(screen.queryByText('Asistencia')).not.toBeInTheDocument();
  });

  it('showsAnErrorWithRetryWhenTheEnrollmentsFail', async () => {
    mountHome(http.get(`${API}/me/program-enrollments`, () => new HttpResponse(null, { status: 500 })), ...studentHomeHandlers());
    expect(await screen.findByText('No pudimos cargar tus datos')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('showsAnAccessDeniedMessageWhenTheEnrollmentsAreForbidden', async () => {
    mountHome(http.get(`${API}/me/program-enrollments`, () => new HttpResponse(null, { status: 403 })), ...studentHomeHandlers());
    expect(await screen.findByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
    expect(screen.queryByText('Próximos eventos')).not.toBeInTheDocument();
    expect(screen.queryByText('Mis materias')).not.toBeInTheDocument();
  });
});

describe('AlumnoInicio — Mis materias', () => {
  it('listsTheCurrentSubjectsWithStatusAndLinksToTheirDetail', async () => {
    mountHome(
      ...studentHomeHandlers({
        sectionEnrollments: [
          sectionEnrollment({ id: 100 }),
          sectionEnrollment({ id: 101, subjectName: 'Programación II', status: 'REGULAR', sectionName: 'Comisión C' }),
          sectionEnrollment({ id: 102, subjectName: 'Inglés Técnico I', status: 'APPROVED' }),
          sectionEnrollment({ id: 103, subjectName: 'Redes', status: 'WAITLISTED' }),
        ],
      }),
    );
    const table = await screen.findByRole('table', { name: 'Materias en curso', hidden: true });
    const rows = within(table).getAllByRole('row', { hidden: true });
    expect(rows).toHaveLength(4);
    expect(within(table).getByRole('link', { name: 'Bases de Datos', hidden: true })).toHaveAttribute('href', '/alumno/mis-carreras/materias/100');
    expect(within(table).getByText('Cursada regular · falta final')).toBeInTheDocument();
    expect(within(table).getByText('En lista de espera')).toBeInTheDocument();
    expect(within(table).queryByText('Inglés Técnico I')).not.toBeInTheDocument();
    expect(within(rows[1]).getByText('Lun y Mié 19:00–21:00')).toBeInTheDocument();
  });

  it('rendersTheSameSubjectsAsCardsForNarrowScreens', async () => {
    mountHome(...studentHomeHandlers({ sectionEnrollments: [sectionEnrollment()] }));
    const cards = (await screen.findAllByRole('link', { name: 'Bases de Datos', hidden: true })).map((link) => link.closest('li'));
    expect(cards.filter(Boolean)).toHaveLength(1);
    expect(within(cards.find(Boolean)).getByText('Docente · Marcos Giménez')).toBeInTheDocument();
  });

  it('showsAnEmptyStateWithAPathToTheStudyPlan', async () => {
    mountHome(...studentHomeHandlers());
    expect(await screen.findByText('Sin materias en curso')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver plan de estudio' })).toHaveAttribute('href', '/alumno/mis-carreras');
  });

  it('showsALoadingSkeletonWhileTheSubjectsLoad', async () => {
    mountHome(http.get(`${API}/me/section-enrollments`, () => new Promise(() => {})), ...studentHomeHandlers());
    expect(await screen.findByText('Cargando tus materias')).toBeInTheDocument();
  });

  it('showsAnErrorWithRetryWhenTheSubjectsFail', async () => {
    mountHome(http.get(`${API}/me/section-enrollments`, () => new HttpResponse(null, { status: 500 })), ...studentHomeHandlers());
    expect(await screen.findByText('No pudimos cargar tus materias')).toBeInTheDocument();
  });

  it('showsAnAccessDeniedMessageWhenTheSubjectsAreForbidden', async () => {
    mountHome(http.get(`${API}/me/section-enrollments`, () => new HttpResponse(null, { status: 403 })), ...studentHomeHandlers());
    expect(await screen.findByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
  });
});

describe('AlumnoInicio — próximos eventos', () => {
  it('listsTheNextCalendarEventsWithTypeAndPlace', async () => {
    mountHome(
      ...studentHomeHandlers({
        calendar: calendarHandler(() =>
          HttpResponse.json(
            calendarResponse({
              events: [classEvent({ id: 'c1', date: '2026-11-23', title: 'Clase · Bases de Datos', startTime: '19:00:00', place: 'Aula 5' })],
            }),
          ),
        ),
      }),
    );
    expect(await screen.findByText('Clase · Bases de Datos')).toBeInTheDocument();
    expect(screen.getByText('19:00 · Aula 5')).toBeInTheDocument();
    expect(screen.getByText('Clase', { selector: 'span' })).toBeInTheDocument();
  });

  it('showsTheEmptyStateWhenThereAreNoUpcomingEvents', async () => {
    mountHome(...studentHomeHandlers());
    expect(await screen.findByText('No hay eventos próximos')).toBeInTheDocument();
  });

  it('showsAnErrorWithRetryWhenTheCalendarFails', async () => {
    mountHome(...studentHomeHandlers({ calendar: calendarHandler(() => new HttpResponse(null, { status: 500 })) }));
    expect(await screen.findByText('No pudimos cargar los próximos eventos')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});

describe('AlumnoInicio — pantalla sin carrera', () => {
  it('keepsTheIdentityWithoutProgramWhenThereAreNoEnrollments', async () => {
    mountHome(...studentHomeHandlers({ enrollments: [programEnrollment({ status: 'WITHDRAWN' })] }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Lucía Fernández' })).toBeInTheDocument();
    expect(screen.queryByText(/Legajo/)).not.toBeInTheDocument();
  });
});
