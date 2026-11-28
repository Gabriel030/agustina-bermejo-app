import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CohortesSection } from './CohortesSection';

const API = 'http://localhost:8080';

function readinessOf(cohortId, { open = false, missing = [] } = {}) {
  const codes = ['CURRENT_PLAN', 'FIRST_YEAR_SECTIONS', 'SECTION_PRECEPTORS', 'COHORT_RESPONSIBLE', 'HOLIDAYS_LOADED'];
  return {
    cohortId,
    open,
    openedAt: open ? '2026-08-01T10:00:00Z' : null,
    ready: missing.length === 0,
    commissionsTotal: 4,
    commissionsWithoutPreceptor: missing.includes('SECTION_PRECEPTORS') ? 1 : 0,
    items: codes.map((code) => ({
      code,
      blocking: code !== 'COHORT_RESPONSIBLE',
      fulfilled: !missing.includes(code),
      reasons: missing.includes(code) ? ['Programación I (A): sin preceptor'] : [],
    })),
  };
}

function mockCohorts(readinessByCohort) {
  server.use(
    http.get(`${API}/programs/7/cohorts`, () =>
      HttpResponse.json(
        Object.keys(readinessByCohort).map((id) => ({
          id: Number(id),
          programId: 7,
          label: `Cohorte ${id}`,
          ordenEnAnio: 1,
          cursadaStart: '2027-03-15',
          admissionStart: '2027-02-01',
          admissionEnd: '2027-03-31',
          admissionOpen: true,
          intake: 'FIRST',
          planId: 3,
          planName: 'Plan 2024 v2',
          openedAt: null,
          responsiblePreceptors: [{ id: 31, name: 'Marcela Quiroga' }, { id: 32, name: 'Julián Ferreyra' }],
        })),
      ),
    ),
    http.get(`${API}/cohorts/:id/readiness`, ({ params }) => HttpResponse.json(readinessByCohort[params.id])),
  );
}

describe('CohortesSection — opening a cohort', () => {
  it('shows the checklist with what is missing and keeps "Abrir" disabled', async () => {
    mockCohorts({ 5: readinessOf(5, { missing: ['COHORT_RESPONSIBLE', 'SECTION_PRECEPTORS'] }) });
    renderWithProviders(<CohortesSection programId={7} />);

    expect(await screen.findByText('Incompleta')).toBeInTheDocument();
    const checklist = screen.getByRole('region', { name: 'Para abrir la cohorte' });
    expect(within(checklist).getByText('3 de 5')).toBeInTheDocument();
    expect(within(checklist).getByRole('link', { name: 'Ir a comisiones' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Abrir' })).toBeDisabled();
  });

  it('enables "Abrir" when everything is complete and calls the open endpoint after confirming', async () => {
    mockCohorts({ 6: readinessOf(6) });
    let opened = 0;
    server.use(
      http.post(`${API}/cohorts/6/open`, () => {
        opened += 1;
        return HttpResponse.json({
          id: 6,
          programId: 7,
          termId: 1,
          planId: 1,
          intake: 'FIRST',
          admissionStart: '2027-02-01',
          admissionEnd: '2027-03-31',
          cursadaStart: '2027-03-15',
          createdAt: '2027-01-10T12:00:00Z',
          openedAt: '2027-02-01T12:00:00Z',
        });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<CohortesSection programId={7} />);

    expect(await screen.findByText('Lista para abrir')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Para abrir la cohorte' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Abrir' }));
    await user.click(await screen.findByRole('button', { name: 'Abrir cohorte' }));

    await waitFor(() => expect(opened).toBe(1));
  });

  it('shows a notice and keeps the cohort unopened when opening fails', async () => {
    mockCohorts({ 6: readinessOf(6) });
    server.use(http.post(`${API}/cohorts/6/open`, () => new HttpResponse(null, { status: 500 })));
    const user = userEvent.setup();
    renderWithProviders(<CohortesSection programId={7} />);

    await user.click(await screen.findByRole('button', { name: 'Abrir' }));
    await user.click(await screen.findByRole('button', { name: 'Abrir cohorte' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos abrir la cohorte.');
    expect(screen.getByRole('alert')).toHaveTextContent('No se hizo ningún cambio.');
    expect(screen.getByText('Lista para abrir')).toBeInTheDocument();
  });

  it('marks an open cohort as "Abierta" without an open button or checklist', async () => {
    mockCohorts({ 8: readinessOf(8, { open: true }) });
    renderWithProviders(<CohortesSection programId={7} />);

    expect(await screen.findByText('Abierta')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Abrir' })).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Para abrir la cohorte' })).not.toBeInTheDocument();
  });

  it('hides every editing action when the user cannot manage cohorts', async () => {
    mockCohorts({ 5: readinessOf(5, { missing: ['SECTION_PRECEPTORS'] }) });
    renderWithProviders(<CohortesSection programId={7} canManage={false} />);

    expect(await screen.findByText('Incompleta')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Abrir' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Nueva cohorte/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Ir a comisiones' })).not.toBeInTheDocument();
  });

  it('shows the empty state when the program has no cohorts', async () => {
    server.use(http.get(`${API}/programs/7/cohorts`, () => HttpResponse.json([])));
    renderWithProviders(<CohortesSection programId={7} />);

    expect(await screen.findByText('Sin cohortes')).toBeInTheDocument();
  });

  it('shows a loading placeholder before the cohorts arrive', () => {
    server.use(http.get(`${API}/programs/7/cohorts`, () => new Promise(() => {})));
    renderWithProviders(<CohortesSection programId={7} />);

    expect(screen.queryByText('Sin cohortes')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Nueva cohorte/ })).toBeEnabled();
  });

  it('shows the responsible preceptors and the plan name from the cohort listing', async () => {
    mockCohorts({ 5: readinessOf(5) });
    renderWithProviders(<CohortesSection programId={7} />);

    expect(await screen.findByText('Marcela Quiroga, Julián Ferreyra')).toBeInTheDocument();
    expect(screen.getByText('Plan 2024 v2')).toBeInTheDocument();
  });
});
