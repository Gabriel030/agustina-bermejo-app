import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { InvitationWaveListPage } from './InvitationWaveListPage';
import { API, buildWave, springPage } from '../test/fixtures';
import { invitationWaveHandlers } from '../test/handlers';
import { renderInvitationScreen } from '../test/render';


function renderList(access) {
  return renderInvitationScreen(<InvitationWaveListPage />, { path: '/director/invitaciones', route: '/director/invitaciones', access });
}

const WAVES = [
  buildWave('RUNNING', { id: 1, name: 'Primer año 2026' }),
  buildWave('PAUSED', { id: 2, name: 'Segundo año 2026', importBatchName: 'Reinscriptos 2025' }),
  buildWave('COMPLETED', { id: 3, name: 'Cursos de verano', importBatchName: 'Cursos de verano 2026' }),
  buildWave('DRAFT', { id: 4, name: 'Ingresantes 2027', importBatchName: 'Ingresantes 2027' }),
  buildWave('CANCELLED', { id: 5, name: 'Prueba piloto', importBatchName: 'Piloto octubre' }),
];

function mockList(content = WAVES, extra = {}) {
  server.use(http.get(`${API}/invitation-waves`, () => HttpResponse.json(springPage(content, extra))));
}

describe('InvitationWaveListPage', () => {
  it('showsEveryWaveWithItsStateBatchAndProgress', async () => {
    mockList();
    renderList();

    expect(await screen.findByRole('link', { name: 'Primer año 2026' })).toHaveAttribute('href', '/director/invitaciones/1');
    expect(screen.getByText('En curso')).toBeInTheDocument();
    expect(screen.getByText('Pausada')).toBeInTheDocument();
    expect(screen.getByText('Completada')).toBeInTheDocument();
    expect(screen.getByText('Borrador')).toBeInTheDocument();
    expect(screen.getByText('Cancelada')).toBeInTheDocument();
    expect(screen.getByText('Reinscriptos 2025')).toBeInTheDocument();
    const row = screen.getByRole('link', { name: 'Primer año 2026' }).closest('tr');
    expect(within(row).getByRole('progressbar', { name: 'Avance de Primer año 2026' })).toHaveAttribute('aria-valuenow', '58');
    expect(within(row).getByText('69 de 118 invitados')).toBeInTheDocument();
  });

  it('offersNewWaveOnlyWithManagePermission', async () => {
    mockList();
    const { unmount } = renderList();
    expect(await screen.findByRole('link', { name: 'Nueva oleada' })).toHaveAttribute('href', '/director/invitaciones/nueva');
    unmount();

    renderList({ read: true, manage: false, send: false });
    await screen.findByRole('link', { name: 'Primer año 2026' });
    expect(screen.queryByRole('link', { name: 'Nueva oleada' })).not.toBeInTheDocument();
  });

  it('showsAnEmptyStateWithTheCreateActionForManagers', async () => {
    mockList([]);
    renderList();

    expect(await screen.findByText('Todavía no hay oleadas')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Nueva oleada' }).length).toBeGreaterThan(0);
  });

  it('explainsWhoCanCreateTheFirstWaveToReadOnlyUsers', async () => {
    mockList([]);
    renderList({ read: true, manage: false, send: false });

    expect(await screen.findByText('Todavía no hay oleadas')).toBeInTheDocument();
    expect(screen.getByText('Un Director o Administrativo con permiso puede crearla.')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Nueva oleada' })).not.toBeInTheDocument();
  });

  it('showsALoadingStateWhileTheListArrives', () => {
    server.use(http.get(`${API}/invitation-waves`, () => new Promise(() => {})));
    renderList();

    expect(screen.getByLabelText('Cargando')).toBeInTheDocument();
  });

  it('showsAnErrorStateWithRetry', async () => {
    server.use(http.get(`${API}/invitation-waves`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })));
    renderList();

    expect(await screen.findByText('No pudimos cargar las oleadas.')).toBeInTheDocument();
    mockList();
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('link', { name: 'Primer año 2026' })).toBeInTheDocument();
  });

  it('hidesTheListWithoutReadPermission', async () => {
    renderList({ read: false, manage: false, send: false });

    expect(await screen.findByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
    expect(screen.getByText(/notification\.read_invitation_waves/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Nueva oleada' })).not.toBeInTheDocument();
  });

  it('turnsAForbiddenAnswerIntoTheNoAccessScreen', async () => {
    server.use(http.get(`${API}/invitation-waves`, () => HttpResponse.json(errorBody('FORBIDDEN'), { status: 403 })));
    renderList();

    expect(await screen.findByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
  });

  it('pagesThroughTheWavesWithTheServerPagination', async () => {
    const requested = [];
    server.use(
      http.get(`${API}/invitation-waves`, ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get('page'));
        requested.push(page);
        return HttpResponse.json(springPage([buildWave('DRAFT', { id: 10 + page, name: `Oleada página ${page + 1}` })], { page, size: 20, total: 45 }));
      }),
    );
    renderList();

    expect(await screen.findByRole('link', { name: 'Oleada página 1' })).toBeInTheDocument();
    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Siguiente/ }));
    expect(await screen.findByRole('link', { name: 'Oleada página 2' })).toBeInTheDocument();
    await waitFor(() => expect(requested).toEqual([0, 1]));
  });

  it('listsTheDefaultHandlersWithoutAnyOverride', async () => {
    server.use(...invitationWaveHandlers({ wave: buildWave('RUNNING') }));
    renderList();

    expect(await screen.findByRole('link', { name: 'Primer año 2026' })).toBeInTheDocument();
  });
});
