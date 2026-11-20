import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithAppCache } from '@/test/renderWithAppCache';
import { recordApiRequests } from '@/test/recordApiRequests';
import { settledRequests } from '@/test/apiRequestCount';
import { errorBody } from '@/test/fixtures/errors';
import { contractList, contractObject } from '@/test/fixtures/fromContract';
import { AdministrativoInicio, DirectorInicio } from '@/features/workspace/pages/StaffHome';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: ['DIRECTOR'] } }),
}));

const API = 'http://localhost:8080';
const daysAgo = (days) => new Date(Date.now() - days * 86400000).toISOString();
const tomorrow = () => new Date(Date.now() + 86400000).toISOString();

const SOURCES = {
  '/graduation-requests': () => contractList('/graduation-requests', [{ requestedAt: daysAgo(9) }, { requestedAt: daysAgo(2) }]),
  '/academic-offering-cycles': () => contractList('/academic-offering-cycles', [{ submittedAt: daysAgo(7) }]),
  '/section-evaluation-configs': () => contractList('/section-evaluation-configs', [{ submittedAt: daysAgo(3) }]),
  '/director-approval-requests': () => contractList('/director-approval-requests', [{ requestedAt: daysAgo(4) }]),
  '/commission-closures': () => contractList('/commission-closures', [{ statusDate: daysAgo(6) }]),
  '/exam-boards': () => contractList('/exam-boards', [{ examDate: tomorrow(), status: 'SCHEDULED' }]),
  '/authorization-requests': () => contractList('/authorization-requests', [{ requestedAt: daysAgo(5), amount: null }]),
  '/regular-certificate-requests': () => contractList('/regular-certificate-requests', [{ requestedAt: daysAgo(3) }]),
  '/program-enrollment-applications/pending': () => contractList('/program-enrollment-applications/pending', [{ requestedAt: daysAgo(5), admissionInvoice: null }]),
  '/attendance/justifications': () => contractList('/attendance/justifications', [{ classDate: daysAgo(4).slice(0, 10) }]),
  '/attendance/change-requests': () => contractList('/attendance/change-requests', [{ createdAt: daysAgo(1) }]),
};

function serve({ billing = 'FULL', empty = false, failing = [] } = {}) {
  const handlers = Object.entries(SOURCES).map(([path, build]) =>
    http.get(`${API}${path}`, () => {
      if (failing.includes(path)) return HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 });
      return HttpResponse.json(empty ? contractList(path, []) : build());
    }),
  );
  server.use(http.get(`${API}/me/modules`, () => HttpResponse.json(contractObject('/me/modules', { billing: { mode: billing } }))), ...handlers);
}

async function readyCard(title) {
  return waitFor(() => {
    const element = screen.getByRole('article', { name: title });
    if (element.querySelector('[aria-busy]')) throw new Error('card still loading');
    return element;
  });
}

