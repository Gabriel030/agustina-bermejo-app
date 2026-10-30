import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Toaster } from 'sonner';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { todayIsoDate } from '@/lib/billing';
import { ManualIssuanceSimulationPanel } from './ManualIssuanceSimulationPanel';
import { MonthlyRunPage } from './MonthlyRunPage';
import { currentPeriod, formatPeriod, isNightlyCompletedOn, isSimulationValid, summarizeSimulation } from './monthlyRunLabels';

const API = 'http://localhost:8080';
const PERIOD = currentPeriod();
const TODAY = todayIsoDate();

const auth = vi.hoisted(() => ({ roles: ['DIRECTOR'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: auth.roles }, roles: auth.roles }),
}));

function line(overrides) {
  return {
    id: 1,
    enrollmentId: 501,
    enrollmentType: 'PROGRAM',
    offeringId: 3,
    result: 'ISSUED',
    chargeCount: 2,
    total: { amount: '1500.50', currency: 'ARS' },
    dueDate: `${PERIOD}-18`,
    errorCode: 'NONE',
    decisionCode: 'NONE',
    message: '',
    ...overrides,
  };
}

function simulationResponse(overrides) {
  return {
    runId: 88,
    period: `${PERIOD}-01`,
    businessDate: TODAY,
    pricingDate: `${PERIOD}-01`,
    status: 'COMPLETED',
    trigger: 'MANUAL',
    startedAt: `${TODAY}T10:00:00`,
    finishedAt: `${TODAY}T10:00:05`,
    alerts: [],
    lines: [line(), line({ id: 2, enrollmentId: 502, total: { amount: '2000.00', currency: 'ARS' }, chargeCount: 1 })],
    ...overrides,
  };
}

function realRun(overrides) {
  return {
    id: 7,
    period: `${PERIOD}-01`,
    businessDate: `${PERIOD}-01`,
    pricingDate: `${PERIOD}-01`,
    issuanceMode: 'DIRECT',
    failureCode: 'NONE',
    mode: 'REAL',
    trigger: 'SCHEDULED',
    status: 'COMPLETED',
    startedAt: `${PERIOD}-01T03:00:00`,
    finishedAt: `${PERIOD}-01T03:10:00`,
    counters: { issued: 1, failed: 0, retriesExhausted: 0, alreadyIssued: 0, notApplicable: 0 },
    ...overrides,
  };
}

function nightly(overrides) {
  return { id: 1, businessDate: TODAY, status: 'COMPLETED', ...overrides };
}

function mockBackend({ runs = [], nightlyRuns = [nightly()], simulation = simulationResponse(), issue } = {}) {
  server.use(
    http.get(`${API}/monthly-invoice-runs`, () => HttpResponse.json(runs, { headers: { 'X-Total-Count': String(runs.length) } })),
    http.get(`${API}/monthly-invoice-runs/:runId/items`, () => HttpResponse.json([], { headers: { 'X-Total-Count': '0' } })),
    http.get(`${API}/billing-runs`, () => HttpResponse.json(nightlyRuns)),
    http.post(`${API}/monthly-invoice-runs/simulations`, () => HttpResponse.json(simulation, { status: 201 })),
    http.post(`${API}/monthly-invoice-runs/manual-issuances`, issue ?? (() => HttpResponse.json(realRun({ id: 9, trigger: 'MANUAL' }), { status: 201 }))),
  );
}

async function openSimulation() {
  renderWithProviders(
    <>
      <MonthlyRunPage />
      <Toaster />
    </>,
  );
  await userEvent.click(await screen.findByRole('button', { name: 'Simular emisión' }));
  return screen.findByTestId('simulation-panel');
}

beforeEach(() => {
  auth.roles = ['DIRECTOR'];
});

