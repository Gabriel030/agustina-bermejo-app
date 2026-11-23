import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { installFixedClock } from '@/test/fixedClock';
import { InvitationWaveDetailPage } from './InvitationWaveDetailPage';
import { API, SETTINGS, buildWave, springPage } from '../test/fixtures';
import { invitationWaveHandlers, lifecycleHandlers, sampleRecipients } from '../test/handlers';
import { dropUnpublishedContractDeviations, renderInvitationScreen } from '../test/render';

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', () => ({ toast: toastMock }));
const saveBlobMock = vi.hoisted(() => vi.fn());
vi.mock('@/lib/saveBlob', () => ({ saveBlob: saveBlobMock }));

dropUnpublishedContractDeviations();
installFixedClock({ now: '2026-11-23T12:00:00-03:00', advanceWithRealTime: true });

beforeEach(() => {
  toastMock.success.mockClear();
  toastMock.error.mockClear();
  saveBlobMock.mockClear();
});

const PATH = '/director/invitaciones/:waveId';

function renderDetail({ wave, recipients = sampleRecipients(), access, route = '/director/invitaciones/1', setup, extraHandlers = [] } = {}) {
  const initial = wave ?? buildWave('RUNNING');
  const lifecycle = lifecycleHandlers(initial, { recipients, overrides: setup?.overrides });
  server.use(...extraHandlers, ...lifecycle.handlers);
  const view = renderInvitationScreen(<InvitationWaveDetailPage />, { path: PATH, route, access });
  return { ...view, lifecycle };
}

const desktopActions = () => screen.getByRole('group', { name: 'Acciones de la oleada' });
const FULL = { read: true, manage: true, send: true };

