import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { StudentCourseDetail } from '@/features/enrollment/pages/StudentCourseDetail';

const API = 'http://localhost:8080';

function subject({ id, name, risk, attendance = [], gradeItems = [], section = {} }) {
  return {
    sectionEnrollment: {
      id,
      courseEnrollmentId: 7,
      courseId: 10,
      courseName: 'Excel para la gestión administrativa',
      courseSubjectId: id,
      courseSubjectName: name,
      sectionId: id,
      attemptNumber: 1,
      enrolledAt: '2026-02-12T10:00:00',
      droppedAt: '2026-02-12T10:00:00',
      finalGradeType: 'PROMOTION',
      sectionName: 'Comisión A',
      instructorTitular: 'Marcos Ledesma',
      instructorAdjunto: '',
      schedule: 'Martes 18:30',
      status: 'IN_PROGRESS',
      finalScore: 0,
      ...section,
    },
    attendance,
    attendanceRisk: risk,
    gradeItems,
  };
}

function record(id, classDate, status) {
  return { id, classDate, status, enrollmentId: 1 };
}

function courseEnrollment(overrides) {
  return {
    id: 7,
    courseId: 10,
    courseName: 'Excel para la gestión administrativa',
    status: 'IN_PROGRESS',
    enrollmentDate: '2026-02-12T10:00:00',
    completionDate: null,
    subjects: [],
    ...overrides,
  };
}

function respondWith(body, status = 200) {
  server.use(http.get(`${API}/me/course-enrollments/:id`, () => HttpResponse.json(body, { status })));
}

function renderDetail(id = 7) {
  return renderWithProviders(
    <Routes>
      <Route path="/alumno/mis-cursos/:id" element={<StudentCourseDetail />} />
    </Routes>,
    { route: `/alumno/mis-cursos/${id}` },
  );
}

const SAFE_RISK = { calculable: true, libre: false, absencePercent: 10, maxAbsencePercent: 30 };
const WARNING_RISK = { calculable: true, libre: false, absencePercent: 25, maxAbsencePercent: 30 };
const LIBRE_RISK = { calculable: true, libre: true, absencePercent: 60, maxAbsencePercent: 30 };

