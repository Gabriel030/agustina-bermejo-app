import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { BroadcastWizard } from './BroadcastWizard';

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

function mockBackend({ onConfirm, confirmResponse, catalog = [{ key: 'PROGRAM', parameters: [] }, { key: 'OVERDUE_DEBT', parameters: [] }], preview = previewBody(), onCreate } = {}) {
  server.use(
    http.get('*/broadcasts/segment-catalog', () => HttpResponse.json(catalog)),
    http.post('*/broadcasts/audience-preview', () => HttpResponse.json(preview)),
    http.post('*/broadcasts', async ({ request }) => {
      onCreate?.(await request.json());
      return HttpResponse.json({ id: 1, status: 'DRAFT' });
    }),
    http.post('*/broadcasts/:id/confirm', ({ params }) => {
      onConfirm?.(params.id);
      return confirmResponse ?? HttpResponse.json({ id: Number(params.id), status: 'CONFIRMED' });
    }),
    http.get(`${API}/message-templates`, () => HttpResponse.json([])),
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
  beforeEach(() => window.localStorage.clear());

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
});