describe('manual issuance simulation panel', () => {
  it('shows the simulation result with installments, total and per item due date', async () => {
    mockBackend();
    const panel = await openSimulation();

    await waitFor(() => expect(within(panel).getByRole('button', { name: /Emitir 3 cuotas/ })).toBeEnabled());
    expect(within(panel).getByText('A emitir').nextSibling).toHaveTextContent('2');
    await userEvent.click(within(panel).getByText('Ver detalle por inscripción'));
    expect(within(panel).getAllByText(/Vence \d\d\/\d\d\/\d{4}/)).toHaveLength(2);
  });

  it('shows no due date for an item without one', async () => {
    mockBackend();
    const simulation = { data: simulationResponse({ lines: [line({ dueDate: null })] }), isPending: false, isError: false };
    renderWithProviders(<ManualIssuanceSimulationPanel period={PERIOD} run={null} isCurrentPeriod isDirector simulation={simulation} onRerun={() => {}} onClose={() => {}} onIssued={() => {}} />);

    await userEvent.click(screen.getByText('Ver detalle por inscripción'));
    expect(screen.getAllByText('Sin vencimiento').length).toBeGreaterThan(1);
  });

  it('lets the administrative user simulate but hides the issue button', async () => {
    auth.roles = ['ADMINISTRATIVO'];
    mockBackend();
    const panel = await openSimulation();

    expect(await within(panel).findByText('A emitir')).toBeInTheDocument();
    expect(within(panel).getByText('La emisión la hace dirección.')).toBeInTheDocument();
    expect(within(panel).queryByRole('button', { name: /Emitir/ })).not.toBeInTheDocument();
  });

  it('shows the issue button to the director', async () => {
    mockBackend();
    const panel = await openSimulation();

    expect(await within(panel).findByRole('button', { name: /Emitir 3 cuotas/ })).toBeInTheDocument();
    expect(within(panel).queryByText('La emisión la hace dirección.')).not.toBeInTheDocument();
  });

  it('disables issuing and lists the unmet precondition when the nightly run is not finished', async () => {
    mockBackend({ nightlyRuns: [nightly({ status: 'RUNNING' })] });
    const panel = await openSimulation();

    await within(panel).findByText('A emitir');
    expect(within(panel).getByTestId('precondition-nightly')).toHaveAttribute('data-ok', 'false');
    expect(within(panel).getByRole('button', { name: /Emitir/ })).toBeDisabled();
  });

  it('disables issuing when the simulation has failed', async () => {
    mockBackend({ simulation: simulationResponse({ status: 'FAILED' }) });
    const panel = await openSimulation();

    await within(panel).findByText('A emitir');
    expect(within(panel).getByTestId('precondition-simulation')).toHaveAttribute('data-ok', 'false');
    expect(within(panel).getByRole('button', { name: /Emitir/ })).toBeDisabled();
  });

  it('disables issuing when the simulation is not from today', async () => {
    mockBackend({ simulation: simulationResponse({ businessDate: '2020-01-01' }) });
    const panel = await openSimulation();

    await within(panel).findByText('A emitir');
    expect(within(panel).getByRole('button', { name: /Emitir/ })).toBeDisabled();
  });

  it('disables issuing when the period already has a real run', async () => {
    mockBackend({ runs: [realRun()] });
    const panel = await openSimulation();

    await within(panel).findByText('A emitir');
    expect(within(panel).getByTestId('precondition-no-run')).toHaveAttribute('data-ok', 'false');
    expect(within(panel).getByRole('button', { name: /Emitir/ })).toBeDisabled();
  });

  it('warns about items that would fail and shows simulation alerts', async () => {
    mockBackend({
      simulation: simulationResponse({
        lines: [line(), line({ id: 2, enrollmentId: 502, result: 'FAILED', message: 'Falta el precio' })],
        alerts: [{ id: 1, alertType: 'PRICE_MISSING', message: 'Sin precio en Tecnicatura', affectedEnrollments: 1, enrollmentType: 'PROGRAM', offeringId: 3 }],
      }),
    });
    const panel = await openSimulation();

    expect(await within(panel).findByText('1 inscripción fallaría y no se emitiría.')).toBeInTheDocument();
    expect(within(panel).getByText('Sin precio en Tecnicatura')).toBeInTheDocument();
  });

  it('shows an error with retry when the simulation request fails', async () => {
    mockBackend();
    server.use(http.post(`${API}/monthly-invoice-runs/simulations`, () => new HttpResponse(null, { status: 500 })));
    const panel = await openSimulation();

    expect(await within(panel).findByText('No pudimos simular la emisión.')).toBeInTheDocument();
  });
});

