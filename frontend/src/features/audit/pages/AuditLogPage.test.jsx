import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AuditLogPage } from './AuditLogPage';

const AUDIT_URL = 'http://localhost:8080/audit-log';

function event(id, overrides = {}) {
  return {
    id,
    action: 'STUDENT_WITHDRAWN',
    actorPersonId: 7,
    actorRole: 'DIRECTOR',
    entityType: 'STUDENT',
    entityId: 1307,
    beforeValue: '{"status":"ACTIVE"}',
    afterValue: '{"status":"WITHDRAWN"}',
    reason: 'Baja voluntaria',
    occurredAt: '2026-11-17T15:42:00',
    ...overrides,
  };
}

function pageOf(items, { number = 0, totalPages = 1 } = {}) {
  return { items, page: number, size: 20, totalElements: totalPages > 1 ? 20 * (totalPages - 1) + items.length : items.length };
}

function renderPage() {
  return renderWithProviders(<AuditLogPage endpoint="/audit-log" deniedAudience="Dirección" />);
}

describe('AuditLogPage', () => {
  it('shows page ordered by date and paginates', async () => {
    const pages = [];
    server.use(
      http.get(AUDIT_URL, ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get('page'));
        pages.push(page);
        return HttpResponse.json(
          page === 0
            ? pageOf([event(2, { reason: 'Reciente', occurredAt: '2026-11-17T15:42:00' }), event(1, { reason: 'Antiguo', occurredAt: '2026-11-16T09:00:00' })], { totalPages: 2 })
            : pageOf([event(0, { reason: 'Segunda página' })], { number: 1, totalPages: 2 }),
        );
      }),
    );
    const user = userEvent.setup();
    renderPage();
    const rows = await screen.findAllByRole('row');
    expect(within(rows[1]).getByText('Reciente')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Antiguo')).toBeInTheDocument();
    expect(screen.getAllByText('Persona #7').length).toBeGreaterThan(0);
    await user.click(screen.getByRole('button', { name: /Siguiente/ }));
    expect(await screen.findByText('Segunda página')).toBeInTheDocument();
    expect(pages).toEqual([0, 1]);
  });

  it('action filter sends the action param', async () => {
    const actions = [];
    server.use(
      http.get(AUDIT_URL, ({ request }) => {
        actions.push(new URL(request.url).searchParams.getAll('action'));
        return HttpResponse.json(pageOf([event(1)]));
      }),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Baja voluntaria');
    await user.click(screen.getByRole('button', { name: 'Filtrar por acción' }));
    await user.click(screen.getByRole('checkbox', { name: /Alta de personal/ }));
    await waitFor(() => expect(actions.at(-1)).not.toContain('STAFF_CREATED'));
    expect(actions.at(-1)).toContain('STUDENT_WITHDRAWN');
    expect(actions.at(-1).length).toBe(23);
  });

  it('role filter narrows the list', async () => {
    server.use(
      http.get(AUDIT_URL, ({ request }) => {
        const roles = new URL(request.url).searchParams.getAll('actorRole');
        const all = [event(1, { actorRole: 'DIRECTOR', reason: 'Del director' }), event(2, { actorRole: 'DOCENTE', reason: 'Del docente' })];
        return HttpResponse.json(pageOf(roles.length ? all.filter((item) => roles.includes(item.actorRole)) : all));
      }),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Del docente');
    await user.click(screen.getByRole('button', { name: 'Filtrar por rol' }));
    await user.click(screen.getByRole('checkbox', { name: /Docente/ }));
    await waitFor(() => expect(screen.queryByText('Del docente')).not.toBeInTheDocument());
    expect(screen.getByText('Del director')).toBeInTheDocument();
  });

  it('unauthorized role is handled', async () => {
    server.use(http.get(AUDIT_URL, () => HttpResponse.json(errorBody('FORBIDDEN'), { status: 403 })));
    renderPage();
    expect(await screen.findByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
    expect(screen.getByText(/solo para Dirección/)).toBeInTheDocument();
  });
});
