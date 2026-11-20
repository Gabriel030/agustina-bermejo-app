import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { LegajoAlumno } from '../LegajoAlumno';
import { studentFixture } from '@/test/fixtures/students';

const auth = vi.hoisted(() => ({ usuario: null }));
const candidatesMock = vi.hoisted(() => ({ data: [] }));
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: auth.usuario }),
}));

vi.mock('@/features/identity/hooks/useEnrollmentChanges', async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...original,
    useChangeSectionCandidates: () => ({ data: candidatesMock.data, isLoading: false, isError: false, isSuccess: true, refetch: () => {} }),
  };
});

const API = 'http://localhost:8080';

const SECTION = {
  id: 11,
  programEnrollmentId: 4,
  sectionId: 1,
  sectionName: 'A',
  subjectId: 1,
  subjectName: 'Historia del Arte I',
  curricularYear: 1,
  curricularTerm: 'FIRST',
  attemptNumber: 1,
  status: 'IN_PROGRESS',
  finalScore: 0,
  subjectCode: 'HA1',
  schedule: 'Lun 18:30-21:30',
  instructorTitular: 'Marcos Peña',
  instructorAdjunto: 'Sin adjunto',
  finalGradeType: 'FINAL_EXAM',
  droppedAt: '2026-12-01T10:00:00',
  enrolledAt: '2026-03-01T10:00:00',
};

const CANDIDATES = [
  { sectionId: 1, name: 'A', availableSeats: 2, slots: [], selectable: false, blockReason: 'CURRENT_SECTION', blockDetail: 'Es su comisión actual' },
  {
    sectionId: 2,
    name: 'B',
    availableSeats: 5,
    slots: [{ dayOfWeek: 'TUESDAY', startTime: '10:00:00', endTime: '12:00:00', classroomName: 'Aula 2' }],
    selectable: true,
    blockReason: null,
    blockDetail: null,
  },
];

function mockStudent() {
  server.use(
    http.get(`${API}/students/9`, () => HttpResponse.json(studentFixture({ id: 9, nombre: 'Valentina', apellido: 'Suárez', status: 'ACTIVE' }))),
    http.get(`${API}/students/9/program-enrollments`, () =>
      HttpResponse.json([{ id: 4, programId: 2, programName: 'Tecnicatura en Gestión Cultural', status: 'ENROLLED', enrollmentDate: '2026-03-01', studentId: 9 }])),
    http.get(`${API}/students/9/course-enrollments`, () => HttpResponse.json([])),
    http.get(`${API}/program-enrollments/4/progress`, () =>
      HttpResponse.json({ programEnrollmentId: 4, creditosAcumulados: 0, creditosRequeridos: 120, promedioGeneral: 0, hasActiveCoursework: true, elegibleParaCompletar: false, obligatoriasPendientes: [], gruposElectivos: [] })),
    http.get(`${API}/regular-certificates/eligibility`, () => HttpResponse.json({ enrollmentId: 4, kind: 'PROGRAM', billingMode: 'OFF', attendanceControlled: true, regular: true, institutionallyRegular: true, paymentStanding: 'VERIFIED', reasons: [] })),
    http.get(`${API}/program-enrollments/4/section-enrollments`, () => HttpResponse.json([SECTION])),
    http.get(`${API}/program-enrollments/4/attendance-summary`, () => HttpResponse.json([])),
  );
}

async function openAcademic(role) {
  auth.usuario = { roles: [role], nombre: 'Ana', apellido: 'Ruiz' };
  const user = userEvent.setup();
  renderWithProviders(
    <Routes>
      <Route path="/alumnos/:id" element={<LegajoAlumno />} />
    </Routes>,
    { route: '/alumnos/9' },
  );
  await user.click(await screen.findByRole('tab', { name: 'Académico' }));
  return user;
}

beforeEach(() => {
  candidatesMock.data = CANDIDATES;
  mockStudent();
});

