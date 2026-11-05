import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { resetCircuits, setRetrySleep } from '@/api/client';
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

function failNextRequest(times = 1) {
  const spy = vi.spyOn(globalThis, 'fetch');
  for (let attempt = 0; attempt < times; attempt += 1) spy.mockRejectedValueOnce(new TypeError('Failed to fetch'));
  return spy;
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
  beforeEach(() => {
    resetCircuits();
    setRetrySleep(async () => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    setRetrySleep(undefined);
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

    expect(await screen.findByText('Director creado')).toBeInTheDocument();
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

  it('showsTheExistingDirectorViewWhenTheServerAnswersDirectorAlreadyExistsOnSubmit', async () => {
    server.use(noDirector(), http.post(PROVISION_URL, () => HttpResponse.json({ code: 'DIRECTOR_ALREADY_EXISTS', retryable: false }, { status: 409 })));
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
    const fetchSpy = failNextRequest(3);

    await submitAndConfirm(user);
    expect(await screen.findByText('No pudimos confirmar si se creó')).toBeInTheDocument();
    expect(screen.getByLabelText('Nombre de la institución')).toHaveValue('Instituto Nuevo Horizonte');
    const failedKey = fetchSpy.mock.calls[0][1].headers.get('Idempotency-Key');

    await submitAndConfirm(user);
    expect(await screen.findByText('Director creado')).toBeInTheDocument();
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
    const fetchSpy = failNextRequest(3);
    await submitAndConfirm(user);
    await screen.findByText('No pudimos confirmar si se creó');
    const failedKey = fetchSpy.mock.calls[0][1].headers.get('Idempotency-Key');

    await user.type(screen.getByLabelText('Apellido'), 'o');
    await submitAndConfirm(user);

    await screen.findByText('Director creado');
    expect(keys).toHaveLength(1);
    expect(keys[0]).not.toBe(failedKey);
  });

  it('duplicateEmailShowsFieldError', async () => {
    server.use(noDirector(), http.post(PROVISION_URL, () => HttpResponse.json({ code: 'EMAIL_ALREADY_EXISTS', retryable: false }, { status: 400 })));
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    await submitAndConfirm(user);

    expect(await screen.findByText('Ya hay una persona registrada con este email')).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-describedby', 'email-error');
    expect(screen.queryByText('No se pudo crear el Director')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('Email')).toHaveFocus());
    await user.type(screen.getByLabelText('Email'), 'x');
    expect(screen.queryByText('Ya hay una persona registrada con este email')).not.toBeInTheDocument();
  });

  it('duplicateDocumentShowsFieldError', async () => {
    server.use(noDirector(), http.post(PROVISION_URL, () => HttpResponse.json({ code: 'DNI_ALREADY_EXISTS', retryable: false }, { status: 400 })));
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    await submitAndConfirm(user);

    expect(await screen.findByText('Ya hay una persona registrada con este documento')).toBeInTheDocument();
    expect(screen.getByLabelText('Número de documento')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.queryByText('No se pudo crear el Director')).not.toBeInTheDocument();
  });

  it('validationFailedShowsAClearGenericMessage', async () => {
    server.use(noDirector(), http.post(PROVISION_URL, () => HttpResponse.json({ code: 'VALIDATION_FAILED', retryable: false, requestId: 'req-400' }, { status: 400 })));
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    await submitAndConfirm(user);

    expect(await screen.findByText('No se pudo crear el Director')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Alguno de los datos no es válido');
  });

  it('otherConflictDoesNotShowTheExistingDirectorView', async () => {
    server.use(noDirector(), http.post(PROVISION_URL, () => HttpResponse.json({ code: 'SOMETHING_ELSE', retryable: false }, { status: 409 })));
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    await submitAndConfirm(user);

    expect(await screen.findByText('No se pudo crear el Director')).toBeInTheDocument();
    expect(screen.queryByText('Ya existe un Director')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Crear Director' })).toBeInTheDocument();
  });

  it('serverErrorShowsRetryHintAndTheRequestId', async () => {
    server.use(noDirector(), http.post(PROVISION_URL, () => HttpResponse.json({ code: 'INTERNAL_ERROR', retryable: false, requestId: 'req-500' }, { status: 500 })));
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    await submitAndConfirm(user);

    expect(await screen.findByText('No se pudo confirmar la creación')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('si el Director ya se había creado, no se duplica');
    expect(screen.getByRole('alert')).toHaveTextContent('req-500');
    expect(screen.getByLabelText('Nombre de la institución')).toHaveValue('Instituto Nuevo Horizonte');
  });

  it('existingInstitutionPrefillsAndLocksTheName', async () => {
    let received;
    server.use(
      http.get(STATUS_URL, () => HttpResponse.json({ directorExists: false, institutionName: 'Instituto Ya Cargado' })),
      http.post(PROVISION_URL, async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ ...provisioned, institutionCreated: false }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);

    const input = await screen.findByLabelText('Nombre de la institución');
    expect(input).toHaveValue('Instituto Ya Cargado');
    expect(input).toBeDisabled();
    expect(screen.getByText('La institución ya existe, se le asigna el Director.')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Nombre'), 'Carolina');
    await user.type(screen.getByLabelText('Apellido'), 'Ledesma');
    await user.type(screen.getByLabelText('Email'), 'carolina.ledesma@nuevohorizonte.edu.ar');
    await user.type(screen.getByLabelText('Número de documento'), '28441907');
    await user.type(screen.getByLabelText('Motivo'), 'Alta inicial del instituto contratado');
    await submitAndConfirm(user);

    expect(await screen.findByText('Director creado')).toBeInTheDocument();
    expect(received.institutionName).toBe('Instituto Ya Cargado');
  });

  it('successShowsTheStoredInstitutionName', async () => {
    server.use(
      noDirector(),
      http.post(PROVISION_URL, () => {
        server.use(http.get(STATUS_URL, () => HttpResponse.json({ directorExists: true, institutionName: 'Nombre Guardado' })));
        return HttpResponse.json({ ...provisioned, institutionCreated: false }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    await submitAndConfirm(user);

    expect(await screen.findByText('Director creado')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('Nombre Guardado')).toBeInTheDocument());
    expect(screen.queryByText('Instituto Nuevo Horizonte')).not.toBeInTheDocument();
  });

  it('successMentionsTheCodeValidity', async () => {
    server.use(noDirector(), http.post(PROVISION_URL, () => HttpResponse.json(provisioned, { status: 201 })));
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    await submitAndConfirm(user);

    const banner = await screen.findByRole('status');
    expect(banner).toHaveTextContent('El código vale 15 minutos');
    expect(banner).toHaveTextContent('Recuperar contraseña');
    await waitFor(() => expect(banner.parentElement).toHaveFocus());
  });

  it('limitsTheLengthOfEachTextInputToTheBackendLimits', async () => {
    server.use(noDirector());
    renderWithProviders(<PrimerDirector />);

    expect(await screen.findByLabelText('Nombre de la institución')).toHaveAttribute('maxLength', '255');
    expect(screen.getByLabelText('Nombre')).toHaveAttribute('maxLength', '255');
    expect(screen.getByLabelText('Apellido')).toHaveAttribute('maxLength', '255');
    expect(screen.getByLabelText('Email')).toHaveAttribute('maxLength', '255');
    expect(screen.getByLabelText('Número de documento')).toHaveAttribute('maxLength', '50');
  });

  it('rejectsATextLongerThanTheBackendLimitEvenIfPasted', async () => {
    server.use(noDirector());
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    fireEvent.change(screen.getByLabelText('Apellido'), { target: { value: 'a'.repeat(256) } });
    await user.click(screen.getByRole('button', { name: 'Crear Director' }));

    expect(screen.getByText('El apellido admite hasta 255 caracteres')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('linksEachErrorToItsInputAndFocusesTheFirstInvalidField', async () => {
    server.use(noDirector());
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);

    await user.click(await screen.findByRole('button', { name: 'Crear Director' }));

    expect(screen.getByLabelText('Nombre de la institución')).toHaveFocus();
    expect(screen.getByLabelText('Nombre de la institución')).toHaveAttribute('aria-describedby', 'institutionName-error');
    expect(document.getElementById('institutionName-error')).toHaveTextContent('Ingresá el nombre de la institución');
    expect(screen.getByLabelText('Número de documento')).toHaveAttribute('aria-describedby', 'documentNumber-error');
    expect(screen.getByLabelText('Motivo')).toHaveAttribute('aria-describedby', 'reason-error');
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
