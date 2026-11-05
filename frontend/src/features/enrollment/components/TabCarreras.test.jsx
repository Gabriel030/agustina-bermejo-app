import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { TabCarreras } from './TabCarreras';

const API = 'http://localhost:8080';

describe('TabCarreras', () => {
  it('shows a retryable load error and lists the items after retrying', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/programs`, () => {
        calls += 1;
        return calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json([{ id: 1, name: 'Diseño Gráfico', status: 'ACTIVE' }]);
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<TabCarreras search="" onSearchChange={() => {}} page={1} onPageChange={() => {}} />);

    expect(await screen.findByText('No pudimos cargar las carreras')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Diseño Gráfico')).toBeInTheDocument();
  });
});
