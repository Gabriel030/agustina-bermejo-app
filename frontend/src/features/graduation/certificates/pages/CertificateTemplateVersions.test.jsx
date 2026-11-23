import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { errorBody } from '@/test/fixtures/errors';
import {
  certificateTemplate,
  certificateTemplateContent,
  certificateTemplateVersion,
  springPage,
} from '@/test/fixtures/certificates';
import { signatorySummary } from '@/test/fixtures/signatories';
import { CertificateTemplateEditor } from '@/features/graduation/certificates/pages/CertificateTemplateEditor';

const API = 'http://localhost:8080';
const auth = vi.hoisted(() => ({ roles: ['DIRECTOR'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: auth.roles } }),
}));

const draftVersion = (contentOverrides = {}) =>
  certificateTemplateVersion({
    id: 2,
    status: 'DRAFT',
    versionNumber: 4,
    basedOnVersionNumber: 3,
    publishedAt: null,
    publishedBy: null,
    content: certificateTemplateContent(contentOverrides),
  });

function withDraft(contentOverrides = {}) {
  server.use(
    http.get(`${API}/certificate-templates/:kind`, () =>
      HttpResponse.json(certificateTemplate({ draft: draftVersion(contentOverrides), hasUnpublishedChanges: true })),
    ),
  );
}

function renderPage() {
  return renderWithProviders(<CertificateTemplateEditor />);
}

function capturePreview() {
  const calls = [];
  server.use(
    http.post(`${API}/certificate-templates/:kind/preview`, async ({ request, params }) => {
      calls.push({ kind: params.kind, body: await request.json() });
      return new HttpResponse(new Uint8Array([0x25, 0x50, 0x44, 0x46]), { headers: { 'Content-Type': 'application/pdf' } });
    }),
  );
  return calls;
}

function capturePublish(response) {
  const calls = [];
  server.use(
    http.post(`${API}/certificate-templates/:kind/publish`, ({ params }) => {
      calls.push(params.kind);
      return response ? response() : HttpResponse.json(certificateTemplateVersion({ id: 2, versionNumber: 4 }));
    }),
  );
  return calls;
}

const VERSIONS = [
  { versionNumber: 2, publishedAt: '2026-11-10T09:00:00', publishedBy: 'Director Prueba' },
  { versionNumber: 3, publishedAt: '2026-11-18T09:05:00', publishedBy: 'Director Prueba' },
];

function withVersions(versions = VERSIONS) {
  server.use(http.get(`${API}/certificate-templates/:kind/versions`, () => HttpResponse.json(versions)));
}

async function openHistory(user) {
  await user.click(await screen.findByRole('button', { name: 'Historial' }));
  return screen.findByRole('dialog', { name: 'Historial de versiones' });
}

function setWide(matches) {
  window.matchMedia = (query) => ({
    matches,
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
  });
}

