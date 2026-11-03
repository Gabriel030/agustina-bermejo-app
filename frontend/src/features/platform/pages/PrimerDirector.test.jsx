import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PrimerDirector } from './PrimerDirector';

const API = 'http://localhost:8080';
const STATUS_URL = `${API}/platform/provisioning/first-director/status`;
const PROVISION_URL = `${API}/platform/provisioning/first-director`;

const noDirector = () => http.get(STATUS_URL, () => HttpResponse.json({ directorExists: false, institutionName: null }));

const provisioned = {
  institutionId: 1,
  institutionCreated: true,
  directorPersonId: 7,
  directorEmail: 'carolina.ledesma@nuevohorizonte.edu.ar',
};

function failNextRequest() {
  return vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(new TypeError('Failed to fetch'));
}

async function fillForm(user) {
  await user.type(await screen.findByLabelText('Nombre de la institución'), 'Instituto Nuevo Horizonte');
  await user.type(screen.getByLabelText('Nombre'), 'Carolina');
  await user.type(screen.getByLabelText('Apellido'), 'Ledesma');
  await user.type(screen.getByLabelText('Email'), 'carolina.ledesma@nuevohorizonte.edu.ar');
  await user.type(screen.getByLabelText('Número de documento'), '28441907');
  await user.type(screen.getByLabelText('Motivo'), 'Alta inicial del instituto contratado');
}

async function submitAndConfirm(user) {
  await user.click(screen.getByRole('button', { name: 'Crear Director' }));
  await user.click(await screen.findByRole('button', { name: 'Sí, crear Director' }));
}

describe('PrimerDirector', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('showsEachMissingFieldAndDoesNotAskForConfirmationWhenTheFormIsEmpty', async () => {
    server.use(noDirector());
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);

    await user.click(await screen.findByRole('button', { name: 'Crear Director' }));

    expect(screen.getByText('Ingresá el nombre de la institución')).toBeInTheDocument();
    expect(screen.getByText('Ingresá el nombre')).toBeInTheDocument();
    expect(screen.getByText('Ingresá el apellido')).toBeInTheDocument();
    expect(screen.getByText('Ingresá un email válido')).toBeInTheDocument();
    expect(screen.getByText('El motivo necesita al menos 5 caracteres')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('rejectsAReasonLongerThan500Characters', async () => {
    server.use(noDirector());
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    fireEvent.change(screen.getByLabelText('Motivo'), { target: { value: 'a'.repeat(501) } });
    await user.click(screen.getByRole('button', { name: 'Crear Director' }));

    expect(screen.getByText('El motivo admite hasta 500 caracteres')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('confirmsInOneStepSendsTheRequestWithAnIdempotencyKeyAndShowsTheSuccess', async () => {
    let received;
    let idempotencyKey;
    server.use(
      noDirector(),
      http.post(PROVISION_URL, async ({ request }) => {
        received = await request.json();
        idempotencyKey = request.headers.get('Idempotency-Key');
        return HttpResponse.json(provisioned, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    await user.click(screen.getByRole('button', { name: 'Crear Director' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('Instituto Nuevo Horizonte');
    expect(dialog).toHaveTextContent('Carolina Ledesma');
    expect(dialog).toHaveTextContent('carolina.ledesma@nuevohorizonte.edu.ar');
    await user.click(screen.getByRole('button', { name: 'Sí, crear Director' }));

    expect(await screen.findByText('Se envió el email para crear su contraseña')).toBeInTheDocument();
    expect(received).toEqual({
      institutionName: 'Instituto Nuevo Horizonte',
      firstName: 'Carolina',
      lastName: 'Ledesma',
      email: 'carolina.ledesma@nuevohorizonte.edu.ar',
      documentType: 'DNI',
      documentNumber: '28441907',
      reason: 'Alta inicial del instituto contratado',
    });
    expect(idempotencyKey).toBeTruthy();
  });

  it('doesNotSendAnythingWhenTheConfirmationIsCancelled', async () => {
    let posts = 0;
    server.use(
      noDirector(),
      http.post(PROVISION_URL, () => {
        posts += 1;
        return HttpResponse.json(provisioned, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    await user.click(screen.getByRole('button', { name: 'Crear Director' }));
    await user.click(await screen.findByRole('button', { name: 'Cancelar' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(posts).toBe(0);
  });

  it('showsTheExistingDirectorViewWithoutFormWhenTheStatusSaysSo', async () => {
    server.use(http.get(STATUS_URL, () => HttpResponse.json({ directorExists: true, institutionName: 'Instituto Nuevo Horizonte' })));
    renderWithProviders(<PrimerDirector />);

    expect(await screen.findByText('Ya existe un Director')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Instituto Nuevo Horizonte');
    expect(screen.queryByRole('button', { name: 'Crear Director' })).not.toBeInTheDocument();
  });

  it('showsTheExistingDirectorViewWhenTheServerAnswers409OnSubmit', async () => {
    server.use(noDirector(), http.post(PROVISION_URL, () => new HttpResponse(null, { status: 409 })));
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    await submitAndConfirm(user);

    expect(await screen.findByText('Ya existe un Director')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Crear Director' })).not.toBeInTheDocument();
  });

  it('keepsTheFormAndRetriesWithTheSameIdempotencyKeyAfterANetworkError', async () => {
    const keys = [];
    server.use(
      noDirector(),
      http.post(PROVISION_URL, ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        return HttpResponse.json(provisioned, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);
    const fetchSpy = failNextRequest();

    await submitAndConfirm(user);
    expect(await screen.findByText('No pudimos confirmar si se creó')).toBeInTheDocument();
    expect(screen.getByLabelText('Nombre de la institución')).toHaveValue('Instituto Nuevo Horizonte');
    const failedKey = fetchSpy.mock.calls[0][1].headers.get('Idempotency-Key');

    await submitAndConfirm(user);
    expect(await screen.findByText('Se envió el email para crear su contraseña')).toBeInTheDocument();
    expect(failedKey).toBeTruthy();
    expect(keys).toEqual([failedKey]);
  });

  it('usesANewIdempotencyKeyWhenTheDataChangedAfterAFailure', async () => {
    const keys = [];
    server.use(
      noDirector(),
      http.post(PROVISION_URL, ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        return HttpResponse.json(provisioned, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);
    const fetchSpy = failNextRequest();
    await submitAndConfirm(user);
    await screen.findByText('No pudimos confirmar si se creó');
    const failedKey = fetchSpy.mock.calls[0][1].headers.get('Idempotency-Key');

    await user.type(screen.getByLabelText('Apellido'), 'o');
    await submitAndConfirm(user);

    await screen.findByText('Se envió el email para crear su contraseña');
    expect(keys).toHaveLength(1);
    expect(keys[0]).not.toBe(failedKey);
  });

  it('offersToRetryWhenTheStatusCannotBeLoaded', async () => {
    failNextRequest();
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);

    expect(await screen.findByText('No pudimos revisar si ya existe un Director')).toBeInTheDocument();
    server.use(noDirector());
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('button', { name: 'Crear Director' })).toBeInTheDocument();
  });
});
