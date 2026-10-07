import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
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
});
