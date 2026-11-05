import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route, useLocation } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CarreraDetalle } from './CarreraDetalle';

vi.setConfig({ testTimeout: 20000 });

const API = 'http://localhost:8080';

function CurrentSearch() {
  return <span data-testid="search">{useLocation().search}</span>;
}

function mount(search) {
  server.use(
    http.get(`${API}/programs/9`, () => HttpResponse.json({ id: 9, name: 'Enfermería', status: 'ACTIVE', totalYears: 3 })),
    http.get(`${API}/programs/9/subjects`, () => HttpResponse.json([])),
  );
  return renderWithProviders(
    <>
      <Routes>
        <Route path="/administrativo/gestion-academica/carreras/:id" element={<CarreraDetalle />} />
      </Routes>
      <CurrentSearch />
    </>,
    { route: `/administrativo/gestion-academica/carreras/9${search}` },
  );
}

describe('CarreraDetalle — tab in the URL', () => {
  it('opens the tab requested in the URL', async () => {
    mount('?tab=cohortes');

    expect(await screen.findByRole('tab', { name: 'Cohortes' })).toHaveAttribute('aria-selected', 'true');
  });

  it('falls back to the study plan on an unknown tab', async () => {
    mount('?tab=inexistente');

    expect(await screen.findByRole('tab', { name: 'Plan de estudios' })).toHaveAttribute('aria-selected', 'true');
  });

  it('writes the tab to the URL when the user changes it', async () => {
    const user = userEvent.setup();
    mount('');

    await user.click(await screen.findByRole('tab', { name: 'Años lectivos' }));

    expect(screen.getByTestId('search')).toHaveTextContent('?tab=anios');
  });
});
