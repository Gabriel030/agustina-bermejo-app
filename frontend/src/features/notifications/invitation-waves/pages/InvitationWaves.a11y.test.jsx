import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from '@/test/axe';
import { server } from '@/test/mocks/server';
import { InvitationWaveCreatePage } from './InvitationWaveCreatePage';
import { InvitationWaveDetailPage } from './InvitationWaveDetailPage';
import { InvitationWaveListPage } from './InvitationWaveListPage';
import { InvitationWavePreviewPage } from './InvitationWavePreviewPage';
import { buildRecipient, buildWave } from '../test/fixtures';
import { invitationWaveHandlers, lifecycleHandlers, sampleRecipients } from '../test/handlers';
import { dropUnpublishedContractDeviations, renderInvitationScreen } from '../test/render';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

dropUnpublishedContractDeviations();

describe('invitation waves accessibility', () => {
  it('listHasNoAxeViolations', async () => {
    server.use(...invitationWaveHandlers({ wave: buildWave('RUNNING') }));
    const { container } = renderInvitationScreen(<InvitationWaveListPage />, { path: '/director/invitaciones', route: '/director/invitaciones' });
    await screen.findByRole('link', { name: 'Primer año 2026' });

    expect(await axe(container)).toHaveNoViolations();
  });

  it('creationFormHasNoAxeViolationsEvenWithErrorsShown', async () => {
    server.use(...invitationWaveHandlers());
    const { container } = renderInvitationScreen(<InvitationWaveCreatePage />, { path: '/director/invitaciones/nueva', route: '/director/invitaciones/nueva' });
    await screen.findByLabelText(/^Nombre/);
    await userEvent.click(screen.getAllByRole('button', { name: 'Crear borrador' })[0]);
    await screen.findByText('Ponele un nombre a la oleada.');

    expect(await axe(container)).toHaveNoViolations();
  });

  it('previewHasNoAxeViolationsInBothTabs', async () => {
    server.use(...invitationWaveHandlers({ wave: buildWave('DRAFT'), recipients: [buildRecipient(1), buildRecipient(2, { status: 'EXCLUDED', exclusionReason: 'NO_EMAIL', email: null })] }));
    const { container } = renderInvitationScreen(<InvitationWavePreviewPage />, { path: '/director/invitaciones/:waveId/vista-previa', route: '/director/invitaciones/1/vista-previa' });
    await screen.findByText('Apellido1, Nombre1');
    expect(await axe(container)).toHaveNoViolations();

    await userEvent.click(screen.getByRole('tab', { name: /Excluidos/ }));
    await screen.findByText('Apellido2, Nombre2');
    expect(await axe(container)).toHaveNoViolations();
  });

  it('detailHasNoAxeViolationsAndTheConfirmationDialogTrapsFocus', async () => {
    const lifecycle = lifecycleHandlers(buildWave('RUNNING'), { recipients: sampleRecipients() });
    server.use(...lifecycle.handlers);
    const { container } = renderInvitationScreen(<InvitationWaveDetailPage />, { path: '/director/invitaciones/:waveId', route: '/director/invitaciones/1' });
    await screen.findByText('Apellido4001, Nombre4001');
    expect(await axe(container)).toHaveNoViolations();

    await userEvent.click(within(screen.getByRole('group', { name: 'Acciones de la oleada' })).getByRole('button', { name: 'Cancelar oleada' }));
    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toContainElement(document.activeElement);
    expect(await axe(document.body)).toHaveNoViolations();
  });

  it('detailWithoutPersonalDataOrSendPermissionHasNoAxeViolations', async () => {
    const hidden = sampleRecipients().map((recipient) => ({ ...recipient, displayName: null, email: null }));
    server.use(...lifecycleHandlers(buildWave('PAUSED'), { recipients: hidden }).handlers);
    const { container } = renderInvitationScreen(<InvitationWaveDetailPage />, {
      path: '/director/invitaciones/:waveId',
      route: '/director/invitaciones/1',
      access: { read: true, manage: true, send: false },
    });
    await screen.findByText('Persona N° 4001');

    expect(await axe(container)).toHaveNoViolations();
  });

  it('noAccessScreenHasNoAxeViolations', async () => {
    const { container } = renderInvitationScreen(<InvitationWaveListPage />, {
      path: '/director/invitaciones',
      route: '/director/invitaciones',
      access: { read: false, manage: false, send: false },
    });
    await screen.findByText('No tenés acceso a esta pantalla');

    expect(await axe(container)).toHaveNoViolations();
  });
});
