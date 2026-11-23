import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { InvitationWaveCreatePage } from './InvitationWaveCreatePage';
import { API, IMPORT_BATCHES, SETTINGS, buildWave } from '../test/fixtures';
import { invitationWaveHandlers } from '../test/handlers';
import { dropUnpublishedContractDeviations, renderInvitationScreen } from '../test/render';

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', () => ({ toast: toastMock }));

dropUnpublishedContractDeviations();

beforeEach(() => {
  toastMock.success.mockClear();
  toastMock.error.mockClear();
});

function renderCreate(access) {
  server.use(...invitationWaveHandlers());
  return renderInvitationScreen(<InvitationWaveCreatePage />, { path: '/director/invitaciones/nueva', route: '/director/invitaciones/nueva', access });
}

async function fillValidForm(user = userEvent) {
  await user.type(await screen.findByLabelText(/^Nombre/), 'Ingresantes 2027');
  await user.selectOptions(screen.getByLabelText(/^Lote de importación/), '12');
}

async function setNumber(label, value) {
  const input = screen.getByLabelText(label);
  await userEvent.clear(input);
  if (value !== '') await userEvent.type(input, value);
  await userEvent.tab();
}

const submitButton = () => screen.getAllByRole('button', { name: 'Crear borrador' })[0];

