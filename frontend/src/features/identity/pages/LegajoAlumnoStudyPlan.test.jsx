import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { LegajoAlumno } from './LegajoAlumno';
import { studentFixture } from '@/test/fixtures/students';
import { activeSubjectEquivalences } from '@/test/fixtures/subjectEquivalences';

const auth = vi.hoisted(() => ({ roles: ['PRECEPTOR'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: auth.roles } }),
}));

const API = 'http://localhost:8080';

const PROGRAM_ENROLLMENT = { id: 4, programId: 2, programName: 'Tecnicatura en Gestión Cultural', status: 'ENROLLED', studentId: 9 };
const SECOND_PROGRAM_ENROLLMENT = { id: 5, programId: 3, programName: 'Profesorado de Arte', status: 'ENROLLED', studentId: 9 };
const COURSE_ENROLLMENT = { id: 7, courseId: 3, courseName: 'Fotografía digital', status: 'ENROLLED', studentId: 9, enrollmentDate: '2026-03-01T00:00:00' };

function subject(overrides) {
  return {
    programSubjectId: 1,
    subjectId: 10,
    subjectCode: 'MAT101',
    subjectName: 'Matemática I',
    credits: 3,
    curricularYear: 1,
    curricularTerm: 'FIRST',
    category: 'MANDATORY',
    electiveGroupId: null,
    state: 'APPROVED',
    finalScore: 8,
    attempts: 1,
    blockedBy: [],
    prerequisites: [],
    currentEnrollmentId: null,
    corequisites: [],
    approvalSource: 'COURSEWORK',
    equivalence: null,
    prerequisitesWaived: false,
    ...overrides,
  };
}

function progress(programEnrollmentId, subjects, programName = 'Tecnicatura en Gestión Cultural') {
  return {
    programEnrollmentId,
    programName,
    subjects,
    summary: {
      completionPercent: 50,
      accumulatedCredits: 3,
      requiredCredits: 6,
      remainingCredits: 3,
      mandatoryTotal: 2,
      mandatoryApproved: 1,
      counts: { approved: 1, inProgress: 0, regularPendingFinal: 0, failed: 0, available: 1, blocked: 0 },
      electiveGroups: [],
      approvedByEquivalence: 0,
      creditsByEquivalence: 0,
      averageScore: null,
      accumulatedCreditsByCoursework: 3,
    },
    warnings: [],
  };
}

const AVAILABLE_SUBJECT = subject({
  programSubjectId: 2,
  subjectCode: 'MAT102',
  subjectName: 'Matemática II',
  state: 'AVAILABLE',
  finalScore: null,
  attempts: 0,
  currentEnrollmentId: 77,
});
const WAIVED_SUBJECT = subject({
  programSubjectId: 3,
  subjectCode: 'FIS101',
  subjectName: 'Física I',
  state: 'AVAILABLE',
  finalScore: null,
  prerequisitesWaived: true,
});

function mockStudent({ programs = [PROGRAM_ENROLLMENT], courses = [] } = {}) {
  server.use(
    http.get(`${API}/students/9`, () => HttpResponse.json(studentFixture({ id: 9, nombre: 'Valentina', apellido: 'Suárez', status: 'ACTIVE' }))),
    http.get(`${API}/students/9/program-enrollments`, () => HttpResponse.json(programs)),
    http.get(`${API}/students/9/course-enrollments`, () => HttpResponse.json(courses)),
    http.get(`${API}/program-enrollments/:id/equivalences`, () => HttpResponse.json([])),
    http.get(`${API}/program-enrollments/4/section-enrollments`, () => HttpResponse.json([])),
    http.get(`${API}/program-enrollments/4/progress`, () => HttpResponse.json({})),
  );
}

function mockProgress(programEnrollmentId, subjects, programName) {
  server.use(
    http.get(`${API}/program-enrollments/${programEnrollmentId}/progress/subjects`, () =>
      HttpResponse.json(progress(programEnrollmentId, subjects, programName)),
    ),
  );
}

function renderLegajo(search = '?view=plan-de-estudio') {
  return renderWithProviders(
    <Routes>
      <Route path="/:rol/alumnos/:id" element={<LegajoAlumno />} />
    </Routes>,
    { route: `/preceptor/alumnos/9${search}` },
  );
}

