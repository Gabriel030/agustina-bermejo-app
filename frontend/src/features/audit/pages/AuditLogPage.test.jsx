import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { AUDIT_ACTION_CODES } from '@/lib/auditLabels';
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
    actorName: 'Marcela Quiroga',
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
    expect(screen.getAllByText('Marcela Quiroga').length).toBeGreaterThan(0);
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
    expect(actions.at(-1).length).toBe(AUDIT_ACTION_CODES.length - 1);
  });

  it('actor name filter sends actorName once the text has two characters', async () => {
    const names = [];
    server.use(
      http.get(AUDIT_URL, ({ request }) => {
        names.push(new URL(request.url).searchParams.get('actorName'));
        return HttpResponse.json(pageOf([event(1)]));
      }),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Baja voluntaria');
    await user.type(screen.getByRole('searchbox', { name: 'Actor: nombre o ID' }), 'M');
    await user.type(screen.getByRole('searchbox', { name: 'Actor: nombre o ID' }), 'a');
    await waitFor(() => expect(names.at(-1)).toBe('Ma'));
    expect(names).not.toContain('M');
    expect(screen.queryByRole('searchbox', { name: 'ID de actor' })).not.toBeInTheDocument();
  });

  it('actor field sends actorPersonId when the text is only digits', async () => {
    const params = [];
    server.use(
      http.get(AUDIT_URL, ({ request }) => {
        params.push(Object.fromEntries(new URL(request.url).searchParams));
        return HttpResponse.json(pageOf([event(1)]));
      }),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Baja voluntaria');
    await user.type(screen.getByRole('searchbox', { name: 'Actor: nombre o ID' }), '12');
    await waitFor(() => expect(params.at(-1)).toMatchObject({ actorPersonId: '12' }));
    expect(params.at(-1)).not.toHaveProperty('actorName');
  });

  it('clearing the actor field drops the actor filter from the query', async () => {
    const names = [];
    server.use(
      http.get(AUDIT_URL, ({ request }) => {
        names.push(new URL(request.url).searchParams.get('actorName'));
        return HttpResponse.json(pageOf([event(1)]));
      }),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Baja voluntaria');
    await user.type(screen.getByRole('searchbox', { name: 'Actor: nombre o ID' }), 'Ma');
    await waitFor(() => expect(names.at(-1)).toBe('Ma'));
    const countBeforeClear = names.length;
    await user.click(screen.getByRole('button', { name: /Limpiar filtros/ }));
    await waitFor(() => expect(names.length).toBeGreaterThan(countBeforeClear));
    expect(names.slice(countBeforeClear)).toEqual([null]);
    expect(screen.getByRole('searchbox', { name: 'Actor: nombre o ID' })).toHaveValue('');
  });

  it('actor column shows the name and the id below it', async () => {
    server.use(http.get(AUDIT_URL, () => HttpResponse.json(pageOf([event(1)]))));
    renderPage();
    const row = (await screen.findByText('Baja voluntaria')).closest('tr');
    expect(within(row).getByText('Marcela Quiroga')).toBeInTheDocument();
    expect(within(row).getByText('#7')).toBeInTheDocument();
  });

  it('role filter narrows the list', async () => {
    server.use(
      http.get(AUDIT_URL, ({ request }) => {
        const roles = new URL(request.url).searchParams.getAll('actorRole');
        const all = [event(1, { actorRole: 'DIRECTOR', reason: 'Del director' }), event(2, { actorRole: 'DOCENTE', actorName: 'Rubén Ledesma', reason: 'Del docente' })];
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

  it('shows the empty state without filters and the no results state with filters', async () => {
    server.use(http.get(AUDIT_URL, () => HttpResponse.json(pageOf([]))));
    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByText('Todavía no hay actividad registrada')).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Tipo de entidad'), 'STUDENT');
    expect(await screen.findByText('Ningún evento coincide')).toBeInTheDocument();
    await user.click(screen.getAllByRole('button', { name: 'Limpiar filtros' })[0]);
    expect(await screen.findByText('Todavía no hay actividad registrada')).toBeInTheDocument();
  });

  it('shows a retry state on a server error', async () => {
    server.use(http.get(AUDIT_URL, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: false }), { status: 500 })));
    renderPage();
    expect(await screen.findByText('No pudimos cargar la auditoría')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('shows loading skeletons while the request is pending', () => {
    server.use(http.get(AUDIT_URL, () => new Promise(() => {})));
    renderPage();
    expect(screen.getByRole('status', { name: 'Cargando auditoría' })).toBeInTheDocument();
  });

  it('rejects an inverted date range without sending it', async () => {
    const calls = [];
    server.use(
      http.get(AUDIT_URL, ({ request }) => {
        calls.push(new URL(request.url).searchParams.get('from'));
        return HttpResponse.json(pageOf([event(1)]));
      }),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Baja voluntaria');
    await user.type(screen.getByLabelText('Hasta'), '2026-11-01');
    await user.type(screen.getByLabelText('Desde'), '2026-11-10');
    expect(await screen.findByRole('alert')).toHaveTextContent('no puede ser posterior');
    expect(calls.every((from) => from === null || from === '')).toBe(true);
  });

  it('opens the detail with masked sensitive values', async () => {
    server.use(
      http.get(AUDIT_URL, () =>
        HttpResponse.json(pageOf([event(1, { action: 'PASSWORD_CHANGED', beforeValue: '{"passwordHash":"abc123"}', afterValue: 'texto { roto' })])),
      ),
    );
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByText('Baja voluntaria'));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/••••••••/, { selector: 'pre' })).toBeInTheDocument();
    expect(dialog).not.toHaveTextContent('abc123');
    expect(within(dialog).getByText('texto { roto')).toBeInTheDocument();
    expect(within(dialog).getByText(/no es JSON válido/)).toBeInTheDocument();
  });

  it('detail counts the changed fields and highlights the new value', async () => {
    server.use(
      http.get(AUDIT_URL, () =>
        HttpResponse.json(pageOf([event(1, { beforeValue: '{"status":"ACTIVE","section":"A"}', afterValue: '{"status":"WITHDRAWN","section":"A"}' })])),
      ),
    );
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByText('Baja voluntaria'));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('1 campo cambió')).toBeInTheDocument();
    expect(within(dialog).getByText('WITHDRAWN')).toHaveClass('bg-warning-bg');
    expect(within(dialog).getByText('ACTIVE')).not.toHaveClass('bg-warning-bg');
  });

  it('queries the platform endpoint when configured for it', async () => {
    let hit = false;
    server.use(
      http.get('http://localhost:8080/platform/audit-log', () => {
        hit = true;
        return HttpResponse.json(pageOf([event(1)]));
      }),
    );
    renderWithProviders(<AuditLogPage endpoint="/platform/audit-log" deniedAudience="el dueño de la plataforma" />);
    await screen.findByText('Baja voluntaria');
    expect(hit).toBe(true);
  });
});
