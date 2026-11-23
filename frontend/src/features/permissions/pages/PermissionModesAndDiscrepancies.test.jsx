import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { errorBody } from '@/test/fixtures/errors';
import { permissionDiscrepancy, permissionDiscrepancySummary, permissionGrid } from '@/test/fixtures/permissions';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PermissionsPage } from './PermissionsPage';

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

const API = 'http://localhost:8080';
const VALID_REASON = 'Sin discrepancias durante dos semanas';
const SHADOW_BUTTON = 'Modo de Administrativo en Avisos: Sombra. Cambiar';

function recordModePuts(response) {
  const bodies = [];
  server.use(
    http.put(`${API}/permissions/modes`, async ({ request }) => {
      bodies.push(await request.json());
      return response ?? HttpResponse.json(permissionGrid());
    }),
  );
  return bodies;
}

function countRequests(path) {
  const counter = { value: 0 };
  server.use(
    http.get(`${API}${path}`, () => {
      counter.value += 1;
      return path.endsWith('summary') ? HttpResponse.json(permissionDiscrepancySummary()) : HttpResponse.json(permissionGrid());
    }),
  );
  return counter;
}

async function openModeDialog(user, name = SHADOW_BUTTON) {
  await user.click(await screen.findByRole('button', { name }));
  return screen.findByRole('dialog');
}

async function writeReason(user, dialog, text = VALID_REASON) {
  await user.type(within(dialog).getByRole('textbox', { name: 'Motivo' }), text);
}

async function openDiscrepanciesTab(user) {
  await user.click(await screen.findByRole('tab', { name: /Discrepancias/ }));
}