describe('StudentCourseDetail', () => {
  it('shows the course header and the subject sections returned by the API', async () => {
    respondWith(
      courseEnrollment({
        subjects: [
          subject({
            id: 1,
            name: 'Planillas y fórmulas',
            risk: SAFE_RISK,
            attendance: [record(1, '2026-03-03', 'PRESENT'), record(2, '2026-03-10', 'ABSENT'), record(3, '2026-03-17', 'JUSTIFIED')],
            gradeItems: [{ id: 1, enrollmentId: 1, examBoardId: 1, type: 'EXAM', sequenceNumber: 1, score: 7.5, date: '2026-04-02T10:00:00' }],
          }),
        ],
      }),
    );

    renderDetail();

    expect(await screen.findByRole('heading', { name: 'Excel para la gestión administrativa' })).toBeInTheDocument();
    expect(screen.getByText('Inscripto el 12/02/2026')).toBeInTheDocument();
    expect(screen.getByText('Cursando')).toBeInTheDocument();
    expect(screen.getByText('Marcos Ledesma')).toBeInTheDocument();
    expect(screen.getByText('Parcial 1')).toBeInTheDocument();
    expect(screen.getByText('7,5')).toBeInTheDocument();
    expect(screen.getByText('02/04/2026')).toBeInTheDocument();
    const clases = screen.getByText('Clases').parentElement;
    expect(within(clases).getByText('3')).toBeInTheDocument();
    const ausentes = screen.getByText('Ausentes').parentElement;
    expect(within(ausentes).getByText('1')).toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.queryByText(/Nota final/)).not.toBeInTheDocument();
  });

  it('shows the not-found state and never data when the enrollment does not belong to the student', async () => {
    respondWith(errorBody('COURSE_ENROLLMENT_NOT_FOUND'), 404);

    renderDetail(99);

    expect(await screen.findByText('No encontramos lo que buscabas')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Volver a Mis cursos/ })).toHaveAttribute('href', '/alumno/mis-cursos');
    expect(screen.queryByText('Excel para la gestión administrativa')).not.toBeInTheDocument();
  });

  it('shows the error state and loads the course after retrying', async () => {
    respondWith(errorBody('INTERNAL_ERROR', { retryable: true }), 500);
    const user = userEvent.setup();

    renderDetail();

    expect(await screen.findByText('No pudimos cargar tu curso')).toBeInTheDocument();
    respondWith(courseEnrollment());
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('heading', { name: 'Excel para la gestión administrativa' })).toBeInTheDocument();
    expect(screen.queryByText('No pudimos cargar tu curso')).not.toBeInTheDocument();
  });

  it('flags a student who is libre with the danger level and the maximum allowed', async () => {
    respondWith(
      courseEnrollment({
        subjects: [subject({ id: 2, name: 'Tablas dinámicas', risk: LIBRE_RISK, attendance: [record(1, '2026-03-03', 'ABSENT')] })],
      }),
    );

    renderDetail();

    const alert = await screen.findByRole('status');
    expect(alert).toHaveAttribute('data-risk', 'danger');
    expect(alert).toHaveTextContent('Quedaste libre en esta materia');
    expect(alert).toHaveTextContent('60%');
    expect(alert).toHaveTextContent('30%');
  });

  it('warns when absences approach the maximum and stays in success when well below it', async () => {
    respondWith(
      courseEnrollment({
        subjects: [
          subject({ id: 1, name: 'Materia en alerta', risk: WARNING_RISK }),
          subject({ id: 2, name: 'Materia en regla', risk: SAFE_RISK }),
        ],
      }),
    );
    const user = userEvent.setup();

    renderDetail();

    expect(await screen.findByRole('status')).toHaveAttribute('data-risk', 'warning');
    await user.click(screen.getByRole('button', { name: /Materia en regla/ }));
    expect(await screen.findByRole('status')).toHaveAttribute('data-risk', 'success');
    expect(screen.getByRole('status')).toHaveTextContent('Estás en regla');
  });

  it('shows no risk message when the risk is not calculable', async () => {
    respondWith(
      courseEnrollment({
        subjects: [subject({ id: 1, name: 'Sin política', risk: { calculable: false, libre: false, absencePercent: 0, maxAbsencePercent: 0 } })],
      }),
    );

    renderDetail();

    expect(await screen.findByText('Sin política')).toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('opens one subject at a time and keeps the focus on the toggled header', async () => {
    respondWith(
      courseEnrollment({
        subjects: [subject({ id: 1, name: 'Primera materia', risk: SAFE_RISK }), subject({ id: 2, name: 'Segunda materia', risk: SAFE_RISK })],
      }),
    );
    const user = userEvent.setup();

    renderDetail();

    const first = await screen.findByRole('button', { name: /Primera materia/ });
    const second = screen.getByRole('button', { name: /Segunda materia/ });
    expect(first).toHaveAttribute('aria-expanded', 'true');
    expect(second).toHaveAttribute('aria-expanded', 'false');

    await user.click(second);

    expect(second).toHaveAttribute('aria-expanded', 'true');
    expect(first).toHaveAttribute('aria-expanded', 'false');
    expect(document.getElementById(second.getAttribute('aria-controls'))).toHaveTextContent('Docente titular');
    expect(second).toHaveFocus();
  });

  it('shows the last five classes first and the rest after asking for all of them', async () => {
    const attendance = Array.from({ length: 7 }, (_, index) => record(index + 1, `2026-03-0${index + 1}`, 'PRESENT'));
    respondWith(courseEnrollment({ subjects: [subject({ id: 1, name: 'Materia larga', risk: SAFE_RISK, attendance })] }));
    const user = userEvent.setup();

    renderDetail();

    expect(await screen.findByText('07/03/2026')).toBeInTheDocument();
    expect(screen.queryByText('01/03/2026')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Ver todas \(7\)/ }));
    expect(screen.getByText('01/03/2026')).toBeInTheDocument();
  });

  it('shows the honest empty state of a waitlisted enrollment without subjects', async () => {
    respondWith(courseEnrollment({ status: 'WAITLISTED' }));

    renderDetail();

    expect(await screen.findByText('Estás en lista de espera')).toBeInTheDocument();
    expect(screen.queryByText('Clases')).not.toBeInTheDocument();
  });

  it('shows the final grade and the completion date of an approved course', async () => {
    respondWith(
      courseEnrollment({
        status: 'APPROVED',
        completionDate: '2026-06-28T10:00:00',
        subjects: [subject({ id: 1, name: 'Planillas', risk: SAFE_RISK, section: { status: 'APPROVED', finalScore: 8.5 } })],
      }),
    );

    renderDetail();

    expect(await screen.findByText('Nota final 8,5')).toBeInTheDocument();
    expect(screen.getByText(/Finalizado el 28\/06\/2026/)).toBeInTheDocument();
    expect(screen.getByText('Aprobado')).toBeInTheDocument();
  });
});
