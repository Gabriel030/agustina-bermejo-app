import { beforeEach, describe, expect, it, vi } from 'vitest';
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
  certificateTemplateImage,
  certificateTemplateVersion,
} from '@/test/fixtures/certificates';
import { CertificateTemplateEditor } from '@/features/graduation/certificates/pages/CertificateTemplateEditor';

const API = 'http://localhost:8080';
const auth = vi.hoisted(() => ({ roles: ['DIRECTOR'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: auth.roles } }),
}));

function renderPage() {
  return renderWithProviders(<CertificateTemplateEditor />);
}

function captureDraftSave(response) {
  const saved = { bodies: [] };
  server.use(
    http.put(`${API}/certificate-templates/:kind/draft`, async ({ request, params }) => {
      saved.bodies.push({ kind: params.kind, body: await request.json() });
      return response ? response() : HttpResponse.json(certificateTemplateVersion({ id: 2, status: 'DRAFT', versionNumber: 4 }));
    }),
  );
  return saved;
}

function pngFile(name = 'logo.png', size = 1024) {
  const file = new File(['x'], name, { type: 'image/png' });
  Object.defineProperty(file, 'size', { value: size });
  return file;
}

async function findTitleField() {
  return screen.findByLabelText('Título del certificado');
}

describe('CertificateTemplateEditor', () => {
  beforeEach(() => {
    auth.roles = ['DIRECTOR'];
    URL.createObjectURL = vi.fn(() => 'blob:logo');
    URL.revokeObjectURL = vi.fn();
  });

  it('shows the published content and keeps save disabled until something changes', async () => {
    renderPage();

    expect(await findTitleField()).toHaveValue('Certificado de aprobación');
    expect(screen.getByText('Publicada v3 · sin cambios')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Guardar borrador' })).toBeDisabled();
  });

  it('edits the texts and saves the draft with signatory ids and null embedded signers', async () => {
    const user = userEvent.setup({ delay: null });
    const saved = captureDraftSave();
    renderPage();

    const title = await findTitleField();
    await user.clear(title);
    await user.type(title, 'Certificado de egreso');
    expect(screen.getByText('Cambios sin guardar')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    await waitFor(() => expect(saved.bodies).toHaveLength(1));
    const { kind, body } = saved.bodies[0];
    expect(kind).toBe('PROGRAM');
    expect(body.content).toMatchObject({
      title: 'Certificado de egreso',
      signatory1Id: 1,
      signatory2Id: 2,
      logoImageId: 1,
      firstSigner: null,
      secondSigner: null,
    });
    expect(await screen.findByText('Borrador guardado.')).toBeInTheDocument();
  });

  it('rejects unknown markers and blocks saving', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage();

    const intro = await screen.findByLabelText('Texto de apertura');
    await user.clear(intro);
    await user.click(intro);
    await user.paste('Se certifica que {holder} y {nickname}');

    expect(screen.getByText('Marcador no permitido: {nickname}.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Guardar borrador' })).toBeDisabled();
  });

  it('accepts every allowed marker', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage();

    const intro = await screen.findByLabelText('Texto de apertura');
    await user.clear(intro);
    await user.click(intro);
    await user.paste('a {holder}{documentType}{documentNumber} b');
    await user.click(await screen.findByLabelText('Texto de aprobación'));
    await user.paste(' {titleKind}{titleName}{hours}{graduationDate}{issuedOn}{registryNumber}');

    expect(screen.queryByText(/Marcador no permitido/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Guardar borrador' })).toBeEnabled();
  });

  it('flags a text longer than its limit and blocks saving', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage();

    const title = await findTitleField();
    await user.clear(title);
    await user.click(title);
    await user.paste('a'.repeat(81));

    expect(screen.getByText('Admite hasta 80 caracteres (tiene 81).')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Guardar borrador' })).toBeDisabled();
  });

  it('uploads a valid PNG logo and saves its id in the draft', async () => {
    const user = userEvent.setup({ delay: null });
    const saved = captureDraftSave();
    server.use(
      http.post(`${API}/certificate-templates/:kind/images`, ({ request }) => {
        expect(new URL(request.url).searchParams.get('purpose')).toBe('LOGO');
        return HttpResponse.json(certificateTemplateImage({ id: 9 }), { status: 201 });
      }),
    );
    renderPage();

    await findTitleField();
    await user.upload(screen.getByLabelText('Archivo del logo'), pngFile());
    await waitFor(() => expect(screen.getByText('Cambios sin guardar')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    await waitFor(() => expect(saved.bodies[0].body.content.logoImageId).toBe(9));
  });

  it('rejects a logo that is not a PNG or exceeds 2 MB without calling the API', async () => {
    const user = userEvent.setup({ delay: null, applyAccept: false });
    const uploads = vi.fn();
    server.use(
      http.post(`${API}/certificate-templates/:kind/images`, () => {
        uploads();
        return HttpResponse.json(certificateTemplateImage(), { status: 201 });
      }),
    );
    renderPage();

    await findTitleField();
    const input = screen.getByLabelText('Archivo del logo');
    await user.upload(input, new File(['x'], 'logo.jpg', { type: 'image/jpeg' }));
    expect(await screen.findByText('La imagen debe ser un PNG de hasta 2 MB.')).toBeInTheDocument();
    await user.upload(input, pngFile('grande.png', 2 * 1024 * 1024 + 1));
    expect(screen.getByText('La imagen debe ser un PNG de hasta 2 MB.')).toBeInTheDocument();

    expect(uploads).not.toHaveBeenCalled();
    expect(screen.getByText('Publicada v3 · sin cambios')).toBeInTheDocument();
  });

  it('shows the server message when the logo upload is refused', async () => {
    const user = userEvent.setup({ delay: null });
    server.use(
      http.post(`${API}/certificate-templates/:kind/images`, () =>
        HttpResponse.json(errorBody('TEMPLATE_IMAGE_INVALID'), { status: 422 }),
      ),
    );
    renderPage();

    await findTitleField();
    await user.upload(screen.getByLabelText('Archivo del logo'), pngFile());

    expect(await screen.findByText('La imagen debe ser un PNG de hasta 2 MB.')).toBeInTheDocument();
  });

  it('removes the logo from the draft', async () => {
    const user = userEvent.setup({ delay: null });
    const saved = captureDraftSave();
    renderPage();

    await findTitleField();
    await user.click(screen.getByRole('button', { name: 'Quitar logo' }));
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    await waitFor(() => expect(saved.bodies[0].body.content.logoImageId).toBeNull());
  });

  it('offers only active signatories allowed for graduation approvals', async () => {
    const requests = [];
    server.events.on('request:start', ({ request }) => requests.push(request.url));
    renderPage();

    const first = await screen.findByLabelText('Firmante', { selector: '#template-signatory-1' });
    const names = within(first).getAllByRole('option').map((option) => option.textContent);

    expect(names).toEqual([
      'Elegí un firmante…',
      'Dra. Lucía Fernández · Secretaria Legal',
      'Lic. Marcos Ledesma · Director Académico',
    ]);
    expect(requests.some((url) => url.includes('documentType=GRADUATION_APPROVAL') && url.includes('active=true'))).toBe(true);
    server.events.removeAllListeners();
  });

  it('shows name and role of the chosen signatory', async () => {
    const user = userEvent.setup({ delay: null });
    server.use(
      http.get(`${API}/certificate-templates/:kind`, () =>
        HttpResponse.json(certificateTemplate({ published: certificateTemplateVersion({ content: certificateTemplateContent({ signatory1Id: null, signatory2Id: null }) }) })),
      ),
    );
    renderPage();

    const first = await screen.findByLabelText('Firmante', { selector: '#template-signatory-1' });
    expect(screen.getAllByText('Falta elegir este firmante.')).toHaveLength(2);
    await user.selectOptions(first, '2');

    expect(screen.getByText('Lic. Marcos Ledesma', { selector: 'b' })).toBeInTheDocument();
    expect(screen.getByText('Director Académico', { selector: 'span' })).toBeInTheDocument();
  });

  it('blocks choosing the same signatory twice', async () => {
    renderPage();

    const second = await screen.findByLabelText('Firmante', { selector: '#template-signatory-2' });
    const first = screen.getByLabelText('Firmante', { selector: '#template-signatory-1' });

    expect(within(second).getByRole('option', { name: /Dra. Lucía Fernández/ })).toBeDisabled();
    expect(within(first).getByRole('option', { name: /Lic. Marcos Ledesma/ })).toBeDisabled();
  });

  it('flags a duplicated saved pair and blocks saving', async () => {
    const user = userEvent.setup({ delay: null });
    server.use(
      http.get(`${API}/certificate-templates/:kind`, () =>
        HttpResponse.json(
          certificateTemplate({
            published: null,
            draft: certificateTemplateVersion({
              status: 'DRAFT',
              content: certificateTemplateContent({ signatory1Id: 1, signatory2Id: 1 }),
            }),
          }),
        ),
      ),
    );
    renderPage();

    expect(await screen.findByText('El firmante 1 y el firmante 2 deben ser personas distintas.')).toBeInTheDocument();
    await user.type(await findTitleField(), '!');
    expect(screen.getByRole('button', { name: 'Guardar borrador' })).toBeDisabled();
  });

  it('warns when the saved signatory is no longer available', async () => {
    server.use(
      http.get(`${API}/certificate-templates/:kind`, () =>
        HttpResponse.json(certificateTemplate({ published: certificateTemplateVersion({ content: certificateTemplateContent({ signatory1Id: 4 }) }) })),
      ),
    );
    renderPage();

    expect(await screen.findByText('El firmante elegido ya no está disponible. Elegí otro.')).toBeInTheDocument();
  });

  it('explains when no signatory has permission for this document', async () => {
    server.use(http.get(`${API}/certificate-signatories`, () => HttpResponse.json({ content: [] })));
    renderPage();

    expect(await screen.findByText('Ningún firmante tiene permiso para este documento.')).toBeInTheDocument();
  });

  it('keeps the edits of a tab when switching to the other one and back', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage();

    const title = await findTitleField();
    await user.type(title, ' X');
    await user.click(screen.getByRole('tab', { name: 'Curso' }));
    expect(await screen.findByLabelText('Título del certificado')).toHaveValue('Certificado de aprobación');
    await user.click(screen.getByRole('tab', { name: 'Carrera' }));

    expect(await findTitleField()).toHaveValue('Certificado de aprobación X');
  });

  it('loads the course template from its own endpoint', async () => {
    const user = userEvent.setup({ delay: null });
    const kinds = [];
    server.use(
      http.get(`${API}/certificate-templates/:kind`, ({ params }) => {
        kinds.push(params.kind);
        return HttpResponse.json(certificateTemplate({ kind: params.kind }));
      }),
    );
    renderPage();

    await findTitleField();
    await user.click(screen.getByRole('tab', { name: 'Curso' }));

    await waitFor(() => expect(kinds).toEqual(['PROGRAM', 'COURSE']));
  });

  it('shows the empty state when the template was never configured', async () => {
    server.use(
      http.get(`${API}/certificate-templates/:kind`, () => HttpResponse.json(certificateTemplate({ published: null, draft: null }))),
    );
    renderPage();

    expect(await screen.findByText('Todavía no hay plantilla')).toBeInTheDocument();
    expect(screen.getByText('Sin versión publicada')).toBeInTheDocument();
  });

  it('shows the loading state while the template is pending', () => {
    server.use(http.get(`${API}/certificate-templates/:kind`, () => new Promise(() => {})));
    renderPage();

    expect(screen.getByLabelText('Cargando')).toBeInTheDocument();
  });

  it('shows the error state and retries the load', async () => {
    const user = userEvent.setup({ delay: null });
    let failing = true;
    server.use(
      http.get(`${API}/certificate-templates/:kind`, () =>
        failing ? HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 }) : HttpResponse.json(certificateTemplate()),
      ),
    );
    renderPage();

    expect(await screen.findByText('No pudimos cargar la plantilla')).toBeInTheDocument();
    failing = false;
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await findTitleField()).toBeInTheDocument();
  });

  it('shows the permission error when the template is forbidden', async () => {
    server.use(
      http.get(`${API}/certificate-templates/:kind`, () => HttpResponse.json(errorBody('FORBIDDEN'), { status: 403 })),
    );
    renderPage();

    expect(await screen.findByText('No pudimos cargar la plantilla')).toBeInTheDocument();
  });

  it('shows the domain message on a 422 save error and keeps the edits', async () => {
    const user = userEvent.setup({ delay: null });
    captureDraftSave(() => HttpResponse.json(errorBody('SIGNATORY_NOT_ALLOWED_FOR_DOCUMENT_TYPE'), { status: 422 }));
    renderPage();

    const title = await findTitleField();
    await user.type(title, '!');
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    expect(await screen.findByText('Ese firmante no tiene permiso para firmar este tipo de documento.')).toBeInTheDocument();
    expect(title).toHaveValue('Certificado de aprobación!');
    expect(screen.getByText('Cambios sin guardar')).toBeInTheDocument();
  });

  it('shows the domain message on a 409 save error', async () => {
    const user = userEvent.setup({ delay: null });
    captureDraftSave(() => HttpResponse.json(errorBody('SIGNATORY_INACTIVE'), { status: 409 }));
    renderPage();

    await user.type(await findTitleField(), '!');
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    expect(await screen.findByText('El firmante está inactivo: activalo o elegí otro.')).toBeInTheDocument();
  });

  it('discards the draft after confirmation', async () => {
    const user = userEvent.setup({ delay: null });
    const discards = vi.fn();
    server.use(
      http.get(`${API}/certificate-templates/:kind`, () =>
        HttpResponse.json(certificateTemplate({ draft: certificateTemplateVersion({ id: 2, status: 'DRAFT', versionNumber: 4 }), hasUnpublishedChanges: true })),
      ),
      http.delete(`${API}/certificate-templates/:kind/draft`, () => {
        discards();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderPage();

    expect(await screen.findByText('Borrador v4 guardado')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Descartar borrador' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Descartar borrador' }));

    await waitFor(() => expect(discards).toHaveBeenCalledTimes(1));
  });

  it('shows the domain message when there is nothing to discard', async () => {
    const user = userEvent.setup({ delay: null });
    server.use(
      http.get(`${API}/certificate-templates/:kind`, () =>
        HttpResponse.json(certificateTemplate({ draft: certificateTemplateVersion({ id: 2, status: 'DRAFT', versionNumber: 4 }) })),
      ),
      http.delete(`${API}/certificate-templates/:kind/draft`, () =>
        HttpResponse.json(errorBody('TEMPLATE_NOTHING_TO_DISCARD'), { status: 409 }),
      ),
    );
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Descartar borrador' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Descartar borrador' }));

    expect(await screen.findByText('No hay cambios para descartar.')).toBeInTheDocument();
  });

  describe('as administrativo', () => {
    beforeEach(() => {
      auth.roles = ['ADMINISTRATIVO'];
    });

    it('renders every field read-only without any edit action', async () => {
      const writes = vi.fn();
      server.use(
        http.put(`${API}/certificate-templates/:kind/draft`, () => {
          writes();
          return HttpResponse.json(certificateTemplateVersion());
        }),
        http.post(`${API}/certificate-templates/:kind/images`, () => {
          writes();
          return HttpResponse.json(certificateTemplateImage(), { status: 201 });
        }),
      );
      renderPage();

      const title = await findTitleField();
      expect(title).toHaveAttribute('readonly');
      expect(screen.getByLabelText('Firmante', { selector: '#template-signatory-1' })).toBeDisabled();
      expect(screen.getByLabelText('Firmante', { selector: '#template-signatory-2' })).toBeDisabled();
      expect(screen.getByText('Solo lectura.')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Guardar borrador' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Descartar borrador' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /logo/i })).not.toBeInTheDocument();
      expect(screen.queryByLabelText('Archivo del logo')).not.toBeInTheDocument();
      expect(writes).not.toHaveBeenCalled();
    });

    it('does not change the value when typing in a read-only field', async () => {
      const user = userEvent.setup({ delay: null });
      renderPage();

      const title = await findTitleField();
      await user.type(title, 'xyz');

      expect(title).toHaveValue('Certificado de aprobación');
      expect(screen.getByText('Publicada v3 · sin cambios')).toBeInTheDocument();
    });
  });

  it('has no accessibility violations', async () => {
    const { container } = renderPage();

    await findTitleField();
    await screen.findByLabelText('Firmante', { selector: '#template-signatory-1' });

    expect(await axe(container)).toHaveNoViolations();
  });
});
