import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CohortesSection } from './CohortesSection';

const API = 'http://localhost:8080';

function readinessWithoutResponsible() {
  const codes = ['CURRENT_PLAN', 'FIRST_YEAR_SECTIONS', 'SECTION_PRECEPTORS', 'COHORT_RESPONSIBLE', 'HOLIDAYS_LOADED'];
  return {
    cohortId: 5,
    open: false,
    openedAt: null,
    ready: false,
    commissionsTotal: 4,
    commissionsWithoutPreceptor: 0,
    items: codes.map((code) => ({
      code,
      blocking: true,
      fulfilled: code !== 'COHORT_RESPONSIBLE',
      reasons: code === 'COHORT_RESPONSIBLE' ? ['La cohorte no tiene responsable'] : [],
    })),
  };
}

function mockCohort(putStatus = 200, requests = []) {
  server.use(
    http.get(`${API}/programs/7/cohorts`, () =>
      HttpResponse.json([
        {
          id: 5,
          programId: 7,
          label: 'Cohorte 5',
          ordenEnAnio: 1,
          cursadaStart: '2027-03-15',
          admissionStart: '2027-02-01',
          admissionEnd: '2027-03-31',
          admissionOpen: true,
          intake: 'FIRST',
          planId: 3,
          planName: 'Plan 2024 v2',
          openedAt: null,
          responsiblePreceptors: [],
        },
      ]),
    ),
    http.get(`${API}/cohorts/5/readiness`, () => HttpResponse.json(readinessWithoutResponsible())),
    http.put(`${API}/cohorts/5/responsible-preceptors`, async ({ request }) => {
      requests.push(await request.json());
      if (putStatus !== 200) return HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: putStatus });
      return HttpResponse.json([{ id: 31, name: 'Marcela Quiroga' }]);
    }),
  );
  return requests;
}

async function openDialog(user) {
  const checklist = await screen.findByRole('region', { name: 'Para abrir la cohorte' });
  await user.click(within(checklist).getByRole('button', { name: 'Completar' }));
  return screen.findByRole('dialog');
}

describe('CohortesSection — completing the responsible', () => {
  it('does not offer "Completar" without manage permission', async () => {
    mockCohort();
    renderWithProviders(<CohortesSection programId={7} canManage={false} />);

    const checklist = await screen.findByRole('region', { name: 'Para abrir la cohorte' });
    expect(within(checklist).queryByRole('button', { name: 'Completar' })).not.toBeInTheDocument();
  });

  it('keeps "Guardar" disabled until a responsible is chosen and then sends the ids', async () => {
    const requests = mockCohort();
    const user = userEvent.setup();
    renderWithProviders(<CohortesSection programId={7} />);

    const dialog = within(await openDialog(user));
    expect(dialog.getByText('Elegí un responsable')).toBeInTheDocument();
    expect(dialog.getByRole('button', { name: 'Guardar' })).toBeDisabled();

    await user.click(dialog.getByRole('button', { name: 'Buscar preceptor' }));
    await user.click(await screen.findByRole('option', { name: /Marcela Quiroga/ }));
    await user.click(screen.getByRole('button', { name: 'Listo' }));
    await user.click(dialog.getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(requests).toEqual([{ responsiblePreceptorIds: [31] }]));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('keeps the dialog open and offers a retry when saving fails', async () => {
    mockCohort(500);
    const user = userEvent.setup();
    renderWithProviders(<CohortesSection programId={7} />);

    const dialog = within(await openDialog(user));
    await user.click(dialog.getByRole('button', { name: 'Buscar preceptor' }));
    await user.click(await screen.findByRole('option', { name: /Marcela Quiroga/ }));
    await user.click(screen.getByRole('button', { name: 'Listo' }));
    await user.click(dialog.getByRole('button', { name: 'Guardar' }));

    expect(await dialog.findByText('No pudimos guardar.')).toBeInTheDocument();
    expect(dialog.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