describe('StaffHome', () => {
  it('director sees pending counts and navigates', async () => {
    serve();
    renderWithAppCache(<DirectorInicio />, { route: '/director/inicio' });
    const approvals = await readyCard('Otras aprobaciones');
    expect(within(approvals).getByText('4')).toBeInTheDocument();
    expect(within(approvals).getByRole('link', { name: 'Otras aprobaciones' })).toHaveAttribute('href', '/director/aprobaciones');
    expect(within(approvals).getByRole('link', { name: /Egresos/ })).toHaveAttribute('href', '/director/aprobaciones/egresos');
    expect(within(approvals).getByText('Más antiguo: hace 9 días')).toBeInTheDocument();
    expect(within(await readyCard('Excepciones elevadas')).getByRole('link', { name: 'Excepciones elevadas' })).toHaveAttribute('href', '/director/aprobaciones/excepciones');
    expect(within(await readyCard('Cierres sin acta')).getByText('Más antiguo: hace 6 días')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Empezar por lo más urgente' })).toHaveAttribute('href', '/director/aprobaciones');
    expect(screen.getByText('Alumnos en riesgo')).toBeInTheDocument();
    expect(screen.getByText('Próximamente')).toBeInTheDocument();
  });

  it('billing cards hidden when billing is off', async () => {
    serve({ billing: 'OFF' });
    renderWithAppCache(<DirectorInicio />, { route: '/director/inicio' });
    await readyCard('Excepciones elevadas');
    expect(screen.queryByText(/Facturación por autorizar/)).not.toBeInTheDocument();
  });

  it('billing cards shown when billing is on', async () => {
    serve({ billing: 'FULL' });
    renderWithAppCache(<DirectorInicio />, { route: '/director/inicio' });
    expect(await readyCard('Facturación por autorizar')).toBeInTheDocument();
  });

  it('one failing card does not break the others', async () => {
    serve({ failing: ['/director-approval-requests'] });
    renderWithAppCache(<DirectorInicio />, { route: '/director/inicio' });
    const alert = await screen.findByRole('alert');
    const failed = alert.closest('article');
    expect(failed).toHaveAccessibleName('Excepciones elevadas');
    expect(alert).toHaveTextContent('No pudimos cargar esta tarjeta');
    expect(screen.getAllByRole('alert')).toHaveLength(1);
    expect(within(await readyCard('Cierres sin acta')).getByText('1')).toBeInTheDocument();
    serve();
    await userEvent.click(within(failed).getByRole('button', { name: 'Reintentar' }));
    expect(within(await readyCard('Excepciones elevadas')).getByText('1')).toBeInTheDocument();
  });

  it('empty state when nothing is pending', async () => {
    serve({ empty: true });
    renderWithAppCache(<DirectorInicio />, { route: '/director/inicio' });
    expect(await screen.findByText('Todo al día')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Empezar por lo más urgente' })).not.toBeInTheDocument();
  });

  it('administrativo does not see director cards', async () => {
    serve();
    renderWithAppCache(<AdministrativoInicio />, { route: '/administrativo/inicio' });
    const graduations = await readyCard('Egresos por verificar');
    expect(within(graduations).getByRole('link', { name: 'Egresos por verificar' })).toHaveAttribute('href', '/administrativo/egresos-por-verificar');
    expect(screen.queryByRole('article', { name: 'Otras aprobaciones' })).not.toBeInTheDocument();
    expect(screen.queryByRole('article', { name: 'Excepciones elevadas' })).not.toBeInTheDocument();
    expect(screen.queryByRole('article', { name: 'Mesas de examen' })).not.toBeInTheDocument();
  });

  it('director requests each source once in parallel', async () => {
    serve();
    const recorder = recordApiRequests();
    renderWithAppCache(<DirectorInicio />, { route: '/director/inicio' });
    await readyCard('Facturación por autorizar');
    await readyCard('Otras aprobaciones');
    const requests = await settledRequests(recorder);
    recorder.stop();
    expect(recorder.duplicates()).toEqual([]);
    expect(requests.filter((request) => request.startsWith('GET /graduation-requests'))).toHaveLength(1);
    expect(requests.filter((request) => request.startsWith('GET /me/modules'))).toHaveLength(1);
    expect(requests.filter((request) => request.startsWith('GET /authorization-requests'))).toHaveLength(1);
  });

  it.each(['FULL', 'OFF'])('administrativo does not request director-only endpoints with billing %s', async (billing) => {
    serve({ billing });
    const forbidden = [];
    ['/financial-dashboard/debtors/page', '/financial-dashboard', '/director-approval-requests', '/authorization-requests'].forEach((path) =>
      server.use(
        http.get(`${API}${path}`, () => {
          forbidden.push(path);
          return HttpResponse.json(errorBody('ACCESS_DENIED'), { status: 403 });
        }),
      ),
    );
    const recorder = recordApiRequests();
    renderWithAppCache(<AdministrativoInicio />, { route: '/administrativo/inicio' });
    await readyCard('Egresos por verificar');
    await readyCard('Cierres a aprobar');
    await settledRequests(recorder);
    recorder.stop();
    expect(forbidden).toEqual([]);
    expect(screen.queryByText(/Morosos/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Facturación por autorizar/)).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('administrativo requests each source once', async () => {
    serve();
    const recorder = recordApiRequests();
    renderWithAppCache(<AdministrativoInicio />, { route: '/administrativo/inicio' });
    await readyCard('Egresos por verificar');
    const requests = await settledRequests(recorder);
    recorder.stop();
    expect(recorder.duplicates()).toEqual([]);
    expect(requests.filter((request) => request.startsWith('GET /commission-closures'))).toHaveLength(1);
  });
});
