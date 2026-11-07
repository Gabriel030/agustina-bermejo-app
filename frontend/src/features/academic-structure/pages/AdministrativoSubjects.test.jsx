import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { AdministrativoSubjects } from './AdministrativoSubjects';
import { subjectFixture } from '@/test/fixtures/subjects';

const API = 'http://localhost:8080';

describe('AdministrativoSubjects', () => {
  it('lists rejected subjects in their own tab so they can be fixed and resubmitted', async () => {
    server.use(
      http.get(`${API}/subjects`, ({ request }) => {
        const status = new URL(request.url).searchParams.get('status');
        if (status === 'REJECTED') {
          return HttpResponse.json([
            subjectFixture({ id: 124, code: 'S119', name: 'Gestión de Datos Clínicos', status: 'REJECTED', rejectionReason: 'Falta bibliografía' }),
          ]);
        }
        return HttpResponse.json([]);
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<AdministrativoSubjects />, { route: '/administrativo/gestion-academica' });

    await user.click(await screen.findByRole('tab', { name: 'Rechazadas' }));

    expect(await screen.findByText('Gestión de Datos Clínicos')).toBeInTheDocument();
    expect(screen.getByText('Rechazada')).toBeInTheDocument();
  });

  it('shows a retryable load error when the subject list fails', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/subjects`, () => {
        calls += 1;
        return calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json([]);
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<AdministrativoSubjects />, { route: '/administrativo/gestion-academica' });

    expect(await screen.findByText('No pudimos cargar las materias')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    await waitFor(() => expect(screen.queryByText('No pudimos cargar las materias')).not.toBeInTheDocument());
  });

  it('finds a subject searching without accents', async () => {
    server.use(
      http.get(`${API}/subjects`, () =>
        HttpResponse.json([
          subjectFixture({ id: 1, code: 'MAT1', name: 'Matemática I', status: 'ACTIVE', gradeMode: 'NUMERIC' }),
          subjectFixture({ id: 2, code: 'ANAT', name: 'Anatomía', status: 'ACTIVE', gradeMode: 'NUMERIC' }),
        ]),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(<AdministrativoSubjects />, { route: '/administrativo/gestion-academica' });
    await screen.findByText('Matemática I');

    await user.type(screen.getByRole('textbox', { name: 'Buscar' }), 'matematica');

    expect(screen.getByText('Matemática I')).toBeInTheDocument();
    expect(screen.queryByText('Anatomía')).not.toBeInTheDocument();
  });

  it('paginates the bank and filters by grading mode', async () => {
    const subjects = Array.from({ length: 12 }, (_, index) => subjectFixture({
      id: index + 1,
      code: `S${String(index + 1).padStart(2, '0')}`,
      name: `Materia ${String(index + 1).padStart(2, '0')}`,
      status: 'ACTIVE',
      gradeMode: index === 11 ? 'PASS_FAIL' : 'NUMERIC',
    }));
    server.use(http.get(`${API}/subjects`, () => HttpResponse.json(subjects)));
    const user = userEvent.setup();
    renderWithProviders(<AdministrativoSubjects />, { route: '/administrativo/gestion-academica' });

    expect(await screen.findByText('Materia 01')).toBeInTheDocument();
    expect(screen.queryByText('Materia 11')).not.toBeInTheDocument();
    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Siguiente/ }));
    expect(await screen.findByText('Materia 11')).toBeInTheDocument();
    expect(screen.queryByText('Materia 01')).not.toBeInTheDocument();
  });

  it('reactivates an inactive subject from its row', async () => {
    let reactivated = 0;
    server.use(
      http.get(`${API}/subjects`, ({ request }) =>
        HttpResponse.json(
          new URL(request.url).searchParams.get('status') === 'INACTIVE'
            ? [subjectFixture({ id: 9, code: 'OLD1', name: 'Histología', status: 'INACTIVE', gradeMode: 'NUMERIC' })]
            : [],
        ),
      ),
      http.post(`${API}/subjects/9/activate`, () => {
        reactivated += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<AdministrativoSubjects />, { route: '/administrativo/gestion-academica' });
    await user.click(await screen.findByRole('tab', { name: 'Inactivas' }));

    await user.click(await screen.findByRole('button', { name: 'Reactivar' }));

    await waitFor(() => expect(reactivated).toBe(1));
  });

  it('has no accessibility violations in the bank table', async () => {
    server.use(
      http.get(`${API}/subjects`, () =>
        HttpResponse.json([subjectFixture({ id: 1, code: 'MAT1', name: 'Matemática I', status: 'ACTIVE', gradeMode: 'NUMERIC', weeklyHours: 3 })]),
      ),
    );
    const { container } = renderWithProviders(<AdministrativoSubjects />, { route: '/administrativo/gestion-academica' });
    await screen.findByText('Matemática I');

    expect(await axe(container.querySelector('table'))).toHaveNoViolations();
  });

  it('returns to the subject tab the user left when coming back to the bank', async () => {
    server.use(http.get(`${API}/subjects`, () => HttpResponse.json([])));
    const user = userEvent.setup();
    renderWithProviders(<AdministrativoSubjects />, { route: '/administrativo/gestion-academica' });

    await user.click(await screen.findByRole('tab', { name: 'Inactivas' }));
    await user.click(screen.getByRole('tab', { name: 'Carreras' }));
    await user.click(screen.getByRole('tab', { name: 'Banco de materias' }));

    expect(await screen.findByRole('tab', { name: 'Inactivas' })).toHaveAttribute('aria-selected', 'true');
  });
});
