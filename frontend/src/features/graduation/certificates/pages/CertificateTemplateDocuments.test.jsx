import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { errorBody } from '@/test/fixtures/errors';
import {
  certificateTemplateContent,
  certificateTemplateImage,
  certificateTemplateVersion,
  documentTemplate,
} from '@/test/fixtures/certificates';
import { CertificateTemplateEditor } from '@/features/graduation/certificates/pages/CertificateTemplateEditor';

const API = 'http://localhost:8080';
const auth = vi.hoisted(() => ({ roles: ['DIRECTOR'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: auth.roles } }),
}));

const DOCUMENTS = [
  { tab: 'Alumno regular', type: 'REGULAR_STUDENT_CERTIFICATE', markers: ['program', 'academicYear'] },
  { tab: 'Inscripción a examen', type: 'EXAM_REGISTRATION_CERTIFICATE', markers: ['subject', 'examDate'] },
  { tab: 'Examen rendido', type: 'EXAM_CERTIFICATE', markers: ['subject', 'examDate'] },
  { tab: 'Materias aprobadas', type: 'APPROVED_SUBJECTS_CERTIFICATE', markers: ['program', 'approvedCount'] },
];

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

function renderPage() {
  return renderWithProviders(<CertificateTemplateEditor />);
}

async function openTab(user, name) {
  await user.click(screen.getByRole('tab', { name }));
  return screen.findByLabelText('Título del documento');
}

function recordRequests() {
  const requests = [];
  server.events.on('request:start', ({ request }) => requests.push(`${request.method} ${new URL(request.url).pathname}${new URL(request.url).search}`));
  return requests;
}

function captureDocumentDraft() {
  const saved = [];
  server.use(
    http.put(`${API}/certificate-templates/documents/:documentType/draft`, async ({ request, params }) => {
      saved.push({ documentType: params.documentType, body: await request.json() });
      return HttpResponse.json(draftVersion());
    }),
  );
  return saved;
}

