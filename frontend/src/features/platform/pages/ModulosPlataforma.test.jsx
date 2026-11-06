import { beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { resetCircuits, setRetrySleep } from '@/api/client';
import { axe } from '@/test/axe';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ModulosPlataforma } from './ModulosPlataforma';

const API = 'http://localhost:8080';
const FEATURES_URL = `${API}/platform/features`;
const actor = { id: 1, fullName: 'Plataforma Admin' };

function feature(code, overrides = {}) {
  return { code, enabled: false, mode: 'OFF', lastReason: 'Alta inicial', updatedAt: '2026-11-01T10:00:00', updatedBy: actor, version: 2, ...overrides };
}

function catalog({ billingMode = 'FULL', overrides = {} } = {}) {
  const base = {
    BILLING: feature('BILLING', { enabled: billingMode !== 'OFF', mode: billingMode }),
    FISCAL_INVOICING: feature('FISCAL_INVOICING'),
    ANALYTICS: feature('ANALYTICS', { enabled: true }),
    PAYMENT_METHOD_CASH: feature('PAYMENT_METHOD_CASH', { enabled: true }),
    PAYMENT_METHOD_BANK_TRANSFER: feature('PAYMENT_METHOD_BANK_TRANSFER', { enabled: true }),
    PAYMENT_METHOD_GATEWAY: feature('PAYMENT_METHOD_GATEWAY'),
    BILLING_NOTIFICATIONS: feature('BILLING_NOTIFICATIONS'),
    EXTERNAL_BILLING: feature('EXTERNAL_BILLING'),
  };
  return Object.values({ ...base, ...overrides });
}

const emptyPage = { content: [], empty: true, first: true, last: true, number: 0, numberOfElements: 0, size: 25, totalElements: 0, totalPages: 0 };

const listFeatures = (data) => http.get(FEATURES_URL, () => HttpResponse.json(data));

