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
const CODES = ['BILLING', 'FISCAL_INVOICING', 'ANALYTICS', 'PAYMENT_METHOD_CASH', 'PAYMENT_METHOD_BANK_TRANSFER', 'PAYMENT_METHOD_GATEWAY', 'BILLING_NOTIFICATIONS', 'EXTERNAL_BILLING'];

function features(billingMode = 'FULL') {
  return CODES.map((code) => ({
    code,
    enabled: code === 'BILLING' ? billingMode !== 'OFF' : false,
    mode: code === 'BILLING' ? billingMode : 'OFF',
    lastReason: 'Alta inicial',
    updatedAt: '2026-11-01T10:00:00',
    updatedBy: actor,
    version: 2,
  }));
}

function change(id, overrides = {}) {
  return {
    id,
    code: 'BILLING',
    actor,
    actorKind: 'HUMAN',
    before: { enabled: false, mode: 'OFF' },
    after: { enabled: true, mode: 'MIRROR' },
    reason: 'Prueba de cálculo',
    changedAt: '2026-11-02T09:30:00',
    ...overrides,
  };
}

function page(content, { number = 0, totalPages = 1 } = {}) {
  return { content, empty: content.length === 0, first: number === 0, last: number === totalPages - 1, number, numberOfElements: content.length, size: 25, totalElements: content.length, totalPages };
}

