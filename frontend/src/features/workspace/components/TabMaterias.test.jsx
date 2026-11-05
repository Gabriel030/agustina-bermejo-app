import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { TabMaterias } from './TabMaterias';

const API = 'http://localhost:8080';

describe('TabMaterias — load failure', () => {
  it('shows the error state and reloads the enrollments on retry', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/me/section-enrollments`, () => {
        calls += 1;
        return calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json([]);
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<TabMaterias carreraId={5} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar tus materias');
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('No tenés materias en curso')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
