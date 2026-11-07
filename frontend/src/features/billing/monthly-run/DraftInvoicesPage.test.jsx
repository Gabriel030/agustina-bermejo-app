import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { installFixedClock } from '@/test/fixedClock';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DraftInvoicesPage } from './DraftInvoicesPage';
import { MonthlyRunPage } from './MonthlyRunPage';

const API = 'http://localhost:8080';
const RUNS_URL = `${API}/monthly-invoice-runs`;
const DRAFTS_URL = `${API}/monthly-invoice-runs/7/draft-invoices`;
const ROUTE = '/director/emision-mensual/7/borradores';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: ['DIRECTOR'] }, roles: ['DIRECTOR'] }),
}));

installFixedClock();

function draftGroup(overrides) {
  return {
    name: 'Tecnicatura en Enfermería',
    enrollmentType: 'PROGRAM',
    feeType: 'MONTHLY_INSTALLMENT',
    draftCount: 2,
    draftTotal: { amount: '1000.00', currency: 'ARS' },
    previousPeriodIssuedTotal: { amount: '900.00', currency: 'ARS' },
    difference: { amount: '100.00', currency: 'ARS' },
    differencePercentage: 11.1,
    invoiceIds: [1, 2],
    ...overrides,
  };
}

const PROGRAM_GROUP = draftGroup();
const COURSE_GROUP = draftGroup({
  name: 'Curso de Primeros Auxilios',
  enrollmentType: 'COURSE',
  draftCount: 2,
  draftTotal: { amount: '250.50', currency: 'ARS' },
  invoiceIds: [2, 3],
});
const SEPARATE_GROUP = draftGroup({
  name: 'Tecnicatura en Radiología',
  draftCount: 1,
  draftTotal: { amount: '500.00', currency: 'ARS' },
  previousPeriodIssuedTotal: { amount: '0.00', currency: 'ARS' },
  difference: { amount: '500.00', currency: 'ARS' },
  differencePercentage: 0,
  invoiceIds: [4],
});

function awaitingReviewRun(overrides) {
  return {
    id: 7,
    period: '2026-11-01',
    businessDate: '2026-11-01',
    pricingDate: '2026-11-01',
    issuanceMode: 'REVIEW',
    failureCode: 'NONE',
    mode: 'REAL',
    trigger: 'SCHEDULED',
    status: 'AWAITING_REVIEW',
    startedAt: '2026-11-01T03:00:00',
    finishedAt: '2026-11-01T03:10:00',
    counters: { issued: 0, failed: 0, retriesExhausted: 0, alreadyIssued: 0, notApplicable: 0 },
    ...overrides,
  };
}

function mockModules(mode) {
  server.use(
    http.get(`${API}/me/modules`, () =>
      HttpResponse.json({
        billing: { mode },
        fiscalInvoicing: { enabled: false },
        analytics: { enabled: false },
        paymentMethods: [],
        billingNotifications: { enabled: false },
        externalBilling: { enabled: false },
      }),
    ),
  );
}

function mockDrafts(groups) {
  server.use(
    http.get(RUNS_URL, () => HttpResponse.json([awaitingReviewRun()], { headers: { 'X-Total-Count': '1' } })),
    http.get(DRAFTS_URL, () => HttpResponse.json(groups)),
  );
}

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/director/emision-mensual/:runId/borradores" element={<DraftInvoicesPage />} />
    </Routes>,
    { route: ROUTE },
  );
}

async function selectGroups(user, ...names) {
  for (const name of names) {
    await user.click(await screen.findByRole('checkbox', { name: new RegExp(`Seleccionar ${name}`) }));
  }
}