describe('permission mode dialog', () => {
  beforeEach(() => {
    toastMock.success.mockClear();
  });

  it('opens from the mode pill showing the three modes with the current one marked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PermissionsPage />);
    const dialog = await openModeDialog(user);

    expect(within(dialog).getByText('Modo de Administrativo en Avisos')).toBeInTheDocument();
    expect(within(dialog).getAllByRole('radio')).toHaveLength(3);
    expect(within(dialog).getByRole('radio', { name: /Sombra/ })).toBeChecked();
    expect(within(dialog).getByText('Actual')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Cambiar modo' })).toBeDisabled();
  });

  it('requires the acknowledgement checkbox to activate a module with discrepancies', async () => {
    const user = userEvent.setup();
    const bodies = recordModePuts();
    renderWithProviders(<PermissionsPage />);
    const dialog = await openModeDialog(user);

    await user.click(within(dialog).getByRole('radio', { name: /Activo/ }));
    expect(within(dialog).getByText('Al pasar a Activo, Administrativo solo va a poder lo que la grilla le dé.', { exact: false })).toBeInTheDocument();
    expect(within(dialog).getByText('3 discrepancias')).toBeInTheDocument();
    await writeReason(user, dialog);
    const confirm = within(dialog).getByRole('button', { name: 'Cambiar modo' });
    expect(confirm).toBeDisabled();

    await user.click(within(dialog).getByRole('checkbox', { name: 'Entiendo que 3 accesos hoy darían un resultado distinto.' }));
    expect(confirm).toBeEnabled();
    await user.click(confirm);

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(bodies).toEqual([{ reason: VALID_REASON, changes: [{ role: 'ADMINISTRATIVO', module: 'announcement', mode: 'ENFORCE' }] }]);
    expect(toastMock.success).toHaveBeenCalledWith('Administrativo en Avisos: modo Activo.');
  });

  it('does not ask for the acknowledgement when the module has no discrepancies', async () => {
    const user = userEvent.setup();
    const bodies = recordModePuts();
    renderWithProviders(<PermissionsPage />);
    const dialog = await openModeDialog(user, 'Modo de Docente en Facturación y cobranza: Legado. Cambiar');

    await user.click(within(dialog).getByRole('radio', { name: /Activo/ }));
    expect(within(dialog).queryByRole('checkbox')).not.toBeInTheDocument();
    await writeReason(user, dialog);
    await user.click(within(dialog).getByRole('button', { name: 'Cambiar modo' }));

    await waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0].changes).toEqual([{ role: 'DOCENTE', module: 'billing', mode: 'ENFORCE' }]);
  });

  it('goes back to Legado from the footer button explaining it is safe', async () => {
    const user = userEvent.setup();
    const bodies = recordModePuts();
    renderWithProviders(<PermissionsPage />);
    const dialog = await openModeDialog(user);

    await user.click(within(dialog).getByRole('button', { name: 'Volver a Legado' }));
    expect(within(dialog).getByRole('radio', { name: /Legado/ })).toBeChecked();
    expect(within(dialog).getByText('Volver a Legado es seguro', { exact: false })).toBeInTheDocument();
    await writeReason(user, dialog);
    await user.click(within(dialog).getByRole('button', { name: 'Cambiar modo' }));

    await waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0].changes).toEqual([{ role: 'ADMINISTRATIVO', module: 'announcement', mode: 'LEGACY' }]);
    expect(toastMock.success).toHaveBeenCalledWith('Administrativo en Avisos: modo Legado.');
  });

  it('hides the Volver a Legado button when the mode is already Legado', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PermissionsPage />);
    const dialog = await openModeDialog(user, 'Modo de Docente en Avisos: Legado. Cambiar');
    expect(within(dialog).queryByRole('button', { name: 'Volver a Legado' })).not.toBeInTheDocument();
  });

  it('keeps the confirm button disabled until the reason has 10 characters', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PermissionsPage />);
    const dialog = await openModeDialog(user);

    await user.click(within(dialog).getByRole('radio', { name: /Legado/ }));
    await writeReason(user, dialog, 'corto');
    expect(within(dialog).getByRole('button', { name: 'Cambiar modo' })).toBeDisabled();
    expect(within(dialog).getByText('5/500')).toBeInTheDocument();
  });

  it('warns about unsaved permission changes of that role and module', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PermissionsPage />);
    await user.click(await screen.findByRole('button', { name: /^Avisos/ }));
    await user.click(screen.getByRole('checkbox', { name: 'Crear avisos — Administrativo' }));
    const dialog = await openModeDialog(user);

    expect(within(dialog).getByText('Tenés cambios sin guardar de este rol en este módulo.', { exact: false })).toBeInTheDocument();
  });

  it.each([
    ['UNKNOWN_PERMISSION_MODULE', 422, 'El módulo indicado no existe en la grilla de permisos.'],
    ['DUPLICATE_PERMISSION_MODE_CHANGE', 422, 'Hay un cambio de modo repetido para el mismo rol y módulo.'],
    ['DIRECTOR_PERMISSIONS_NOT_EDITABLE', 422, 'El Director conserva todos los permisos y no se puede editar.'],
    ['PERMISSION_GRID_FORBIDDEN', 403, 'Solo el Director puede editar la grilla de permisos.'],
  ])('shows the translated %s error without closing the dialog', async (code, status, message) => {
    const user = userEvent.setup();
    recordModePuts(HttpResponse.json(errorBody(code), { status }));
    renderWithProviders(<PermissionsPage />);
    const dialog = await openModeDialog(user);

    await user.click(within(dialog).getByRole('button', { name: 'Volver a Legado' }));
    await writeReason(user, dialog);
    await user.click(within(dialog).getByRole('button', { name: 'Cambiar modo' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(message);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(toastMock.success).not.toHaveBeenCalled();
  });

  it('refetches the grid and the discrepancy summary after saving', async () => {
    const user = userEvent.setup();
    recordModePuts();
    const gridReads = countRequests('/permissions/grid');
    const summaryReads = countRequests('/permissions/discrepancies/summary');
    renderWithProviders(<PermissionsPage />);
    const dialog = await openModeDialog(user);
    await waitFor(() => expect(summaryReads.value).toBe(1));
    expect(gridReads.value).toBe(1);

    await user.click(within(dialog).getByRole('button', { name: 'Volver a Legado' }));
    await writeReason(user, dialog);
    await user.click(within(dialog).getByRole('button', { name: 'Cambiar modo' }));

    await waitFor(() => expect(gridReads.value).toBe(2));
    await waitFor(() => expect(summaryReads.value).toBe(2));
  });

  it('blocks activating while the discrepancy summary is loading', async () => {
    const user = userEvent.setup();
    server.use(http.get(`${API}/permissions/discrepancies/summary`, () => new Promise(() => {})));
    renderWithProviders(<PermissionsPage />);
    const dialog = await openModeDialog(user);

    await user.click(within(dialog).getByRole('radio', { name: /Activo/ }));
    await writeReason(user, dialog);
    expect(within(dialog).getByText('Estamos verificando las discrepancias de este módulo…')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Cambiar modo' })).toBeDisabled();
  });

  it('blocks activating when the summary failed and recovers on retry', async () => {
    const user = userEvent.setup();
    let failing = true;
    server.use(
      http.get(`${API}/permissions/discrepancies/summary`, () =>
        failing ? HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 }) : HttpResponse.json(permissionDiscrepancySummary()),
      ),
    );
    renderWithProviders(<PermissionsPage />);
    const dialog = await openModeDialog(user);

    await user.click(within(dialog).getByRole('radio', { name: /Activo/ }));
    await writeReason(user, dialog);
    expect(await within(dialog).findByText('No pudimos verificar las discrepancias. Reintentá antes de pasar a Activo.', {}, { timeout: 8000 })).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Cambiar modo' })).toBeDisabled();

    failing = false;
    await user.click(within(dialog).getByRole('button', { name: 'Reintentar' }));
    await within(dialog).findByRole('checkbox', { name: /Entiendo que 3 accesos/ });
    expect(within(dialog).queryByText('No pudimos verificar las discrepancias. Reintentá antes de pasar a Activo.')).not.toBeInTheDocument();
  }, 20000);

  it('shows the discrepancy pill on the module in the grid', async () => {
    renderWithProviders(<PermissionsPage />);
    expect(await screen.findByText('3 discrepancias')).toBeInTheDocument();
  });

  it('hides the Discrepancias tab and the mode action from a read-only role', async () => {
    server.use(http.get(`${API}/permissions/grid`, () => HttpResponse.json(permissionGrid({ editable: false }))));
    renderWithProviders(<PermissionsPage />);

    expect(await screen.findByText('Estás viendo la grilla en modo lectura.')).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: /Discrepancias/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Cambiar$/ })).not.toBeInTheDocument();
  });
});

