import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoMisCursos } from '@/features/enrollment/pages/AlumnoMisCursos';

const API = 'http://localhost:8080';

function enrollment(overrides) {
  return {
    id: 1,
    studentId: 1,
    courseId: 10,
    courseName: 'Excel Avanzado',
    status: 'IN_PROGRESS',
    enrollmentDate: '2026-09-01T10:00:00',
    completionDate: null,
    progressPercent: null,
    ...overrides,
  };
}

function respondWith(list) {
  server.use(http.get(`${API}/me/course-enrollments`, () => HttpResponse.json(list)));
}

describe('AlumnoMisCursos', () => {
  it('shows in-progress courses with their progress bar and the catalog shortcut', async () => {
    respondWith([enrollment({ progressPercent: 60 })]);

    renderWithProviders(<AlumnoMisCursos />);

    expect(await screen.findByText('Excel Avanzado')).toBeInTheDocument();
    expect(screen.getByText('Cursando')).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '60');
    expect(screen.getByRole('link', { name: /Ver oferta de cursos/ })).toHaveAttribute('href', '/catalogo?tab=cursos');
  });

  it('hides the progress bar when the course has no active modules', async () => {
    respondWith([enrollment({ progressPercent: null })]);

    renderWithProviders(<AlumnoMisCursos />);

    expect(await screen.findByText('Excel Avanzado')).toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });

  it('labels a waitlisted enrollment as in progress with the waitlist label', async () => {
    respondWith([enrollment({ status: 'WAITLISTED' })]);

    renderWithProviders(<AlumnoMisCursos />);

    expect(await screen.findByText('En lista de espera')).toBeInTheDocument();
  });

  it('moves finished enrollments to the Terminados tab with their agreed labels', async () => {
    respondWith([
      enrollment({ id: 1, status: 'IN_PROGRESS' }),
      enrollment({ id: 2, courseName: 'Inglés B1', status: 'APPROVED' }),
      enrollment({ id: 3, courseName: 'Dibujo', status: 'FAILED' }),
      enrollment({ id: 4, courseName: 'Fotografía', status: 'WITHDRAWN' }),
      enrollment({ id: 5, courseName: 'Cerámica', status: 'WAITLIST_EXPIRED' }),
    ]);
    const user = userEvent.setup();

    renderWithProviders(<AlumnoMisCursos />);
    await screen.findByText('Excel Avanzado');
    expect(screen.queryByText('Inglés B1')).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: /Terminados/ }));

    expect(await screen.findByText('Inglés B1')).toBeInTheDocument();
    expect(screen.getByText('Aprobado')).toBeInTheDocument();
    expect(screen.getByText('Desaprobado')).toBeInTheDocument();
    expect(screen.getByText('Dado de baja')).toBeInTheDocument();
    expect(screen.getByText('Lista de espera vencida')).toBeInTheDocument();
    expect(screen.queryByText('Excel Avanzado')).not.toBeInTheDocument();
  });

  it('shows the empty states with the catalog action when nothing is in progress', async () => {
    respondWith([]);
    const user = userEvent.setup();

    renderWithProviders(<AlumnoMisCursos />);

    expect(await screen.findByText('No estás cursando ningún curso')).toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: /Terminados/ }));
    expect(await screen.findByText('Todavía no terminaste ningún curso')).toBeInTheDocument();
  });
});
