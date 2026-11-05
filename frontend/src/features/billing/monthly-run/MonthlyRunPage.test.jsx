import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { roleRoutesFor } from '@/routes/featureRoutes';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { MonthlyRunPage } from './MonthlyRunPage';
import { RunCounterCards } from './RunCounterCards';
import { RunSupervisionBanner } from './RunSupervisionBanner';
import { countProblemItems, currentPeriod, formatPeriod } from './monthlyRunLabels';

const API = 'http://localhost:8080';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: ['ADMINISTRATIVO'] }, roles: ['ADMINISTRATIVO'] }),
}));
const PERIOD = currentPeriod();

function run(overrides) {
  return {
    id: 7,
    period: `${PERIOD}-01`,
    businessDate: `${PERIOD}-01`,
    pricingDate: `${PERIOD}-01`,
    issuanceMode: 'DIRECT',
    failureCode: 'NONE',
    mode: 'REAL',
    trigger: 'SCHEDULED',
    status: 'COMPLETED_WITH_FAILURES',
    startedAt: `${PERIOD}-01T03:00:00`,
    finishedAt: `${PERIOD}-01T03:10:00`,
    counters: { issued: 430, failed: 5, retriesExhausted: 2, alreadyIssued: 12, notApplicable: 0 },
    ...overrides,
  };
}

function item(overrides) {
  return { id: 1, enrollmentId: 501, enrollmentType: 'PROGRAM', result: 'FAILED', attempts: 2, errorCode: 'PRICE_MISSING', decisionCode: 'NONE', message: 'Falta el precio', nextRetryAt: `${PERIOD}-02T03:00:00`, invoiceIds: [], ...overrides };
}

function mockRuns(runs, items = [], total = items.length) {
  server.use(
    http.get(`${API}/monthly-invoice-runs`, () => HttpResponse.json(runs, { headers: { 'X-Total-Count': String(runs.length) } })),
    http.get(`${API}/monthly-invoice-runs/:runId/items`, () => HttpResponse.json(items, { headers: { 'X-Total-Count': String(total) } })),
  );
}