describe('CertificateTemplateEditor history, publish and preview', () => {
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    auth.roles = ['DIRECTOR'];
    URL.createObjectURL = vi.fn(() => 'blob:preview');
    URL.revokeObjectURL = vi.fn();
  });

  afterEach(() => {
    window.matchMedia = originalMatchMedia;
    server.events.removeAllListeners();
  });

  it('lists the versions in a sheet newest first and marks the current one', async () => {
    const user = userEvent.setup({ delay: null });
    withVersions();
    renderPage();

    const sheet = await openHistory(user);
    const items = await within(sheet).findAllByRole('listitem');

    expect(within(items[0]).getByText('Versión 3')).toBeInTheDocument();
    expect(within(items[0]).getByText('Vigente')).toBeInTheDocument();
    expect(within(items[0]).getByText(/18\/11\/2026 09:05 · Director Prueba/)).toBeInTheDocument();
    expect(within(items[1]).getByText('Versión 2')).toBeInTheDocument();
    expect(within(items[1]).getByText('Anterior')).toBeInTheDocument();
  });

  it('returns the focus to the history button when the sheet closes', async () => {
    const user = userEvent.setup({ delay: null });
    withVersions();
    renderPage();

    const button = await screen.findByRole('button', { name: 'Historial' });
    await user.click(button);
    await user.click(within(await screen.findByRole('dialog', { name: 'Historial de versiones' })).getByRole('button', { name: 'Cerrar' }));

    await waitFor(() => expect(button).toHaveFocus());
  });

  it('shows the empty state when there are no published versions', async () => {
    const user = userEvent.setup({ delay: null });
    withVersions([]);
    renderPage();

    const sheet = await openHistory(user);

    expect(await within(sheet).findByText('Todavía no hay versiones')).toBeInTheDocument();
  });

  it('shows the versions loading state', async () => {
    const user = userEvent.setup({ delay: null });
    server.use(http.get(`${API}/certificate-templates/:kind/versions`, () => new Promise(() => {})));
    renderPage();

    const sheet = await openHistory(user);

    expect(await within(sheet).findByLabelText('Cargando versiones')).toBeInTheDocument();
  });

  it('shows the versions error state and retries', async () => {
    const user = userEvent.setup({ delay: null });
    let failing = true;
    server.use(
      http.get(`${API}/certificate-templates/:kind/versions`, () =>
        failing ? HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 }) : HttpResponse.json(VERSIONS),
      ),
    );
    renderPage();

    const sheet = await openHistory(user);
    expect(await within(sheet).findByText('No pudimos cargar las versiones')).toBeInTheDocument();
    failing = false;
    await user.click(within(sheet).getByRole('button', { name: 'Reintentar' }));

    expect(await within(sheet).findByText('Versión 3')).toBeInTheDocument();
  });

  it('opens a version read-only with its texts and signers', async () => {
    const user = userEvent.setup({ delay: null });
    withVersions();
    renderPage();

    await openHistory(user);
    await user.click(await screen.findByRole('button', { name: 'Ver versión 3' }));

    const dialog = await screen.findByRole('dialog', { name: 'Versión 3 · solo lectura' });
    expect(await within(dialog).findByText('Certificado de aprobación')).toBeInTheDocument();
    expect(within(dialog).getByText('Dra. Lucía Fernández · Secretaria Legal')).toBeInTheDocument();
    expect(within(dialog).getByText('Lic. Marcos Ledesma · Director Académico')).toBeInTheDocument();
    expect(within(dialog).queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('shows the domain message when a version is not found', async () => {
    const user = userEvent.setup({ delay: null });
    withVersions();
    server.use(
      http.get(`${API}/certificate-templates/:kind/versions/:number`, () =>
        HttpResponse.json(errorBody('TEMPLATE_VERSION_NOT_FOUND'), { status: 404 }),
      ),
    );
    renderPage();

    await openHistory(user);
    await user.click(await screen.findByRole('button', { name: 'Ver versión 3' }));

    expect(await screen.findByText('No encontramos lo que buscabas')).toBeInTheDocument();
  });

  it('loads an older version as an unsaved working copy without asking when there is no draft', async () => {
    const user = userEvent.setup({ delay: null });
    withVersions();
    server.use(
      http.get(`${API}/certificate-templates/:kind/versions/:number`, () =>
        HttpResponse.json(certificateTemplateVersion({ versionNumber: 2, content: certificateTemplateContent({ title: 'Título de la v2' }) })),
      ),
    );
    renderPage();

    await openHistory(user);
    expect(screen.queryByRole('button', { name: 'Usar la versión 3 como base' })).not.toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: 'Usar la versión 2 como base' }));

    expect(await screen.findByLabelText('Título del certificado')).toHaveValue('Título de la v2');
    expect(screen.queryByRole('dialog', { name: 'Historial de versiones' })).not.toBeInTheDocument();
    expect(screen.getByText('Cargamos la v2 como borrador. Guardalo para conservarlo.')).toBeInTheDocument();
    expect(screen.getByText('Cambios sin guardar')).toBeInTheDocument();
  });

  it('asks before replacing the saved draft when using a version as base', async () => {
    const user = userEvent.setup({ delay: null });
    withVersions();
    withDraft();
    server.use(
      http.get(`${API}/certificate-templates/:kind/versions/:number`, () =>
        HttpResponse.json(certificateTemplateVersion({ versionNumber: 2, content: certificateTemplateContent({ title: 'Título de la v2' }) })),
      ),
    );
    renderPage();

    await openHistory(user);
    await user.click(await screen.findByRole('button', { name: 'Usar la versión 2 como base' }));
    const confirmation = await screen.findByRole('alertdialog', { name: 'Reemplazar el borrador' });
    expect(screen.getByLabelText('Título del certificado')).toHaveValue('Certificado de aprobación');
    await user.click(within(confirmation).getByRole('button', { name: 'Cancelar' }));
    expect(screen.getByLabelText('Título del certificado')).toHaveValue('Certificado de aprobación');

    await user.click(screen.getByRole('button', { name: 'Usar la versión 2 como base' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Reemplazar borrador' }));

    await waitFor(() => expect(screen.getByLabelText('Título del certificado')).toHaveValue('Título de la v2'));
  });

  it('shows the domain message when the base version cannot be loaded', async () => {
    const user = userEvent.setup({ delay: null });
    withVersions();
    server.use(
      http.get(`${API}/certificate-templates/:kind/versions/:number`, () =>
        HttpResponse.json(errorBody('TEMPLATE_VERSION_NOT_FOUND'), { status: 404 }),
      ),
    );
    renderPage();

    const sheet = await openHistory(user);
    await user.click(await within(sheet).findByRole('button', { name: 'Usar la versión 2 como base' }));

    expect(await within(sheet).findByText('No encontramos esa versión de la plantilla.')).toBeInTheDocument();
  });

  it('publishes the saved draft after confirming and creates the next version', async () => {
    const user = userEvent.setup({ delay: null });
    const published = capturePublish();
    withDraft();
    renderPage();

    const publish = await screen.findByRole('button', { name: 'Publicar' });
    await waitFor(() => expect(publish).toBeEnabled());
    await user.click(publish);
    const dialog = await screen.findByRole('alertdialog');
    expect(within(dialog).getByText('Publicar la versión 4')).toBeInTheDocument();
    expect(within(dialog).getByText(/no se puede editar/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Publicar v4' }));

    await waitFor(() => expect(published).toEqual(['PROGRAM']));
    expect(await screen.findByText('Versión 4 publicada.')).toBeInTheDocument();
  });

  it('does not publish when the confirmation is cancelled', async () => {
    const user = userEvent.setup({ delay: null });
    const published = capturePublish();
    withDraft();
    renderPage();

    const publish = await screen.findByRole('button', { name: 'Publicar' });
    await waitFor(() => expect(publish).toBeEnabled());
    await user.click(publish);
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Cancelar' }));

    expect(published).toEqual([]);
  });

  it('keeps publish disabled without a draft and keeps the default hint', async () => {
    renderPage();

    expect(await screen.findByRole('button', { name: 'Publicar' })).toBeDisabled();
    expect(screen.getByText('Las emisiones nuevas usan la versión publicada.')).toBeInTheDocument();
  });

  it('keeps publish disabled and says to save first while there are unsaved changes', async () => {
    const user = userEvent.setup({ delay: null });
    withDraft();
    renderPage();

    await user.type(await screen.findByLabelText('Título del certificado'), '!');

    expect(screen.getByRole('button', { name: 'Publicar' })).toBeDisabled();
    expect(screen.getByText('Guardá el borrador para poder publicar.')).toBeInTheDocument();
  });

  it('says to fix the errors when the unsaved changes are invalid', async () => {
    const user = userEvent.setup({ delay: null });
    withDraft();
    renderPage();

    const title = await screen.findByLabelText('Título del certificado');
    await user.clear(title);

    expect(screen.getByText('Corregí los errores para guardar.')).toBeInTheDocument();
  });

  it('lets the draft be published with only the first signatory', async () => {
    withDraft({ signatory2Id: null });
    renderPage();

    const publish = await screen.findByRole('button', { name: 'Publicar' });

    await waitFor(() => expect(publish).toBeEnabled());
    expect(screen.getByText('Todavía no se usa: falta publicar.')).toBeInTheDocument();
  });

  it('blocks publishing when the draft has no signatory', async () => {
    withDraft({ signatory1Id: null, signatory2Id: null });
    renderPage();

    expect(await screen.findByText('Elegí al menos un firmante para poder publicar.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publicar' })).toBeDisabled();
  });

  it('blocks publishing when the chosen signatory has no signature loaded', async () => {
    server.use(
      http.get(`${API}/certificate-signatories`, () =>
        HttpResponse.json(springPage([signatorySummary({ id: 1, name: 'Dra. Lucía Fernández', hasSignature: false })])),
      ),
    );
    withDraft({ signatory2Id: null });
    renderPage();

    expect(await screen.findAllByText(/Dra\. Lucía Fernández no tiene firma cargada\. Cargala en Firmantes para poder publicar\./)).not.toHaveLength(0);
    expect(screen.getByRole('button', { name: 'Publicar' })).toBeDisabled();
    expect(screen.getByText('Sin firma cargada')).toBeInTheDocument();
  });

  it('blocks publishing when the saved signatory is no longer available', async () => {
    withDraft({ signatory1Id: 4 });
    renderPage();

    expect(await screen.findByText('El firmante elegido ya no está disponible. Elegí otro para poder publicar.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publicar' })).toBeDisabled();
  });

  it('shows the domain message when publishing an incomplete template', async () => {
    const user = userEvent.setup({ delay: null });
    capturePublish(() => HttpResponse.json(errorBody('TEMPLATE_INCOMPLETE'), { status: 422 }));
    withDraft();
    renderPage();

    const publish = await screen.findByRole('button', { name: 'Publicar' });
    await waitFor(() => expect(publish).toBeEnabled());
    await user.click(publish);
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Publicar v4' }));

    expect(await screen.findByText(/La plantilla está incompleta/)).toBeInTheDocument();
  });

  it('shows the domain message when a signatory became inactive at publish time', async () => {
    const user = userEvent.setup({ delay: null });
    capturePublish(() => HttpResponse.json(errorBody('SIGNATORY_INACTIVE'), { status: 409 }));
    withDraft();
    renderPage();

    const publish = await screen.findByRole('button', { name: 'Publicar' });
    await waitFor(() => expect(publish).toBeEnabled());
    await user.click(publish);
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Publicar v4' }));

    expect(await screen.findByText('El firmante está inactivo: activalo o elegí otro.')).toBeInTheDocument();
  });

  it('shows the domain message when the user lost the permission to publish', async () => {
    const user = userEvent.setup({ delay: null });
    capturePublish(() => HttpResponse.json(errorBody('FORBIDDEN'), { status: 403 }));
    withDraft();
    renderPage();

    const publish = await screen.findByRole('button', { name: 'Publicar' });
    await waitFor(() => expect(publish).toBeEnabled());
    await user.click(publish);
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Publicar v4' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/permiso/i);
  });

  it('previews the published version first and lets the director switch to the saved draft', async () => {
    const user = userEvent.setup({ delay: null });
    const calls = capturePreview();
    withDraft();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Vista previa' }));
    const dialog = await screen.findByRole('dialog', { name: 'Vista previa del PDF' });
    await waitFor(() => expect(dialog.querySelector('iframe')).toHaveAttribute('src', 'blob:preview'));
    expect(calls).toEqual([{ kind: 'PROGRAM', body: { source: 'PUBLISHED' } }]);

    await user.click(within(dialog).getByRole('switch', { name: 'Ver borrador' }));

    await waitFor(() => expect(calls).toHaveLength(2));
    expect(calls[1]).toEqual({ kind: 'PROGRAM', body: { source: 'DRAFT' } });
  });

  it('shows the preview panel next to the form on wide screens', async () => {
    setWide(true);
    const calls = capturePreview();
    renderPage();

    const panel = await screen.findByRole('complementary', { name: 'Vista previa' });

    await waitFor(() => expect(panel.querySelector('iframe')).toHaveAttribute('src', 'blob:preview'));
    expect(screen.queryByRole('button', { name: 'Vista previa' })).not.toBeInTheDocument();
    expect(within(panel).getByRole('switch', { name: 'Ver borrador' })).toBeDisabled();
    expect(calls[0].body).toEqual({ source: 'PUBLISHED' });
  });

  it('warns that the preview shows the saved content while there are unsaved changes', async () => {
    const user = userEvent.setup({ delay: null });
    capturePreview();
    setWide(true);
    renderPage();

    await user.type(await screen.findByLabelText('Título del certificado'), '!');

    expect(screen.getByText('La vista previa muestra lo guardado. Guardá el borrador para ver tus cambios.')).toBeInTheDocument();
  });

  it('shows the empty preview when nothing was ever published', async () => {
    setWide(true);
    server.use(
      http.get(`${API}/certificate-templates/:kind`, () => HttpResponse.json(certificateTemplate({ published: null, draft: null }))),
    );
    renderPage();

    expect(await screen.findByText('No hay versión publicada para previsualizar.')).toBeInTheDocument();
  });

  it('shows the pending-backend message and retries when the preview returns 422', async () => {
    const user = userEvent.setup({ delay: null });
    let failing = true;
    setWide(true);
    server.use(
      http.post(`${API}/certificate-templates/:kind/preview`, () =>
        failing
          ? HttpResponse.json(errorBody('DOCUMENT_OPERATION_PENDING'), { status: 422 })
          : new HttpResponse(new Uint8Array([0x25, 0x50, 0x44, 0x46]), { headers: { 'Content-Type': 'application/pdf' } }),
      ),
    );
    renderPage();

    const panel = await screen.findByRole('complementary', { name: 'Vista previa' });
    expect(await within(panel).findByText('Esta operación de documentos todavía no está disponible.')).toBeInTheDocument();
    failing = false;
    await user.click(within(panel).getByRole('button', { name: 'Reintentar' }));

    await waitFor(() => expect(panel.querySelector('iframe')).toBeInTheDocument());
  });

  it('shows the domain message when the preview is refused as incomplete', async () => {
    const user = userEvent.setup({ delay: null });
    server.use(
      http.post(`${API}/certificate-templates/:kind/preview`, () => HttpResponse.json(errorBody('TEMPLATE_INCOMPLETE'), { status: 422 })),
    );
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Vista previa' }));

    expect(await screen.findByText(/La plantilla está incompleta/)).toBeInTheDocument();
  });

  describe('as administrativo', () => {
    beforeEach(() => {
      auth.roles = ['ADMINISTRATIVO'];
    });

    it('can see the history and the published preview but cannot publish or use a version as base', async () => {
      const user = userEvent.setup({ delay: null });
      const calls = capturePreview();
      const published = capturePublish();
      withVersions();
      withDraft();
      renderPage();

      expect(await screen.findByLabelText('Título del certificado')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Publicar' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Descartar borrador' })).not.toBeInTheDocument();
      const sheet = await openHistory(user);
      expect(await within(sheet).findByText('Versión 2')).toBeInTheDocument();
      expect(within(sheet).queryByRole('button', { name: /como base/ })).not.toBeInTheDocument();
      await user.click(within(sheet).getByRole('button', { name: 'Cerrar' }));
      await user.click(screen.getByRole('button', { name: 'Vista previa' }));

      await waitFor(() => expect(calls).toHaveLength(1));
      expect(calls[0].body).toEqual({ source: 'PUBLISHED' });
      expect(screen.queryByRole('switch', { name: 'Ver borrador' })).not.toBeInTheDocument();
      expect(published).toEqual([]);
    });
  });

  it('has no accessibility violations with a draft and the history open', async () => {
    const user = userEvent.setup({ delay: null });
    withVersions();
    withDraft();
    const { container } = renderPage();

    await screen.findByLabelText('Primer firmante');
    expect(await axe(container)).toHaveNoViolations();
    const sheet = await openHistory(user);
    await within(sheet).findByText('Versión 3');

    expect(await axe(sheet)).toHaveNoViolations();
  });
});
