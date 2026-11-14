import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes, useLocation } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { termFixture } from '@/test/fixtures/terms';
import {
  closedClosureListItem,
  closureListItem,
  closureListPage,
  closureListPath,
  mockClosureList,
  pendingClosureListItem,
} from '@/test/fixtures/closureList';
import { ClosureListPage } from './ClosureListPage';

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>;
}

function renderList(route = '/administrativo/cierres-de-comision', role = 'administrativo') {
  server.use(http.get('http://localhost:8080/terms', () => HttpResponse.json([termFixture({ id: 5, description: '1.er cuatrimestre 2026' })])));
  return renderWithProviders(
    <>
      <Routes>
        <Route path="/:role/cierres-de-comision" element={<ClosureListPage role={role} />} />
      </Routes>
      <LocationProbe />
    </>,
    { route },
  );
}

const threeItems = () => [closureListItem(), pendingClosureListItem(), closedClosureListItem()];

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ClosureListPage', () => {
  it('showsBackendCountsInTheKpiCardsAndOnePillPerRow', async () => {
    mockClosureList(server, () => closureListPage(threeItems(), { counts: { all: 14, teacherConfirmed: 4, draftAvailable: 6, closed: 3, reopened: 1 } }));
    renderList();

    expect(await screen.findByRole('button', { name: '14 Todas' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '4 Para aprobar' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '6 Pendientes' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '3 Cerradas' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '1 Reabiertas' })).toBeInTheDocument();
    const row = screen.getByRole('rowheader', { name: /Teorías Socioculturales/ }).closest('tr');
    expect(within(row).getAllByText('Confirmada por el docente')).toHaveLength(1);
    expect(within(row).getByText('1.er cuatrimestre 2026')).toBeInTheDocument();
  });

  it('choosingAKpiCardFiltersByStatusThroughTheUrlAndTogglesBack', async () => {
    const requests = mockClosureList(server, () => closureListPage(threeItems()));
    renderList();
    await screen.findByRole('button', { name: '3 Todas' });

    await userEvent.click(screen.getByRole('button', { name: '1 Cerradas' }));

    await waitFor(() => expect(requests.at(-1)).toMatchObject({ status: 'CLOSED', page: '0', size: '10' }));
    expect(screen.getByTestId('location')).toHaveTextContent('?status=CLOSED');
    expect(screen.getByRole('button', { name: '1 Cerradas' })).toHaveAttribute('aria-pressed', 'true');

    await userEvent.click(screen.getByRole('button', { name: '1 Cerradas' }));

    await waitFor(() => expect(requests.at(-1)).not.toHaveProperty('status'));
    expect(screen.getByTestId('location')).not.toHaveTextContent('status');
  });

  it('readsTheFiltersFromTheUrlAndSendsThemToTheBackend', async () => {
    const requests = mockClosureList(server, () => closureListPage(threeItems()));
    renderList('/administrativo/cierres-de-comision?status=REOPENED&termId=5&q=salud&page=2');

    await screen.findByRole('button', { name: '3 Todas' });

    expect(requests[0]).toEqual({ status: 'REOPENED', termId: '5', q: 'salud', page: '1', size: '10' });
    expect(screen.getByRole('button', { name: '0 Reabiertas' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('searchbox')).toHaveValue('salud');
  });

  it('choosingATermFiltersByItAndResetsThePage', async () => {
    const requests = mockClosureList(server, () => closureListPage(threeItems(), { totalElements: 25 }));
    renderList('/administrativo/cierres-de-comision?page=3');
    await screen.findByRole('button', { name: '3 Todas' });
    await screen.findByRole('option', { name: '1.er cuatrimestre 2026' });

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Término' }), '5');

    await waitFor(() => expect(requests.at(-1)).toMatchObject({ termId: '5', page: '0' }));
  });

  it('searchingByTextSendsTheQueryAndClearFiltersRestoresTheList', async () => {
    const requests = mockClosureList(server, () => closureListPage(threeItems()));
    renderList();
    await screen.findByRole('button', { name: '3 Todas' });
    expect(screen.queryByRole('button', { name: 'Limpiar filtros' })).not.toBeInTheDocument();

    await userEvent.type(screen.getByRole('searchbox'), 'ética');

    await waitFor(() => expect(requests.at(-1)).toMatchObject({ q: 'ética' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Limpiar filtros' }));

    await waitFor(() => expect(requests.at(-1)).not.toHaveProperty('q'));
    expect(screen.getByRole('searchbox')).toHaveValue('');
  });

  it('rendersOneCardPerRowOnMobileWithStatusAndTheSheetLink', async () => {
    vi.spyOn(window, 'matchMedia').mockImplementation(() => ({ matches: true, addEventListener() {}, removeEventListener() {} }));
    mockClosureList(server, () => closureListPage(threeItems()));
    renderList();

    await screen.findByRole('link', { name: 'Abrir planilla de Teorías Socioculturales, Comisión A · Mañana' });
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getAllByText('Estado').length).toBeGreaterThanOrEqual(3);
    expect(screen.getAllByRole('link', { name: /Abrir planilla|Ver acta/ })).toHaveLength(3);
  });

  it('doesNotForceAHorizontalMinimumWidthOnDesktop', async () => {
    mockClosureList(server, () => closureListPage(threeItems()));
    renderList();

    await screen.findByRole('table');
    expect(screen.getByRole('table').style.minWidth).not.toBe('1160px');
  });

  it('pagesThroughTheBackendTotal', async () => {
    const requests = mockClosureList(server, () => closureListPage(threeItems(), { totalElements: 25 }));
    renderList();
    await screen.findByText('Página 1 de 3');

    await userEvent.click(screen.getByRole('button', { name: /Siguiente/ }));

    await waitFor(() => expect(requests.at(-1)).toMatchObject({ page: '1' }));
    expect(await screen.findByText('Página 2 de 3')).toBeInTheDocument();
  });

  it('linksEachRowToItsSheetWithTheSectionKindAndShowsViewReportOnClosedRows', async () => {
    mockClosureList(server, () => closureListPage(threeItems()));
    renderList('/director/cierres-de-comision', 'director');

    const open = await screen.findByRole('link', { name: 'Abrir planilla de Teorías Socioculturales, Comisión A · Mañana' });
    expect(open).toHaveAttribute('href', '/director/cierre-de-comision/101?type=PROGRAM');
    const viewReport = screen.getByRole('link', { name: /Ver acta de Ética Profesional/ });
    expect(viewReport).toHaveAttribute('href', '/director/cierre-de-comision/109?type=PROGRAM');
    const closedRow = screen.getByRole('rowheader', { name: /Ética Profesional/ }).closest('tr');
    expect(within(closedRow).getByText('ACTA-2026-0042')).toBeInTheDocument();
    expect(within(closedRow).getByText('Libro L28 · Folio 93')).toBeInTheDocument();
  });

  it('showsDashesForPendingRowsWithoutSnapshotAndTheFirstInstructorWithTheRest', async () => {
    mockClosureList(server, () =>
      closureListPage([pendingClosureListItem({ instructorNames: ['A. Pérez', 'B. Gómez', 'C. Díaz'] })]),
    );
    renderList();

    const row = (await screen.findByRole('rowheader', { name: /Psicología Social/ })).closest('tr');

    expect(within(row).getByText('A. Pérez +2')).toBeInTheDocument();
    expect(within(row).getByText('Pendiente del docente')).toBeInTheDocument();
    expect(within(row).getByText('sin entregar')).toBeInTheDocument();
    expect(within(row).getAllByText('—').length).toBeGreaterThanOrEqual(3);
  });

  it('showsTheEmptyStateWhenNothingMatches', async () => {
    mockClosureList(server, () => closureListPage([], { counts: { all: 0, teacherConfirmed: 0, draftAvailable: 0, closed: 0, reopened: 0 } }));
    renderList('/administrativo/cierres-de-comision?q=zzz');

    expect(await screen.findByText('Ninguna comisión coincide con la búsqueda.')).toBeInTheDocument();
  });

  it('showsTheLoadErrorWithRetry', async () => {
    server.use(http.get(closureListPath, () => new HttpResponse(null, { status: 500 })));
    renderList();

    expect(await screen.findByText('No pudimos cargar los cierres de comisión')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
