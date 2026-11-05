import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoMisCarreras } from '@/features/workspace/pages/AlumnoMisCarreras';

const API = 'http://localhost:8080';

describe('AlumnoMisCarreras', () => {
  it('keeps showing a graduated program with its finished state instead of the "not enrolled" empty state', async () => {
    server.use(
      http.get(`${API}/me/program-enrollments`, () =>
        HttpResponse.json([
          {
            id: 5,
            studentId: 1,
            programId: 2,
            programName: 'Profesorado de Educación Inicial',
            status: 'COMPLETED',
            enrollmentDate: '2022-03-01T10:00:00',
            completionDate: '2026-10-03T03:54:00',
          },
        ]),
      ),
      http.get(`${API}/me/program-enrollments/5/progress`, () =>
        HttpResponse.json({
          programEnrollmentId: 5,
          creditosAcumulados: 144,
          creditosRequeridos: 144,
          obligatoriasPendientes: [],
          gruposElectivos: [],
          elegibleParaCompletar: true,
          esRegular: false,
          promedioGeneral: 8.1,
        }),
      ),
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );

    renderWithProviders(<AlumnoMisCarreras />);

    expect(await screen.findByText('Carrera finalizada')).toBeInTheDocument();
    expect(screen.getAllByText(/Profesorado de Educación Inicial/).length).toBeGreaterThan(0);
    expect(screen.queryByText('Todavía no estás matriculado en ninguna carrera')).not.toBeInTheDocument();
  });

  function mockEnrollment(esRegular) {
    server.use(
      http.get(`${API}/me/program-enrollments`, () =>
        HttpResponse.json([
          {
            id: 5,
            studentId: 1,
            programId: 2,
            programName: 'Profesorado de Educación Inicial',
            status: 'ACTIVE',
            enrollmentDate: '2022-03-01T10:00:00',
            completionDate: '2026-10-03T03:54:00',
          },
        ]),
      ),
      http.get(`${API}/me/program-enrollments/5/progress`, () =>
        HttpResponse.json({
          programEnrollmentId: 5,
          creditosAcumulados: 40,
          creditosRequeridos: 144,
          obligatoriasPendientes: [],
          gruposElectivos: [],
          elegibleParaCompletar: false,
          esRegular,
          promedioGeneral: 8.1,
        }),
      ),
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
  }

  it('labels a program with an active enrollment as such', async () => {
    mockEnrollment(true);
    renderWithProviders(<AlumnoMisCarreras />);
    expect(await screen.findByText('Inscripción activa')).toBeInTheDocument();
    expect(screen.queryByText('Sin inscripción activa')).not.toBeInTheDocument();
  });

  it('labels a program without an active enrollment without claiming the student is not attending', async () => {
    mockEnrollment(false);
    renderWithProviders(<AlumnoMisCarreras />);
    expect(await screen.findByText('Sin inscripción activa')).toBeInTheDocument();
    expect(screen.queryByText('No estás cursando')).not.toBeInTheDocument();
  });

  it('does not show an invented weekly hours column in the study plan', async () => {
    mockEnrollment(true);
    renderWithProviders(<AlumnoMisCarreras />, { route: '/?tab=plan-de-estudio' });
    expect(await screen.findByText('Correlativas')).toBeInTheDocument();
    expect(screen.queryByText('Hs. semanales')).not.toBeInTheDocument();
  });

  it('downloads the regular and approved-subjects certificates for the selected program', async () => {
    mockEnrollment(false);
    const requested = [];
    server.use(
      http.get(`${API}/me/program-enrollments/5/certificates/:kind`, ({ params }) => {
        requested.push(params.kind);
        return new HttpResponse(new Uint8Array([37, 80, 68, 70]), {
          headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="constancia.pdf"' },
        });
      }),
    );
    URL.createObjectURL = vi.fn(() => 'blob:certificate');
    URL.revokeObjectURL = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<AlumnoMisCarreras />);

    await user.click(await screen.findByRole('button', { name: 'Constancia de alumno regular' }));
    await waitFor(() => expect(requested).toEqual(['regular']));
    await user.click(screen.getByRole('button', { name: 'Constancia de materias aprobadas' }));
    await waitFor(() => expect(requested).toEqual(['regular', 'approved-subjects']));
  });
});

describe('AlumnoMisCarreras — load failure', () => {
  it('shows the error state and reloads the programs on retry', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/me/program-enrollments`, () => {
        calls += 1;
        return new HttpResponse(null, { status: 500 });
      }),
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
    const user = userEvent.setup();
    renderWithProviders(<AlumnoMisCarreras />);

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar tus carreras');
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    await waitFor(() => expect(calls).toBe(2));
  });
});
