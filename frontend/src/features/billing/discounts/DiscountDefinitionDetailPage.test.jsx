import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DiscountDefinitionDetailPage } from './DiscountDefinitionDetailPage';
import { API, definition, mockOffers, money, promotion } from './discountFixtures';

const auth = vi.hoisted(() => ({ roles: ['DIRECTOR'] }));
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: auth.roles } }),
}));

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

vi.setConfig({ testTimeout: 20000 });

function renderDetail(id) {
  return renderWithProviders(
    <Routes>
      <Route path="/:role/descuentos/:id" element={<DiscountDefinitionDetailPage />} />
      <Route path="/:role/descuentos" element={<p>Lista de descuentos</p>} />
    </Routes>,
    { route: `/${auth.roles[0].toLowerCase()}/descuentos/${id}` },
  );
}

function mockDefinition(entry) {
  server.use(http.get(`${API}/discount-definitions/${entry.id}`, () => HttpResponse.json(entry)));
}

function mockClosure(status, body, onRequest) {
  server.use(
    http.post(`${API}/discount-definitions/3/closure`, async ({ request }) => {
      onRequest?.(await request.json());
      return HttpResponse.json(body, { status });
    }),
  );
}

describe('DiscountDefinitionDetailPage', () => {
  beforeEach(() => {
    auth.roles = ['DIRECTOR'];
    toastMock.success.mockClear();
    toastMock.error.mockClear();
    mockOffers();
  });

  it('shows every term of a promotion in read only fields', async () => {
    mockDefinition(promotion({ programCohortId: 31, estimatedQuotaCostStatus: 'ESTIMATED', estimatedQuotaCost: money('3300000.00'), capPerInvoice: money('120000.00') }));
    renderDetail(3);

    expect(await screen.findByRole('heading', { name: 'Cohorte agosto 2026' }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByText('Abierta')).toBeInTheDocument();
    expect(screen.getByLabelText('Aplica a')).toHaveValue('Cuota mensual');
    expect(screen.getByLabelText('Cálculo')).toHaveValue('20 % · 3 cuotas');
    expect(screen.getByLabelText('Ventana de inscripción')).toHaveValue('01/08/2026 – 30/09/2026');
    expect(screen.getByLabelText('Cupo')).toHaveValue('18 de 20 usados');
    expect(screen.getByLabelText('Tope por factura').value).toMatch(/120\.000,00/);
    expect(screen.getByLabelText('Costo estimado del cupo completo').value).toMatch(/3\.300\.000,00/);
    await waitFor(() => expect(screen.getByLabelText('Alcance')).toHaveValue('Enfermería · Febrero 2027'));
    expect(screen.getByLabelText('Aplica a')).toHaveAttribute('readonly');
  });

  it('closes a promotion directly for the director with a required reason', async () => {
    mockDefinition(promotion());
    let body;
    mockClosure(201, { id: 3, status: 'CLOSED', authorizationRequestId: null }, (payload) => (body = payload));
    const user = userEvent.setup();
    renderDetail(3);

    await user.click(await screen.findByRole('button', { name: 'Cerrar promoción' }));
    expect(screen.getByText(/Deja de ofrecerse desde hoy/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Cerrar promoción', hidden: false }));
    expect(screen.getByText('El motivo es obligatorio.')).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText(/Qué lo motiva/), 'Se reemplazó por la de 2027.');
    await user.click(screen.getByRole('button', { name: 'Cerrar promoción' }));

    expect(await screen.findByText('Lista de descuentos')).toBeInTheDocument();
    expect(body).toEqual({ reason: 'Se reemplazó por la de 2027.' });
    expect(toastMock.success).toHaveBeenCalledWith('Promoción cerrada.', undefined);
  });

  it('lets the administrative only propose the closure and links to their requests', async () => {
    auth.roles = ['ADMINISTRATIVO'];
    mockDefinition(promotion());
    mockClosure(202, { authorizationRequestId: 55 });
    const user = userEvent.setup();
    renderDetail(3);

    await user.click(await screen.findByRole('button', { name: 'Proponer cierre' }));
    expect(screen.getByText(/El director decide. Mientras tanto la promoción sigue abierta/)).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText(/Qué lo motiva/), 'Ya no corresponde.');
    await user.click(screen.getByRole('button', { name: 'Enviar al director' }));

    expect(await screen.findByText('Lista de descuentos')).toBeInTheDocument();
    const [message, options] = toastMock.success.mock.calls[0];
    expect(message).toBe('Pedido de cierre enviado al director.');
    expect(options.action.label).toBe('Ver mis pedidos');
  });

  it('keeps the dialog open and explains when the closure is rejected', async () => {
    mockDefinition(definition({ id: 3 }));
    mockClosure(409, { code: 'DISCOUNT_DEFINITION_CLOSED', violations: [{ code: 'DISCOUNT_DEFINITION_CLOSED', message: 'La promoción está cerrada.' }] });
    const user = userEvent.setup();
    renderDetail(3);

    await user.click(await screen.findByRole('button', { name: 'Cerrar descuento' }));
    await user.type(screen.getByPlaceholderText(/Qué lo motiva/), 'Motivo');
    await user.click(screen.getByRole('button', { name: 'Cerrar descuento' }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith('La promoción está cerrada.'));
    expect(screen.queryByText('Lista de descuentos')).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Qué lo motiva/)).toHaveValue('Motivo');
  });

  it('shows the closure reason and offers no close action on a closed definition', async () => {
    mockDefinition(promotion({ status: 'CLOSED', cohortPromotionStatus: 'CLOSED', closedAt: '2026-10-05T10:00:00Z', closureReason: 'Se reemplazó por la de 2027.' }));
    renderDetail(3);

    expect(await screen.findByText('Cerrada el 05/10/2026.')).toBeInTheDocument();
    expect(screen.getByText(/Se reemplazó por la de 2027\./)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Cerrar|Proponer cierre/ })).not.toBeInTheDocument();
  });

  it('explains a missing definition', async () => {
    server.use(http.get(`${API}/discount-definitions/99`, () => new HttpResponse(null, { status: 404 })));
    renderDetail(99);

    expect(await screen.findByText('No encontramos este descuento o esta promoción.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
  });

  it('offers a retry when the detail cannot be loaded', async () => {
    server.use(http.get(`${API}/discount-definitions/3`, () => new HttpResponse(null, { status: 500 })));
    const user = userEvent.setup();
    renderDetail(3);

    expect(await screen.findByText('No pudimos cargar los datos.')).toBeInTheDocument();
    mockDefinition(promotion());
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('heading', { name: 'Cohorte agosto 2026' })).toBeInTheDocument();
  });
});
