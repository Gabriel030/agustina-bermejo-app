import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Toaster } from 'sonner';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { errorBody } from '@/test/fixtures/errors';
import { springPage } from '@/test/fixtures/certificates';
import { signatoryDetail, signatoryList } from '@/test/fixtures/signatories';
import {
  signatoryListEmptyHandler,
  signatoryListForbiddenHandler,
  signatoryRoleInvalidHandler,
  signatorySignatureInvalidHandler,
} from '@/test/mocks/certificateErrorHandlers';
import { CertificateSignatories } from '@/features/graduation/signatories/pages/CertificateSignatories';

const API = 'http://localhost:8080';
const auth = vi.hoisted(() => ({ roles: ['DIRECTOR'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: auth.roles } }),
}));

function renderPage() {
  return renderWithProviders(
    <>
      <CertificateSignatories />
      <Toaster />
    </>,
  );
}

function capture(method, path, response) {
  const calls = [];
  server.use(
    http[method](`${API}${path}`, async ({ request, params }) => {
      const contentType = request.headers.get('content-type') ?? '';
      const body = contentType.includes('application/json') ? await request.json() : null;
      calls.push({ params, body, url: request.url });
      return response ? response({ params, body }) : HttpResponse.json(signatoryDetail({ id: Number(params.id ?? 5) }));
    }),
  );
  return calls;
}

async function openSignatory(user, name) {
  await user.click(await screen.findByRole('button', { name: new RegExp(name) }));
}

function pngFile(overrides = {}) {
  return new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], 'firma.png', { type: 'image/png', ...overrides });
}