describe('InvitationWaveDetailPage by state', () => {
  it.each([
    ['DRAFT', 'Borrador', /Borrador: todavía no se envió ningún mail/, ['Iniciar oleada', 'Exportar reporte CSV', 'Cancelar oleada']],
    ['RUNNING', 'En curso', /En curso: la próxima tanda de 30 mails sale hoy a las 14:30/, ['Pausar', 'Reintentar fallidos (6)', 'Exportar reporte CSV', 'Cancelar oleada']],
    ['PAUSED', 'Pausada', /Pausada: no se envía nada hasta que la reanudes/, ['Reanudar', 'Reintentar fallidos (6)', 'Exportar reporte CSV', 'Cancelar oleada']],
    ['COMPLETED', 'Completada', /Completada: se procesaron todos los destinatarios/, ['Exportar reporte CSV']],
    ['CANCELLED', 'Cancelada', /Cancelada el 23\/11\/2026\. Los pendientes quedaron liberados/, ['Exportar reporte CSV']],
  ])('%s shows its banner and only the actions that make sense', async (status, label, banner, actions) => {
    renderDetail({ wave: buildWave(status) });

    await screen.findByRole('heading', { level: 1, name: 'Primer año 2026' });
    expect(screen.getAllByText(label).length).toBeGreaterThan(0);
    expect(screen.getByText(banner)).toBeInTheDocument();
    const names = within(desktopActions()).getAllByRole('button').map((button) => button.textContent);
    expect(names).toEqual(actions);
  });

  it('offersRetryOnACompletedWaveOnlyWhenSomethingFailed', async () => {
    renderDetail({ wave: buildWave('COMPLETED', { failedTotal: 2 }) });

    await screen.findByRole('heading', { level: 1 });
    expect(within(desktopActions()).getByRole('button', { name: 'Reintentar fallidos (2)' })).toBeInTheDocument();
  });

  it('describesTheDraftWithTheBatchAndTheCreationDate', async () => {
    renderDetail({ wave: buildWave('DRAFT') });

    expect(await screen.findByText('Lote: Carga inicial 2026 · Creada el 18/11/2026')).toBeInTheDocument();
  });

  it('showsTheRunningRhythmAndTheProgressFromSentPlusActivated', async () => {
    renderDetail({ wave: buildWave('RUNNING') });

    expect(await screen.findByText('Tandas de 30 · cada 60 min · tope 120 por día')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Avance de la oleada' })).toHaveAttribute('aria-valuenow', '58');
    expect(screen.getByText('69 de 118 invitados')).toBeInTheDocument();
  });

  it('saysWhenTheNextBatchIsNotToday', async () => {
    renderDetail({ wave: buildWave('RUNNING', { nextRunAt: '2099-01-05T12:00:00Z' }) });

    expect(await screen.findByText(/la próxima tanda de 30 mails sale el 05\/01\/2099 a las 09:00/)).toBeInTheDocument();
  });

  it('survivesARunningWaveWithoutNextRunAt', async () => {
    renderDetail({ wave: buildWave('RUNNING', { nextRunAt: null }) });

    expect(await screen.findByText(/los mails salen de a tandas dentro del horario de envío/)).toBeInTheDocument();
  });
});

describe('InvitationWaveDetailPage recipients', () => {
  it('showsTheSixTotalsAsFiltersWithTheirCounts', async () => {
    renderDetail({ wave: buildWave('RUNNING') });

    const filters = await screen.findByRole('group', { name: 'Filtrar destinatarios por estado' });
    const labels = within(filters).getAllByRole('button').map((button) => button.textContent);
    expect(labels).toEqual(['28 Pendientes', '15 En cola', '40 Enviados', '6 Fallidos', '22 Excluidos', '29 Activaron']);
  });

  it('filtersTheRecipientsByStatusAndKeepsItInTheUrl', async () => {
    const { container } = renderDetail({ wave: buildWave('RUNNING') });
    await screen.findByText('Apellido4001, Nombre4001');
    expect(screen.getByText('Apellido4004, Nombre4004')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '6 Fallidos' }));

    expect(await screen.findByRole('heading', { level: 2, name: /Destinatarios · 1 fallidos/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '6 Fallidos' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByText('Apellido4001, Nombre4001')).not.toBeInTheDocument();
    expect(container).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: 'Quitar filtro' }));
    expect(await screen.findByText('Apellido4001, Nombre4001')).toBeInTheDocument();
  });

  it('startsFilteredWhenTheUrlCarriesAStatus', async () => {
    renderDetail({ wave: buildWave('RUNNING'), route: '/director/invitaciones/1?status=SENT' });

    expect(await screen.findByText('Apellido4003, Nombre4003')).toBeInTheDocument();
    expect(screen.queryByText('Apellido4001, Nombre4001')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '40 Enviados' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('explainsEachStatusInTheDetailColumn', async () => {
    renderDetail({ wave: buildWave('RUNNING') });
    await screen.findByText('Apellido4001, Nombre4001');

    const row = (id) => screen.getByText(`Apellido${id}, Nombre${id}`).closest('tr');
    expect(within(row(4002)).getByText('Sale en la próxima tanda')).toBeInTheDocument();
    expect(within(row(4003)).getByText('Esperando que active')).toBeInTheDocument();
    expect(within(row(4004)).getByText('El proveedor de mail rechazó el envío (2 intentos)')).toBeInTheDocument();
    expect(within(row(4005)).getByText('Activó su cuenta')).toBeInTheDocument();
    expect(within(row(4006)).getAllByText('Sin email')).toHaveLength(2);
    expect(within(row(4003)).getByText('22/11/2026 12:10')).toBeInTheDocument();
  });

  it('showsThePersonNumberAndANoticeWithoutPersonalDataPermission', async () => {
    const hidden = sampleRecipients().map((recipient) => ({ ...recipient, displayName: null, email: null }));
    renderDetail({ wave: buildWave('RUNNING'), recipients: hidden });

    expect(await screen.findByText('Persona N° 4001')).toBeInTheDocument();
    expect(screen.getByText(/No tenés permiso para ver datos personales/)).toBeInTheDocument();
    expect(screen.queryByText(/ejemplo\.edu\.ar/)).not.toBeInTheDocument();
  });

  it('showsAnEmptyStateWhenAStatusHasNoRecipients', async () => {
    renderDetail({ wave: buildWave('RUNNING'), recipients: [] });

    expect(await screen.findByText('No hay destinatarios en este estado')).toBeInTheDocument();
  });

  it('pagesTheRecipientsOnTheServer', async () => {
    const requested = [];
    const extraHandlers = [
      http.get(`${API}/invitation-waves/1/recipients`, ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get('page'));
        requested.push(page);
        return HttpResponse.json(
          springPage([{ ...sampleRecipients()[0], personId: 7000 + page, displayName: `Persona de la página ${page + 1}` }], { page, size: 20, total: 45 }),
        );
      }),
    ];
    renderDetail({ wave: buildWave('RUNNING'), extraHandlers });

    expect(await screen.findByText('Persona de la página 1')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Siguiente/ }));
    expect(await screen.findByText('Persona de la página 2')).toBeInTheDocument();
    expect(requested).toEqual([0, 1]);
  });
});