describe('CertificateTemplateEditor document tabs', () => {
  beforeEach(() => {
    auth.roles = ['DIRECTOR'];
    URL.createObjectURL = vi.fn(() => 'blob:document');
    URL.revokeObjectURL = vi.fn();
  });

  afterEach(() => {
    server.events.removeAllListeners();
  });

  it('shows the six documents as tabs and as a grouped select', async () => {
    renderPage();

    const tabs = (await screen.findAllByRole('tab')).map((tab) => tab.textContent);
    const select = screen.getByLabelText('Documento');
    const groups = within(select).getAllByRole('group');

    expect(tabs).toEqual(['Egreso carrera', 'Egreso curso', 'Alumno regular', 'Inscripción a examen', 'Examen rendido', 'Materias aprobadas']);
    expect(groups.map((group) => group.getAttribute('label'))).toEqual(['Egresos', 'Constancias']);
    expect(within(groups[1]).getAllByRole('option')).toHaveLength(4);
  });

  it('switches the document from the select', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.selectOptions(await screen.findByLabelText('Documento'), 'EXAM_CERTIFICATE');

    expect(await screen.findByLabelText('Título del documento')).toHaveValue('Certificado de aprobación');
    expect(screen.getByRole('tab', { name: 'Examen rendido' })).toHaveAttribute('aria-selected', 'true');
  });

  it.each(DOCUMENTS)('loads $tab from its own documents endpoint with its markers and signatories', async ({ tab, type, markers }) => {
    const user = userEvent.setup({ delay: null });
    const requests = recordRequests();
    renderPage();

    await openTab(user, tab);

    await waitFor(() => expect(requests).toContain(`GET /certificate-templates/documents/${type}`));
    expect(requests.some((request) => request.includes(`documentType=${type}`) && request.includes('active=true'))).toBe(true);
    markers.forEach((marker) => expect(screen.getByText(`{${marker}}`)).toBeInTheDocument());
    expect(screen.getByText('Publicada v3')).toBeInTheDocument();
    expect(screen.getByLabelText('Texto principal')).toHaveValue('ha aprobado la carrera');
    expect(screen.queryByLabelText('Texto de dictado y evaluación')).not.toBeInTheDocument();
  });

  it('rejects a marker that belongs to another document', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage();

    await openTab(user, 'Alumno regular');
    const body = screen.getByLabelText('Texto principal');
    await user.clear(body);
    await user.click(body);
    await user.paste('{holder} rindió {subject}');

    expect(screen.getByText('Marcador no permitido: {subject}.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Guardar borrador' })).toBeDisabled();
  });

  it('accepts the markers of the document and saves the draft to its endpoint', async () => {
    const user = userEvent.setup({ delay: null });
    const saved = captureDocumentDraft();
    renderPage();

    await openTab(user, 'Examen rendido');
    const body = screen.getByLabelText('Texto principal');
    await user.clear(body);
    await user.click(body);
    await user.paste('{holder} rindió {subject} el {examDate}');
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0].documentType).toBe('EXAM_CERTIFICATE');
    expect(saved[0].body.content).toMatchObject({
      approvalText: '{holder} rindió {subject} el {examDate}',
      signatory1Id: 1,
      signatory2Id: 2,
      firstSigner: null,
      secondSigner: null,
    });
    expect(await screen.findByText('Borrador guardado.')).toBeInTheDocument();
  });

  it('keeps the edits of each document when switching tabs', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage();

    const title = await openTab(user, 'Alumno regular');
    await user.type(title, ' X');
    await openTab(user, 'Materias aprobadas');
    await user.click(screen.getByRole('tab', { name: 'Alumno regular' }));

    expect(await screen.findByLabelText('Título del documento')).toHaveValue('Certificado de aprobación X');
  });

  it('offers only the signatories allowed for the document and lets the second one be empty', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage();

    await openTab(user, 'Examen rendido');
    const first = await screen.findByLabelText('Primer firmante');
    const second = screen.getByLabelText('Segundo firmante (opcional)');

    expect(within(first).getAllByRole('option').map((option) => option.textContent)).toEqual([
      'Elegir firmante',
      'Lic. Marcos Ledesma · Director Académico',
      'Prof. Elena Quiroga · Coordinadora de Exámenes',
    ]);
    expect(within(second).getAllByRole('option')[0]).toHaveTextContent('Sin segundo firmante');
  });

  it('blocks publishing when a chosen signatory has no signature and says why', async () => {
    const user = userEvent.setup({ delay: null });
    server.use(
      http.get(`${API}/certificate-templates/documents/:documentType`, ({ params }) =>
        HttpResponse.json(
          documentTemplate(params.documentType, {
            draft: draftVersion({ signatory1Id: 3, signatory2Id: null }),
            hasUnpublishedChanges: true,
          }),
        ),
      ),
    );
    renderPage();

    await openTab(user, 'Examen rendido');

    expect(await screen.findAllByText(/Prof\. Elena Quiroga no tiene firma cargada\. Cargala en Firmantes para poder publicar\./)).not.toHaveLength(0);
    expect(screen.getByText('Sin firma cargada')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publicar' })).toBeDisabled();
  });

  it('publishes a document draft on its own endpoint', async () => {
    const user = userEvent.setup({ delay: null });
    const published = [];
    server.use(
      http.get(`${API}/certificate-templates/documents/:documentType`, ({ params }) =>
        HttpResponse.json(documentTemplate(params.documentType, { draft: draftVersion({ signatory1Id: 2, signatory2Id: null }), hasUnpublishedChanges: true })),
      ),
      http.post(`${API}/certificate-templates/documents/:documentType/publish`, ({ params }) => {
        published.push(params.documentType);
        return HttpResponse.json(certificateTemplateVersion({ versionNumber: 4 }));
      }),
    );
    renderPage();

    await openTab(user, 'Materias aprobadas');
    const publish = screen.getByRole('button', { name: 'Publicar' });
    await waitFor(() => expect(publish).toBeEnabled());
    await user.click(publish);
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Publicar v4' }));

    await waitFor(() => expect(published).toEqual(['APPROVED_SUBJECTS_CERTIFICATE']));
  });

  it('discards a document draft on its own endpoint', async () => {
    const user = userEvent.setup({ delay: null });
    const discarded = [];
    server.use(
      http.get(`${API}/certificate-templates/documents/:documentType`, ({ params }) =>
        HttpResponse.json(documentTemplate(params.documentType, { draft: draftVersion(), hasUnpublishedChanges: true })),
      ),
      http.delete(`${API}/certificate-templates/documents/:documentType/draft`, ({ params }) => {
        discarded.push(params.documentType);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderPage();

    await openTab(user, 'Inscripción a examen');
    await user.click(screen.getByRole('button', { name: 'Descartar borrador' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Descartar borrador' }));

    await waitFor(() => expect(discarded).toEqual(['EXAM_REGISTRATION_CERTIFICATE']));
  });

  it('uploads the logo to the document images endpoint', async () => {
    const user = userEvent.setup({ delay: null });
    const uploads = [];
    server.use(
      http.post(`${API}/certificate-templates/documents/:documentType/images`, ({ params, request }) => {
        uploads.push({ documentType: params.documentType, purpose: new URL(request.url).searchParams.get('purpose') });
        return HttpResponse.json(certificateTemplateImage({ id: 9 }), { status: 201 });
      }),
    );
    renderPage();

    await openTab(user, 'Alumno regular');
    const file = new File(['x'], 'logo.png', { type: 'image/png' });
    await user.upload(screen.getByLabelText('Archivo del logo'), file);

    await waitFor(() => expect(uploads).toEqual([{ documentType: 'REGULAR_STUDENT_CERTIFICATE', purpose: 'LOGO' }]));
    expect(await screen.findByText('Cambios sin guardar')).toBeInTheDocument();
  });

  it('lists the document versions and loads one as base using the documents endpoints', async () => {
    const user = userEvent.setup({ delay: null });
    const requests = recordRequests();
    server.use(
      http.get(`${API}/certificate-templates/documents/:documentType/versions`, () =>
        HttpResponse.json([
          { versionNumber: 2, publishedAt: '2026-11-10T09:00:00', publishedBy: 'Director Prueba' },
          { versionNumber: 3, publishedAt: '2026-11-18T09:05:00', publishedBy: 'Director Prueba' },
        ]),
      ),
    );
    renderPage();

    await openTab(user, 'Alumno regular');
    await user.click(screen.getByRole('button', { name: 'Historial' }));
    await user.click(await screen.findByRole('button', { name: 'Usar la versión 2 como base' }));

    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Historial de versiones' })).not.toBeInTheDocument());
    expect(requests).toContain('GET /certificate-templates/documents/REGULAR_STUDENT_CERTIFICATE/versions');
    expect(requests).toContain('GET /certificate-templates/documents/REGULAR_STUDENT_CERTIFICATE/versions/2');
    expect(screen.getByText('Cargamos la v2 como borrador. Guardalo para conservarlo.')).toBeInTheDocument();
  });

  it('previews a document with its own preview endpoint', async () => {
    const user = userEvent.setup({ delay: null });
    const calls = [];
    server.use(
      http.post(`${API}/certificate-templates/documents/:documentType/preview`, async ({ params, request }) => {
        calls.push({ documentType: params.documentType, body: await request.json() });
        return new HttpResponse(new Uint8Array([0x25, 0x50, 0x44, 0x46]), { headers: { 'Content-Type': 'application/pdf' } });
      }),
    );
    renderPage();

    await openTab(user, 'Examen rendido');
    await user.click(screen.getByRole('button', { name: 'Vista previa' }));

    await waitFor(() => expect(calls).toEqual([{ documentType: 'EXAM_CERTIFICATE', body: { source: 'PUBLISHED' } }]));
  });

  it('shows the pending-backend message and retries when the document template returns 422', async () => {
    const user = userEvent.setup({ delay: null });
    let failing = true;
    server.use(
      http.get(`${API}/certificate-templates/documents/:documentType`, ({ params }) =>
        failing
          ? HttpResponse.json(errorBody('DOCUMENT_OPERATION_PENDING'), { status: 422 })
          : HttpResponse.json(documentTemplate(params.documentType)),
      ),
    );
    renderPage();

    await user.click(screen.getByRole('tab', { name: 'Alumno regular' }));

    expect(await screen.findByText('Esta plantilla todavía no está disponible')).toBeInTheDocument();
    expect(screen.getByText('Esta operación de documentos todavía no está disponible.')).toBeInTheDocument();
    failing = false;
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByLabelText('Título del documento')).toBeInTheDocument();
  });

  it('shows the not-found state when the document template does not exist', async () => {
    const user = userEvent.setup({ delay: null });
    server.use(
      http.get(`${API}/certificate-templates/documents/:documentType`, () =>
        HttpResponse.json(errorBody('CERTIFICATE_NOT_FOUND'), { status: 404 }),
      ),
    );
    renderPage();

    await user.click(screen.getByRole('tab', { name: 'Alumno regular' }));

    expect(await screen.findByText('No encontramos lo que buscabas')).toBeInTheDocument();
  });

  it('shows the load error with retry when the document template returns 403', async () => {
    const user = userEvent.setup({ delay: null });
    server.use(
      http.get(`${API}/certificate-templates/documents/:documentType`, () => HttpResponse.json(errorBody('FORBIDDEN'), { status: 403 })),
    );
    renderPage();

    await user.click(screen.getByRole('tab', { name: 'Alumno regular' }));

    expect(await screen.findByText('No pudimos cargar la plantilla')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('shows the domain message when saving a document draft returns 409', async () => {
    const user = userEvent.setup({ delay: null });
    server.use(
      http.put(`${API}/certificate-templates/documents/:documentType/draft`, () =>
        HttpResponse.json(errorBody('SIGNATORY_NOT_ALLOWED_FOR_DOCUMENT_TYPE'), { status: 409 }),
      ),
    );
    renderPage();

    const title = await openTab(user, 'Alumno regular');
    await user.type(title, '!');
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    expect(await screen.findByText('Ese firmante no tiene permiso para firmar este tipo de documento.')).toBeInTheDocument();
    expect(title).toHaveValue('Certificado de aprobación!');
  });

  it('shows the empty state of a document that was never configured', async () => {
    const user = userEvent.setup({ delay: null });
    server.use(
      http.get(`${API}/certificate-templates/documents/:documentType`, ({ params }) =>
        HttpResponse.json(documentTemplate(params.documentType, { published: null, draft: null })),
      ),
    );
    renderPage();

    await user.click(screen.getByRole('tab', { name: 'Materias aprobadas' }));

    expect(await screen.findByText('Todavía no hay plantilla')).toBeInTheDocument();
    expect(screen.getByText('Sin plantilla')).toBeInTheDocument();
    expect(screen.getByText(/Completá textos, logo y firmantes de «Constancia de materias aprobadas»/)).toBeInTheDocument();
    expect(screen.queryByText('Obligatorio.')).not.toBeInTheDocument();
  });

  it('explains when no signatory can sign the document and links to Firmantes', async () => {
    const user = userEvent.setup({ delay: null });
    server.use(http.get(`${API}/certificate-signatories`, () => HttpResponse.json({ content: [] })));
    renderPage();

    await openTab(user, 'Alumno regular');

    expect(await screen.findByText('Nadie puede firmar este documento')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a Firmantes' })).toHaveAttribute('href', expect.stringMatching(/\/firmantes$/));
  });

  it('shows the signatories error with retry', async () => {
    const user = userEvent.setup({ delay: null });
    server.use(http.get(`${API}/certificate-signatories`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })));
    renderPage();

    await openTab(user, 'Alumno regular');

    expect(await screen.findByText('No pudimos cargar los firmantes.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  describe('as administrativo', () => {
    beforeEach(() => {
      auth.roles = ['ADMINISTRATIVO'];
    });

    it.each(DOCUMENTS)('shows $tab read-only without edit actions', async ({ tab }) => {
      const user = userEvent.setup({ delay: null });
      const writes = vi.fn();
      server.use(
        http.put(`${API}/certificate-templates/documents/:documentType/draft`, () => {
          writes();
          return HttpResponse.json(draftVersion());
        }),
      );
      renderPage();

      const title = await openTab(user, tab);

      expect(title).toHaveAttribute('readonly');
      expect(screen.getByLabelText('Texto principal')).toHaveAttribute('readonly');
      expect(screen.getByLabelText('Primer firmante')).toBeDisabled();
      expect(screen.getByLabelText('Segundo firmante (opcional)')).toBeDisabled();
      expect(screen.getByText('Solo lectura.')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Guardar borrador' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Publicar' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Descartar borrador' })).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Historial' })).toBeInTheDocument();
      expect(writes).not.toHaveBeenCalled();
    });

    it('shows the read-only empty state when the Director never configured the document', async () => {
      const user = userEvent.setup({ delay: null });
      server.use(
        http.get(`${API}/certificate-templates/documents/:documentType`, ({ params }) =>
          HttpResponse.json(documentTemplate(params.documentType, { published: null, draft: null })),
        ),
      );
      renderPage();

      await user.click(screen.getByRole('tab', { name: 'Alumno regular' }));

      expect(await screen.findByText('El Director todavía no configuró este documento.')).toBeInTheDocument();
    });
  });

  it('has no accessibility violations on a document tab', async () => {
    const user = userEvent.setup({ delay: null });
    const { container } = renderPage();

    await openTab(user, 'Alumno regular');
    await screen.findByLabelText('Primer firmante');

    expect(await axe(container)).toHaveNoViolations();
  });
});