describe('permission discrepancies tab', () => {
  it('shows the total of occurrences in the tab counter', async () => {
    renderWithProviders(<PermissionsPage />);
    const tab = await screen.findByRole('tab', { name: /Discrepancias/ });
    await waitFor(() => expect(tab).toHaveTextContent('3'));
  });

  it('lists discrepancies with a clear direction and the summary line', async () => {
    const user = userEvent.setup();
    server.use(
      http.get(`${API}/permissions/discrepancies`, () =>
        HttpResponse.json({
          items: [permissionDiscrepancy(), permissionDiscrepancy({ permissionCode: 'announcement.create', direction: 'LEGACY_ALLOWED_GRID_DENIED', count: 1 })],
          page: 0,
          size: 20,
          totalElements: 2,
        }),
      ),
    );
    renderWithProviders(<PermissionsPage />);
    await openDiscrepanciesTab(user);

    expect(await screen.findByText('¿Qué es una discrepancia?')).toBeInTheDocument();
    expect(await screen.findAllByText('La regla vieja negaba y la grilla permitiría')).not.toHaveLength(0);
    expect(screen.getAllByText('La regla vieja permitía y la grilla negaría')).not.toHaveLength(0);
    expect(screen.getAllByText('Eliminar avisos')).not.toHaveLength(0);
    expect(screen.getAllByText('announcement.delete')).not.toHaveLength(0);
    expect(screen.getByText('2 permisos con diferencias, 3 ocurrencias')).toBeInTheDocument();
    expect(screen.getByText('Mostrando 1–2 de 2')).toBeInTheDocument();
  });

  it('sends the module parameter when filtering by module', async () => {
    const user = userEvent.setup();
    const urls = [];
    server.use(
      http.get(`${API}/permissions/discrepancies`, ({ request }) => {
        urls.push(new URL(request.url));
        return HttpResponse.json({ items: [permissionDiscrepancy()], page: 0, size: 20, totalElements: 1 });
      }),
    );
    renderWithProviders(<PermissionsPage />);
    await openDiscrepanciesTab(user);
    await screen.findAllByText('Eliminar avisos');
    expect(urls[0].searchParams.has('module')).toBe(false);

    await user.selectOptions(screen.getByRole('combobox', { name: 'Filtrar discrepancias por módulo' }), 'announcement');

    await waitFor(() => expect(urls.at(-1).searchParams.get('module')).toBe('announcement'));
    expect(urls.at(-1).searchParams.get('page')).toBe('0');
  });

  it('paginates with the backend page and size', async () => {
    const user = userEvent.setup();
    const pages = [];
    server.use(
      http.get(`${API}/permissions/discrepancies`, ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get('page'));
        pages.push(page);
        const item = permissionDiscrepancy({ permissionCode: page === 0 ? 'announcement.delete' : 'announcement.create' });
        return HttpResponse.json({ items: [item], page, size: 1, totalElements: 2 });
      }),
    );
    renderWithProviders(<PermissionsPage />);
    await openDiscrepanciesTab(user);

    expect(await screen.findByText('Mostrando 1–1 de 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByText('Mostrando 2–2 de 2')).toBeInTheDocument();
    expect(pages).toContain(1);
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
  });

  it('shows the empty state when there are no discrepancies', async () => {
    const user = userEvent.setup();
    server.use(http.get(`${API}/permissions/discrepancies`, () => HttpResponse.json({ items: [], page: 0, size: 20, totalElements: 0 })));
    renderWithProviders(<PermissionsPage />);
    await openDiscrepanciesTab(user);

    expect(await screen.findByText('Sin discrepancias')).toBeInTheDocument();
  });

  it('shows an error with a retry button that recovers', async () => {
    const user = userEvent.setup();
    let failing = true;
    server.use(
      http.get(`${API}/permissions/discrepancies`, () =>
        failing ? HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 }) : HttpResponse.json({ items: [permissionDiscrepancy()], page: 0, size: 20, totalElements: 1 }),
      ),
    );
    renderWithProviders(<PermissionsPage />);
    await openDiscrepanciesTab(user);

    const retry = await screen.findByRole('button', { name: 'Reintentar' }, { timeout: 8000 });
    failing = false;
    await user.click(retry);
    expect((await screen.findAllByText('Eliminar avisos')).length).toBeGreaterThan(0);
  }, 20000);

  it('jumps to the grid expanding and highlighting the module', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PermissionsPage />);
    await openDiscrepanciesTab(user);
    const [showInGrid] = await screen.findAllByRole('button', { name: 'Ver en la grilla' });
    await user.click(showInGrid);

    expect(screen.getByRole('tab', { name: 'Permisos' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('button', { name: /^Avisos/ })).toHaveAttribute('aria-expanded', 'true');
    expect(document.getElementById('permission-section-announcement')).toHaveClass('outline-brand');
  });
});
