import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route, useLocation } from 'react-router-dom';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { StaffSectionAnnouncements } from './StaffSectionAnnouncements';
import { ANNOUNCEMENTS, PROGRAM_CYCLE, SECTIONS } from './staffReadReceiptsFixtures';

const API = 'http://localhost:8080';

function mockBackend({ announcements = ANNOUNCEMENTS, status = 200 } = {}) {
  server.use(
    http.get(`${API}/academic-offering-cycles/15`, () => HttpResponse.json(PROGRAM_CYCLE)),
    http.get(`${API}/academic-offering-cycles/15/sections`, () => HttpResponse.json(SECTIONS)),
    http.get(`${API}/subject-sections/31/announcements/read-receipts`, () =>
      status === 200 ? HttpResponse.json(announcements) : HttpResponse.json(errorBody('error'), { status }),
    ),
  );
}

function CurrentLocation() {
  return <div data-testid="location">{useLocation().pathname}</div>;
}

function renderList(role = 'preceptor') {
  return renderWithProviders(
    <>
      <Routes>
        <Route path={`/${role}/oferta-academica/ciclos/:cycleId/comisiones/:sectionId/avisos`} element={<StaffSectionAnnouncements />} />
        <Route path="*" element={null} />
      </Routes>
      <CurrentLocation />
    </>,
    { route: `/${role}/oferta-academica/ciclos/15/comisiones/31/avisos` },
  );
}

describe('StaffSectionAnnouncements', () => {
  it('lists the announcements with status, dispatch date and opened share', async () => {
    mockBackend();
    renderList();

    const row = (await screen.findByText('Cambio de aula para el parcial')).closest('tr');
    expect(within(row).getByText('Urgente')).toBeInTheDocument();
    expect(within(row).getByText('Enviado')).toBeInTheDocument();
    expect(within(row).getByText('18 de 22')).toBeInTheDocument();
    expect(screen.getByText('Historia del Arte · Com. B · Mar 18:30 · Prof. Peña')).toBeInTheDocument();
  });

  it('shows scheduled announcements as calculated on send and untracked ones as without record', async () => {
    mockBackend();
    renderList();

    const scheduled = (await screen.findByText('Visita al museo')).closest('tr');
    expect(within(scheduled).getByText('Programado')).toBeInTheDocument();
    expect(within(scheduled).getByText('Se calcula al enviar')).toBeInTheDocument();
    const untracked = screen.getByText('Programa de la materia').closest('tr');
    expect(within(untracked).getByText('Sin registro')).toBeInTheDocument();
    expect(within(untracked).queryByRole('link')).not.toBeInTheDocument();
  });

  it('opens the readers page from the link of a tracked announcement', async () => {
    mockBackend();
    renderList('director');
    await userEvent.click(await screen.findByRole('link', { name: 'Quién lo abrió de Material de la unidad 3' }));

    expect(screen.getByTestId('location')).toHaveTextContent('/director/oferta-academica/ciclos/15/comisiones/31/avisos/2');
  });

  it('shows the empty state when the section has no announcements', async () => {
    mockBackend({ announcements: [] });
    renderList();

    expect(await screen.findByText('Esta comisión todavía no tiene avisos')).toBeInTheDocument();
  });

  it('shows a loading skeleton while the announcements arrive', () => {
    mockBackend();
    renderList();

    expect(screen.getByLabelText('Cargando')).toBeInTheDocument();
  });

  it('offers a retry when the announcements fail to load', async () => {
    mockBackend({ status: 500 });
    renderList();

    expect(await screen.findByText('No pudimos cargar los avisos')).toBeInTheDocument();
    mockBackend();
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Material de la unidad 3')).toBeInTheDocument();
  });
});