describe('DraftInvoicesPage', () => {
  it('lists the groups in Spanish with the difference against the previous month', async () => {
    mockDrafts([PROGRAM_GROUP, SEPARATE_GROUP]);
    renderPage();

    expect(await screen.findByText('Tecnicatura en Enfermería')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Borradores de Noviembre 2026' })).toBeInTheDocument();
    expect(screen.getByText('+11,1%')).toBeInTheDocument();
    expect(screen.getByText('Nuevo este mes')).toBeInTheDocument();
    expect(screen.getByText('Sin selección')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Aprobar y emitir/ })).toBeDisabled();
  });

  it('counts a shared invoice once and warns that it is processed whole', async () => {
    mockDrafts([PROGRAM_GROUP, COURSE_GROUP, SEPARATE_GROUP]);
    const user = userEvent.setup();
    renderPage();

    await selectGroups(user, 'Tecnicatura en Enfermería', 'Curso de Primeros Auxilios');

    expect(await screen.findByText('3 facturas seleccionadas')).toBeInTheDocument();
    expect(screen.getByText(/1.250,50/)).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Una factura abarca más de un grupo: se procesa completa, con todas sus líneas.');
  });

  it('warns about the groups left out when only one of the sharing groups is selected', async () => {
    mockDrafts([PROGRAM_GROUP, COURSE_GROUP]);
    const user = userEvent.setup();
    renderPage();

    await selectGroups(user, 'Tecnicatura en Enfermería');
    await user.click(screen.getByRole('button', { name: /Aprobar y emitir/ }));

    const dialog = await screen.findByRole('alertdialog');
    expect(within(dialog).getByText(/Incluye líneas de: Curso de Primeros Auxilios/)).toBeInTheDocument();
    expect(within(dialog).getByText(/o más/)).toBeInTheDocument();
  });

  it('approves the deduplicated ids with an idempotency key and refreshes the list', async () => {
    let drafts = [PROGRAM_GROUP, COURSE_GROUP];
    let sent;
    server.use(
      http.get(RUNS_URL, () => HttpResponse.json([awaitingReviewRun()], { headers: { 'X-Total-Count': '1' } })),
      http.get(DRAFTS_URL, () => HttpResponse.json(drafts)),
      http.post(`${DRAFTS_URL}/approvals`, async ({ request }) => {
        const body = await request.json();
        sent = { key: request.headers.get('Idempotency-Key'), body };
        drafts = [];
        return HttpResponse.json({ invoiceIds: body.invoiceIds, count: body.invoiceIds.length });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await selectGroups(user, 'Tecnicatura en Enfermería', 'Curso de Primeros Auxilios');
    await user.click(screen.getByRole('button', { name: /Aprobar y emitir/ }));
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Emitir' }));

    expect(await screen.findByText('No quedan borradores por revisar')).toBeInTheDocument();
    expect(sent.body).toEqual({ invoiceIds: [1, 2, 3] });
    expect(sent.key).toMatch(/\S{16,}/);
  });

  it('reuses the key after an unknown outcome and generates a new one for a new attempt', async () => {
    const keys = [];
    let outcomes = ['network', 'ok'];
    server.use(
      http.get(RUNS_URL, () => HttpResponse.json([awaitingReviewRun()], { headers: { 'X-Total-Count': '1' } })),
      http.get(DRAFTS_URL, () => HttpResponse.json([PROGRAM_GROUP])),
      http.post(`${DRAFTS_URL}/approvals`, async ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        const outcome = outcomes.shift();
        if (outcome === 'network') return HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 });
        const body = await request.json();
        return HttpResponse.json({ invoiceIds: body.invoiceIds, count: body.invoiceIds.length });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await selectGroups(user, 'Tecnicatura en Enfermería');
    await user.click(screen.getByRole('button', { name: /Aprobar y emitir/ }));
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Emitir' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('No pudimos confirmar si se emitieron');
    await user.click(within(dialog).getByRole('button', { name: 'Emitir' }));
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());

    expect(new Set(keys).size).toBe(1);
    expect(keys).toHaveLength(2);

    outcomes = ['ok'];
    await selectGroups(user, 'Tecnicatura en Enfermería');
    await user.click(screen.getByRole('button', { name: /Aprobar y emitir/ }));
    const secondDialog = await screen.findByRole('alertdialog');
    await user.click(within(secondDialog).getByRole('button', { name: 'Emitir' }));
    await waitFor(() => expect(keys.length).toBeGreaterThanOrEqual(3));
    expect(keys[keys.length - 1]).not.toBe(keys[0]);
  });

  it('refreshes the list and closes the dialog when a draft is no longer pending', async () => {
    let drafts = [PROGRAM_GROUP, SEPARATE_GROUP];
    server.use(
      http.get(RUNS_URL, () => HttpResponse.json([awaitingReviewRun()], { headers: { 'X-Total-Count': '1' } })),
      http.get(DRAFTS_URL, () => HttpResponse.json(drafts)),
      http.post(`${DRAFTS_URL}/approvals`, () => {
        drafts = [SEPARATE_GROUP];
        return HttpResponse.json(errorBody('DRAFT_NOT_PENDING'), { status: 409 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await selectGroups(user, 'Tecnicatura en Enfermería');
    await user.click(screen.getByRole('button', { name: /Aprobar y emitir/ }));
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Emitir' }));

    await waitFor(() => expect(screen.queryByText('Tecnicatura en Enfermería')).not.toBeInTheDocument());
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(screen.getByText('Tecnicatura en Radiología')).toBeInTheDocument();
    expect(screen.getByText('Sin selección')).toBeInTheDocument();
  });

  it('requires a reason to discard, shows the counter and sends the trimmed reason', async () => {
    let sent;
    server.use(
      http.get(RUNS_URL, () => HttpResponse.json([awaitingReviewRun()], { headers: { 'X-Total-Count': '1' } })),
      http.get(DRAFTS_URL, () => HttpResponse.json([PROGRAM_GROUP, SEPARATE_GROUP])),
      http.post(`${DRAFTS_URL}/discards`, async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ invoiceIds: sent.invoiceIds, count: sent.invoiceIds.length });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await selectGroups(user, 'Tecnicatura en Radiología');
    await user.click(screen.getByRole('button', { name: 'Descartar seleccionadas' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('0/500')).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Descartar' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('El motivo del descarte es obligatorio.');
    expect(sent).toBeUndefined();

    await user.type(within(dialog).getByRole('textbox'), '  Precio mal cargado  ');
    expect(within(dialog).getByText('22/500')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Descartar' }));

    await waitFor(() => expect(sent).toEqual({ invoiceIds: [4], reason: 'Precio mal cargado' }));
  });

  it('shows the drafts read-only when billing is in mirror mode', async () => {
    mockModules('MIRROR');
    mockDrafts([PROGRAM_GROUP]);
    renderPage();

    expect(await screen.findByText(/modo espejo/)).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /Seleccionar Tecnicatura en Enfermería/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Descartar seleccionadas' })).toBeDisabled();
  });

  it('offers a retry when the drafts cannot be loaded', async () => {
    server.use(
      http.get(RUNS_URL, () => HttpResponse.json([awaitingReviewRun()], { headers: { 'X-Total-Count': '1' } })),
      http.get(DRAFTS_URL, () => HttpResponse.json(errorBody('MONTHLY_INVOICE_RUN_NOT_AWAITING_REVIEW'), { status: 409 })),
    );
    renderPage();

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});

describe('review banner in the monthly run page', () => {
  it('links to the drafts when the run awaits review', async () => {
    server.use(
      http.get(RUNS_URL, () => HttpResponse.json([awaitingReviewRun()], { headers: { 'X-Total-Count': '1' } })),
      http.get(`${RUNS_URL}/:runId/items`, () => HttpResponse.json([], { headers: { 'X-Total-Count': '0' } })),
    );
    renderWithProviders(<MonthlyRunPage />, { route: '/director/emision-mensual' });

    expect(await screen.findByText('Los borradores de Noviembre 2026 esperan revisión')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Revisar borradores' })).toHaveAttribute('href', '/director/emision-mensual/7/borradores');
    expect(screen.getByText('En revisión')).toBeInTheDocument();
  });
});