describe('InvitationWaveDetailPage actions', () => {
  it('startsADraftOnlyAfterTheConfirmationSummary', async () => {
    const { lifecycle } = renderDetail({ wave: buildWave('DRAFT') });
    await screen.findByRole('heading', { level: 1 });

    await userEvent.click(within(desktopActions()).getByRole('button', { name: 'Iniciar oleada' }));

    const dialog = await screen.findByRole('alertdialog', { name: 'Iniciar «Primer año 2026»' });
    const summary = within(dialog).getByRole('list', { name: 'Resumen de la acción' });
    expect(within(summary).getByText('Destinatarios pendientes').nextSibling).toHaveTextContent('118');
    expect(within(summary).getByText('Ritmo').nextSibling).toHaveTextContent('30 mails cada 60 min');
    expect(within(summary).getByText('Tope diario').nextSibling).toHaveTextContent('120 por día');
    expect(within(summary).getByText('Horario de envío').nextSibling).toHaveTextContent('09:00 a 20:00');
    expect(lifecycle.state.calls).toEqual([]);

    await userEvent.click(within(dialog).getByRole('button', { name: 'Iniciar oleada' }));

    await waitFor(() => expect(lifecycle.state.calls).toEqual(['start']));
    await waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Oleada iniciada', { description: undefined }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect((await screen.findAllByText('En curso')).length).toBeGreaterThan(0);
  });

  it('doesNothingWhenTheConfirmationIsDismissed', async () => {
    const { lifecycle } = renderDetail({ wave: buildWave('DRAFT') });
    await screen.findByRole('heading', { level: 1 });

    await userEvent.click(within(desktopActions()).getByRole('button', { name: 'Iniciar oleada' }));
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(lifecycle.state.calls).toEqual([]);
  });

  it('pausesRightAwayWithoutAConfirmation', async () => {
    const { lifecycle } = renderDetail({ wave: buildWave('RUNNING') });
    await screen.findByRole('heading', { level: 1 });

    await userEvent.click(within(desktopActions()).getByRole('button', { name: 'Pausar' }));

    await waitFor(() => expect(lifecycle.state.calls).toEqual(['pause']));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    await waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Oleada pausada', { description: undefined }));
    expect(await screen.findByText(/Pausada: no se envía nada/)).toBeInTheDocument();
  });

  it('resumesAPausedWaveAfterConfirming', async () => {
    const { lifecycle } = renderDetail({ wave: buildWave('PAUSED') });
    await screen.findByRole('heading', { level: 1 });

    await userEvent.click(within(desktopActions()).getByRole('button', { name: 'Reanudar' }));
    const dialog = await screen.findByRole('alertdialog', { name: 'Reanudar «Primer año 2026»' });
    expect(within(dialog).getByText('Quedan por enviar').nextSibling).toHaveTextContent('51');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reanudar oleada' }));

    await waitFor(() => expect(lifecycle.state.calls).toEqual(['resume']));
    expect(await screen.findByText(/En curso:/)).toBeInTheDocument();
  });

  it('cancelsAWaveWithADestructiveConfirmationThatExplainsWhatStays', async () => {
    const { lifecycle } = renderDetail({ wave: buildWave('RUNNING') });
    await screen.findByRole('heading', { level: 1 });

    await userEvent.click(within(desktopActions()).getByRole('button', { name: 'Cancelar oleada' }));

    const dialog = await screen.findByRole('alertdialog', { name: 'Cancelar «Primer año 2026»' });
    expect(within(dialog).getByText('Esto no se puede deshacer.')).toBeInTheDocument();
    expect(within(dialog).getByText('Se liberan').nextSibling).toHaveTextContent('49 pendientes, en cola o fallidos');
    expect(within(dialog).getByText('Motivo que queda').nextSibling).toHaveTextContent('Oleada cancelada');
    expect(within(dialog).getByText('Mails ya enviados').nextSibling).toHaveTextContent('69 (no se retiran)');
    expect(within(dialog).getByText(/Los enlaces ya enviados siguen vigentes/)).toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Cancelar' })).not.toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar oleada' }));

    await waitFor(() => expect(lifecycle.state.calls).toEqual(['cancel']));
    await waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Oleada cancelada', { description: 'Los pendientes quedaron liberados.' }));
    expect(await screen.findByText(/Cancelada el/)).toBeInTheDocument();
  });

  it('keepsTheWaveWhenTheCancellationIsDismissedWithVolver', async () => {
    const { lifecycle } = renderDetail({ wave: buildWave('RUNNING') });
    await screen.findByRole('heading', { level: 1 });

    await userEvent.click(within(desktopActions()).getByRole('button', { name: 'Cancelar oleada' }));
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Volver' }));

    expect(lifecycle.state.calls).toEqual([]);
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('retriesTheFailedSendsAfterConfirming', async () => {
    const { lifecycle } = renderDetail({ wave: buildWave('RUNNING') });
    await screen.findByRole('heading', { level: 1 });

    await userEvent.click(within(desktopActions()).getByRole('button', { name: 'Reintentar fallidos (6)' }));
    const dialog = await screen.findByRole('alertdialog', { name: 'Reintentar envíos fallidos' });
    expect(within(dialog).getByText('Envíos a reintentar').nextSibling).toHaveTextContent('6');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reintentar 6' }));

    await waitFor(() => expect(lifecycle.state.calls).toEqual(['retry-failed']));
    await waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Reintento programado', { description: '6 envíos vuelven a la cola.' }));
  });

  it('showsTheDomainMessageWithItsCodeAndRefreshesWhenTheTransitionIsInvalid', async () => {
    const { lifecycle } = renderDetail({
      wave: buildWave('DRAFT'),
      setup: { overrides: { start: () => HttpResponse.json(errorBody('INVITATION_WAVE_INVALID_TRANSITION'), { status: 409 }) } },
    });
    await screen.findByRole('heading', { level: 1 });

    await userEvent.click(within(desktopActions()).getByRole('button', { name: 'Iniciar oleada' }));
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Iniciar oleada' }));

    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith('La oleada ya no está en un estado que permita esa acción.', {
        description: 'Código: INVITATION_WAVE_INVALID_TRANSITION',
      }),
    );
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(lifecycle.state.calls).toEqual(['start']);
  });

  it('explainsAnEmptyWaveWhenStartingWithoutPendingPeople', async () => {
    renderDetail({
      wave: buildWave('DRAFT', { eligibleCount: 0, invitableCount: 0 }),
      setup: { overrides: { start: () => HttpResponse.json(errorBody('INVITATION_WAVE_WITHOUT_RECIPIENTS'), { status: 409 }) } },
    });
    await screen.findByRole('heading', { level: 1 });

    await userEvent.click(within(desktopActions()).getByRole('button', { name: 'Iniciar oleada' }));
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Iniciar oleada' }));

    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith('La oleada no tiene destinatarios pendientes para iniciar.', {
        description: 'Código: INVITATION_WAVE_WITHOUT_RECIPIENTS',
      }),
    );
  });

  it('reportsANetworkFailureWithoutACode', async () => {
    renderDetail({ wave: buildWave('RUNNING'), setup: { overrides: { pause: () => HttpResponse.error() } } });
    await screen.findByRole('heading', { level: 1 });

    await userEvent.click(within(desktopActions()).getByRole('button', { name: 'Pausar' }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalled());
    expect(toastMock.error.mock.calls[0][1]).toEqual({ description: undefined });
  });
});

