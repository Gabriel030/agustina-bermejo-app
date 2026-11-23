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
} from '@/test/fixtures/certificates';
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

describe('CertificateTemplateEditor versions, publish and preview', () => {
  beforeEach(() => {
    auth.roles = ['DIRECTOR'];
    URL.createObjectURL = vi.fn(() => 'blob:preview');
    URL.revokeObjectURL = vi.fn();
  });

  afterEach(() => {
    server.events.removeAllListeners();
  });

  it('lists the published versions newest first and marks the current one', async () => {
    server.use(
      http.get(`${API}/certificate-templates/:kind/versions`, () =>
        HttpResponse.json([
          { versionNumber: 2, publishedAt: '2026-11-10T09:00:00', publishedBy: 'Director Prueba' },
          { versionNumber: 3, publishedAt: '2026-11-18T09:05:00', publishedBy: 'Director Prueba' },
        ]),
      ),
    );
    renderPage();

    const items = await screen.findAllByRole('listitem');
    expect(within(items[0]).getByText('Versión 3 · vigente')).toBeInTheDocument();
    expect(within(items[0]).getByText(/18\/11\/2026 09:05 por Director Prueba/)).toBeInTheDocument();
    expect(within(items[1]).getByText('Versión 2')).toBeInTheDocument();
  });

  it('shows the empty state when there are no published versions', async () => {
    server.use(http.get(`${API}/certificate-templates/:kind/versions`, () => HttpResponse.json([])));
    renderPage();

    expect(await screen.findByText('Todavía no hay versiones publicadas.')).toBeInTheDocument();
  });

  it('shows the versions loading state', async () => {
    server.use(http.get(`${API}/certificate-templates/:kind/versions`, () => new Promise(() => {})));
    renderPage();

    expect(await screen.findByLabelText('Cargando versiones')).toBeInTheDocument();
  });

  it('shows the versions error state and retries', async () => {
    const user = userEvent.setup({ delay: null });
    let failing = true;
    server.use(
      http.get(`${API}/certificate-templates/:kind/versions`, () =>
        failing
          ? HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })
          : HttpResponse.json([{ versionNumber: 3, publishedAt: '2026-11-18T09:05:00', publishedBy: 'Director Prueba' }]),
      ),
    );
    renderPage();

    expect(await screen.findByText('No pudimos cargar las versiones')).toBeInTheDocument();
    failing = false;
    const retryButtons = screen.getAllByRole('button', { name: 'Reintentar' });
    await user.click(retryButtons[retryButtons.length - 1]);

    expect(await screen.findByText('Versión 3 · vigente')).toBeInTheDocument();
  });

  it('opens a version read-only with its texts and signers', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Ver versión 3' }));

    const dialog = await screen.findByRole('dialog', { name: 'Versión 3' });
    expect(await within(dialog).findByText('Certificado de aprobación')).toBeInTheDocument();
    expect(within(dialog).getByText('Dra. Lucía Fernández · Secretaria Legal')).toBeInTheDocument();
    expect(within(dialog).getByText('Lic. Marcos Ledesma · Director Académico')).toBeInTheDocument();
    expect(within(dialog).queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('shows the domain message when a version is not found', async () => {
    const user = userEvent.setup({ delay: null });
    server.use(
      http.get(`${API}/certificate-templates/:kind/versions/:number`, () =>
        HttpResponse.json(errorBody('TEMPLATE_VERSION_NOT_FOUND'), { status: 404 }),
      ),
    );
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Ver versión 3' }));

    expect(await screen.findByText('No encontramos lo que buscabas')).toBeInTheDocument();
  });

  it('lists the data the system fills in with its markers', async () => {
    renderPage();

    const heading = await screen.findByRole('heading', { name: 'Datos que completa el sistema' });
    const card = heading.parentElement;
    expect(within(card).getByText('{registryNumber}')).toBeInTheDocument();
    expect(within(card).getByText('Pérez, Camila')).toBeInTheDocument();
  });

  it('publishes the saved draft after confirming and creates the next version', async () => {
    const user = userEvent.setup({ delay: null });
    const published = capturePublish();
    withDraft();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Publicar' }));
    const dialog = await screen.findByRole('alertdialog');
    expect(within(dialog).getByText('Publicar la versión 4')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Publicar v4' }));

    await waitFor(() => expect(published).toEqual(['PROGRAM']));
  });

  it('does not publish when the confirmation is cancelled', async () => {
    const user = userEvent.setup({ delay: null });
    const published = capturePublish();
    withDraft();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Publicar' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Cancelar' }));

    expect(published).toEqual([]);
  });

  it('keeps publish disabled without a draft', async () => {
    renderPage();

    expect(await screen.findByRole('button', { name: 'Publicar' })).toBeDisabled();
  });

  it('keeps publish disabled while there are unsaved changes', async () => {
    const user = userEvent.setup({ delay: null });
    withDraft();
    renderPage();

    await user.type(await screen.findByLabelText('Título del certificado'), '!');

    expect(screen.getByRole('button', { name: 'Publicar' })).toBeDisabled();
  });

  it('keeps publish disabled and explains why when a signatory is missing', async () => {
    withDraft({ signatory2Id: null });
    renderPage();

    expect(await screen.findByText('Elegí los dos firmantes para poder publicar.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publicar' })).toBeDisabled();
  });

  it('shows the domain message when publishing an incomplete template', async () => {
    const user = userEvent.setup({ delay: null });
    capturePublish(() => HttpResponse.json(errorBody('TEMPLATE_INCOMPLETE'), { status: 422 }));
    withDraft();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Publicar' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Publicar v4' }));

    expect(await screen.findByText(/La plantilla está incompleta/)).toBeInTheDocument();
  });

  it('shows the domain message when a signatory became inactive at publish time', async () => {
    const user = userEvent.setup({ delay: null });
    capturePublish(() => HttpResponse.json(errorBody('SIGNATORY_INACTIVE'), { status: 409 }));
    withDraft();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Publicar' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Publicar v4' }));

    expect(await screen.findByText('El firmante está inactivo: activalo o elegí otro.')).toBeInTheDocument();
  });

  it('previews the saved draft for the director when there is one', async () => {
    const user = userEvent.setup({ delay: null });
    const calls = capturePreview();
    withDraft();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Vista previa' }));

    const dialog = await screen.findByRole('dialog', { name: 'Vista previa de la plantilla' });
    expect(within(dialog).getByText(/Se ve el borrador guardado/)).toBeInTheDocument();
    expect(dialog.querySelector('iframe')).toHaveAttribute('src', 'blob:preview');
    expect(calls).toEqual([{ kind: 'PROGRAM', body: { source: 'DRAFT' } }]);
  });

  it('previews the published version when the director has no draft', async () => {
    const user = userEvent.setup({ delay: null });
    const calls = capturePreview();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Vista previa' }));

    expect(await screen.findByText(/Se ve la versión publicada/)).toBeInTheDocument();
    expect(calls[0].body).toEqual({ source: 'PUBLISHED' });
  });

  it('previews the course template with its own kind', async () => {
    const user = userEvent.setup({ delay: null });
    const calls = capturePreview();
    renderPage();

    await screen.findByLabelText('Título del certificado');
    await user.click(screen.getByRole('tab', { name: 'Curso' }));
    await user.click(await screen.findByRole('button', { name: 'Vista previa' }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0].kind).toBe('COURSE');
  });

  it('disables the preview while there are unsaved changes', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.type(await screen.findByLabelText('Título del certificado'), '!');

    expect(screen.getByRole('button', { name: 'Vista previa' })).toBeDisabled();
  });

  it('disables the preview when nothing was ever published', async () => {
    server.use(
      http.get(`${API}/certificate-templates/:kind`, () => HttpResponse.json(certificateTemplate({ published: null, draft: null }))),
    );
    renderPage();

    expect(await screen.findByRole('button', { name: 'Vista previa' })).toBeDisabled();
  });

  it('shows the error when the preview fails', async () => {
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

    it('can see versions and preview the published one but cannot publish', async () => {
      const user = userEvent.setup({ delay: null });
      const calls = capturePreview();
      const published = capturePublish();
      withDraft();
      renderPage();

      expect(await screen.findByText('Versión 3 · vigente')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Publicar' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Descartar borrador' })).not.toBeInTheDocument();
      await user.click(screen.getByRole('button', { name: 'Vista previa' }));

      await waitFor(() => expect(calls).toHaveLength(1));
      expect(calls[0].body).toEqual({ source: 'PUBLISHED' });
      expect(published).toEqual([]);
    });
  });

  it('has no accessibility violations with a draft, versions and the system data', async () => {
    withDraft();
    const { container } = renderPage();

    await screen.findByText('Versión 3 · vigente');
    await screen.findByLabelText('Firmante', { selector: '#template-signatory-1' });

    expect(await axe(container)).toHaveNoViolations();
  });
});