afterEach(() => {
  auth.roles = ['PRECEPTOR'];
});

describe('LegajoAlumno study plan tab', () => {
  it('opens on the study plan tab from the view query and keeps the other tabs working', async () => {
    mockStudent();
    mockProgress(4, [subject(), AVAILABLE_SUBJECT]);
    const user = userEvent.setup();
    renderLegajo();

    expect(await screen.findByRole('tab', { name: 'Plan de estudio' })).toHaveAttribute('aria-selected', 'true');
    expect(await screen.findByText('Matemática I')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Financiero' }));
    expect(screen.getByRole('tab', { name: 'Financiero' })).toHaveAttribute('aria-selected', 'true');
  });

  it.each(['PRECEPTOR', 'ADMINISTRATIVO', 'DIRECTOR'])('shows the plan with the staff wording for %s', async (role) => {
    auth.roles = [role];
    mockStudent();
    mockProgress(4, [subject(), AVAILABLE_SUBJECT]);
    renderLegajo('?view=plan-de-estudio&vista=mapa');

    expect(await screen.findByRole('heading', { name: 'Avance en el plan' })).toBeInTheDocument();
    expect(screen.queryByText('Tu avance en el plan')).not.toBeInTheDocument();
    expect(await screen.findByText('Matemática I')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Proyección de egreso' })).toBeInTheDocument();
  });

  it('describes an available subject in third person and sends the staff member to the Evaluación tab', async () => {
    mockStudent();
    mockProgress(4, [subject(), AVAILABLE_SUBJECT]);
    const user = userEvent.setup();
    renderLegajo('?view=plan-de-estudio&vista=mapa');

    await user.click(await screen.findByRole('button', { name: /Matemática II/ }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByText('Ya cumple las correlativas de esta materia.')).toBeInTheDocument();
    expect(within(dialog).queryByText(/Podés inscribirte/)).not.toBeInTheDocument();
    expect(within(dialog).queryByRole('link', { name: 'Ver mi cursada' })).not.toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Ver cursada en Evaluación' }));

    expect(await screen.findByRole('tab', { name: 'Evaluación' })).toHaveAttribute('aria-selected', 'true');
  });

  it('marks a subject exempted from prerequisites as read only', async () => {
    mockStudent();
    mockProgress(4, [subject(), WAIVED_SUBJECT]);
    const user = userEvent.setup();
    renderLegajo('?view=plan-de-estudio&vista=mapa');

    await user.click(await screen.findByRole('button', { name: /Física I/ }));

    expect(within(await screen.findByRole('dialog')).getByText('Eximida de correlativas')).toBeInTheDocument();
  });

  it('shows an empty state when the plan has no subjects', async () => {
    mockStudent();
    mockProgress(4, []);
    renderLegajo();

    expect(await screen.findByText('El plan todavía no tiene materias')).toBeInTheDocument();
    expect(screen.getByText('Cuando se carguen van a aparecer acá.')).toBeInTheDocument();
  });

  it('shows the error with Reintentar and loads the plan again', async () => {
    mockStudent();
    let calls = 0;
    server.use(
      http.get(`${API}/program-enrollments/4/progress/subjects`, () => {
        calls += 1;
        return calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(progress(4, [subject()]));
      }),
    );
    const user = userEvent.setup();
    renderLegajo();

    await user.click(await screen.findByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Matemática I')).toBeInTheDocument();
  });

  it('keeps the tab visible and shows no-access inside it when the read is forbidden', async () => {
    auth.roles = ['SUBDIRECTOR'];
    mockStudent();
    server.use(http.get(`${API}/program-enrollments/4/progress/subjects`, () => new HttpResponse(null, { status: 403 })));
    renderLegajo();

    expect(await screen.findByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Plan de estudio' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
  });

  it('loads the plan of the other career when the picker changes', async () => {
    mockStudent({ programs: [PROGRAM_ENROLLMENT, SECOND_PROGRAM_ENROLLMENT] });
    mockProgress(4, [subject()]);
    mockProgress(5, [subject({ programSubjectId: 9, subjectCode: 'ART100', subjectName: 'Dibujo I' })], 'Profesorado de Arte');
    const user = userEvent.setup();
    renderLegajo();

    expect(await screen.findByText('Matemática I')).toBeInTheDocument();
    await user.click(screen.getByRole('combobox', { name: 'Inscripción' }));
    await user.click(await screen.findByRole('option', { name: /Profesorado de Arte/ }));

    expect(await screen.findByText('Dibujo I')).toBeInTheDocument();
    expect(screen.queryByText('Matemática I')).not.toBeInTheDocument();
  });

  it('says courses have no study plan', async () => {
    mockStudent({ programs: [], courses: [COURSE_ENROLLMENT] });
    renderLegajo();

    expect(await screen.findByText('Los cursos no tienen plan de estudio')).toBeInTheDocument();
  });

  it('keeps the map working when equivalences are forbidden and shows no-access only inside their section', async () => {
    mockStudent();
    mockProgress(4, [subject(), AVAILABLE_SUBJECT]);
    server.use(http.get(`${API}/program-enrollments/4/equivalences`, () => new HttpResponse(null, { status: 403 })));
    renderLegajo('?view=plan-de-estudio&vista=mapa');

    const section = await screen.findByRole('region', { name: 'Equivalencias' });
    expect(await within(section).findByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
    expect(await screen.findAllByRole('button', { name: /Matemática I/ })).not.toHaveLength(0);
    expect(screen.getByRole('heading', { name: 'Avance en el plan' })).toBeInTheDocument();
  });

  it('shows the equivalences section below the plan with the read-only notice for the preceptor', async () => {
    mockStudent();
    mockProgress(4, [subject()]);
    server.use(
      http.get(`${API}/program-enrollments/4/equivalences`, () =>
        HttpResponse.json(activeSubjectEquivalences()),
      ),
    );
    renderLegajo();

    const section = await screen.findByRole('region', { name: 'Equivalencias' });
    expect(await within(section).findByText('Lengua y Comunicación')).toBeInTheDocument();
    expect(within(section).getByText('Solo lectura')).toBeInTheDocument();
    expect(within(section).queryByRole('button', { name: /Cargar|Anular/ })).not.toBeInTheDocument();
  });

  it.each(['ADMINISTRATIVO', 'DIRECTOR'])('shows the section with the record action and without the read-only notice for %s', async (role) => {
    auth.roles = [role];
    mockStudent();
    mockProgress(4, [subject()]);
    server.use(http.get(`${API}/program-enrollments/4/equivalences`, () => HttpResponse.json(activeSubjectEquivalences())));
    renderLegajo();

    const section = await screen.findByRole('region', { name: 'Equivalencias' });
    expect(await within(section).findByText('Lengua y Comunicación')).toBeInTheDocument();
    expect(within(section).queryByText('Solo lectura')).not.toBeInTheDocument();
    expect(within(section).getByRole('button', { name: 'Cargar equivalencia' })).toBeEnabled();
  });

  it.each(['PRECEPTOR', 'SUBDIRECTOR'])('never shows the record action to %s', async (role) => {
    auth.roles = [role];
    mockStudent();
    mockProgress(4, [subject()]);
    server.use(http.get(`${API}/program-enrollments/4/equivalences`, () => HttpResponse.json(activeSubjectEquivalences())));
    renderLegajo();

    const section = await screen.findByRole('region', { name: 'Equivalencias' });
    expect(await within(section).findByText('Lengua y Comunicación')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Cargar equivalencia|Anular equivalencia/ })).not.toBeInTheDocument();
  });

  it('jumps from the equivalence count in the summary to the section', async () => {
    mockStudent();
    const withEquivalence = progress(4, [subject()]);
    withEquivalence.summary.approvedByEquivalence = 2;
    server.use(
      http.get(`${API}/program-enrollments/4/progress/subjects`, () => HttpResponse.json(withEquivalence)),
    );
    const user = userEvent.setup();
    renderLegajo('?view=plan-de-estudio&vista=mapa');

    await user.click(await screen.findByRole('link', { name: 'ver' }));

    expect(await screen.findByRole('region', { name: 'Equivalencias' })).toHaveFocus();
    expect(screen.getByText(/Incluye/)).toBeInTheDocument();
  });
});
