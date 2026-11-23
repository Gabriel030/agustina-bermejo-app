import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '@/test/mocks/server';
import { InvitationWaveDetailPage } from '../pages/InvitationWaveDetailPage';
import { buildWave } from '../test/fixtures';
import { lifecycleHandlers, sampleRecipients } from '../test/handlers';
import { dropUnpublishedContractDeviations, renderInvitationScreen } from '../test/render';

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', () => ({ toast: toastMock }));

dropUnpublishedContractDeviations();

beforeEach(() => {
  toastMock.success.mockClear();
  toastMock.error.mockClear();
});

function renderBar(wave, access) {
  const lifecycle = lifecycleHandlers(wave, { recipients: sampleRecipients() });
  server.use(...lifecycle.handlers);
  renderInvitationScreen(<InvitationWaveDetailPage />, { path: '/director/invitaciones/:waveId', route: '/director/invitaciones/1', access });
  return lifecycle;
}

const bar = () => screen.findByRole('region', { name: 'Acciones de la oleada' });

describe('WaveMobileBar', () => {
  it('summarizesTheProgressAndTheFailuresNextToTheMainAction', async () => {
    renderBar(buildWave('RUNNING'));

    const region = await bar();
    expect(within(region).getByText('69 de 118')).toBeInTheDocument();
    expect(within(region).getByText('6 fallidos')).toBeInTheDocument();
    expect(within(region).getByRole('button', { name: 'Pausar' })).toBeInTheDocument();
  });

  it('movesTheSecondaryActionsToTheMoreActionsMenu', async () => {
    renderBar(buildWave('RUNNING'));

    const region = await bar();
    await userEvent.click(within(region).getByRole('button', { name: 'Más acciones' }));

    const menu = await screen.findByRole('menu');
    expect(within(menu).getAllByRole('menuitem').map((item) => item.textContent)).toEqual(['Reintentar fallidos (6)', 'Exportar reporte CSV', 'Cancelar oleada']);
  });

  it('opensTheSameConfirmationFromTheMenu', async () => {
    const lifecycle = renderBar(buildWave('RUNNING'));

    const region = await bar();
    await userEvent.click(within(region).getByRole('button', { name: 'Más acciones' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Cancelar oleada' }));

    const dialog = await screen.findByRole('alertdialog', { name: 'Cancelar «Primer año 2026»' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar oleada' }));
    await waitFor(() => expect(lifecycle.state.calls).toEqual(['cancel']));
  });

  it('showsTheSensitiveItemsDisabledWithoutSendPermission', async () => {
    renderBar(buildWave('RUNNING'), { read: true, manage: true, send: false });

    const region = await bar();
    await userEvent.click(within(region).getByRole('button', { name: 'Más acciones' }));

    const cancel = await screen.findByRole('menuitem', { name: 'Cancelar oleada' });
    expect(cancel).toHaveAttribute('data-disabled');
    expect(screen.getByRole('menuitem', { name: 'Exportar reporte CSV' })).not.toHaveAttribute('data-disabled');
  });

  it('promotesTheCsvToTheMainActionForReadOnlyUsers', async () => {
    renderBar(buildWave('RUNNING'), { read: true, manage: false, send: false });

    const region = await bar();
    expect(within(region).getByRole('button', { name: 'Exportar reporte CSV' })).toBeInTheDocument();
    expect(within(region).queryByRole('button', { name: 'Más acciones' })).not.toBeInTheDocument();
  });

  it('hintsCompletedOrNoFailuresWhenNothingFailed', async () => {
    renderBar(buildWave('COMPLETED'));

    expect(within(await bar()).getByText('Completada')).toBeInTheDocument();
  });
});
