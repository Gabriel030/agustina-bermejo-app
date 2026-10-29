import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { roleRoutesFor } from '@/routes/featureRoutes';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { BillingRunsPage } from './BillingRunsPage';
import { countNightsWithErrors, formatRunDuration } from './nightlyRunLabels';

const API = 'http://localhost:8080';

function step(overrides) {
  return {
    id: 1,
    step: 'GATEWAY_SYNC',
    status: 'COMPLETED',
    candidates: 3,
    processed: 3,
    skipped: 0,
    failed: 0,
    notApplicableCode: null,
    startedAt: '2026-10-28T03:00:10',
    finishedAt: '2026-10-28T03:00:20',
    ...overrides,
  };
}

function night(overrides) {
  return {
    id: 10,
    businessDate: '2026-10-28',
    status: 'COMPLETED',
    trigger: 'SCHEDULED',
    durationSeconds: 252,
    failedSteps: 0,
    stepsWithErrors: 0,
    startedAt: '2026-10-28T03:00:00',
    finishedAt: '2026-10-28T03:04:12',
    steps: [step({ id: 1 })],
    ...overrides,
  };
}

const failingNight = night({
  id: 11,
  businessDate: '2026-10-27',
  status: 'COMPLETED_WITH_ERRORS',
  durationSeconds: 280,
  stepsWithErrors: 1,
  steps: [
    step({ id: 2, step: 'ACCOUNT_CONSISTENCY', status: 'COMPLETED_WITH_ERRORS', candidates: 1204, processed: 1203, skipped: 0, failed: 1 }),
    step({ id: 3, step: 'GATEWAY_SYNC', status: 'NOT_APPLICABLE', candidates: 0, processed: 0, notApplicableCode: 'GATEWAY_DISABLED' }),
  ],
});

const heldAccountItem = {
  id: 90,
  status: 'FAILED',
  subjectType: 'CURRENT_ACCOUNT',
  subjectId: 77,
  decisionCode: 'INCONSISTENT_ACCOUNT',
  errorCode: '',
  message: 'La cuenta del alumno no cierra: queda fuera de este paso hasta que se revise',
};

function useRuns(runs) {
  server.use(http.get(`${API}/billing-runs`, () => HttpResponse.json(runs)));
}

describe('BillingRunsPage', () => {
  it('shows a failing step in red with its reason and counts the nights with errors in the tab badge', async () => {
    useRuns([night(), failingNight]);
    server.use(http.get(`${API}/billing-runs/11/steps/ACCOUNT_CONSISTENCY/items`, () => HttpResponse.json([heldAccountItem])));
    const user = userEvent.setup();
    renderWithProviders(<BillingRunsPage />);

    const tab = await screen.findByRole('tab', { name: /Procesos nocturnos/ });
    expect(await within(tab).findByText('1')).toHaveAttribute('data-slot', 'tab-count');

    await user.click(screen.getByRole('combobox', { name: 'Noche' }));
    await user.click(await screen.findByRole('option', { name: /27\/10\/2026/ }));

    const row = (await screen.findByText('Consistencia de cuentas')).closest('tr');
    expect(within(row).getAllByRole('cell')[5].firstChild).toHaveClass('text-danger');
    expect(await within(row).findByText(/La cuenta del alumno no cierra/)).toHaveClass('text-danger');
  });

  it('switches the night from the selector without reloading the list', async () => {
    useRuns([night(), failingNight]);
    let listCalls = 0;
    server.use(
      http.get(`${API}/billing-runs`, () => {
        listCalls += 1;
        return HttpResponse.json([night(), failingNight]);
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<BillingRunsPage />);

    expect(await screen.findByText('Sincronización con pasarelas')).toBeInTheDocument();
    expect(screen.queryByText('Consistencia de cuentas')).not.toBeInTheDocument();

    await user.click(screen.getByRole('combobox', { name: 'Noche' }));
    await user.click(await screen.findByRole('option', { name: /27\/10\/2026/ }));

    expect(await screen.findByText('Consistencia de cuentas')).toBeInTheDocument();
    expect(listCalls).toBe(1);
  });

  it('explains a not applicable step with its reason', async () => {
    useRuns([failingNight]);
    renderWithProviders(<BillingRunsPage />);

    expect(await screen.findByText('No aplica: pasarela desactivada')).toBeInTheDocument();
  });

  it('lists skipped and failed items with their code and flags the held account', async () => {
    useRuns([failingNight]);
    server.use(
      http.get(`${API}/billing-runs/11/steps/ACCOUNT_CONSISTENCY/items`, () =>
        HttpResponse.json([heldAccountItem, { ...heldAccountItem, id: 91, status: 'SKIPPED', subjectType: 'INVOICE', subjectId: 5, decisionCode: 'OTHER_CODE', message: 'Otra cosa' }]),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(<BillingRunsPage />);

    await user.click((await screen.findAllByRole('button', { name: 'Ver omitidos y con error' }))[0]);

    expect(await screen.findByText('Cuenta retenida')).toBeInTheDocument();
    expect(screen.getByText('Cuenta corriente #77')).toBeInTheDocument();
    expect(screen.getByText('INCONSISTENT_ACCOUNT')).toBeInTheDocument();
    expect(screen.getByText('Omitido')).toBeInTheDocument();
  });

  it('shows an empty state when there are no nights', async () => {
    useRuns([]);
    renderWithProviders(<BillingRunsPage />);

    expect(await screen.findByText('Todavía no hay noches registradas')).toBeInTheDocument();
  });

  it('shows an error with a retry that loads the nights', async () => {
    server.use(http.get(`${API}/billing-runs`, () => new HttpResponse(null, { status: 500 })));
    const user = userEvent.setup();
    renderWithProviders(<BillingRunsPage />);

    expect(await screen.findByText('No pudimos cargar los procesos nocturnos')).toBeInTheDocument();
    useRuns([night()]);
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Sincronización con pasarelas')).toBeInTheDocument();
  });

  it('shows a loading skeleton while the nights load', () => {
    server.use(http.get(`${API}/billing-runs`, () => new Promise(() => {})));
    renderWithProviders(<BillingRunsPage />);

    expect(document.querySelector('[aria-busy="true"]')).not.toBeNull();
  });
});

describe('nightly run helpers', () => {
  it('counts failed and with-errors nights but not completed or running ones', () => {
    const runs = [{ status: 'COMPLETED' }, { status: 'COMPLETED_WITH_ERRORS' }, { status: 'FAILED' }, { status: 'RUNNING' }];

    expect(countNightsWithErrors(runs)).toBe(2);
    expect(countNightsWithErrors(undefined)).toBe(0);
  });

  it('formats durations in minutes and seconds', () => {
    expect(formatRunDuration(252)).toBe('4 min 12 s');
    expect(formatRunDuration(45)).toBe('45 s');
  });
});

describe('nightly runs access by role', () => {
  it.each(['administrativo', 'director'])('%s has the route and the menu entry', (role) => {
    expect(roleRoutesFor(role).map((route) => route.path)).toContain('corridas-de-facturacion');
    expect(ROLE_CONFIG[role].pages).toContain('corridas-de-facturacion');
  });

  it.each(['alumno', 'docente', 'preceptor'])('%s has neither the route nor the menu entry', (role) => {
    expect(roleRoutesFor(role).map((route) => route.path)).not.toContain('corridas-de-facturacion');
    expect(ROLE_CONFIG[role].pages).not.toContain('corridas-de-facturacion');
  });
});
