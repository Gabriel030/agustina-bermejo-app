import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { StaffAnnouncementReaders } from './StaffAnnouncementReaders';
import { ANNOUNCEMENTS, PROGRAM_CYCLE, RECIPIENTS, SECTIONS, manyRecipients } from './staffReadReceiptsFixtures';

const API = 'http://localhost:8080';

function mockBackend({ recipients = RECIPIENTS, recipientsStatus = 200 } = {}) {
  server.use(
    http.get(`${API}/academic-offering-cycles/15`, () => HttpResponse.json(PROGRAM_CYCLE)),
    http.get(`${API}/academic-offering-cycles/15/sections`, () => HttpResponse.json(SECTIONS)),
    http.get(`${API}/subject-sections/31/announcements/read-receipts`, () => HttpResponse.json(ANNOUNCEMENTS)),
    http.get(`${API}/announcements/:id/recipients`, () =>
      recipientsStatus === 200 ? HttpResponse.json(recipients) : HttpResponse.json(errorBody('error'), { status: recipientsStatus }),
    ),
  );
}

function renderReaders(announcementId = 1) {
  return renderWithProviders(
    <Routes>
      <Route
        path="/director/oferta-academica/ciclos/:cycleId/comisiones/:sectionId/avisos/:announcementId"
        element={<StaffAnnouncementReaders />}
      />
    </Routes>,
    { route: `/director/oferta-academica/ciclos/15/comisiones/31/avisos/${announcementId}` },
  );
}

describe('StaffAnnouncementReaders', () => {
  it('starts on the unopened tab when someone has not opened it', async () => {
    mockBackend();
    renderReaders();

    expect(await screen.findByText('Acosta, Joaquín')).toBeInTheDocument();
    expect(screen.getByText('Cabrera, Julieta')).toBeInTheDocument();
    expect(screen.queryByText('Benítez, Lucía')).not.toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Sin abrir/ })).toHaveAttribute('aria-selected', 'true');
  });

  it('starts on the opened tab when everybody opened it and shows when', async () => {
    mockBackend({ recipients: [RECIPIENTS[1]] });
    renderReaders();

    const row = (await screen.findByText('Benítez, Lucía')).closest('tr');
    expect(screen.getByRole('tab', { name: /Lo abrieron/ })).toHaveAttribute('aria-selected', 'true');
    expect(within(row).getByText(/03\/11\/2026/)).toBeInTheDocument();
  });

  it('switches tabs and links each student to the record', async () => {
    mockBackend();
    renderReaders();
    await screen.findByText('Acosta, Joaquín');
    await userEvent.click(screen.getByRole('tab', { name: /Lo abrieron/ }));

    expect(screen.getByRole('link', { name: 'Benítez, Lucía' })).toHaveAttribute('href', '/director/alumnos/2');
    expect(screen.queryByText('Acosta, Joaquín')).not.toBeInTheDocument();
  });

  it('filters by name or file and reports when nothing matches', async () => {
    mockBackend();
    renderReaders();
    await screen.findByText('Acosta, Joaquín');
    const search = screen.getByPlaceholderText('Buscar por nombre o legajo');

    await userEvent.type(search, 'cabrera');
    expect(screen.queryByText('Acosta, Joaquín')).not.toBeInTheDocument();
    expect(screen.getByText('Cabrera, Julieta')).toBeInTheDocument();

    await userEvent.clear(search);
    await userEvent.type(search, 'zzz');
    expect(screen.getByText('Sin resultados')).toBeInTheDocument();
  });

  it('paginates ten recipients per page', async () => {
    mockBackend({ recipients: manyRecipients(23) });
    renderReaders();

    expect(await screen.findByText('Alumno 01, Test')).toBeInTheDocument();
    expect(screen.queryByText('Alumno 11, Test')).not.toBeInTheDocument();
    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Siguiente/ }));
    expect(screen.getByText('Alumno 11, Test')).toBeInTheDocument();
  });

  it('tells that everybody opened it when the unopened tab is emptied by the data', async () => {
    mockBackend({ recipients: [RECIPIENTS[1]] });
    renderReaders();
    await screen.findByText('Benítez, Lucía');
    await userEvent.click(screen.getByRole('tab', { name: /Sin abrir/ }));

    expect(screen.getByText('Lo abrieron todos')).toBeInTheDocument();
  });

  it('tells that nobody opened it yet on the opened tab', async () => {
    mockBackend({ recipients: [RECIPIENTS[0]] });
    renderReaders();
    await screen.findByText('Acosta, Joaquín');
    await userEvent.click(screen.getByRole('tab', { name: /Lo abrieron/ }));

    expect(screen.getByText('Todavía nadie lo abrió')).toBeInTheDocument();
  });

  it('explains there is no record for announcements sent before read receipts', async () => {
    mockBackend();
    renderReaders(4);

    expect(await screen.findByText('Este aviso no tiene registro de aperturas')).toBeInTheDocument();
  });

  it('shows the empty state when the announcement had no recipients', async () => {
    mockBackend({ recipients: [] });
    renderReaders();

    expect(await screen.findByText('Este aviso no tuvo destinatarios')).toBeInTheDocument();
  });

  it('offers a retry when the recipients fail to load', async () => {
    mockBackend({ recipientsStatus: 500 });
    renderReaders();

    expect(await screen.findByText('No pudimos cargar quién lo abrió')).toBeInTheDocument();
    mockBackend();
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Acosta, Joaquín')).toBeInTheDocument();
  });

  it('shows not found for an announcement that is not in the section', async () => {
    mockBackend();
    renderReaders(99);

    expect(await screen.findByText('No encontramos lo que buscabas')).toBeInTheDocument();
  });
});
