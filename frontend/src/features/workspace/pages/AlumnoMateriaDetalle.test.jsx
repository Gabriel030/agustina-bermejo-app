import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { contractList } from '@/test/fixtures/fromContract';
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

  it('shows the error state and reloads the enrollments on retry', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/me/section-enrollments`, () => {
        calls += 1;
        return new HttpResponse(null, { status: 500 });
      }),
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <Routes>
        <Route path="/alumno/materias/:id" element={<AlumnoMateriaDetalle />} />
      </Routes>,
      { route: '/alumno/materias/7' },
    );
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar la materia');
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    await waitFor(() => expect(calls).toBe(2));
  });

  it('shows the not found state when the enrollment does not exist', async () => {
    server.use(http.get(`${API}/me/section-enrollments`, () => HttpResponse.json([])), http.get(`${API}/*`, () => HttpResponse.json([])));
    renderWithProviders(
      <Routes>
        <Route path="/alumno/materias/:id" element={<AlumnoMateriaDetalle />} />
      </Routes>,
      { route: '/alumno/materias/7' },
    );
    expect(await screen.findByText('Materia no encontrada')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Volver a Mis carreras' })).toBeInTheDocument();
  });

  it('asks for confirmation before withdrawing from the subject', async () => {
    const user = userEvent.setup();
    renderDetail('IN_PROGRESS');
    await user.click(await screen.findByRole('button', { name: 'Darme de baja' }));
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('¿Dar de baja esta materia?');
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
  });

  it('shows the withdraw error and clears it when the dialog is reopened', async () => {
    const user = userEvent.setup();
    renderDetail('IN_PROGRESS');
    server.use(http.post(`${API}/me/section-enrollments/7/withdraw`, () => new HttpResponse(null, { status: 500 })));
    await user.click(await screen.findByRole('button', { name: 'Darme de baja' }));
    await user.click(screen.getByRole('button', { name: 'Sí, darme de baja' }));
    expect(await screen.findByText('No pudimos procesar la baja. Probá de nuevo.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: 'Darme de baja' }));
    expect(await screen.findByRole('alertdialog')).toBeInTheDocument();
    expect(screen.queryByText('No pudimos procesar la baja. Probá de nuevo.')).not.toBeInTheDocument();
  });

  it('withdraws and goes back to Mis carreras on success', async () => {
    const user = userEvent.setup();
    server.use(
      http.get(`${API}/me/section-enrollments`, () =>
        HttpResponse.json(
          contractList('/me/section-enrollments', [
            { id: 7, subjectName: 'Didáctica General', sectionName: 'Comisión A', status: 'IN_PROGRESS', subjectCode: 'DG1' },
          ]),
        ),
      ),
      http.get(`${API}/me/section-enrollments/7/attendance/risk`, () =>
        HttpResponse.json({ absencePercent: 0, calculable: false, libre: false, maxAbsencePercent: 25 }),
      ),
      http.post(`${API}/me/section-enrollments/7/withdraw`, () => new HttpResponse(null, { status: 204 })),
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
    renderWithProviders(
      <Routes>
        <Route path="/alumno/materias/:id" element={<AlumnoMateriaDetalle />} />
        <Route path="/alumno/mis-carreras" element={<div>Pantalla Mis carreras</div>} />
      </Routes>,
      { route: '/alumno/materias/7' },
    );
    await user.click(await screen.findByRole('button', { name: 'Darme de baja' }));
    await user.click(screen.getByRole('button', { name: 'Sí, darme de baja' }));
    expect(await screen.findByText('Pantalla Mis carreras')).toBeInTheDocument();
  });

  it('shows the access denied state without retry when the enrollments return 403', async () => {
    server.use(
      http.get(`${API}/me/section-enrollments`, () => new HttpResponse(null, { status: 403 })),
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
    renderWithProviders(
      <Routes>
        <Route path="/alumno/materias/:id" element={<AlumnoMateriaDetalle />} />
      </Routes>,
      { route: '/alumno/materias/7' },
    );
    expect(await screen.findByRole('alert')).toHaveTextContent('No tenés acceso a esta pantalla');
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
  });
});
