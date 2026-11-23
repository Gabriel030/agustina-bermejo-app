import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { InvitationWavePreviewPage } from './InvitationWavePreviewPage';
import { API, buildRecipient, buildWave, withoutPersonalData } from '../test/fixtures';
import { invitationWaveHandlers } from '../test/handlers';
import { dropUnpublishedContractDeviations, renderInvitationScreen } from '../test/render';

dropUnpublishedContractDeviations();

const PATH = '/director/invitaciones/:waveId/vista-previa';
const ROUTE = '/director/invitaciones/1/vista-previa';

function pending(count) {
  return Array.from({ length: count }, (_, index) => buildRecipient(4000 + index));
}

function excluded() {
  return [
    buildRecipient(5001, { status: 'EXCLUDED', exclusionReason: 'NO_EMAIL', email: null }),
    buildRecipient(5002, { status: 'EXCLUDED', exclusionReason: 'ALREADY_ACTIVE' }),
    buildRecipient(5003, { status: 'EXCLUDED', exclusionReason: 'IN_OTHER_WAVE' }),
  ];
}

function renderPreview({ wave = buildWave('DRAFT'), recipients = [...pending(25), ...excluded()], access } = {}) {
  server.use(...invitationWaveHandlers({ wave, recipients }));
  return renderInvitationScreen(<InvitationWavePreviewPage />, { path: PATH, route: ROUTE, access });
}

describe('InvitationWavePreviewPage', () => {
  it('confirmsTheDraftAndShowsTheEligibleAndExcludedTotals', async () => {
    renderPreview();

    expect(await screen.findByText(/Creamos el borrador/)).toHaveTextContent('«Primer año 2026»');
    expect(screen.getByTestId('eligible-total')).toHaveTextContent('118');
    expect(screen.getByTestId('excluded-total')).toHaveTextContent('22');
  });

  it('estimatesTheBatchesAndDaysFromTheRhythmAndTheSendWindow', async () => {
    renderPreview();

    expect(await screen.findByText('Estimado: 4 tandas de hasta 30 cada 60 minutos, con tope de 120 por día. Termina en 1 día.')).toBeInTheDocument();
  });

  it('estimatesMoreDaysWhenTheDailyCapIsSmall', async () => {
    renderPreview({ wave: buildWave('DRAFT', { batchSize: 10, dailyCap: 40 }) });

    expect(await screen.findByText(/Estimado: 12 tandas de hasta 10 cada 60 minutos, con tope de 40 por día\. Termina en 3 días\./)).toBeInTheDocument();
  });

  it('listsTheEligibleRecipientsFirstAsPendingWithServerPagination', async () => {
    renderPreview();

    const row = (await screen.findByText('Apellido4000, Nombre4000')).closest('tr');
    expect(within(row).getByText('persona4000@ejemplo.edu.ar')).toBeInTheDocument();
    expect(within(row).getByText('Pendiente')).toBeInTheDocument();
    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Siguiente/ }));
    expect(await screen.findByText('Apellido4010, Nombre4010')).toBeInTheDocument();
    expect(screen.queryByText('Apellido4000, Nombre4000')).not.toBeInTheDocument();
  });

  it('switchesToTheExcludedTabWithTheReasonOfEachPerson', async () => {
    renderPreview();
    await screen.findByText('Apellido4000, Nombre4000');
    await userEvent.click(screen.getByRole('tab', { name: /Excluidos/ }));

    const noEmail = (await screen.findByText('Apellido5001, Nombre5001')).closest('tr');
    expect(within(noEmail).getAllByText('Sin email')).toHaveLength(2);
    expect(screen.getByText('Ya activó su cuenta', { selector: 'td *' })).toBeInTheDocument();
    expect(screen.getByText('Ya está en otra oleada', { selector: 'td *' })).toBeInTheDocument();
    expect(screen.queryByText('Apellido4000, Nombre4000')).not.toBeInTheDocument();
  });

  it('summarizesTheExcludedByReasonFromTheWaveCounts', async () => {
    renderPreview();
    await userEvent.click(await screen.findByRole('tab', { name: /Excluidos/ }));

    const summary = await screen.findByRole('list', { name: 'Excluidos por motivo' });
    expect(within(summary).getByText('Sin email · 8')).toBeInTheDocument();
    expect(within(summary).getByText('Ya activó su cuenta · 6')).toBeInTheDocument();
    expect(within(summary).getByText('No elegible · 3')).toBeInTheDocument();
    expect(within(summary).getByText('Ya está en otra oleada · 5')).toBeInTheDocument();
  });

  it('showsThePersonNumberAndANoticeWithoutPersonalDataPermission', async () => {
    renderPreview({ recipients: pending(3).map(withoutPersonalData) });

    expect(await screen.findByText('Persona N° 4000')).toBeInTheDocument();
    expect(screen.getByText(/No tenés permiso para ver datos personales/)).toBeInTheDocument();
    expect(screen.queryByText(/ejemplo\.edu\.ar/)).not.toBeInTheDocument();
  });

  it('warnsWhenThereIsNobodyToInvite', async () => {
    renderPreview({ wave: buildWave('DRAFT', { eligibleCount: 0, invitableCount: 0, excludedTotal: 30 }), recipients: [] });

    expect(await screen.findByText(/No hay personas para invitar en esta oleada/)).toBeInTheDocument();
    expect(screen.getByText('No hay personas elegibles para invitar.')).toBeInTheDocument();
  });

  it('goesToTheWaveOrBackToTheList', async () => {
    renderPreview();
    await screen.findByText(/Creamos el borrador/);

    await userEvent.click(screen.getAllByRole('button', { name: 'Ir a la oleada' })[0]);
    expect(await screen.findByTestId('location-probe')).toHaveTextContent('/director/invitaciones/1');
  });

  it('showsItsOwnScreenWhenTheWaveDoesNotExist', async () => {
    server.use(
      http.get(`${API}/invitation-waves/1`, () => HttpResponse.json(errorBody('INVITATION_WAVE_NOT_FOUND'), { status: 404 })),
      ...invitationWaveHandlers(),
    );
    renderInvitationScreen(<InvitationWavePreviewPage />, { path: PATH, route: ROUTE });

    expect(await screen.findByText('No encontramos esa oleada de invitaciones.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a Invitaciones' })).toHaveAttribute('href', '/director/invitaciones');
  });

  it('hidesThePreviewWithoutReadPermission', async () => {
    renderPreview({ access: { read: false, manage: false, send: false } });

    expect(await screen.findByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
  });
});