describe('MonthlyRunPage', () => {
  it('shows counters and items translated to Spanish with the total shown', async () => {
    mockRuns([run()], [item(), item({ id: 2, enrollmentId: 502, result: 'RETRIES_EXHAUSTED' })], 437);
    renderWithProviders(<MonthlyRunPage />);

    expect(await screen.findByText('Inscripción #501')).toBeInTheDocument();
    expect(screen.getByTestId('counter-issued')).toHaveTextContent('430');
    expect(screen.getByTestId('counter-failed')).toHaveTextContent('5');
    expect(screen.getByTestId('counter-retriesExhausted')).toHaveTextContent('2');
    expect(screen.getByText('Ya emitidas antes: 12')).toBeInTheDocument();
    expect(screen.getByText('Mostrando 2 de 437')).toBeInTheDocument();
    expect(screen.getAllByText('Reintentos agotados').length).toBeGreaterThan(0);
    expect(screen.getByText('Con fallas')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows the missed run banner when the current period has no real run', async () => {
    mockRuns([]);
    renderWithProviders(<MonthlyRunPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent(`La emisión mensual de ${formatPeriod(PERIOD)} todavía no corrió.`);
    expect(screen.getByText(`No hay corrida de ${formatPeriod(PERIOD)}`)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Simular emisión' })).toBeEnabled();
  });

  it('ignores simulation runs when looking for the real run of the period', async () => {
    mockRuns([run({ id: 9, mode: 'SIMULATION' })]);
    renderWithProviders(<MonthlyRunPage />);

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });

  it('disables the simulate button while the run is in progress', async () => {
    mockRuns([run({ status: 'RUNNING' })]);
    renderWithProviders(<MonthlyRunPage />);

    await screen.findByText('En curso');
    expect(screen.getByRole('button', { name: 'Simular emisión' })).toBeDisabled();
    expect(screen.getByRole('progressbar', { name: 'Avance de la corrida' })).toBeInTheDocument();
    expect(screen.getByTestId('run-progress-processed')).toHaveTextContent('449');
    expect(screen.queryByTestId('counter-issued')).not.toBeInTheDocument();
  });

  it('shows an error state with retry when the run cannot be loaded', async () => {
    server.use(http.get(`${API}/monthly-invoice-runs`, () => new HttpResponse(null, { status: 500 })));
    renderWithProviders(<MonthlyRunPage />);

    expect(await screen.findByText('No pudimos cargar la corrida del período.')).toBeInTheDocument();
  });

  it('retries a single item from the row menu', async () => {
    const retried = vi.fn();
    mockRuns([run()], [item()]);
    server.use(
      http.post(`${API}/monthly-invoice-runs/7/items/1/retries`, () => {
        retried();
        return HttpResponse.json(item({ result: 'ISSUED' }));
      }),
    );
    renderWithProviders(<MonthlyRunPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Acciones de Inscripción #501' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: /Reintentar ahora/ }));

    await waitFor(() => expect(retried).toHaveBeenCalledTimes(1));
  });

  it('retries every failed item in bulk but skips the ones that are not retryable', async () => {
    const retriedIds = [];
    mockRuns([run()], [item({ id: 1 }), item({ id: 2, result: 'RETRIES_EXHAUSTED' }), item({ id: 3, result: 'ISSUED' })]);
    server.use(
      http.post(`${API}/monthly-invoice-runs/7/items/:itemId/retries`, ({ params }) => {
        retriedIds.push(params.itemId);
        return HttpResponse.json(item({ result: 'ISSUED' }));
      }),
    );
    renderWithProviders(<MonthlyRunPage />);

    await userEvent.click(await screen.findByRole('button', { name: /Reintentar fallidos/ }));

    await waitFor(() => expect(retriedIds).toEqual(['1', '2']));
  });

  it('disables the bulk retry when there is nothing to retry', async () => {
    mockRuns([run()], [item({ result: 'ISSUED' })]);
    renderWithProviders(<MonthlyRunPage />);

    await screen.findByText('Inscripción #501');
    expect(screen.getByRole('button', { name: /Reintentar fallidos/ })).toBeDisabled();
  });
});

describe('RunSupervisionBanner', () => {
  it('stays hidden for a past period without run', () => {
    renderWithProviders(<RunSupervisionBanner period="2026-01" run={null} isCurrentPeriod={false} />);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('warns when the current run failed', () => {
    renderWithProviders(<RunSupervisionBanner period={PERIOD} run={run({ status: 'FAILED', failureCode: 'BOOM' })} isCurrentPeriod />);
    expect(screen.getByRole('alert')).toHaveTextContent('falló (BOOM)');
  });

  it('stays hidden when the current run completed', () => {
    renderWithProviders(<RunSupervisionBanner period={PERIOD} run={run()} isCurrentPeriod />);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});

describe('RunCounterCards and labels', () => {
  it('defaults missing counters to zero', () => {
    renderWithProviders(<RunCounterCards counters={undefined} />);
    expect(screen.getByTestId('counter-issued')).toHaveTextContent('0');
  });

  it('adds failed and exhausted items as problems', () => {
    expect(countProblemItems(run())).toBe(7);
    expect(countProblemItems(null)).toBe(0);
  });
});

describe('monthly run access by role', () => {
  it.each(['administrativo', 'director'])('%s has the route and the menu entry', (role) => {
    expect(roleRoutesFor(role).map((route) => route.path)).toContain('emision-mensual');
    expect(ROLE_CONFIG[role].pages).toContain('emision-mensual');
  });

  it.each(['alumno', 'docente', 'preceptor'])('%s is blocked', (role) => {
    expect(roleRoutesFor(role).map((route) => route.path)).not.toContain('emision-mensual');
    expect(ROLE_CONFIG[role].pages).not.toContain('emision-mensual');
  });
});
