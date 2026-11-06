import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { LegajoAlumno } from './LegajoAlumno';

const auth = vi.hoisted(() => ({ usuario: null }));
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: auth.usuario }),
}));

const API = 'http://localhost:8080';

const SECTION = {
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
  status: 'IN_PROGRESS',
  finalScore: 0,
  schedule: 'Lun 18:30-21:30',
  instructorTitular: 'Marcos Peña',
  instructorAdjunto: 'Sin adjunto',
  finalGradeType: 'FINAL_EXAM',
  droppedAt: '2026-12-01T10:00:00',
  enrolledAt: '2026-03-01T10:00:00',
};

const ABSENCE = {
  id: 41,
  enrollmentId: 11,
  classDate: '2026-11-05',
  status: 'ABSENT',
  justificationAttachmentUrl: '',
  justificationReason: '',
  justificationRejectionReason: '',
  justifiedAt: '2026-11-05T08:00:00',
  justificationDeadline: '2026-11-12',
  justifiable: true,
};

function mockStudent() {
  server.use(
    http.get(`${API}/students/9`, () => HttpResponse.json({ id: 9, nombre: 'Valentina', apellido: 'Suárez', status: 'ACTIVE' })),
    http.get(`${API}/students/9/program-enrollments`, () =>
      HttpResponse.json([{ id: 4, programId: 2, programName: 'Tecnicatura en Gestión Cultural', status: 'ENROLLED', studentId: 9 }])),
    http.get(`${API}/students/9/course-enrollments`, () => HttpResponse.json([])),
    http.get(`${API}/program-enrollments/4/progress`, () =>
      HttpResponse.json({
        programEnrollmentId: 4,
        creditosAcumulados: 10,
        creditosRequeridos: 120,
        promedioGeneral: 7,
        esRegular: true,
        elegibleParaCompletar: false,
        obligatoriasPendientes: [],
        gruposElectivos: [],
      })),
    http.get(`${API}/program-enrollments/4/section-enrollments`, () => HttpResponse.json([SECTION])),
    http.get(`${API}/program-enrollments/4/attendance-summary`, () => HttpResponse.json([])),
    http.get(`${API}/section-enrollments/11/attendance`, () => HttpResponse.json([ABSENCE])),
  );
}

async function openAbsences(user) {
  renderWithProviders(
    <Routes>
      <Route path="/alumnos/:id" element={<LegajoAlumno />} />
    </Routes>,
    { route: '/alumnos/9' },
  );
  await user.click(await screen.findByRole('tab', { name: 'Académico' }));
  await user.click(await screen.findByRole('button', { name: 'Ver detalle de la cursada' }));
  return screen.findByText('05/11/2026');
}

beforeEach(() => {
  mockStudent();
});

describe('LegajoAlumno absences', () => {
  it.each(['PRECEPTOR', 'ADMINISTRATIVO'])('lets %s justify an absence from the expanded subject', async (role) => {
    auth.usuario = { roles: [role], nombre: 'Ana', apellido: 'Ruiz' };
    const user = userEvent.setup();
    await openAbsences(user);

    await user.click(screen.getByRole('button', { name: 'Justificar la falta del 05/11/2026 de Valentina Suárez' }));

    const dialog = await screen.findByRole('dialog', { name: 'Justificar falta' });
    expect(within(dialog).getByText('Queda registrado a nombre de Ana Ruiz.')).toBeInTheDocument();
    expect(within(dialog).getByText(/Valentina Suárez · Historia del Arte I · Com\. A/)).toBeInTheDocument();
  });

  it('shows the absences read only to the director', async () => {
    auth.usuario = { roles: ['DIRECTOR'], nombre: 'Marina', apellido: 'Díaz' };
    const user = userEvent.setup();
    await openAbsences(user);

    expect(screen.getByText('Dentro de plazo')).toBeInTheDocument();
    expect(screen.getByText('Solo lectura')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Justificar la falta/ })).not.toBeInTheDocument();
  });
});
