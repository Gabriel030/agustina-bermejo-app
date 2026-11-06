import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { LegajoAlumno } from './LegajoAlumno';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: ['PRECEPTOR'] } }),
}));

const API = 'http://localhost:8080';

const PROGRAM_ENROLLMENT = { id: 4, programId: 2, programName: 'Tecnicatura en Gestión Cultural', status: 'ENROLLED', studentId: 9 };
const COURSE_ENROLLMENT = { id: 7, courseId: 3, courseName: 'Fotografía digital', status: 'ENROLLED', studentId: 9 };

function section(overrides) {
  return {
    id: 11,
    programEnrollmentId: 4,
    sectionId: 1,
    sectionName: 'A',
    subjectId: 1,
    subjectCode: 'HA1',
    subjectName: 'Historia del Arte I',
    curricularYear: 1,
    curricularTerm: 'FIRST',
    attemptNumber: 1,
    status: 'APPROVED',
    finalScore: 8.5,
    schedule: 'Lun 18:30-21:30',
    instructorTitular: 'Marcos Peña',
    instructorAdjunto: 'Sin adjunto',
    finalGradeType: 'FINAL_EXAM',
    droppedAt: '2026-12-01T10:00:00',
    enrolledAt: '2026-03-01T10:00:00',
    ...overrides,
  };
}

const SECTIONS = [
  section({}),
  section({ id: 12, sectionId: 2, sectionName: 'B', subjectId: 2, subjectName: 'Gestión de Proyectos', curricularYear: 2, curricularTerm: 'SECOND', status: 'FAILED', finalScore: 3, instructorTitular: 'Julia Ferrero', instructorAdjunto: 'Raúl Díaz' }),
];

function mockStudent({ programs = [PROGRAM_ENROLLMENT], courses = [] } = {}) {
  const gradeRequests = [];
  server.use(
    http.get(`${API}/students/9`, () => HttpResponse.json({ id: 9, nombre: 'Valentina', apellido: 'Suárez', status: 'ACTIVE' })),
    http.get(`${API}/students/9/program-enrollments`, () => HttpResponse.json(programs)),
    http.get(`${API}/students/9/course-enrollments`, () => HttpResponse.json(courses)),
    http.get(`${API}/program-enrollments/4/progress`, () =>
      HttpResponse.json({
        programEnrollmentId: 4,
        creditosAcumulados: 62,
        creditosRequeridos: 120,
        promedioGeneral: 7.84,
        esRegular: true,
        elegibleParaCompletar: false,
        obligatoriasPendientes: ['Antropología Cultural', 'Legislación Cultural'],
        gruposElectivos: [{ electiveGroupId: 1, electiveGroupName: 'Electivas de gestión', minRequired: 2, maxAllowed: 3, cantidadAprobada: 2, cumplido: true }],
      })),
    http.get(`${API}/program-enrollments/4/section-enrollments`, () => HttpResponse.json(SECTIONS)),
    http.get(`${API}/program-enrollments/4/attendance-summary`, () =>
      HttpResponse.json([])),
    http.get(`${API}/section-enrollments/:id/attendance`, () => HttpResponse.json([])),
    http.get(`${API}/section-enrollments/:id/grade-items`, ({ params }) => {
      gradeRequests.push(params.id);
      return HttpResponse.json(
        params.id === '12'
          ? [{ id: 1, enrollmentId: 12, examBoardId: 1, type: 'EXAM', sequenceNumber: 1, score: 3, date: '2026-04-15T10:00:00' }]
          : [],
      );
    }),
    http.get(`${API}/course-enrollments/7/progress`, () =>
      HttpResponse.json({ courseEnrollmentId: 7, elegibleParaCompletar: false, modulosPendientes: ['Edición y revelado'] })),
  );
  return gradeRequests;
}

function renderLegajo() {
  return renderWithProviders(
    <Routes>
      <Route path="/alumnos/:id" element={<LegajoAlumno />} />
    </Routes>,
    { route: '/alumnos/9' },
  );
}

