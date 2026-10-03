import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
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
      await screen.findByText(/Esta carrera todavía no tiene una plantilla de aranceles/),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Crear cohorte' })).toBeDisabled();
  });

  it('offers the existing fee templates of the program', async () => {
    mockTemplates([{ id: 3, termId: 7, recurringFeeInvoicesCount: 10 }]);
    renderWithProviders(<NuevaCohortDialog open onClose={() => {}} programId={12} />);

    expect(await screen.findByRole('option', { name: '2027 — 10 cuotas' })).toBeInTheDocument();
    expect(screen.queryByText(/todavía no tiene una plantilla de aranceles/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Crear cohorte' })).toBeEnabled();
  });
});
