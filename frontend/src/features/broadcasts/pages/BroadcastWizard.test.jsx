import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { toast } from 'sonner';
import { validateAgainstContract } from '@/test/contract/validateAgainstContract';
import { BroadcastWizard } from './BroadcastWizard';
import { seedWizardDraft } from './seedWizardDraft';

vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

const API = 'http://localhost:8080';

vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ usuario: { id: 7 }, roles: ['DIRECTOR'] }) }));

const previewBody = (overrides = {}) => ({
  total: 10,
  included: 8,
  excludedByReason: { OPTED_OUT: 2 },
  sample: [
    { personId: 1, fullName: 'Ana Paz', addressMasked: 'an••@gmail.com', status: 'INCLUDED', exclusionReason: null },
    { personId: 2, fullName: 'Luis Sosa', addressMasked: 'lu••@gmail.com', status: 'EXCLUDED', exclusionReason: 'OPTED_OUT' },
  ],
  ...overrides,
});

function contractViolations(pathname, body) {
  return validateAgainstContract({ method: 'POST', pathname, request: { hasBody: true, body } }).violations;
}

function rejectContractViolations(pathname, body) {
  const violations = contractViolations(pathname, body);
  return violations.length > 0 ? HttpResponse.json({ code: 'CONTRACT_VIOLATION', message: violations.join('; ') }, { status: 400 }) : null;
}

function mockBackend({ onPreview, onConfirm, confirmResponse, catalog = [{ key: 'PROGRAM', parameters: [] }, { key: 'OVERDUE_DEBT', parameters: [] }], preview = previewBody(), onCreate, templates = [], createResponse } = {}) {
  server.use(
    http.get('*/broadcasts/segment-catalog', () => HttpResponse.json(catalog)),
    http.post('*/broadcasts/audience-preview', async ({ request }) => {
      const body = await request.json();
      onPreview?.(body);
      return rejectContractViolations('/broadcasts/audience-preview', body) ?? HttpResponse.json(preview);
    }),
    http.post('*/broadcasts', async ({ request }) => {
      const body = await request.json();
      onCreate?.(body);
      const rejection = rejectContractViolations('/broadcasts', body);
      if (rejection) return rejection;
      return createResponse?.() ?? HttpResponse.json({ id: 1, status: 'DRAFT' });
    }),
    http.post('*/broadcasts/:id/confirm', ({ params }) => {
      onConfirm?.(params.id);
      return confirmResponse ?? HttpResponse.json({ id: Number(params.id), status: 'CONFIRMED' });
    }),
    http.get(`${API}/message-templates`, () => HttpResponse.json(templates)),
    http.post(`${API}/message-templates/render-preview`, async ({ request }) => {
      const { subject, bodyText } = await request.json();
      return HttpResponse.json({ subject, body: bodyText.replace('{{nombre}}', 'Ana') });
    }),
    http.get(`${API}/programs`, () => HttpResponse.json([])),
    http.get(`${API}/subjects`, () => HttpResponse.json([])),
  );
}

async function reachReview() {
  await userEvent.type(await screen.findByLabelText('Nombre interno'), 'Mesas');
  await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Siguiente' })).toBeEnabled());
  await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
  await userEvent.type(await screen.findByLabelText('Asunto'), 'Hola');
  await userEvent.type(screen.getByLabelText('Mensaje'), 'Hola nombre');
  await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
}

