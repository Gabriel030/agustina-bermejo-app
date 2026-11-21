import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { errorBody } from '@/test/fixtures/errors';
import { permissionCatalog, permissionGrid } from '@/test/fixtures/permissions';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PermissionsPage } from './PermissionsPage';

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

const API = 'http://localhost:8080';
const VALID_REASON = 'Necesitan revisar comprobantes';

function bigCatalog(count) {
  return {
    modules: [
      {
        module: 'announcement',
        permissions: Array.from({ length: count }, (_, index) => ({ code: `announcement.perm${index}`, description: `Permiso ${index}`, fixedToDirector: false, sensitive: false })),
      },
    ],
  };
}

function recordPuts(responses = []) {
  const bodies = [];
  server.use(
    http.put(`${API}/permissions/grid`, async ({ request }) => {
      bodies.push(await request.json());
      const next = responses.shift();
      return next ?? HttpResponse.json(permissionGrid());
    }),
  );
  return bodies;
}

async function openModule(user, name = /Avisos/) {
  await user.click(await screen.findByRole('button', { name }));
}

async function openReview(user) {
  await user.click(screen.getByRole('button', { name: 'Revisar y guardar' }));
  return screen.findByRole('dialog');
}

describe('PermissionsPage', () => {
  beforeEach(() => {
    toastMock.success.mockClear();
  });

  it('loads the grid with modules collapsed and expands one on click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PermissionsPage />);

    const avisos = await screen.findByRole('button', { name: /Avisos/ });
    expect(avisos).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText('Crear avisos')).not.toBeInTheDocument();
    expect(screen.getByText('3 de 3 permisos')).toBeInTheDocument();

    await user.click(avisos);
    expect(screen.getByText('Crear avisos')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Crear avisos — Administrativo' })).toHaveAttribute('aria-checked', 'true');
  });

  it('shows the mode of each role and the legend', async () => {
    renderWithProviders(<PermissionsPage />);
    await screen.findByRole('button', { name: /Avisos/ });
    expect(screen.getByText('Modo de Administrativo en Avisos:', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('¿Qué significan Legado, Sombra y Activo?')).toBeInTheDocument();
  });

  it('expands matching modules while searching', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PermissionsPage />);
    await screen.findByRole('button', { name: /Avisos/ });

    await user.type(screen.getByRole('searchbox', { name: 'Buscar permisos' }), 'revocar');

    expect(await screen.findByText('Revocar la aprobación de un pago')).toBeInTheDocument();
    expect(screen.queryByText('Crear avisos')).not.toBeInTheDocument();
    expect(screen.getByText('1 de 3 permisos')).toBeInTheDocument();
  });

  it('counts a pending change in the bar and discard reverts it', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PermissionsPage />);
    await openModule(user);

    const checkbox = screen.getByRole('checkbox', { name: 'Eliminar avisos — Preceptor' });
    expect(checkbox).toHaveAttribute('aria-checked', 'false');
    await user.click(checkbox);

    expect(checkbox).toHaveAttribute('aria-checked', 'true');
    const bar = screen.getByRole('region', { name: 'Cambios sin guardar' });
    expect(within(bar).getByText(/1 cambio sin guardar/)).toBeInTheDocument();
    expect(within(bar).getByText(/en 1 módulo/)).toBeInTheDocument();

    await user.click(within(bar).getByRole('button', { name: 'Descartar' }));
    expect(checkbox).toHaveAttribute('aria-checked', 'false');
    expect(screen.queryByRole('region', { name: 'Cambios sin guardar' })).not.toBeInTheDocument();
  });

  it('toggling back to the original value removes the pending change', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PermissionsPage />);
    await openModule(user);
    const checkbox = screen.getByRole('checkbox', { name: 'Eliminar avisos — Preceptor' });

    await user.click(checkbox);
    await user.click(checkbox);

    expect(screen.queryByRole('region', { name: 'Cambios sin guardar' })).not.toBeInTheDocument();
  });

  it('shows sensitive permissions with a keyboard reachable pill and keeps them editable', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PermissionsPage />);
    await openModule(user, /Facturación/);

    expect(screen.getByText('1 sensibles', { exact: false })).toBeInTheDocument();
    expect(screen.getByText((_, element) => element?.tagName === 'P' && element.textContent === 'Los permisos sensibles se pueden delegar; cada cambio se audita como evento destacado.')).toBeInTheDocument();
    const pill = screen.getByLabelText('Permiso sensible: requiere confirmación reforzada');
    expect(pill).toHaveAttribute('tabindex', '0');
    expect(pill).toHaveTextContent('Sensible');
    expect(screen.queryByText('Solo Director')).not.toBeInTheDocument();
    const checkbox = screen.getByRole('checkbox', { name: 'Revocar la aprobación de un pago — Docente' });
    await user.click(checkbox);
    expect(checkbox).toHaveAttribute('aria-checked', 'true');
  });

  it('separates sensitive changes in the review and requires the acknowledgement', async () => {
    const user = userEvent.setup();
    const bodies = recordPuts();
    renderWithProviders(<PermissionsPage />);
    await user.click(await screen.findByRole('button', { name: /Avisos/ }));
    await user.click(screen.getByRole('checkbox', { name: 'Eliminar avisos — Preceptor' }));
    await user.click(await screen.findByRole('button', { name: /Facturación/ }));
    await user.click(screen.getByRole('checkbox', { name: 'Revocar la aprobación de un pago — Docente' }));

    const dialog = await openReview(user);
    expect(within(dialog).getByText('Confirmación reforzada: 1 permiso sensible')).toBeInTheDocument();
    expect(within(dialog).getByText(/Se registra como evento destacado en la auditoría/)).toBeInTheDocument();
    const confirm = within(dialog).getByRole('button', { name: 'Guardar 2 cambios' });
    await user.type(within(dialog).getByLabelText('Motivo del cambio (obligatorio)'), VALID_REASON);
    expect(confirm).toBeDisabled();

    await user.click(within(dialog).getByRole('checkbox', { name: 'Entiendo que delego un permiso sensible' }));
    expect(confirm).toBeEnabled();
    await user.click(confirm);

    await waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0].sensitiveConfirmed).toBe(true);
  });

  it('does not send sensitiveConfirmed when no sensitive permission changes', async () => {
    const user = userEvent.setup();
    const bodies = recordPuts();
    renderWithProviders(<PermissionsPage />);
    await openModule(user);
    await user.click(screen.getByRole('checkbox', { name: 'Eliminar avisos — Preceptor' }));
    const dialog = await openReview(user);
    expect(within(dialog).queryByText(/Confirmación reforzada/)).not.toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Motivo del cambio'), VALID_REASON);
    await user.click(within(dialog).getByRole('button', { name: 'Guardar 1 cambio' }));

    await waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0]).not.toHaveProperty('sensitiveConfirmed');
  });

  it('shows the confirmation error without closing the dialog or losing changes', async () => {
    const user = userEvent.setup();
    recordPuts([HttpResponse.json(errorBody('SENSITIVE_PERMISSION_CONFIRMATION_REQUIRED'), { status: 422 })]);
    renderWithProviders(<PermissionsPage />);
    await openModule(user, /Facturación/);
    await user.click(screen.getByRole('checkbox', { name: 'Revocar la aprobación de un pago — Docente' }));
    const dialog = await openReview(user);
    await user.type(within(dialog).getByLabelText('Motivo del cambio (obligatorio)'), VALID_REASON);
    await user.click(within(dialog).getByRole('checkbox', { name: 'Entiendo que delego un permiso sensible' }));
    await user.click(within(dialog).getByRole('button', { name: 'Guardar 1 cambio' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Cambiar un permiso sensible exige una confirmación reforzada.');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Cambios sin guardar' })).toHaveTextContent('1 cambio sin guardar');
  });

  it('shows the Subdirector column only when the backend returns that role', async () => {
    const user = userEvent.setup();
    const { unmount } = renderWithProviders(<PermissionsPage />);
    await openModule(user);
    expect(screen.queryByRole('checkbox', { name: /Subdirector/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Subdirector' })).not.toBeInTheDocument();
    unmount();

    const grid = permissionGrid();
    grid.roles.splice(1, 0, { role: 'SUBDIRECTOR', permissionCodes: ['announcement.create'] });
    server.use(http.get(`${API}/permissions/grid`, () => HttpResponse.json(grid)));
    renderWithProviders(<PermissionsPage />);
    await openModule(user);
    expect(screen.getByRole('checkbox', { name: 'Crear avisos — Subdirector' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('button', { name: 'Subdirector' })).toBeInTheDocument();
  });

  it('shows the read-only mode without checkboxes or change bar when the grid is not editable', async () => {
    const user = userEvent.setup();
    server.use(http.get(`${API}/permissions/grid`, () => HttpResponse.json(permissionGrid({ editable: false }))));
    renderWithProviders(<PermissionsPage />);
    await openModule(user);

    expect(screen.getByText('Estás viendo la grilla en modo lectura.')).toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Crear avisos — Administrativo: tiene el permiso' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Eliminar avisos — Preceptor: no lo tiene' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Cambios sin guardar' })).not.toBeInTheDocument();
  });

  it('requires a reason of at least 10 characters and sends the right body', async () => {
    const user = userEvent.setup();
    const bodies = recordPuts();
    renderWithProviders(<PermissionsPage />);
    await openModule(user);
    await user.click(screen.getByRole('checkbox', { name: 'Eliminar avisos — Preceptor' }));
    await user.click(screen.getByRole('checkbox', { name: 'Crear avisos — Administrativo' }));

    const dialog = await openReview(user);
    expect(within(dialog).getByText('Revisar 2 cambios antes de guardar')).toBeInTheDocument();
    const confirm = within(dialog).getByRole('button', { name: 'Guardar 2 cambios' });
    expect(confirm).toBeDisabled();

    await user.type(within(dialog).getByLabelText('Motivo del cambio'), 'corto');
    expect(confirm).toBeDisabled();
    await user.clear(within(dialog).getByLabelText('Motivo del cambio'));
    await user.type(within(dialog).getByLabelText('Motivo del cambio'), VALID_REASON);
    expect(within(dialog).getByText(`${VALID_REASON.length}/500`)).toBeInTheDocument();
    await user.click(confirm);

    await waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Guardamos 2 cambios. Quedaron en la auditoría.'));
    expect(bodies).toHaveLength(1);
    expect(bodies[0].reason).toBe(VALID_REASON);
    expect(bodies[0].changes).toEqual(
      expect.arrayContaining([
        { role: 'PRECEPTOR', permissionCode: 'announcement.delete', granted: true },
        { role: 'ADMINISTRATIVO', permissionCode: 'announcement.create', granted: false },
      ]),
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('splits more than 200 changes into ordered calls', async () => {
    const user = userEvent.setup();
    server.use(
      http.get(`${API}/permissions/catalog`, () => HttpResponse.json(bigCatalog(201))),
      http.get(`${API}/permissions/grid`, () => HttpResponse.json(permissionGrid())),
    );
    const bodies = recordPuts();
    renderWithProviders(<PermissionsPage />);
    await openModule(user);
    const boxes = await screen.findAllByRole('checkbox', { name: /— Preceptor$/ });
    expect(boxes).toHaveLength(201);
    for (const box of boxes) await user.click(box);

    const dialog = await openReview(user);
    await user.type(within(dialog).getByLabelText('Motivo del cambio'), VALID_REASON);
    await user.click(within(dialog).getByRole('button', { name: 'Guardar 201 cambios' }));

    await waitFor(() => expect(bodies).toHaveLength(2));
    expect(bodies[0].changes).toHaveLength(200);
    expect(bodies[1].changes).toHaveLength(1);
    expect(bodies[0].changes[0].permissionCode).toBe('announcement.perm0');
    expect(bodies[1].changes[0].permissionCode).toBe('announcement.perm200');
    await waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Guardamos 201 cambios. Quedaron en la auditoría.'));
  }, 30000);

  it.each([
    [403, 'PERMISSION_GRID_FORBIDDEN', 'No se guardó nada. No tenés permiso para cambiar la grilla. Solo el Director puede editarla; si creés que es un error, pedíselo a él.'],
    [422, 'UNKNOWN_PERMISSION_CODE', 'El permiso indicado no existe en el catálogo.'],
  ])('shows the translated %i error and keeps the changes', async (status, code, message) => {
    const user = userEvent.setup();
    recordPuts([HttpResponse.json(errorBody(code), { status })]);
    renderWithProviders(<PermissionsPage />);
    await openModule(user);
    await user.click(screen.getByRole('checkbox', { name: 'Eliminar avisos — Preceptor' }));

    const dialog = await openReview(user);
    await user.type(within(dialog).getByLabelText('Motivo del cambio'), VALID_REASON);
    await user.click(within(dialog).getByRole('button', { name: 'Guardar 1 cambio' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(message);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(toastMock.success).not.toHaveBeenCalled();
    expect(screen.getByRole('region', { name: 'Cambios sin guardar' })).toHaveTextContent('1 cambio sin guardar');
  });

  it('keeps the unsaved chunk when a later call fails', async () => {
    const user = userEvent.setup();
    server.use(
      http.get(`${API}/permissions/catalog`, () => HttpResponse.json(bigCatalog(201))),
      http.get(`${API}/permissions/grid`, () => HttpResponse.json(permissionGrid())),
    );
    recordPuts([HttpResponse.json(permissionGrid()), HttpResponse.json(errorBody('UNKNOWN_PERMISSION_CODE'), { status: 422 })]);
    renderWithProviders(<PermissionsPage />);
    await openModule(user);
    for (const box of await screen.findAllByRole('checkbox', { name: /— Preceptor$/ })) await user.click(box);

    const dialog = await openReview(user);
    await user.type(within(dialog).getByLabelText('Motivo del cambio'), VALID_REASON);
    await user.click(within(dialog).getByRole('button', { name: 'Guardar 201 cambios' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('El permiso indicado no existe en el catálogo.');
    expect(screen.getByRole('region', { name: 'Cambios sin guardar' })).toHaveTextContent('1 cambio sin guardar');
  }, 30000);

  it('shows the loading skeleton', () => {
    renderWithProviders(<PermissionsPage />);
    expect(screen.getByRole('status', { name: 'Cargando permisos' })).toBeInTheDocument();
  });

  it('shows the empty state when the catalog has no permissions', async () => {
    server.use(http.get(`${API}/permissions/catalog`, () => HttpResponse.json({ modules: [] })));
    renderWithProviders(<PermissionsPage />);
    expect(await screen.findByText('Todavía no hay permisos cargados')).toBeInTheDocument();
  });

  it('shows a retry button when loading fails and recovers', async () => {
    const user = userEvent.setup();
    let failing = true;
    server.use(
      http.get(`${API}/permissions/grid`, () => (failing ? HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 }) : HttpResponse.json(permissionGrid()))),
      http.get(`${API}/permissions/catalog`, () => HttpResponse.json(permissionCatalog())),
    );
    renderWithProviders(<PermissionsPage />);

    expect(await screen.findByText('No pudimos cargar los permisos')).toBeInTheDocument();
    failing = false;
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('button', { name: /Avisos/ })).toBeInTheDocument();
  });

  it('shows the no-access state for a 403', async () => {
    server.use(http.get(`${API}/permissions/grid`, () => HttpResponse.json(errorBody('FORBIDDEN'), { status: 403 })));
    renderWithProviders(<PermissionsPage />);
    expect(await screen.findByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
    expect(screen.getByText(/el Subdirector puede verla sin editarla/)).toBeInTheDocument();
  });
});
