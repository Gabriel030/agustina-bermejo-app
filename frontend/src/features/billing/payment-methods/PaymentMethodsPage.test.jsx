import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { resetCircuits, setRetrySleep } from '@/api/client';
import { createDeferred } from '@/test/deferred';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PaymentMethodsPage } from './PaymentMethodsPage';

const API = 'http://localhost:8080';
const SETTINGS_URL = `${API}/institute-settings`;
const AUDIT_URL = `${SETTINGS_URL}/audit`;
const actor = { id: 1, fullName: 'Marcela Ferreyra' };

const auth = vi.hoisted(() => ({ usuario: { roles: ['DIRECTOR'] } }));
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ usuario: auth.usuario }) }));

const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast }));

function settings(overrides = {}) {
  return {
    paymentMethods: [
      { code: 'CASH', enabled: true, effective: true, platformEnabled: true, version: 2 },
      { code: 'BANK_TRANSFER', enabled: true, effective: true, platformEnabled: true, version: 3 },
      { code: 'GATEWAY', enabled: false, effective: false, platformEnabled: false, version: 1 },
    ],
    billingNotifications: { enabled: true, effective: true, platformEnabled: true, version: 4 },
    ...overrides,
  };
}

function change(id, code, before, after, reason, changedAt) {
  return { id, code, before, after, reason, changedAt, actor };
}

function auditPage(content) {
  return { content, empty: content.length === 0, first: true, last: true, number: 0, numberOfElements: content.length, size: 100, totalElements: content.length, totalPages: 1 };
}

const AUDIT = [
  change(4, 'PAYMENT_METHOD_CASH', false, true, 'Reapertura de caja de secretaría', '2026-11-02T10:42:00'),
  change(3, 'BILLING_NOTIFICATIONS', true, false, 'Pausa mientras se revisan los importes', '2026-10-28T16:05:00'),
  change(2, 'PAYMENT_METHOD_BANK_TRANSFER', false, true, 'Alta de la cuenta bancaria', '2026-10-14T09:18:00'),
];

function serveSettings(data = settings()) {
  server.use(http.get(SETTINGS_URL, () => HttpResponse.json(data)));
}

function rejectWith(status, body) {
  return () => HttpResponse.json({ retryable: false, ...body }, { status });
}

async function openDialogFor(user, name) {
  await user.click(await screen.findByRole('switch', { name }));
  return screen.findByRole('alertdialog');
}