describe('LegajoAlumno academic and evaluation tabs', () => {
  it('shows progress numbers and the enrolled subjects with their attendance', async () => {
    mockStudent();
    const user = userEvent.setup();
    renderLegajo();

    await user.click(await screen.findByRole('tab', { name: 'Académico' }));

    expect(await screen.findByText('62')).toBeInTheDocument();
    expect(screen.getByText('7,84')).toBeInTheDocument();
    expect(screen.getByText('Alumno regular')).toBeInTheDocument();
    expect(screen.getByText('Todavía no')).toBeInTheDocument();
    const subjectRow = (await screen.findByText('Historia del Arte I')).closest('tr');
    expect(within(subjectRow).getByText('Aprobada')).toBeInTheDocument();
    expect(within(subjectRow).getByText('—')).toBeInTheDocument();
    expect(within(subjectRow).getByText('8,5')).toBeInTheDocument();
    expect(screen.queryByText('Disponible próximamente')).not.toBeInTheDocument();
  });

  it('filters the subjects by status from the column header', async () => {
    mockStudent();
    const user = userEvent.setup();
    renderLegajo();
    await user.click(await screen.findByRole('tab', { name: 'Académico' }));
    await screen.findByText('Gestión de Proyectos');

    await user.click(screen.getByRole('button', { name: 'Filtrar por Estado' }));
    await user.click(await screen.findByRole('checkbox', { name: /Desaprobada/ }));

    expect(screen.queryByText('Gestión de Proyectos')).not.toBeInTheDocument();
    expect(screen.getByText('Historia del Arte I')).toBeInTheDocument();
  });

  it('opens the grades of only the chosen subject from the expanded detail', async () => {
    const gradeRequests = mockStudent();
    const user = userEvent.setup();
    renderLegajo();
    await user.click(await screen.findByRole('tab', { name: 'Académico' }));
    await user.click((await screen.findAllByRole('button', { name: 'Ver detalle de la cursada' }))[1]);

    expect(screen.getByText('Titular Julia Ferrero · Adjunto Raúl Díaz')).toBeInTheDocument();
    expect(gradeRequests).toEqual([]);
    await user.click(screen.getByRole('button', { name: 'Ver notas →' }));

    expect(await screen.findByText('Parcial')).toBeInTheDocument();
    expect(screen.getByText('15/04/2026')).toBeInTheDocument();
    expect(screen.getByText('Desaprobada')).toBeInTheDocument();
    expect(gradeRequests).toEqual(['12']);
  });

  it('shows the empty state when the selected subject has no grades', async () => {
    mockStudent();
    const user = userEvent.setup();
    renderLegajo();

    await user.click(await screen.findByRole('tab', { name: 'Evaluación' }));

    expect(await screen.findByText('Sin notas cargadas')).toBeInTheDocument();
  });

  it('shows pending modules for a course enrollment', async () => {
    mockStudent({ programs: [], courses: [COURSE_ENROLLMENT] });
    const user = userEvent.setup();
    renderLegajo();

    await user.click(await screen.findByRole('tab', { name: 'Académico' }));

    expect(await screen.findByText('Módulos pendientes', { selector: 'div' })).toBeInTheDocument();
    expect(screen.getByText('Módulos pendientes · 1')).toBeInTheDocument();
  });

  it('shows the empty state when the student has no enrollments', async () => {
    mockStudent({ programs: [] });
    const user = userEvent.setup();
    renderLegajo();

    await user.click(await screen.findByRole('tab', { name: 'Académico' }));

    expect(await screen.findByText('Sin inscripciones')).toBeInTheDocument();
  });

  it('offers a retry when the progress cannot be loaded', async () => {
    mockStudent();
    server.use(http.get(`${API}/program-enrollments/4/progress`, () => HttpResponse.json({ message: 'boom' }, { status: 500 })));
    const user = userEvent.setup();
    renderLegajo();

    await user.click(await screen.findByRole('tab', { name: 'Académico' }));

    expect(await screen.findByText('No se pudo cargar el avance académico')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
