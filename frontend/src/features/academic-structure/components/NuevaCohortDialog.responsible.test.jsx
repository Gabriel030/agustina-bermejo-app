import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { NuevaCohortDialog } from './NuevaCohortDialog';
import { termFixture } from '@/test/fixtures/terms';

const API = 'http://localhost:8080';

async function fillCohortForm(user) {
  await screen.findByRole('option', { name: '2027 — 10 cuotas' });
  const selects = document.querySelectorAll('select');
  await user.selectOptions(selects[0], '3');
  await user.selectOptions(selects[1], 'FIRST');
  const dates = document.querySelectorAll('input[type="date"]');
  await user.type(dates[0], '2026-11-01');
  await user.type(dates[1], '2026-12-15');
  await user.type(dates[2], '2027-03-01');
}

function mockCreation() {
  const requests = [];
  server.use(
    http.get(`${API}/programs/12/year-billing-templates`, () =>
      HttpResponse.json([{ id: 3, termId: 7, recurringFeeInvoicesCount: 10 }]),
    ),
    http.get(`${API}/terms`, () => HttpResponse.json([termFixture({ id: 7, year: 2027, description: 'Ciclo lectivo 2027' })])),
    http.post(`${API}/year-billing-templates/3/cohorts`, async ({ request }) => {
      requests.push(await request.json());
      return HttpResponse.json(
        {
          id: 1,
          planId: 21,
          intake: 'FIRST',
          admissionStart: '2026-11-01',
          admissionEnd: '2026-12-15',
          cursadaStart: '2027-03-01',
          createdAt: '2027-01-10T12:00:00Z',
          createdByName: 'Ana Paz',
        },
        { status: 201 },
      );
    }),
  );
  return requests;
}

describe('NuevaCohortDialog — responsible preceptors', () => {
  it('shows an error and does not send the request when no preceptor was chosen', async () => {
    const requests = mockCreation();
    const user = userEvent.setup();
    renderWithProviders(<NuevaCohortDialog open onClose={() => {}} programId={12} />);

    await fillCohortForm(user);
    await user.click(screen.getByRole('button', { name: 'Crear cohorte' }));

    expect(await screen.findByText('Elegí al menos un preceptor.')).toBeInTheDocument();
    expect(requests).toHaveLength(0);
  });

  it('sends the ids of the chosen preceptors when creating the cohort', async () => {
    const requests = mockCreation();
    const user = userEvent.setup();
    renderWithProviders(<NuevaCohortDialog open onClose={() => {}} programId={12} />);

    await fillCohortForm(user);
    await user.click(screen.getByRole('button', { name: 'Buscar preceptor' }));
    await user.click(await screen.findByRole('option', { name: /Marcela Quiroga/ }));
    await user.click(screen.getByRole('option', { name: /Julián Ferreyra/ }));
    await user.click(screen.getByRole('button', { name: 'Listo' }));
    await user.click(screen.getByRole('button', { name: 'Crear cohorte' }));

    await waitFor(() => expect(requests).toHaveLength(1));
    expect(requests[0].responsiblePreceptorIds).toEqual([31, 32]);
  });
});
