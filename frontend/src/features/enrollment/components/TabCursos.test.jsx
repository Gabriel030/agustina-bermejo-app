import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { TabCursos } from './TabCursos';

const API = 'http://localhost:8080';

describe('TabCursos', () => {
  it('shows a retryable load error and lists the items after retrying', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/courses`, () => {
        calls += 1;
        return calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json([{ id: 1, name: 'Primeros Auxilios', status: 'ACTIVE' }]);
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<TabCursos search="" onSearchChange={() => {}} page={1} onPageChange={() => {}} />);

    expect(await screen.findByText('No pudimos cargar los cursos')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Primeros Auxilios')).toBeInTheDocument();
  });
});