describe('InvitationWaveDetailPage permissions', () => {
  it('withoutManageShowsNoLifecycleActionsAndKeepsTheCsvForReaders', async () => {
    renderDetail({ wave: buildWave('RUNNING'), access: { read: true, manage: false, send: false } });

    await screen.findByRole('heading', { level: 1 });
    expect(screen.getByText(/Tenés acceso de solo lectura/)).toBeInTheDocument();
    const names = within(desktopActions()).getAllByRole('button').map((button) => button.textContent);
    expect(names).toEqual(['Exportar reporte CSV']);
    expect(screen.queryByRole('button', { name: 'Pausar' })).not.toBeInTheDocument();
  });

  it('withoutSendKeepsTheSensitiveButtonsVisibleButDisabledWithTheReason', async () => {
    const { lifecycle } = renderDetail({ wave: buildWave('PAUSED'), access: { read: true, manage: true, send: false } });
    await screen.findByRole('heading', { level: 1 });

    const reason = screen.getByText(/necesitás el permiso/);
    expect(reason).toHaveTextContent('notification.send_invitation_wave');
    for (const name of ['Reanudar', 'Reintentar fallidos (6)', 'Cancelar oleada']) {
      const button = within(desktopActions()).getByRole('button', { name });
      expect(button).toHaveAttribute('aria-disabled', 'true');
      expect(button).toHaveAccessibleDescription(/necesitás el permiso/);
    }

    await userEvent.click(within(desktopActions()).getByRole('button', { name: 'Reanudar' }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(lifecycle.state.calls).toEqual([]);
  });

  it('withoutSendStillAllowsPausingBecauseItOnlyNeedsManage', async () => {
    const { lifecycle } = renderDetail({ wave: buildWave('RUNNING'), access: { read: true, manage: true, send: false } });
    await screen.findByRole('heading', { level: 1 });

    const pause = within(desktopActions()).getByRole('button', { name: 'Pausar' });
    expect(pause).not.toHaveAttribute('aria-disabled');
    await userEvent.click(pause);

    await waitFor(() => expect(lifecycle.state.calls).toEqual(['pause']));
  });

  it('learnsTheMissingSendPermissionFromAForbiddenAnswer', async () => {
    const { lifecycle } = renderDetail({
      wave: buildWave('DRAFT'),
      setup: { overrides: { start: () => HttpResponse.json(errorBody('FORBIDDEN'), { status: 403 }) } },
    });
    await screen.findByRole('heading', { level: 1 });

    await userEvent.click(within(desktopActions()).getByRole('button', { name: 'Iniciar oleada' }));
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Iniciar oleada' }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith('No tenés permiso para hacer esto.', { description: 'Código: FORBIDDEN' }));
    await waitFor(() => expect(within(desktopActions()).getByRole('button', { name: 'Iniciar oleada' })).toHaveAttribute('aria-disabled', 'true'));
    expect(lifecycle.state.calls).toEqual(['start']);
  });

  it('hidesTheWaveWithoutReadPermission', async () => {
    renderDetail({ wave: buildWave('RUNNING'), access: { read: false, manage: false, send: false } });

    expect(await screen.findByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { level: 1, name: 'Primer año 2026' })).not.toBeInTheDocument();
  });
});

describe('InvitationWaveDetailPage CSV report', () => {
  it('downloadsTheReportWithTheNameFromTheServer', async () => {
    server.use(
      http.get(`${API}/invitation-waves/1/report.csv`, () =>
        new HttpResponse('﻿personId,status\n4001,PENDING\n', {
          headers: { 'Content-Type': 'text/csv; charset=UTF-8', 'Content-Disposition': 'attachment; filename="invitaciones-oleada-1-20261123.csv"' },
        }),
      ),
    );
    renderDetail({ wave: buildWave('COMPLETED') });
    await screen.findByRole('heading', { level: 1 });

    await userEvent.click(within(desktopActions()).getByRole('button', { name: 'Exportar reporte CSV' }));

    await waitFor(() => expect(saveBlobMock).toHaveBeenCalledTimes(1));
    expect(saveBlobMock.mock.calls[0][1]).toBe('invitaciones-oleada-1-20261123.csv');
    expect(saveBlobMock.mock.calls[0][0].size).toBeGreaterThan(0);
    await waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Reporte generado', { description: 'El archivo se descarga como invitaciones-oleada-1-20261123.csv.' }));
  });

  it('fallsBackToTheDocumentedNameWhenTheServerSendsNone', async () => {
    server.use(http.get(`${API}/invitation-waves/1/report.csv`, () => new HttpResponse('personId\n', { headers: { 'Content-Type': 'text/csv' } })));
    renderDetail({ wave: buildWave('COMPLETED') });
    await screen.findByRole('heading', { level: 1 });

    await userEvent.click(within(desktopActions()).getByRole('button', { name: 'Exportar reporte CSV' }));

    await waitFor(() => expect(saveBlobMock).toHaveBeenCalled());
    expect(saveBlobMock.mock.calls[0][1]).toMatch(/^invitaciones-oleada-1-\d{8}\.csv$/);
  });

  it('reportsAFailedDownloadWithItsCode', async () => {
    server.use(http.get(`${API}/invitation-waves/1/report.csv`, () => HttpResponse.json(errorBody('INVITATION_WAVE_NOT_FOUND'), { status: 404 })));
    renderDetail({ wave: buildWave('COMPLETED') });
    await screen.findByRole('heading', { level: 1 });

    await userEvent.click(within(desktopActions()).getByRole('button', { name: 'Exportar reporte CSV' }));

    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith('No encontramos esa oleada de invitaciones.', { description: 'Código: INVITATION_WAVE_NOT_FOUND' }),
    );
    expect(saveBlobMock).not.toHaveBeenCalled();
  });
});