describe('AcademicoTab enrollment changes', () => {
  it('shows the subject kebab to the preceptor but not the program withdrawal', async () => {
    await openAcademic('PRECEPTOR');

    expect(await screen.findByRole('button', { name: /Acciones de Historia del Arte I, comisión A/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Acciones de la carrera/ })).not.toBeInTheDocument();
  });

  it('shows the program withdrawal to the director', async () => {
    await openAcademic('DIRECTOR');

    expect(await screen.findByRole('button', { name: /Acciones de la carrera/ })).toBeInTheDocument();
  });

  it('blocks a short reason and sends the trimmed reason when withdrawing a subject', async () => {
    let body = null;
    server.use(
      http.post(`${API}/section-enrollments/11/withdraw`, async ({ request }) => {
        body = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = await openAcademic('PRECEPTOR');
    await user.click(await screen.findByRole('button', { name: /Acciones de Historia del Arte I/ }));
    await user.click(await screen.findByRole('menuitem', { name: /Dar de baja de la materia/ }));

    const dialog = await screen.findByRole('dialog', { name: 'Dar de baja de la materia' });
    await user.type(within(dialog).getByLabelText('Motivo'), 'corto');
    await user.click(within(dialog).getByRole('button', { name: 'Dar de baja de la materia' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('al menos 10');
    expect(body).toBeNull();

    await user.clear(within(dialog).getByLabelText('Motivo'));
    await user.type(within(dialog).getByLabelText('Motivo'), '  Dejó de cursar por trabajo  ');
    await user.click(within(dialog).getByRole('button', { name: 'Dar de baja de la materia' }));
    await waitFor(() => expect(body).toEqual({ reason: 'Dejó de cursar por trabajo' }));
  });

  it('requires typing the confirmation word to withdraw the program', async () => {
    const user = await openAcademic('ADMINISTRATIVO');
    await user.click(await screen.findByRole('button', { name: /Acciones de la carrera/ }));
    await user.click(await screen.findByRole('menuitem', { name: 'Dar de baja de la carrera' }));

    const dialog = await screen.findByRole('dialog', { name: 'Dar de baja de la carrera' });
    const confirm = within(dialog).getByRole('button', { name: 'Dar de baja de la carrera' });
    expect(confirm).toBeDisabled();
    await user.type(within(dialog).getByLabelText(/Escribí BAJA/), 'bája');
    expect(confirm).toBeEnabled();
  });

  it('changes the section in two steps and shows the overlap error inside the panel', async () => {
    server.use(
      http.post(`${API}/section-enrollments/11/change-section`, () =>
        HttpResponse.json(
          { code: 'SECTION_SCHEDULE_OVERLAP', retryable: false, details: { conflictingSubject: 'Álgebra', day: 'martes', from: '10:00', to: '12:00' } },
          { status: 409 },
        ),
      ),
    );
    const user = await openAcademic('PRECEPTOR');
    await user.click(await screen.findByRole('button', { name: /Acciones de Historia del Arte I/ }));
    await user.click(await screen.findByRole('menuitem', { name: /Cambiar de comisión/ }));

    const sheet = await screen.findByRole('dialog', { name: 'Cambiar de comisión' });
    expect(await within(sheet).findByText('Com. B')).toBeInTheDocument();
    expect(within(sheet).getByRole('button', { name: 'Continuar' })).toBeDisabled();
    await user.click(within(sheet).getByLabelText(/Com\. B/));
    await user.click(within(sheet).getByRole('button', { name: 'Continuar' }));

    expect(within(sheet).getByText('Asistencia y notas')).toBeInTheDocument();
    await user.type(within(sheet).getByLabelText('Motivo'), 'Superposición con el trabajo');
    await user.click(within(sheet).getByRole('button', { name: 'Confirmar el cambio' }));

    expect(await within(sheet).findByText('Se superpone con Álgebra (martes de 10:00 a 12:00).')).toBeInTheDocument();
    expect(within(sheet).getByRole('button', { name: 'Elegir otra comisión' })).toBeInTheDocument();
  });
});
