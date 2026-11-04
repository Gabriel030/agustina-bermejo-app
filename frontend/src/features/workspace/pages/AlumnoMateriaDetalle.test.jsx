import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoMateriaDetalle } from '@/features/workspace/pages/AlumnoMateriaDetalle';

const API = 'http://localhost:8080';

function renderDetail(status) {
  server.use(
    http.get(`${API}/me/section-enrollments`, () =>
      HttpResponse.json([
        {
          id: 7,
          sectionId: 3,
          subjectId: 4,
          subjectName: 'Didáctica General',
          sectionName: 'Comisión A',
          status,
          instructorTitular: 'Ana Pérez',
          schedule: 'Lunes 18:00',
          instructorAdjunto: 'Luis Gómez',
          attemptNumber: 1,
          curricularTerm: 'FIRST',
          curricularYear: 1,
          droppedAt: '2026-09-02T10:00:00',
          enrolledAt: '2026-03-02T10:00:00',
          finalGradeType: 'FINAL_EXAM',
          finalScore: 0,
          programEnrollmentId: 2,
          subjectCode: 'DG1',
        },
      ]),
    ),
    http.get(`${API}/me/section-enrollments/7/grade-items`, () =>
      HttpResponse.json([{ id: 1, enrollmentId: 7, type: 'EXAM', sequenceNumber: 5, score: 8, date: '2026-09-01T10:00:00', examBoardId: 1 }]),
    ),
    http.get(`${API}/me/section-enrollments/7/attendance/risk`, () =>
      HttpResponse.json({ absencePercent: 0, calculable: false, libre: false, maxAbsencePercent: 25 }),
    ),
    http.get(`${API}/*`, () => HttpResponse.json([])),
  );
  return renderWithProviders(
    <Routes>
      <Route path="/alumno/materias/:id" element={<AlumnoMateriaDetalle />} />
    </Routes>,
    { route: '/alumno/materias/7' },
  );
}

describe('AlumnoMateriaDetalle', () => {
  it('does not show invented syllabus or schedule content', async () => {
    renderDetail('IN_PROGRESS');
    expect(await screen.findByText('Didáctica General')).toBeInTheDocument();
    expect(screen.queryByText(/Temario/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Cronograma/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Objetivos/i)).not.toBeInTheDocument();
  });

  it('translates the expired waitlist status and extra grade types', async () => {
    renderDetail('WAITLIST_EXPIRED');
    expect(await screen.findByText('Lista de espera vencida')).toBeInTheDocument();
    expect(await screen.findByText(/Parcial 5: 8/)).toBeInTheDocument();
    expect(screen.queryByText(/EXAM/)).not.toBeInTheDocument();
  });
});