describe('InvitationWaveDetailPage load states', () => {
  it('showsALoadingSkeletonWhileTheWaveArrives', () => {
    server.use(http.get(`${API}/invitation-waves/1`, () => new Promise(() => {})), http.get(`${API}/invitation-waves/settings`, () => HttpResponse.json(SETTINGS)));
    renderInvitationScreen(<InvitationWaveDetailPage />, { path: PATH, route: '/director/invitaciones/1' });

    expect(screen.getByLabelText('Cargando')).toBeInTheDocument();
  });

  it('showsItsOwnScreenWhenTheWaveDoesNotExist', async () => {
    server.use(
      http.get(`${API}/invitation-waves/1`, () => HttpResponse.json(errorBody('INVITATION_WAVE_NOT_FOUND'), { status: 404 })),
      ...invitationWaveHandlers(),
    );
    renderInvitationScreen(<InvitationWaveDetailPage />, { path: PATH, route: '/director/invitaciones/1' });

    expect(await screen.findByText('No encontramos esa oleada de invitaciones.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a Invitaciones' })).toHaveAttribute('href', '/director/invitaciones');
  });

  it('showsAnErrorStateWithRetry', async () => {
    server.use(http.get(`${API}/invitation-waves/1`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })), http.get(`${API}/invitation-waves/settings`, () => HttpResponse.json(SETTINGS)));
    renderInvitationScreen(<InvitationWaveDetailPage />, { path: PATH, route: '/director/invitaciones/1' });

    expect(await screen.findByText('No pudimos cargar la oleada')).toBeInTheDocument();
    server.use(...invitationWaveHandlers({ wave: buildWave('RUNNING'), recipients: sampleRecipients() }));
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Primer año 2026' })).toBeInTheDocument();
  });

  it('showsTheNoAccessScreenWhenTheServerForbidsReading', async () => {
    server.use(http.get(`${API}/invitation-waves/1`, () => HttpResponse.json(errorBody('FORBIDDEN'), { status: 403 })), http.get(`${API}/invitation-waves/settings`, () => HttpResponse.json(SETTINGS)));
    renderInvitationScreen(<InvitationWaveDetailPage />, { path: PATH, route: '/director/invitaciones/1' });

    expect(await screen.findByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
  });

  it('showsAnErrorInsideTheRecipientsWhenOnlyThatListFails', async () => {
    server.use(
      http.get(`${API}/invitation-waves/1/recipients`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })),
      ...invitationWaveHandlers({ wave: buildWave('RUNNING') }),
    );
    renderInvitationScreen(<InvitationWaveDetailPage />, { path: PATH, route: '/director/invitaciones/1' });

    expect(await screen.findByText('No pudimos cargar los destinatarios.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Primer año 2026' })).toBeInTheDocument();
  });
});