describe('manual issuance confirmation dialog', () => {
  async function openDialog() {
    const panel = await openSimulation();
    const button = await within(panel).findByRole('button', { name: /Emitir 3 cuotas/ });
    await waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);
    return screen.findByRole('dialog');
  }

  it('shows what will be issued and sends the period with the simulation id', async () => {
    let body;
    mockBackend({
      issue: async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(realRun({ id: 9, trigger: 'MANUAL' }), { status: 201 });
      },
    });
    const dialog = await openDialog();

    expect(within(dialog).getByText(`¿Emitir las cuotas de ${formatPeriod(PERIOD)}?`)).toBeInTheDocument();
    expect(within(dialog).getByText('3')).toBeInTheDocument();
    expect(within(dialog).getByText(/\d\d\/\d\d\/\d{4}/)).toBeInTheDocument();
    expect(within(dialog).getByText('Esta acción no se puede deshacer.')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Emitir' }));

    await waitFor(() => expect(body).toEqual({ period: PERIOD, simulationRunId: 88 }));
    expect(await screen.findByText(`Emitimos las cuotas de ${formatPeriod(PERIOD)}.`)).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByTestId('simulation-panel')).not.toBeInTheDocument());
  });

  it('cancels without calling the backend', async () => {
    const issued = vi.fn();
    mockBackend({
      issue: () => {
        issued();
        return HttpResponse.json(realRun(), { status: 201 });
      },
    });
    const dialog = await openDialog();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(issued).not.toHaveBeenCalled();
  });

  it.each([
    ['MANUAL_ISSUANCE_OUTSIDE_CURRENT_MONTH', 'Solo se puede emitir a mano el mes en curso.'],
    ['MANUAL_ISSUANCE_REQUIRES_SIMULATION', 'La simulación ya no es válida. Volvé a simular antes de emitir.'],
    ['NIGHTLY_BILLING_NOT_COMPLETED', 'La corrida nocturna de hoy todavía no terminó. Esperá a que finalice para emitir.'],
    ['MONTHLY_INVOICE_RUN_ALREADY_EXISTS', 'Este período ya tiene una corrida. Revisala en la pantalla.'],
  ])('maps the 409 %s to a clear message', async (code, message) => {
    mockBackend({ issue: () => HttpResponse.json({ code, violations: [] }, { status: 409 }) });
    const dialog = await openDialog();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Emitir' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(message);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('falls back to a generic message for an unknown failure', async () => {
    mockBackend({ issue: () => new HttpResponse(null, { status: 500 }) });
    const dialog = await openDialog();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Emitir' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('No pudimos emitir las cuotas. Intentá de nuevo.');
  });
});

describe('simulation helpers', () => {
  it('summarizes installments, total in cents and due date range', () => {
    const summary = summarizeSimulation(
      simulationResponse({
        lines: [
          line({ total: { amount: '0.10', currency: 'ARS' }, dueDate: '2026-11-18' }),
          line({ id: 2, total: { amount: '0.20', currency: 'ARS' }, dueDate: '2026-11-20' }),
          line({ id: 3, result: 'FAILED', total: { amount: '999.00', currency: 'ARS' } }),
        ],
      }),
    );
    expect(summary).toMatchObject({ issuableCount: 2, installmentCount: 4, failingCount: 1, firstDueDate: '2026-11-18', lastDueDate: '2026-11-20' });
    expect(summary.total.amount).toBe('0.30');
  });

  it('rejects a simulation of another period, another day or without items to issue', () => {
    const base = simulationResponse();
    expect(isSimulationValid(base, PERIOD, TODAY)).toBe(true);
    expect(isSimulationValid(base, '2020-01', TODAY)).toBe(false);
    expect(isSimulationValid(base, PERIOD, '2020-01-01')).toBe(false);
    expect(isSimulationValid({ ...base, lines: [] }, PERIOD, TODAY)).toBe(false);
    expect(isSimulationValid(null, PERIOD, TODAY)).toBe(false);
  });

  it('accepts a nightly run only if it finished on the given day', () => {
    expect(isNightlyCompletedOn([nightly()], TODAY)).toBe(true);
    expect(isNightlyCompletedOn([nightly({ status: 'COMPLETED_WITH_ERRORS' })], TODAY)).toBe(true);
    expect(isNightlyCompletedOn([nightly({ status: 'FAILED' })], TODAY)).toBe(false);
    expect(isNightlyCompletedOn([nightly({ businessDate: '2020-01-01' })], TODAY)).toBe(false);
  });
});
