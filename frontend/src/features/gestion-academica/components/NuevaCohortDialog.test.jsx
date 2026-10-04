import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { NuevaCohortDialog } from './NuevaCohortDialog';

const API = 'http://localhost:8080';

function mockTemplates(templates) {
  server.use(
    http.get(`${API}/programs/12/year-billing-templates`, () => HttpResponse.json(templates)),
    http.get(`${API}/terms`, () => HttpResponse.json([{ id: 7, year: 2027, description: 'Ciclo lectivo 2027' }])),
  );
}

describe('NuevaCohortDialog', () => {
  it('explains that the program has no fee template yet and does not allow creating the cohort', async () => {
    mockTemplates([]);
    renderWithProviders(<NuevaCohortDialog open onClose={() => {}} programId={12} />);

    expect(
      await screen.findByText(/Esta carrera todavía no tiene un año lectivo configurado/),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Crear cohorte' })).toBeDisabled();
  });

  it('offers the existing fee templates of the program', async () => {
    mockTemplates([{ id: 3, termId: 7, recurringFeeInvoicesCount: 10 }]);
    renderWithProviders(<NuevaCohortDialog open onClose={() => {}} programId={12} />);

    expect(await screen.findByRole('option', { name: '2027 — 10 cuotas' })).toBeInTheDocument();
    expect(screen.queryByText(/todavía no tiene un año lectivo configurado/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Crear cohorte' })).toBeEnabled();
  });

  it('sends the chosen enrollment fee month, February by default, when creating the cohort', async () => {
    mockTemplates([{ id: 3, termId: 7, recurringFeeInvoicesCount: 10 }]);
    let body;
    server.use(
      http.post(`${API}/year-billing-templates/3/cohorts`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ id: 1 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<NuevaCohortDialog open onClose={() => {}} programId={12} />);

    await screen.findByRole('option', { name: '2027 — 10 cuotas' });
    expect(screen.queryByLabelText('Mes de matrícula')).not.toBeInTheDocument();
    const selects = document.querySelectorAll('select');
    await user.selectOptions(selects[0], '3');
    await user.selectOptions(selects[1], 'FIRST');
    const dates = document.querySelectorAll('input[type="date"]');
    await user.type(dates[0], '2026-11-01');
    await user.type(dates[1], '2026-12-15');
    await user.type(dates[2], '2027-03-01');
    await user.click(screen.getByRole('button', { name: 'Crear cohorte' }));

    await waitFor(() => expect(body).toMatchObject({ intake: 'FIRST' }));
  });
});