describe('InvitationWaveCreatePage', () => {
  it('startsWithTheDefaultRhythmAndTheSendWindowFromTheSettings', async () => {
    renderCreate();

    expect(await screen.findByLabelText(/^Tamaño de tanda/)).toHaveValue(30);
    expect(screen.getByLabelText(/^Ritmo \(minutos\)/)).toHaveValue(60);
    expect(screen.getByLabelText(/^Tope diario/)).toHaveValue(120);
    expect(screen.getByText(/Máximo del instituto: 300 por día/)).toBeInTheDocument();
    expect(screen.getByText(/Se enviarán hasta 30 mails cada 60 minutos, con un máximo de 120 por día\. Los mails salen entre las 09:00 y las 20:00\./)).toBeInTheDocument();
  });

  it('listsTheImportBatchesWithTheirPendingPeople', async () => {
    renderCreate();

    expect(await screen.findByRole('option', { name: /Ingresantes 2027 · 85 personas/ })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /Carga inicial 2026 · 140 personas/ })).toBeInTheDocument();
  });

  it('showsInlineErrorsForEveryRequiredFieldOnSubmit', async () => {
    renderCreate();
    await screen.findByLabelText(/^Nombre/);
    await userEvent.click(submitButton());

    expect(await screen.findByText('Ponele un nombre a la oleada.')).toBeInTheDocument();
    expect(screen.getByText('Elegí un lote de importación.')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Nombre/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText(/^Nombre/)).toHaveAccessibleDescription(/Ponele un nombre a la oleada\./);
  });

  it('validatesTheNumbersAgainstTheLimitsFromTheSettings', async () => {
    renderCreate();
    await screen.findByLabelText(/^Tope diario/);

    await setNumber(/^Tope diario/, '450');
    expect(await screen.findByText('El tope diario no puede superar el máximo del instituto (300).')).toBeInTheDocument();

    await setNumber(/^Tope diario/, '40');
    await setNumber(/^Tamaño de tanda/, '50');
    expect(await screen.findByText('La tanda no puede superar el tope diario (40).')).toBeInTheDocument();

    await setNumber(/^Tamaño de tanda/, '150');
    await setNumber(/^Tope diario/, '200');
    expect(await screen.findByText('La tanda no puede superar el máximo permitido (100).')).toBeInTheDocument();

    await setNumber(/^Ritmo \(minutos\)/, '2000');
    expect(await screen.findByText('El ritmo tiene que estar entre 1 y 1440 minutos.')).toBeInTheDocument();

    await setNumber(/^Tamaño de tanda/, '0');
    expect(await screen.findByText('Ingresá un tamaño entero mayor a 0.')).toBeInTheDocument();
  });

  it('revalidatesTheBatchSizeWhenTheDailyCapChanges', async () => {
    renderCreate();
    await screen.findByLabelText(/^Tope diario/);

    await setNumber(/^Tope diario/, '20');
    expect(await screen.findByText('La tanda no puede superar el tope diario (20).')).toBeInTheDocument();
    await setNumber(/^Tope diario/, '60');
    await waitFor(() => expect(screen.queryByText(/La tanda no puede superar el tope diario/)).not.toBeInTheDocument());
  });

  it('rejectsAnImportBatchBiggerThanTheMaximumOfRecipients', async () => {
    renderCreate();
    await userEvent.selectOptions(await screen.findByLabelText(/^Lote de importación/), '13');
    await userEvent.tab();

    expect(await screen.findByText(/El lote tiene más personas de las que admite una oleada \(máximo 5\.000\)/)).toBeInTheDocument();
  });

  it('countsTheFieldsToFixInTheMobileBar', async () => {
    renderCreate();
    await screen.findByLabelText(/^Nombre/);

    expect(screen.getByText('2 campos para corregir')).toBeInTheDocument();
    await fillValidForm();
    expect(await screen.findByText('Todo listo para crear')).toBeInTheDocument();
  });

  it('createsTheDraftAndGoesToThePreview', async () => {
    let body;
    server.use(
      ...invitationWaveHandlers(),
      http.post(`${API}/invitation-waves`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(buildWave('DRAFT', { id: 9, name: 'Ingresantes 2027' }), { status: 201 });
      }),
    );
    renderInvitationScreen(<InvitationWaveCreatePage />, { path: '/director/invitaciones/nueva', route: '/director/invitaciones/nueva' });
    await fillValidForm();
    await userEvent.click(submitButton());

    expect(await screen.findByTestId('location-probe')).toHaveTextContent('/director/invitaciones/9/vista-previa');
    expect(body).toEqual({ name: 'Ingresantes 2027', importBatchId: 12, batchSize: 30, intervalMinutes: 60, dailyCap: 120 });
  });

  it('showsTheDomainMessageWithItsCodeWhenTheCreationFails', async () => {
    server.use(
      ...invitationWaveHandlers(),
      http.post(`${API}/invitation-waves`, () => HttpResponse.json(errorBody('INVITATION_PERSON_ALREADY_INVITED'), { status: 409 })),
    );
    renderInvitationScreen(<InvitationWaveCreatePage />, { path: '/director/invitaciones/nueva', route: '/director/invitaciones/nueva' });
    await fillValidForm();
    await userEvent.click(submitButton());

    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith('Alguna persona ya está pendiente de invitación en otra oleada. Volvé a armar la oleada.', {
        description: 'Código: INVITATION_PERSON_ALREADY_INVITED',
      }),
    );
    expect(screen.queryByTestId('location-probe')).not.toBeInTheDocument();
    expect(screen.getByLabelText(/^Nombre/)).toHaveValue('Ingresantes 2027');
  });

  it('marksTheBatchFieldWhenTheServerDoesNotFindTheBatch', async () => {
    server.use(
      ...invitationWaveHandlers(),
      http.post(`${API}/invitation-waves`, () => HttpResponse.json(errorBody('INVITATION_WAVE_IMPORT_BATCH_NOT_FOUND'), { status: 404 })),
    );
    renderInvitationScreen(<InvitationWaveCreatePage />, { path: '/director/invitaciones/nueva', route: '/director/invitaciones/nueva' });
    await fillValidForm();
    await userEvent.click(submitButton());

    expect(await screen.findByText('No encontramos ese lote de importación.')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Lote de importación/)).toHaveAttribute('aria-invalid', 'true');
  });

  it('blocksTheScreenWithoutManagePermission', async () => {
    renderCreate({ read: true, manage: false, send: false });

    expect(await screen.findByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
    expect(screen.getByText(/notification\.manage_invitation_waves/)).toBeInTheDocument();
    expect(screen.queryAllByRole('button', { name: 'Crear borrador' })).toHaveLength(0);
  });

  it('showsALoadingStateAndAnErrorWithRetryWhenTheSettingsFail', async () => {
    server.use(
      http.get(`${API}/invitation-waves/settings`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })),
      ...invitationWaveHandlers(),
    );
    renderInvitationScreen(<InvitationWaveCreatePage />, { path: '/director/invitaciones/nueva', route: '/director/invitaciones/nueva' });

    expect(screen.getByLabelText('Cargando')).toBeInTheDocument();
    expect(await screen.findByText('No pudimos cargar los datos para crear la oleada')).toBeInTheDocument();
    server.use(http.get(`${API}/invitation-waves/settings`, () => HttpResponse.json(SETTINGS)));
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByLabelText(/^Tamaño de tanda/)).toBeInTheDocument();
  });

  it('usesTheBatchesFromTheContractWithoutInventingFields', () => {
    expect(IMPORT_BATCHES.every((batch) => Object.keys(batch).sort().join() === 'committedAt,id,name,studentsPendingActivation')).toBe(true);
  });
});