describe('BroadcastWizard', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.clearAllMocks();
    seedWizardDraft(7);
  });

  it('walks the four steps and confirms the broadcast', async () => {
    let created = null;
    let confirmedId = null;
    mockBackend({ onCreate: (body) => (created = body), onConfirm: (id) => (confirmedId = id) });
    renderWithProviders(<BroadcastWizard />);

    await userEvent.type(await screen.findByLabelText('Nombre interno'), 'Mesas');
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    expect(await screen.findByText('Reciben')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Siguiente' })).toBeEnabled());
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await userEvent.type(await screen.findByLabelText('Asunto'), 'Hola');
    await userEvent.type(screen.getByLabelText('Mensaje'), 'Hola nombre');
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar envío' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Enviar' }));

    expect(await screen.findByText('Envío confirmado')).toBeInTheDocument();
    expect(created).toMatchObject({ name: 'Mesas', channel: 'EMAIL', category: 'INSTITUTIONAL', subject: 'Hola' });
    expect(confirmedId).toBe('1');
  });

  it('requires a subject for email', async () => {
    mockBackend();
    renderWithProviders(<BroadcastWizard />);
    await userEvent.type(await screen.findByLabelText('Nombre interno'), 'Mesas');
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Siguiente' })).toBeEnabled());
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await userEvent.type(await screen.findByLabelText('Mensaje'), 'Hola');

    expect(screen.getByText('El asunto es obligatorio en los correos.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
  });

  it('shows included and excluded counts with reasons', async () => {
    mockBackend();
    renderWithProviders(<BroadcastWizard />);
    await userEvent.type(await screen.findByLabelText('Nombre interno'), 'Mesas');
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByText('8')).toBeInTheDocument();
    expect(screen.getAllByText('Dado de baja')).toHaveLength(2);
    expect(screen.getByText('Luis Sosa')).toBeInTheDocument();
  });

  it('hides overdue debt when the catalog does not list it', async () => {
    mockBackend({ catalog: [{ key: 'PROGRAM', parameters: [] }] });
    renderWithProviders(<BroadcastWizard />);
    await userEvent.type(await screen.findByLabelText('Nombre interno'), 'Mesas');
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await screen.findByText('Reciben');

    expect(screen.queryByLabelText('Con deuda vencida')).not.toBeInTheDocument();
  });

  it('shows empty state when nobody matches', async () => {
    mockBackend({ preview: previewBody({ total: 0, included: 0, excludedByReason: {}, sample: [] }) });
    renderWithProviders(<BroadcastWizard />);
    await userEvent.type(await screen.findByLabelText('Nombre interno'), 'Mesas');
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByText('Sin destinatarios')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
  });

  it('offers to exclude recipients with missing variables', async () => {
    mockBackend({ preview: previewBody({ excludedByReason: { MISSING_TEMPLATE_VARIABLE: 3 } }) });
    renderWithProviders(<BroadcastWizard />);
    await userEvent.type(await screen.findByLabelText('Nombre interno'), 'Mesas');
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Siguiente' })).toBeEnabled());
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByRole('button', { name: 'Excluir a esos 3' })).toBeInTheDocument();
  });

  it('keeps the draft in localStorage per user', async () => {
    mockBackend();
    renderWithProviders(<BroadcastWizard />);
    await userEvent.type(await screen.findByLabelText('Nombre interno'), 'Mesas');

    await waitFor(() => expect(JSON.parse(window.localStorage.getItem('broadcast-draft:7')).name).toBe('Mesas'));
  });

  it('confirm dialog shows recipients and days', async () => {
    mockBackend({ preview: previewBody({ total: 450, included: 450 }) });
    renderWithProviders(<BroadcastWizard />);
    await reachReview();
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar envío' }));

    expect(
      await screen.findByText('Se enviará a 450 personas en 3 días. Esta acción no se puede deshacer, pero podés cancelar los pendientes.'),
    ).toBeInTheDocument();
  });

  it('confirm shows conflict when no eligible recipients', async () => {
    mockBackend({ confirmResponse: HttpResponse.json({ code: 'EMPTY_AUDIENCE', message: 'x' }, { status: 409 }) });
    renderWithProviders(<BroadcastWizard />);
    await reachReview();
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar envío' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Enviar' }));

    expect(await screen.findByText('No hay destinatarios elegibles.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Elegir otro segmento' }));
    expect(await screen.findByText('Reciben')).toBeInTheDocument();
  });

  it('sends only the template id when a template is chosen', async () => {
    const bodies = [];
    mockBackend({ onCreate: (body) => bodies.push(body), templates: [{ id: 5, name: 'Mesas', channel: 'EMAIL', category: 'INSTITUTIONAL', active: true, updatedAt: '2026-11-01T10:00:00Z', subject: 'Asunto base', bodyText: 'Cuerpo base' }] });
    renderWithProviders(<BroadcastWizard />);
    await userEvent.type(await screen.findByLabelText('Nombre interno'), 'Mesas');
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Siguiente' })).toBeEnabled());
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await userEvent.selectOptions(await screen.findByLabelText('Plantilla'), 'Mesas');
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar envío' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Enviar' }));

    await screen.findByText('Envío confirmado');
    expect(bodies).toHaveLength(1);
    expect(bodies[0]).toStrictEqual({ name: 'Mesas', channel: 'EMAIL', category: 'INSTITUTIONAL', segmentSpec: { version: 1, quantifier: 'ALL', criteria: [{ key: 'ENROLLMENT_STATUS', value: 'IN_COURSE' }] }, templateId: 5 });
  });

  it('creates a new draft when the message changed after a failed confirm', async () => {
    const bodies = [];
    let confirms = 0;
    mockBackend({ onCreate: (body) => bodies.push(body) });
    server.use(
      http.post('*/broadcasts/:id/confirm', ({ params }) => {
        confirms += 1;
        if (confirms === 1) return HttpResponse.json({ code: 'INTERNAL_ERROR' }, { status: 500 });
        return HttpResponse.json({ id: Number(params.id), status: 'CONFIRMED' });
      }),
    );
    renderWithProviders(<BroadcastWizard />);
    await reachReview();
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar envío' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Enviar' }));
    await waitFor(() => expect(confirms).toBe(1));
    await userEvent.click(await screen.findByRole('button', { name: 'Atrás' }));
    await userEvent.type(await screen.findByLabelText('Mensaje'), ' cambiado');
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar envío' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Enviar' }));

    await screen.findByText('Envío confirmado');
    expect(bodies).toHaveLength(2);
    expect(bodies[1].bodyText).toBe('Hola nombre cambiado');
  });

  it('reuses the draft when retrying with the same content', async () => {
    let creates = 0;
    let confirms = 0;
    mockBackend({ onCreate: () => (creates += 1) });
    server.use(
      http.post('*/broadcasts/:id/confirm', ({ params }) => {
        confirms += 1;
        if (confirms === 1) return HttpResponse.json({ code: 'INTERNAL_ERROR' }, { status: 500 });
        return HttpResponse.json({ id: Number(params.id), status: 'CONFIRMED' });
      }),
    );
    renderWithProviders(<BroadcastWizard />);
    await reachReview();
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar envío' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Enviar' }));
    await waitFor(() => expect(confirms).toBe(1));
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar envío' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Enviar' }));

    await screen.findByText('Envío confirmado');
    expect(creates).toBe(1);
  });

  it('treats an illegal status transition on confirm as already confirmed', async () => {
    mockBackend({ confirmResponse: HttpResponse.json({ code: 'ILLEGAL_BROADCAST_STATUS_TRANSITION' }, { status: 409 }) });
    renderWithProviders(<BroadcastWizard />);
    await reachReview();
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar envío' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Enviar' }));

    expect(await screen.findByText('Envío confirmado')).toBeInTheDocument();
  });

  it('sends nothing when the confirm dialog is cancelled', async () => {
    let creates = 0;
    let confirms = 0;
    mockBackend({ onCreate: () => (creates += 1), onConfirm: () => (confirms += 1) });
    renderWithProviders(<BroadcastWizard />);
    await reachReview();
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar envío' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Volver' }));

    expect(creates).toBe(0);
    expect(confirms).toBe(0);
  });

  it('sends a single create and confirm on double click', async () => {
    let creates = 0;
    let confirms = 0;
    mockBackend({ onCreate: () => (creates += 1), onConfirm: () => (confirms += 1) });
    renderWithProviders(<BroadcastWizard />);
    await reachReview();
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar envío' }));
    const send = await screen.findByRole('button', { name: 'Enviar' });
    fireEvent.click(send);
    fireEvent.click(send);

    await screen.findByText('Envío confirmado');
    expect(creates).toBe(1);
    expect(confirms).toBe(1);
  });

  it('confirm dialog shows channel and category', async () => {
    mockBackend();
    renderWithProviders(<BroadcastWizard />);
    await reachReview();
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar envío' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByText('Correo')).toBeInTheDocument();
    expect(within(dialog).getByText('Institucional')).toBeInTheDocument();
  });

  it('shows the template content error from the backend', async () => {
    mockBackend({ createResponse: () => HttpResponse.json({ code: 'BROADCAST_CONTENT_WITH_TEMPLATE' }, { status: 400 }) });
    renderWithProviders(<BroadcastWizard />);
    await reachReview();
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar envío' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Enviar' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Con una plantilla no se puede enviar asunto ni mensaje propios.'));
  });

  it('sends a segment spec that matches the contract', async () => {
    const previews = [];
    let created = null;
    seedWizardDraft(7, { statuses: ['REGULAR'], staffRoles: ['DOCENTE'], overdueDebt: true, programIds: [4], subjectId: 9 });
    mockBackend({
      catalog: [{ key: 'PROGRAM', parameters: [] }, { key: 'OVERDUE_DEBT', parameters: [] }],
      onPreview: (body) => previews.push(body),
      onCreate: (body) => (created = body),
    });
    renderWithProviders(<BroadcastWizard />);
    await userEvent.type(await screen.findByLabelText('Nombre interno'), 'Mesas');
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await waitFor(() => expect(previews.at(-1)?.segmentSpec.criteria).toHaveLength(5));
    const previewed = previews.at(-1);

    expect(previewed.segmentSpec).toEqual({
      version: 1,
      quantifier: 'ALL',
      criteria: [
        { key: 'PROGRAM', value: '4' },
        { key: 'SUBJECT', value: '9' },
        { key: 'ENROLLMENT_STATUS', value: 'REGULAR' },
        { key: 'OVERDUE_DEBT', value: '' },
        { key: 'STAFF_ROLE', value: 'DOCENTE' },
      ],
    });
    expect(contractViolations('/broadcasts/audience-preview', previewed)).toEqual([]);
    expect(created).toBeNull();
  });

  it('does not request the audience until a criterion is chosen', async () => {
    window.localStorage.clear();
    const onPreview = vi.fn();
    mockBackend({ onPreview });
    renderWithProviders(<BroadcastWizard />);
    await userEvent.type(await screen.findByLabelText('Nombre interno'), 'Mesas');
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByText('Elegí un criterio')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
    expect(onPreview).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Cursando' }));
    await waitFor(() => expect(onPreview).toHaveBeenCalledTimes(1));
    expect(contractViolations('/broadcasts/audience-preview', onPreview.mock.calls[0][0])).toEqual([]);
  });

  it('keeps a single enrollment status selected at a time', async () => {
    window.localStorage.clear();
    mockBackend();
    renderWithProviders(<BroadcastWizard />);
    await userEvent.type(await screen.findByLabelText('Nombre interno'), 'Mesas');
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Cursando' }));
    await userEvent.click(screen.getByRole('button', { name: 'Cursada regular' }));

    expect(screen.getByRole('button', { name: 'Cursando' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('button', { name: 'Cursada regular' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('flags a body that breaks the contract', () => {
    expect(contractViolations('/broadcasts/audience-preview', { channel: 'EMAIL', category: 'INSTITUTIONAL', segmentSpec: { criteria: [{ key: 'SECTION', values: [1] }] }, page: 0, size: 10 })).not.toEqual([]);
  });
});