function localIso(offsetMs) {
  const date = new Date(Date.now() + offsetMs);
  const pad = (value) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function preview(overrides = {}) {
  return {
    code: 'BILLING',
    confirmationExpiresAt: localIso(15 * 60 * 1000),
    confirmationToken: 'token-1',
    currentMode: 'FULL',
    currentVersion: 2,
    targetMode: 'MIRROR',
    impact: {
      invoicesAwaitingPayment: 4,
      invoicesUnderPaymentReview: 1,
      openInvoices: 12,
      overdueInvoices: 3,
      paymentsPendingReconciliation: 2,
      pendingDraftInvoices: 0,
      refundsInProgress: 1,
      runsInProgress: 0,
      scheduledJobs: ['MONTHLY_INVOICE_RUN'],
    },
    ...overrides,
  };
}

const PREVIEW_URL = `${FEATURES_URL}/BILLING/preview`;

describe('ModulosPlataforma history and previous check', () => {
  beforeEach(() => {
    resetCircuits();
    setRetrySleep(async () => {});
  });

  it('listsTheChangesWithWhoWhenFromToAndReason', async () => {
    server.use(
      http.get(FEATURES_URL, () => HttpResponse.json(features())),
      http.get(`${FEATURES_URL}/changes`, () => HttpResponse.json(page([change(1), change(2, { actor: { id: 0, fullName: 'Sistema interno' }, actorKind: 'SERVICE', code: 'ANALYTICS', before: { enabled: true, mode: 'OFF' }, after: { enabled: false, mode: 'OFF' } })]))),
    );
    renderWithProviders(<ModulosPlataforma />);

    const section = await screen.findByRole('region', { name: 'Historial de cambios' });
    const rows = await within(section).findAllByRole('row');
    expect(rows).toHaveLength(3);
    expect(within(rows[1]).getByText('02/11/2026 09:30')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Plataforma Admin')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Apagada')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Calcula sin cobrar')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Prueba de cálculo')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Habilitado')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Deshabilitado')).toBeInTheDocument();
  });

  it('filtersByModuleAndPagesWithTheRequestedParameters', async () => {
    const urls = [];
    server.use(
      http.get(FEATURES_URL, () => HttpResponse.json(features())),
      http.get(`${FEATURES_URL}/changes`, ({ request }) => {
        urls.push(new URL(request.url).search);
        return HttpResponse.json(page([change(1)], { number: Number(new URL(request.url).searchParams.get('page')), totalPages: 2 }));
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<ModulosPlataforma />);

    await screen.findByText('Prueba de cálculo');
    await user.click(screen.getByRole('button', { name: /Siguiente/ }));
    await waitFor(() => expect(urls).toContain('?page=1&size=25'));
    await user.selectOptions(screen.getByLabelText('Módulo'), 'ANALYTICS');
    await waitFor(() => expect(urls).toContain('?page=0&size=25&code=ANALYTICS'));
  });

  it('showsTheEmptyStateAndTheLoadErrorWithRetry', async () => {
    let calls = 0;
    server.use(
      http.get(FEATURES_URL, () => HttpResponse.json(features())),
      http.get(`${FEATURES_URL}/changes`, () => {
        calls += 1;
        return calls === 1 ? HttpResponse.json({ code: 'FORBIDDEN', retryable: false }, { status: 403 }) : HttpResponse.json(page([]));
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<ModulosPlataforma />);

    const section = await screen.findByRole('region', { name: 'Historial de cambios' });
    expect(await within(section).findByText('No pudimos cargar el historial')).toBeInTheDocument();
    await user.click(within(section).getByRole('button', { name: 'Reintentar' }));
    expect(await within(section).findByText('Todavía no hay cambios')).toBeInTheDocument();
  });

  it('hasNoAccessibilityViolations', async () => {
    server.use(
      http.get(FEATURES_URL, () => HttpResponse.json(features())),
      http.get(`${FEATURES_URL}/changes`, () => HttpResponse.json(page([change(1)]))),
    );
    const { container } = renderWithProviders(<ModulosPlataforma />);
    await screen.findByText('Prueba de cálculo');
    expect(await axe(container)).toHaveNoViolations();
  });

  it('leavesCompleteThroughTheImpactStepAndSendsThePreviewToken', async () => {
    let body;
    server.use(
      http.get(FEATURES_URL, () => HttpResponse.json(features())),
      http.get(`${FEATURES_URL}/changes`, () => HttpResponse.json(page([]))),
      http.get(PREVIEW_URL, ({ request }) => {
        expect(new URL(request.url).searchParams.get('target')).toBe('MIRROR');
        return HttpResponse.json(preview());
      }),
      http.put(`${FEATURES_URL}/BILLING`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ code: 'BILLING', enabled: true, mode: 'MIRROR', version: 3 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<ModulosPlataforma />);

    await user.click(await screen.findByRole('radio', { name: /Calcula sin cobrar/ }));
    const dialog = await screen.findByRole('alertdialog');
    expect(await within(dialog).findByText('Facturas abiertas')).toBeInTheDocument();
    expect(within(dialog).getByText('12')).toBeInTheDocument();
    expect(within(dialog).getByText('MONTHLY_INVOICE_RUN')).toBeInTheDocument();
    expect(within(dialog).getByText(/Paso 1 de 2/)).toBeInTheDocument();
    expect(within(dialog).queryByLabelText('Motivo')).not.toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Continuar' }));
    expect(within(dialog).getByText(/Paso 2 de 2/)).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Motivo'), 'Freno de emergencia');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar' }));

    await waitFor(() => expect(body).toEqual({ enabled: true, mode: 'MIRROR', reason: 'Freno de emergencia', previewConfirmationToken: 'token-1' }));
  });

  it('asksToRecalculateWhenThePreviewExpiredAndKeepsTheReason', async () => {
    let calls = 0;
    server.use(
      http.get(FEATURES_URL, () => HttpResponse.json(features())),
      http.get(`${FEATURES_URL}/changes`, () => HttpResponse.json(page([]))),
      http.get(PREVIEW_URL, () => {
        calls += 1;
        return HttpResponse.json(calls === 1 ? preview({ confirmationExpiresAt: localIso(1500) }) : preview({ confirmationToken: 'token-2' }));
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<ModulosPlataforma />);

    await user.click(await screen.findByRole('radio', { name: /Calcula sin cobrar/ }));
    const dialog = await screen.findByRole('alertdialog');
    await user.click(await within(dialog).findByRole('button', { name: 'Continuar' }));
    await user.type(within(dialog).getByLabelText('Motivo'), 'Motivo conservado');
    expect(await within(dialog).findByText('La verificación venció.', {}, { timeout: 4000 })).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Recalcular' }));
    await waitFor(() => expect(calls).toBe(2));
    await user.click(await within(dialog).findByRole('button', { name: 'Continuar' }));
    expect(within(dialog).getByLabelText('Motivo')).toHaveValue('Motivo conservado');
  });

  it('returnsToTheImpactStepWhenTheBackendRejectsTheToken', async () => {
    let calls = 0;
    server.use(
      http.get(FEATURES_URL, () => HttpResponse.json(features())),
      http.get(`${FEATURES_URL}/changes`, () => HttpResponse.json(page([]))),
      http.get(PREVIEW_URL, () => {
        calls += 1;
        return HttpResponse.json(preview());
      }),
      http.put(`${FEATURES_URL}/BILLING`, () => HttpResponse.json({ code: 'INVALID_TRANSITION_CONFIRMATION', retryable: false }, { status: 409 })),
    );
    const user = userEvent.setup();
    renderWithProviders(<ModulosPlataforma />);

    await user.click(await screen.findByRole('radio', { name: /Apagada/ }));
    const dialog = await screen.findByRole('alertdialog');
    await user.click(await within(dialog).findByRole('button', { name: 'Continuar' }));
    await user.type(within(dialog).getByLabelText('Motivo'), 'Motivo válido');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar' }));

    expect(await within(dialog).findByText(/Paso 1 de 2/)).toBeInTheDocument();
    await waitFor(() => expect(calls).toBe(2));
  });

  it('showsAnErrorWithRetryWhenThePreviewFails', async () => {
    server.use(
      http.get(FEATURES_URL, () => HttpResponse.json(features())),
      http.get(`${FEATURES_URL}/changes`, () => HttpResponse.json(page([]))),
      http.get(PREVIEW_URL, () => HttpResponse.json({ code: 'INTERNAL_ERROR', retryable: false }, { status: 500 })),
    );
    const user = userEvent.setup();
    renderWithProviders(<ModulosPlataforma />);

    await user.click(await screen.findByRole('radio', { name: /Calcula sin cobrar/ }));
    const dialog = await screen.findByRole('alertdialog');
    expect(await within(dialog).findByText('No pudimos calcular el impacto del cambio.')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Continuar' })).toBeDisabled();
  });
});