describe('CertificateSignatories', () => {
  beforeEach(() => {
    auth.roles = ['DIRECTOR'];
    URL.createObjectURL = vi.fn(() => 'blob:signature');
    URL.revokeObjectURL = vi.fn();
  });

  it('lists signatories with their role and whether they are active', async () => {
    renderPage();

    expect(await screen.findByText('Dra. Lucía Fernández')).toBeInTheDocument();
    expect(screen.getByText('Secretaria Legal')).toBeInTheDocument();
    expect(screen.getByText('Mg. Ricardo Paz')).toBeInTheDocument();
    expect(screen.getAllByText('Activo')).toHaveLength(3);
    expect(screen.getAllByText('Inactivo')).toHaveLength(1);
  });

  it('shows the signature only when a signatory is opened', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Dra. Lucía Fernández');
    expect(screen.queryByAltText('Firma de Dra. Lucía Fernández')).not.toBeInTheDocument();

    await openSignatory(user, 'Dra. Lucía Fernández');

    expect(await screen.findByAltText('Firma de Dra. Lucía Fernández')).toHaveAttribute('src', 'blob:signature');
  });

  it('shows where the signatory is used and which document types they may sign', async () => {
    const user = userEvent.setup();
    renderPage();
    await openSignatory(user, 'Lic. Marcos Ledesma');

    const usage = await screen.findByText('Se usa en');
    expect(usage.parentElement).toHaveTextContent('Certificado de egreso de carrera · versión 3 (Publicada)');
    expect(usage.parentElement).toHaveTextContent('Certificado de egreso de carrera · versión 4 (Borrador)');
    const permissions = screen.getByRole('group', { name: 'Permisos por tipo de documento' });
    expect(within(permissions).getAllByRole('checkbox')).toHaveLength(4);
    within(permissions).getAllByRole('checkbox').forEach((box) => expect(box).toBeChecked());
  });

  it('shows no template usage message for a signatory that is not used', async () => {
    const user = userEvent.setup();
    renderPage();
    await openSignatory(user, 'Prof. Elena Quiroga');

    expect(await screen.findByText('Ninguna plantilla por ahora.')).toBeInTheDocument();
    expect(screen.getByText('Sin firma')).toBeInTheDocument();
  });

  it('creates a signatory with the typed name, role and permissions', async () => {
    const user = userEvent.setup();
    const calls = capture('post', '/certificate-signatories', ({ body }) => HttpResponse.json(signatoryDetail({ id: 5, ...body }), { status: 201 }));
    renderPage();
    await screen.findByText('Dra. Lucía Fernández');

    await user.click(screen.getByRole('button', { name: 'Nuevo firmante' }));
    const create = screen.getByRole('button', { name: 'Crear firmante' });
    expect(create).toBeDisabled();
    await user.type(screen.getByLabelText('Nombre y apellido'), '  Ing. Sofía Maidana ');
    await user.click(screen.getByRole('button', { name: 'Secretaria Legal' }));
    await user.click(screen.getByRole('checkbox', { name: 'Constancia de examen' }));
    await user.click(screen.getByRole('checkbox', { name: 'Certificado de egreso' }));
    await user.click(create);

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0].body).toEqual({
      name: 'Ing. Sofía Maidana',
      role: 'Secretaria Legal',
      allowedDocumentTypes: ['GRADUATION_APPROVAL', 'EXAM_CERTIFICATE'],
    });
    expect(await screen.findByText('Firmante creado.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Crear firmante' })).not.toBeInTheDocument();
  });

  it('uploads a staged signature right after creating the signatory', async () => {
    const user = userEvent.setup();
    const uploads = capture('put', '/certificate-signatories/:id/signature');
    renderPage();
    await screen.findByText('Dra. Lucía Fernández');

    await user.click(screen.getByRole('button', { name: 'Nuevo firmante' }));
    await user.type(screen.getByLabelText('Nombre y apellido'), 'Ing. Sofía Maidana');
    await user.type(screen.getByLabelText('Rol o cargo'), 'Rectora');
    await user.upload(screen.getByLabelText('Archivo de firma'), pngFile());
    expect(uploads).toHaveLength(0);
    await user.click(screen.getByRole('button', { name: 'Crear firmante' }));

    await waitFor(() => expect(uploads).toHaveLength(1));
    expect(uploads[0].params.id).toBe('5');
  });

  it('shows the free text role suggestions and clears the role when choosing Otro', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Dra. Lucía Fernández');
    await user.click(screen.getByRole('button', { name: 'Nuevo firmante' }));

    const suggestions = screen.getByRole('group', { name: 'Sugerencias de rol' });
    expect(within(suggestions).getAllByRole('button').map((button) => button.textContent)).toEqual([
      'Secretaria Legal',
      'Director Académico',
      'Otro',
    ]);
    await user.click(within(suggestions).getByRole('button', { name: 'Director Académico' }));
    expect(screen.getByLabelText('Rol o cargo')).toHaveValue('Director Académico');
    await user.click(within(suggestions).getByRole('button', { name: 'Otro' }));
    expect(screen.getByLabelText('Rol o cargo')).toHaveValue('');
    expect(screen.getByLabelText('Rol o cargo')).toHaveFocus();
  });

  it('edits the name and role and sends them with the current permissions', async () => {
    const user = userEvent.setup();
    const calls = capture('put', '/certificate-signatories/:id', ({ params, body }) =>
      HttpResponse.json(signatoryDetail({ id: Number(params.id), ...body })),
    );
    renderPage();
    await openSignatory(user, 'Dra. Lucía Fernández');

    const save = await screen.findByRole('button', { name: 'Guardar cambios' });
    expect(save).toBeDisabled();
    const role = screen.getByLabelText('Rol o cargo');
    await user.clear(role);
    await user.type(role, 'Secretaria General');
    await user.click(save);

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0].params.id).toBe('1');
    expect(calls[0].body).toEqual({
      name: 'Dra. Lucía Fernández',
      role: 'Secretaria General',
      allowedDocumentTypes: ['GRADUATION_APPROVAL', 'REGULAR_STUDENT_CERTIFICATE'],
    });
    expect(await screen.findByText('Cambios guardados.')).toBeInTheDocument();
  });

  it('changes the permissions per document type and warns about templates that lose the signatory', async () => {
    const user = userEvent.setup();
    const calls = capture('put', '/certificate-signatories/:id', ({ params, body }) =>
      HttpResponse.json(signatoryDetail({ id: Number(params.id), ...body })),
    );
    renderPage();
    await openSignatory(user, 'Dra. Lucía Fernández');
    await screen.findByRole('button', { name: 'Guardar cambios' });

    expect(screen.getByRole('checkbox', { name: 'Certificado de egreso' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Constancia de alumno regular' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Constancia de materias aprobadas' })).not.toBeChecked();
    await user.click(screen.getByRole('checkbox', { name: 'Certificado de egreso' }));
    await user.click(screen.getByRole('checkbox', { name: 'Constancia de materias aprobadas' }));

    expect(screen.getByText(/Si le quitás el permiso, esas plantillas quedan sin este firmante/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0].body.allowedDocumentTypes).toEqual(['REGULAR_STUDENT_CERTIFICATE', 'APPROVED_SUBJECTS_CERTIFICATE']);
  });

  it('asks for confirmation before deactivating and then deactivates', async () => {
    const user = userEvent.setup();
    const calls = capture('post', '/certificate-signatories/:id/deactivate', ({ params }) =>
      HttpResponse.json(signatoryDetail({ id: Number(params.id), active: false })),
    );
    renderPage();
    await openSignatory(user, 'Dra. Lucía Fernández');

    await user.click(await screen.findByRole('button', { name: 'Desactivar' }));
    const dialog = await screen.findByRole('alertdialog');
    expect(within(dialog).getByText(/Está elegido en plantillas/)).toBeInTheDocument();
    expect(calls).toHaveLength(0);
    await user.click(within(dialog).getByRole('button', { name: 'Desactivar firmante' }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0].params.id).toBe('1');
    expect(await screen.findByText('Firmante desactivado.')).toBeInTheDocument();
  });

  it('does not deactivate when the confirmation is cancelled', async () => {
    const user = userEvent.setup();
    const calls = capture('post', '/certificate-signatories/:id/deactivate');
    renderPage();
    await openSignatory(user, 'Dra. Lucía Fernández');

    await user.click(await screen.findByRole('button', { name: 'Desactivar' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Cancelar' }));

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(calls).toHaveLength(0);
  });

  it('activates an inactive signatory without confirmation', async () => {
    const user = userEvent.setup();
    const calls = capture('post', '/certificate-signatories/:id/activate', ({ params }) =>
      HttpResponse.json(signatoryDetail({ id: Number(params.id), active: true })),
    );
    renderPage();
    await openSignatory(user, 'Mg. Ricardo Paz');

    await user.click(await screen.findByRole('button', { name: 'Activar' }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0].params.id).toBe('4');
    expect(await screen.findByText('Firmante activado.')).toBeInTheDocument();
  });

  it('uploads a valid PNG signature for an existing signatory', async () => {
    const user = userEvent.setup();
    const uploads = capture('put', '/certificate-signatories/:id/signature');
    renderPage();
    await openSignatory(user, 'Prof. Elena Quiroga');

    await user.upload(await screen.findByLabelText('Archivo de firma'), pngFile());

    await waitFor(() => expect(uploads).toHaveLength(1));
    expect(uploads[0].params.id).toBe('3');
    expect(await screen.findByText('Firma actualizada.')).toBeInTheDocument();
  });

  it.each([
    ['is not a PNG', () => pngFile({ type: 'image/jpeg' })],
    ['is a PNG larger than 2 MB', () => new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'firma.png', { type: 'image/png' })],
  ])('rejects a signature file that %s without sending it', async (_label, makeFile) => {
    const user = userEvent.setup({ applyAccept: false });
    const uploads = capture('put', '/certificate-signatories/:id/signature');
    renderPage();
    await openSignatory(user, 'Prof. Elena Quiroga');

    await user.upload(await screen.findByLabelText('Archivo de firma'), makeFile());

    expect(await screen.findByRole('alert')).toHaveTextContent('La firma debe ser un PNG de hasta 2 MB.');
    expect(uploads).toHaveLength(0);
  });

  it('shows the server message when the signature upload is rejected with 422', async () => {
    const user = userEvent.setup();
    server.use(signatorySignatureInvalidHandler);
    renderPage();
    await openSignatory(user, 'Prof. Elena Quiroga');

    await user.upload(await screen.findByLabelText('Archivo de firma'), pngFile());

    expect(await screen.findByText('La firma debe ser un PNG de hasta 2 MB.')).toBeInTheDocument();
  });

  it('removes the signature of a signatory', async () => {
    const user = userEvent.setup();
    const calls = capture('delete', '/certificate-signatories/:id/signature', ({ params }) =>
      HttpResponse.json(signatoryDetail({ id: Number(params.id), hasSignature: false })),
    );
    renderPage();
    await openSignatory(user, 'Dra. Lucía Fernández');

    await user.click(await screen.findByRole('button', { name: 'Quitar firma' }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(await screen.findByText('Firma quitada.')).toBeInTheDocument();
  });

  it('hides every editing control from the administrative role', async () => {
    auth.roles = ['ADMINISTRATIVO'];
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText(/Solo lectura/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Nuevo firmante' })).not.toBeInTheDocument();
    await openSignatory(user, 'Dra. Lucía Fernández');
    expect(await screen.findByAltText('Firma de Dra. Lucía Fernández')).toBeInTheDocument();
    expect(screen.getByLabelText('Nombre y apellido')).toHaveAttribute('readonly');
    screen.getAllByRole('checkbox').forEach((box) => expect(box).toBeDisabled());
    ['Guardar cambios', 'Desactivar', 'Activar', 'Subir firma', 'Reemplazar firma', 'Quitar firma', 'Cancelar'].forEach((name) =>
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument(),
    );
    expect(screen.queryByLabelText('Archivo de firma')).not.toBeInTheDocument();
  });

  it('shows the editing controls to the director', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole('button', { name: 'Nuevo firmante' })).toBeInTheDocument();
    expect(screen.queryByText(/Solo lectura/)).not.toBeInTheDocument();
    await openSignatory(user, 'Dra. Lucía Fernández');
    expect(await screen.findByRole('button', { name: 'Guardar cambios' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Desactivar' })).toBeInTheDocument();
  });

  it('shows the domain message when saving a change returns 404', async () => {
    const user = userEvent.setup();
    server.use(http.put(`${API}/certificate-signatories/:id`, () => HttpResponse.json(errorBody('SIGNATORY_NOT_FOUND'), { status: 404 })));
    renderPage();
    await openSignatory(user, 'Dra. Lucía Fernández');
    await user.type(await screen.findByLabelText('Nombre y apellido'), ' Jr.');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('No encontramos al firmante.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeInTheDocument();
  });

  it('shows the domain message when deactivating returns 409', async () => {
    const user = userEvent.setup();
    server.use(http.post(`${API}/certificate-signatories/:id/deactivate`, () => HttpResponse.json(errorBody('SIGNATORY_INACTIVE'), { status: 409 })));
    renderPage();
    await openSignatory(user, 'Dra. Lucía Fernández');
    await user.click(await screen.findByRole('button', { name: 'Desactivar' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Desactivar firmante' }));

    expect(await screen.findByText('El firmante está inactivo: activalo o elegí otro.')).toBeInTheDocument();
  });

  it('shows the domain message when creating returns 422', async () => {
    const user = userEvent.setup();
    server.use(signatoryRoleInvalidHandler);
    renderPage();
    await screen.findByText('Dra. Lucía Fernández');
    await user.click(screen.getByRole('button', { name: 'Nuevo firmante' }));
    await user.type(screen.getByLabelText('Nombre y apellido'), 'Ing. Sofía Maidana');
    await user.type(screen.getByLabelText('Rol o cargo'), 'Rectora');
    await user.click(screen.getByRole('button', { name: 'Crear firmante' }));

    expect(await screen.findByText('El rol del firmante es obligatorio y admite hasta 80 caracteres.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Crear firmante' })).toBeInTheDocument();
  });

  it('shows the empty state with a create button for the director', async () => {
    server.use(signatoryListEmptyHandler);
    renderPage();

    expect(await screen.findByText('Todavía no hay firmantes cargados')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Crear el primer firmante' })).toBeInTheDocument();
  });

  it('shows the empty state without create buttons for the administrative role', async () => {
    auth.roles = ['ADMINISTRATIVO'];
    server.use(signatoryListEmptyHandler);
    renderPage();

    expect(await screen.findByText('Todavía no hay firmantes cargados')).toBeInTheDocument();
    expect(screen.getByText(/Pedile al Director que cargue los firmantes/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Crear el primer firmante' })).not.toBeInTheDocument();
  });

  it('shows a loading state while the list is loading', async () => {
    server.use(http.get(`${API}/certificate-signatories`, () => new Promise(() => {})));
    renderPage();

    expect(await screen.findByRole('status', { name: 'Cargando firmantes' })).toBeInTheDocument();
  });

  it('shows the error state and retries the load', async () => {
    const user = userEvent.setup();
    let attempts = 0;
    server.use(
      http.get(`${API}/certificate-signatories`, () => {
        attempts += 1;
        return attempts === 1
          ? HttpResponse.json(errorBody('INTERNAL'), { status: 500 })
          : HttpResponse.json(springPage(signatoryList()));
      }),
    );
    renderPage();

    expect(await screen.findByText('No pudimos cargar los firmantes')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Nuevo firmante' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Dra. Lucía Fernández')).toBeInTheDocument();
  });

  it('shows the no permission state when the list answers 403', async () => {
    server.use(signatoryListForbiddenHandler);
    renderPage();

    expect(await screen.findByText('No tenés permiso para ver los firmantes')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Nuevo firmante' })).not.toBeInTheDocument();
  });

  it('has no accessibility violations with a signatory opened', async () => {
    const user = userEvent.setup();
    const { container } = renderPage();
    await openSignatory(user, 'Lic. Marcos Ledesma');
    await screen.findByAltText('Firma de Lic. Marcos Ledesma');

    expect(await axe(container)).toHaveNoViolations();
  });

  it('has no accessibility violations in the read only view and the new signatory form', async () => {
    auth.roles = ['ADMINISTRATIVO'];
    const readOnly = renderPage();
    await screen.findByText('Dra. Lucía Fernández');
    expect(await axe(readOnly.container)).toHaveNoViolations();
    readOnly.unmount();

    auth.roles = ['DIRECTOR'];
    const user = userEvent.setup();
    const director = renderPage();
    await user.click(await screen.findByRole('button', { name: 'Nuevo firmante' }));
    expect(await axe(director.container)).toHaveNoViolations();
  });
});