describe('PaymentMethodsPage', () => {
  beforeEach(() => {
    resetCircuits();
    setRetrySleep(async () => {});
    auth.usuario = { roles: ['DIRECTOR'] };
    toast.success.mockClear();
    server.use(http.get(AUDIT_URL, () => HttpResponse.json(auditPage(AUDIT))));
  });

  it('showsCashAndTransferWithSwitchesAndTheGatewayAsNotEnabledWithoutSwitch', async () => {
    serveSettings();
    renderWithProviders(<PaymentMethodsPage />);

    expect(await screen.findByRole('switch', { name: 'Efectivo' })).toBeChecked();
    expect(screen.getByRole('switch', { name: 'Transferencia' })).toBeChecked();
    expect(screen.queryByRole('switch', { name: 'Pasarela online' })).not.toBeInTheDocument();
    expect(screen.getByText('No habilitado')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Más información sobre pasarela online' })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Avisos de facturación' })).toBeChecked();
  });

  it('explainsTheMasterSwitchOfBillingNotificationsInATip', async () => {
    serveSettings();
    const user = userEvent.setup();
    renderWithProviders(<PaymentMethodsPage />);

    await user.click(await screen.findByRole('button', { name: 'Más información sobre avisos de facturación' }));

    expect(await screen.findByText(/no sale ningún aviso de cobranza a los alumnos/)).toBeInTheDocument();
  });

  it('keepsTheSwitchUntouchedUntilTheChangeIsConfirmed', async () => {
    serveSettings();
    const user = userEvent.setup();
    renderWithProviders(<PaymentMethodsPage />);

    const dialog = await openDialogFor(user, 'Efectivo');

    expect(within(dialog).getByText('Desactivar Efectivo')).toBeInTheDocument();
    expect(within(dialog).getAllByText('Hoy').length).toBeGreaterThan(0);
    expect(within(dialog).getAllByText('Nuevo').length).toBeGreaterThan(0);
    expect(within(dialog).getByText('Diferencia')).toBeInTheDocument();
    expect(within(dialog).getByText('Se desactiva')).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Efectivo', hidden: true })).toBeChecked();
  });

  it('requiresAReasonOfFiveToFiveHundredCharactersWithACounter', async () => {
    serveSettings();
    const user = userEvent.setup();
    renderWithProviders(<PaymentMethodsPage />);

    const dialog = await openDialogFor(user, 'Efectivo');
    const confirm = within(dialog).getByRole('button', { name: 'Confirmar' });
    expect(confirm).toBeDisabled();
    expect(within(dialog).getByText('0/500')).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText('Motivo'), 'abcd');
    expect(confirm).toBeDisabled();
    expect(within(dialog).getByText('Mínimo 5 caracteres')).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText('Motivo'), 'e');
    expect(confirm).toBeEnabled();
    expect(within(dialog).getByText('5/500')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Motivo')).toHaveAttribute('maxlength', '500');
  });

  it('sendsTheVersionInIfMatchAndTheReasonThenRefreshesTheSwitch', async () => {
    let current = settings();
    const seen = [];
    server.use(
      http.get(SETTINGS_URL, () => HttpResponse.json(current)),
      http.put(`${SETTINGS_URL}/payment-methods/CASH`, async ({ request }) => {
        seen.push({ ifMatch: request.headers.get('If-Match'), body: await request.json() });
        current = settings({ paymentMethods: settings().paymentMethods.map((m) => (m.code === 'CASH' ? { ...m, enabled: false, effective: false, version: 3 } : m)) });
        return HttpResponse.json(current.paymentMethods[0]);
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<PaymentMethodsPage />);

    const dialog = await openDialogFor(user, 'Efectivo');
    await user.type(within(dialog).getByLabelText('Motivo'), '  Cierre de caja por inventario  ');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar' }));

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(seen).toEqual([{ ifMatch: '2', body: { enabled: false, reason: 'Cierre de caja por inventario' } }]);
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Efectivo' })).not.toBeChecked());
    expect(toast.success).toHaveBeenCalledWith('Efectivo desactivado');
  });

  it('changesBillingNotificationsThroughTheirOwnEndpoint', async () => {
    serveSettings();
    const seen = [];
    server.use(
      http.put(`${SETTINGS_URL}/billing-notifications`, async ({ request }) => {
        seen.push({ ifMatch: request.headers.get('If-Match'), body: await request.json() });
        return HttpResponse.json({ enabled: false, effective: false, platformEnabled: true, version: 5 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<PaymentMethodsPage />);

    const dialog = await openDialogFor(user, 'Avisos de facturación');
    await user.type(within(dialog).getByLabelText('Motivo'), 'Pausa por revisión de importes');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar' }));

    await waitFor(() => expect(seen).toHaveLength(1));
    expect(seen[0]).toEqual({ ifMatch: '4', body: { enabled: false, reason: 'Pausa por revisión de importes' } });
  });

  it('showsTheLastDigitalMethodErrorInsideTheDialogAndBlocksConfirmUntilTheReasonChanges', async () => {
    serveSettings(settings({ paymentMethods: settings().paymentMethods.map((m) => (m.code === 'CASH' ? { ...m, enabled: false, effective: false } : m)) }));
    server.use(
      http.put(
        `${SETTINGS_URL}/payment-methods/BANK_TRANSFER`,
        rejectWith(409, { code: 'LAST_DIGITAL_PAYMENT_METHOD_REQUIRED', details: { openOnlineInvoices: 4 } }),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(<PaymentMethodsPage />);

    const dialog = await openDialogFor(user, 'Transferencia');
    await user.type(within(dialog).getByLabelText('Motivo'), 'Cambio de cuenta bancaria');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('No se puede desactivar el último medio digital mientras hay 4 cobros online abiertos.');
    expect(within(dialog).getByRole('button', { name: 'Confirmar' })).toBeDisabled();
    expect(screen.getByRole('switch', { name: 'Transferencia', hidden: true })).toBeChecked();

    await user.type(within(dialog).getByLabelText('Motivo'), '.');
    expect(within(dialog).queryByRole('alert')).not.toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Confirmar' })).toBeEnabled();
  });

  it('tellsWhoAndWhenWhenAnotherDirectorChangedItFirstAndReloadsOnRetry', async () => {
    let current = settings();
    let reads = 0;
    server.use(
      http.get(SETTINGS_URL, () => {
        reads += 1;
        return HttpResponse.json(current);
      }),
      http.put(`${SETTINGS_URL}/payment-methods/CASH`, () => {
        current = settings({ paymentMethods: settings().paymentMethods.map((m) => (m.code === 'CASH' ? { ...m, version: 3 } : m)) });
        return HttpResponse.json(
          { code: 'STALE_INSTITUTE_SETTING_VERSION', retryable: false, details: { changedAt: '2026-11-03T14:05:00', changedById: 7, changedByName: 'Julián Acosta' } },
          { status: 409 },
        );
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<PaymentMethodsPage />);

    const dialog = await openDialogFor(user, 'Efectivo');
    await user.type(within(dialog).getByLabelText('Motivo'), 'Cierre por inventario');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Lo cambió Julián Acosta el 03/11/2026 14:05.');
    await user.click(within(dialog).getByRole('button', { name: 'Recargar y reintentar' }));

    await waitFor(() => expect(reads).toBe(2));
    expect(within(dialog).queryByRole('alert')).not.toBeInTheDocument();
    expect(within(dialog).getByLabelText('Motivo')).toHaveValue('Cierre por inventario');
  });

  it('showsTheScreenInReadOnlyModeForTheAdministrativeWithASingleBannerAndNoAuditTab', async () => {
    auth.usuario = { roles: ['ADMINISTRATIVO'] };
    serveSettings();
    renderWithProviders(<PaymentMethodsPage />);

    expect(await screen.findByText('Solo lectura. Los cambios los hace el director.')).toBeInTheDocument();
    expect(screen.getAllByText('Solo lectura. Los cambios los hace el director.')).toHaveLength(1);
    expect(screen.queryByRole('switch')).not.toBeInTheDocument();
    expect(screen.getAllByText('Activo')).toHaveLength(3);
    expect(screen.queryByRole('tab', { name: 'Auditoría' })).not.toBeInTheDocument();
  });

  it('showsSkeletonsWhileLoadingAndThenTheContent', async () => {
    const gate = createDeferred();
    server.use(
      http.get(SETTINGS_URL, async () => {
        await gate.promise;
        return HttpResponse.json(settings());
      }),
    );
    renderWithProviders(<PaymentMethodsPage />);

    expect(screen.getByLabelText('Cargando medios de pago')).toHaveAttribute('aria-busy', 'true');
    gate.resolve();
    expect(await screen.findByRole('switch', { name: 'Efectivo' })).toBeInTheDocument();
  });

  it('offersRetryWhenTheSettingsFailToLoad', async () => {
    let fail = true;
    server.use(
      http.get(SETTINGS_URL, () => (fail ? HttpResponse.json({ code: 'INTERNAL_ERROR', retryable: false }, { status: 500 }) : HttpResponse.json(settings()))),
    );
    const user = userEvent.setup();
    renderWithProviders(<PaymentMethodsPage />);

    expect(await screen.findByText('No pudimos cargar los medios de pago')).toBeInTheDocument();
    fail = false;
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('switch', { name: 'Efectivo' })).toBeInTheDocument();
  });

  it('listsTheAuditWithWhoWhenAndWhyAndFiltersByWhatChanged', async () => {
    serveSettings();
    const user = userEvent.setup();
    renderWithProviders(<PaymentMethodsPage />);

    await user.click(await screen.findByRole('tab', { name: 'Auditoría' }));

    expect(await screen.findByText('Reapertura de caja de secretaría')).toBeInTheDocument();
    expect(screen.getAllByText('Marcela Ferreyra')).toHaveLength(3);
    expect(screen.getByText('02/11/2026 10:42')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Filtrar por Qué cambió' }));
    await user.click(await screen.findByRole('checkbox', { name: /Efectivo/ }));

    await waitFor(() => expect(screen.queryByText('Reapertura de caja de secretaría')).not.toBeInTheDocument());
    expect(screen.getByText('Pausa mientras se revisan los importes')).toBeInTheDocument();
  });

  it('offersRetryInTheAuditTabWhenTheHistoryFails', async () => {
    serveSettings();
    server.use(http.get(AUDIT_URL, () => HttpResponse.json({ code: 'INTERNAL_ERROR', retryable: false }, { status: 500 })));
    const user = userEvent.setup();
    renderWithProviders(<PaymentMethodsPage />);

    await user.click(await screen.findByRole('tab', { name: 'Auditoría' }));

    expect(await screen.findByText('No pudimos cargar el historial.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