describe('ModulosPlataforma', () => {
  beforeEach(() => {
    resetCircuits();
    setRetrySleep(async () => {});
    server.use(http.get(`${FEATURES_URL}/changes`, () => HttpResponse.json(emptyPage)));
  });

  it('listsTheEightModulesInTwoGroupsWithTheirStatus', async () => {
    server.use(listFeatures(catalog()));
    renderWithProviders(<ModulosPlataforma />);

    expect(await screen.findByRole('heading', { name: /Cobros y pagos/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Reportes y estadísticas/ })).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(8);
    expect(screen.getByText('Bloqueado: importación sin definir')).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Efectivo' })).toBeChecked();
    expect(screen.getByRole('switch', { name: 'Estadísticas' })).toBeChecked();
    expect(screen.getByRole('switch', { name: 'Facturación externa' })).toBeDisabled();
  });

  it('marksPaymentDependentsAsUnavailableWhenBillingIsNotComplete', async () => {
    server.use(listFeatures(catalog({ billingMode: 'MIRROR' })));
    renderWithProviders(<ModulosPlataforma />);

    await screen.findByRole('heading', { name: /Cobros y pagos/ });
    expect(screen.getAllByText('No disponible · Necesita Facturación completa')).toHaveLength(5);
    expect(screen.getByRole('switch', { name: 'Efectivo' })).toBeDisabled();
    expect(screen.getByRole('switch', { name: 'Estadísticas' })).toBeEnabled();
  });

  it('disablesCompleteBillingUntilTheCutoverProcessExists', async () => {
    server.use(listFeatures(catalog({ billingMode: 'MIRROR' })));
    renderWithProviders(<ModulosPlataforma />);

    const complete = await screen.findByRole('radio', { name: /Completa/ });
    expect(complete).toBeDisabled();
    expect(complete).toHaveAccessibleDescription('Requiere el proceso de corte');
  });

  it('opensTheDiffDialogAndRequiresAReasonOfAtLeastFiveCharacters', async () => {
    server.use(listFeatures(catalog()));
    const user = userEvent.setup();
    renderWithProviders(<ModulosPlataforma />);

    await user.click(await screen.findByRole('switch', { name: 'Efectivo' }));
    const dialog = await screen.findByRole('alertdialog', { name: 'Deshabilitar Efectivo' });
    expect(within(dialog).getByRole('columnheader', { name: 'Hoy' })).toBeInTheDocument();
    expect(within(dialog).getByRole('columnheader', { name: 'Nuevo' })).toBeInTheDocument();
    expect(within(dialog).getByRole('columnheader', { name: 'Diferencia' })).toBeInTheDocument();
    expect(within(dialog).getByText('Se deshabilita')).toBeInTheDocument();
    const confirm = within(dialog).getByRole('button', { name: 'Confirmar' });
    expect(confirm).toBeDisabled();
    await user.type(within(dialog).getByLabelText('Motivo'), 'abcd');
    expect(confirm).toBeDisabled();
    await user.type(within(dialog).getByLabelText('Motivo'), 'e');
    expect(confirm).toBeEnabled();
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('warnsThatEnablingAPaymentMethodLeavesTheDecisionToTheDirector', async () => {
    server.use(listFeatures(catalog()));
    const user = userEvent.setup();
    renderWithProviders(<ModulosPlataforma />);

    await user.click(await screen.findByRole('switch', { name: 'Pasarela de pago (Mercado Pago)' }));
    const dialog = await screen.findByRole('alertdialog', { name: 'Habilitar Pasarela de pago (Mercado Pago)' });
    expect(within(dialog).getByText('Esto lo habilita para el instituto. El Director decide si lo prende.')).toBeInTheDocument();
  });

  it('sendsTheBillingModeChangeWithTheModeAndEnabledFlag', async () => {
    let body;
    server.use(
      listFeatures(catalog({ billingMode: 'OFF' })),
      http.put(`${FEATURES_URL}/BILLING`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ code: 'BILLING', enabled: true, mode: 'MIRROR', version: 3 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<ModulosPlataforma />);

    await user.click(await screen.findByRole('radio', { name: /Calcula sin cobrar/ }));
    const dialog = await screen.findByRole('alertdialog', { name: 'Pasar Facturación a «Calcula sin cobrar»' });
    expect(within(dialog).getByText('Calcula y emite las cuotas')).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Motivo'), 'Prueba de cálculo');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar' }));

    await waitFor(() => expect(body).toEqual({ enabled: true, mode: 'MIRROR', reason: 'Prueba de cálculo' }));
  });

  it('keepsTheReasonAndOffersTheCurrentStateOnAStaleVersion', async () => {
    let current = catalog({ billingMode: 'OFF' });
    const ifMatch = [];
    server.use(
      http.get(FEATURES_URL, () => HttpResponse.json(current)),
      http.put(`${FEATURES_URL}/BILLING`, ({ request }) => {
        ifMatch.push(request.headers.get('If-Match'));
        current = catalog({ billingMode: 'MIRROR', overrides: { BILLING: feature('BILLING', { enabled: true, mode: 'MIRROR', version: 3 }) } });
        return HttpResponse.json({ code: 'STALE_FEATURE_VERSION', retryable: false }, { status: 409 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<ModulosPlataforma />);

    await user.click(await screen.findByRole('radio', { name: /Calcula sin cobrar/ }));
    const dialog = await screen.findByRole('alertdialog');
    await user.type(within(dialog).getByLabelText('Motivo'), 'Motivo largo');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar' }));

    expect(await within(dialog).findByText(/Alguien cambió este módulo/)).toBeInTheDocument();
    expect(ifMatch).toEqual(['2']);
    await user.click(within(dialog).getByRole('button', { name: 'Ver estado actual' }));

    expect(await within(dialog).findByText('Ya estaba así.')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Motivo')).toHaveValue('Motivo largo');
    expect(within(dialog).getByRole('button', { name: 'Cerrar' })).toBeInTheDocument();
  });

  it('showsAReadableMessageWhenTheBackendRejectsTheChange', async () => {
    server.use(
      listFeatures(catalog({ billingMode: 'OFF' })),
      http.put(`${FEATURES_URL}/BILLING`, () => HttpResponse.json({ code: 'INVALID_BILLING_MODE_TRANSITION', retryable: false }, { status: 409 })),
    );
    const user = userEvent.setup();
    renderWithProviders(<ModulosPlataforma />);

    await user.click(await screen.findByRole('radio', { name: /Calcula sin cobrar/ }));
    const dialog = await screen.findByRole('alertdialog');
    await user.type(within(dialog).getByLabelText('Motivo'), 'Motivo válido');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar' }));

    expect(await within(dialog).findByText(/Ese cambio de modo no está permitido/)).toBeInTheDocument();
  });

  it('showsTheLoadErrorStateAndRetries', async () => {
    let calls = 0;
    server.use(
      http.get(FEATURES_URL, () => {
        calls += 1;
        return calls === 1 ? HttpResponse.json({ code: 'FORBIDDEN', retryable: false }, { status: 403 }) : HttpResponse.json(catalog());
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<ModulosPlataforma />);

    expect(await screen.findByText('No pudimos cargar los módulos')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('heading', { name: /Cobros y pagos/ })).toBeInTheDocument();
  });

  it('showsTheEmptyStateWhenThereAreNoModules', async () => {
    server.use(listFeatures([]));
    renderWithProviders(<ModulosPlataforma />);

    expect(await screen.findByText('Todavía no hay módulos configurados')).toBeInTheDocument();
  });

  it('hasNoAxeViolationsInTheListAndInTheDialog', async () => {
    server.use(listFeatures(catalog()));
    const user = userEvent.setup();
    const { container } = renderWithProviders(<ModulosPlataforma />);

    await screen.findByRole('heading', { name: /Cobros y pagos/ });
    expect(await axe(container)).toHaveNoViolations();
    await user.click(screen.getByRole('switch', { name: 'Efectivo' }));
    await screen.findByRole('alertdialog');
    expect(await axe(document.body)).toHaveNoViolations();
  });
});
