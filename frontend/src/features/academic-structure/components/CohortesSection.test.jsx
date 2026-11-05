import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CohortesSection } from './CohortesSection';

const API = 'http://localhost:8080';

describe('CohortesSection — load failure', () => {
  it('shows the error state, blocks creating a cohort and reloads on retry', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/programs/7/cohorts`, () => {
        calls += 1;
        return calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json([]);
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<CohortesSection programId={7} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar las cohortes');
    expect(screen.getByRole('button', { name: /Nueva cohorte/ })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Sin cohortes')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
