import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse, delay } from 'msw';
import { resetCircuits, setRetrySleep } from '@/api/client';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PrimerDirector } from './PrimerDirector';

const API = 'http://localhost:8080';
const STATUS_URL = `${API}/platform/provisioning/first-director/status`;
const PROVISION_URL = `${API}/platform/provisioning/first-director`;

const noDirector = () => http.get(STATUS_URL, () => HttpResponse.json({ directorExists: false, institutionName: null }));
const provisioned = { institutionId: 1, institutionCreated: true, directorPersonId: 7, directorEmail: 'carolina.ledesma@nuevohorizonte.edu.ar' };

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

describe('PrimerDirector flow', () => {
  beforeEach(() => {
    resetCircuits();
    setRetrySleep(async () => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    setRetrySleep(undefined);
  });

  it('showsAFieldErrorWithItsPrefixOnlyAfterLeavingTheField', async () => {
    server.use(noDirector());
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);

    const email = await screen.findByLabelText('Email');
    await user.type(email, 'lucia@');
    expect(screen.queryByText('Ingresá un email válido')).not.toBeInTheDocument();
    await user.tab();

    expect(await screen.findByText('Ingresá un email válido')).toBeInTheDocument();
    expect(screen.getByText('Ingresá un email válido').closest('p')).toHaveTextContent('Error: Ingresá un email válido');
    expect(email).toHaveAttribute('aria-describedby', 'email-error');
  });

  it('listsTheMissingFieldsNextToTheDisabledButtonAndEnablesItWhenTheFormIsValid', async () => {
    server.use(noDirector());
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);

    const button = await screen.findByRole('button', { name: 'Crear Director' });
    expect(button).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByText('Para crear falta: Institución, Nombre, Apellido, Email, Documento, Motivo')).toBeInTheDocument();

    await fillForm(user);

    expect(button).toHaveAttribute('aria-disabled', 'false');
    expect(screen.getByText('Listo para crear')).toBeInTheDocument();
  });

  it('changesTheDocumentHelpWhenTheTypeChanges', async () => {
    server.use(noDirector());
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);

    expect(await screen.findByText('El DNI tiene 7 u 8 números, sin puntos ni espacios')).toBeInTheDocument();
    await user.click(screen.getByRole('combobox', { name: 'Tipo de documento' }));
    await user.click(await screen.findByRole('option', { name: 'Pasaporte' }));

    expect(await screen.findByText('El pasaporte tiene entre 4 y 20 letras o números, sin espacios ni guiones')).toBeInTheDocument();
  });

  it('summarizesTheDirectorAndAnnouncesAnAlertDialogBeforeCreating', async () => {
    server.use(noDirector());
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    await user.click(screen.getByRole('button', { name: 'Crear Director' }));

    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toHaveTextContent('Se envía un email y no se puede deshacer.');
    expect(dialog).toHaveTextContent('DNI 28441907');
  });

  it('marksTheFormAsBusyWhileCreatingAndIgnoresASecondClick', async () => {
    let posts = 0;
    server.use(
      noDirector(),
      http.post(PROVISION_URL, async () => {
        posts += 1;
        await delay(150);
        return HttpResponse.json(provisioned, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    const { container } = renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    await submitAndConfirm(user);

    const button = await screen.findByRole('button', { name: 'Creando Director…' });
    expect(button).toHaveAttribute('aria-disabled', 'true');
    expect(container.querySelector('form')).toHaveAttribute('aria-busy', 'true');
    await user.click(button);
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    await screen.findByText('Director creado');
    expect(posts).toBe(1);
  });

  it('showsTheTimelineAndTheRealLinksOnSuccess', async () => {
    server.use(noDirector(), http.post(PROVISION_URL, () => HttpResponse.json(provisioned, { status: 201 })));
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    await submitAndConfirm(user);

    expect(await screen.findByText('Qué pasa ahora')).toBeInTheDocument();
    expect(screen.getByText('El Director crea su contraseña')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver en Auditoría' })).toHaveAttribute('href', '/platform_admin/auditoria');
    expect(screen.getByRole('link', { name: 'Ir a Módulos' })).toHaveAttribute('href', '/platform_admin/modulos');
  });

  it('marksTheLockedInstitutionWithAnExistsPill', async () => {
    server.use(http.get(STATUS_URL, () => HttpResponse.json({ directorExists: false, institutionName: 'Instituto Ya Cargado' })));
    renderWithProviders(<PrimerDirector />);

    await screen.findByLabelText('Nombre de la institución');

    expect(screen.getByText('Ya existe')).toBeInTheDocument();
  });

  it('showsAPermissionStateWithoutFormWhenTheStatusIsForbidden', async () => {
    server.use(http.get(STATUS_URL, () => HttpResponse.json({ code: 'FORBIDDEN', retryable: false }, { status: 403 })));
    renderWithProviders(<PrimerDirector />);

    expect(await screen.findByText('No tenés permiso para esta pantalla')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Crear Director' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a Módulos' })).toHaveAttribute('href', '/platform_admin/modulos');
  });

  it('showsALoadingCardWhileCheckingForAnExistingDirector', async () => {
    server.use(http.get(STATUS_URL, async () => {
      await delay(100);
      return HttpResponse.json({ directorExists: false, institutionName: null });
    }));
    renderWithProviders(<PrimerDirector />);

    expect(screen.getByText('Revisando si la institución ya tiene Director…')).toBeInTheDocument();
    await screen.findByRole('button', { name: 'Crear Director' });
  });

  it('movesFocusToTheFailureBannerAfterAServerError', async () => {
    server.use(noDirector(), http.post(PROVISION_URL, () => HttpResponse.json({ code: 'INTERNAL_ERROR', retryable: false, requestId: 'req-500' }, { status: 500 })));
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    await submitAndConfirm(user);

    const banner = await screen.findByRole('alert');
    await waitFor(() => expect(banner).toHaveFocus());
  });

  it('sendsASingleRequestWhenTheConfirmationButtonIsDoubleClicked', async () => {
    let posts = 0;
    server.use(
      noDirector(),
      http.post(PROVISION_URL, async () => {
        posts += 1;
        await delay(200);
        return HttpResponse.json(provisioned, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);
    await user.click(screen.getByRole('button', { name: 'Crear Director' }));

    const confirm = await screen.findByRole('button', { name: 'Sí, crear Director' });
    act(() => {
      confirm.click();
      confirm.click();
    });

    await screen.findByText('Director creado');
    expect(posts).toBe(1);
  });

  it('announcesTheDocumentErrorAsAnAlert', async () => {
    server.use(noDirector(), http.post(PROVISION_URL, () => HttpResponse.json({ code: 'DNI_ALREADY_EXISTS', retryable: false }, { status: 400 })));
    const user = userEvent.setup();
    renderWithProviders(<PrimerDirector />);
    await fillForm(user);

    await submitAndConfirm(user);

    const message = await screen.findByText('Ya hay una persona registrada con este documento');
    expect(message.closest('[role="alert"]')).toHaveAttribute('id', 'documentNumber-error');
  });
});
