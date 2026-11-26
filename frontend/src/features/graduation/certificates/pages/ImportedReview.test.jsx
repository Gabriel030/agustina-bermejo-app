import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { errorBody } from '@/test/fixtures/errors';
import { graduationCertificateReview, springPage } from '@/test/fixtures/certificates';
import { REVIEW_BATCH_LIMIT } from '@/features/graduation/certificates/lib/importedReview';
import { StaffGraduationCertificates } from '@/features/graduation/certificates/pages/StaffGraduationCertificates';

const API = 'http://localhost:8080';
const auth = vi.hoisted(() => ({ roles: ['DIRECTOR'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: auth.roles } }),
}));

const tomas = graduationCertificateReview({ graduationRequestId: 31, studentFullName: 'Gómez, Tomás', documentNumber: '41003221' });
const lucia = graduationCertificateReview({ graduationRequestId: 32, studentFullName: 'Ruiz, Lucía', documentNumber: '39555111' });

function reviewList(rows, { totalElements = rows.length } = {}) {
  const requests = [];
  server.use(
    http.get(`${API}/graduation-certificate-reviews`, ({ request }) => {
      requests.push(new URL(request.url).searchParams);
      return HttpResponse.json(springPage(rows, { totalElements }));
    }),
  );
  return requests;
}

function rejectWith(path, code, status = 409) {
  server.use(http.post(`${API}/graduation-certificate-reviews/${path}`, () => HttpResponse.json(errorBody(code), { status })));
}

async function openReviewTab(user) {
  await user.click(await screen.findByRole('tab', { name: /Revisión de importados/ }));
}

function renderPage() {
  return renderWithProviders(<StaffGraduationCertificates />);
}

describe('StaffGraduationCertificates · Revisión de importados', () => {
  beforeEach(() => {
    auth.roles = ['DIRECTOR'];
    server.use(http.get(`${API}/graduation-certificates/eligible`, () => HttpResponse.json(springPage([]))));
  });

  it('does not render the tab nor request the reviews API without the director role', async () => {
    auth.roles = ['ADMINISTRATIVO'];
    const requests = reviewList([tomas]);
    renderPage();

    expect(await screen.findByRole('tab', { name: 'Emitidos' })).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: /Revisión de importados/ })).not.toBeInTheDocument();
    expect(requests).toHaveLength(0);
  });

  it('shows the pending count in the tab and lists pending reviews by default', async () => {
    const user = userEvent.setup();
    const requests = reviewList([tomas, lucia], { totalElements: 7 });
    renderPage();

    expect(await screen.findByRole('tab', { name: 'Revisión de importados, 7 pendientes' })).toBeInTheDocument();
    await openReviewTab(user);

    expect(await screen.findByText('Gómez, Tomás')).toBeInTheDocument();
    expect(screen.getByText('Ruiz, Lucía')).toBeInTheDocument();
    expect(screen.getAllByText('Pendiente').length).toBeGreaterThan(0);
    expect(requests.some((params) => params.get('status') === 'PENDING_REVIEW' && params.get('size') === '25')).toBe(true);
  });

  it('shows the loading state while the list is pending', async () => {
    const user = userEvent.setup();
    server.use(http.get(`${API}/graduation-certificate-reviews`, () => new Promise(() => {})));
    renderPage();
    await openReviewTab(user);

    expect(await screen.findByLabelText('Cargando')).toBeInTheDocument();
  });

  it('shows the empty state when there is nothing to review', async () => {
    const user = userEvent.setup();
    reviewList([]);
    renderPage();
    await openReviewTab(user);

    expect(await screen.findByText('No hay egresos para revisar')).toBeInTheDocument();
  });

  it('shows the error state and retries', async () => {
    const user = userEvent.setup();
    let failing = true;
    server.use(
      http.get(`${API}/graduation-certificate-reviews`, () =>
        failing ? HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 }) : HttpResponse.json(springPage([tomas])),
      ),
    );
    renderPage();
    await openReviewTab(user);

    expect(await screen.findByText('No pudimos cargar los egresos importados')).toBeInTheDocument();
    failing = false;
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Gómez, Tomás')).toBeInTheDocument();
  });

  it('filters by status and by name or document', async () => {
    const user = userEvent.setup();
    const requests = reviewList([tomas]);
    renderPage();
    await openReviewTab(user);
    await screen.findByText('Gómez, Tomás');

    await user.click(screen.getByRole('tab', { name: 'Habilitados' }));
    await waitFor(() => expect(requests.some((params) => params.get('status') === 'ENABLED')).toBe(true));

    await user.type(screen.getByRole('searchbox', { name: 'Buscar por nombre o documento' }), '4100');
    await waitFor(() => expect(requests.some((params) => params.get('status') === 'ENABLED' && params.get('query') === '4100')).toBe(true));
  });

  it('enables a single certificate directly without a confirmation dialog', async () => {
    const user = userEvent.setup();
    const enabled = vi.fn();
    reviewList([tomas]);
    server.use(
      http.post(`${API}/graduation-certificate-reviews/:id/enable`, ({ params }) => {
        enabled(params.id);
        return HttpResponse.json({ ...tomas, reviewStatus: 'ENABLED' });
      }),
    );
    renderPage();
    await openReviewTab(user);

    await user.click(await screen.findByRole('button', { name: 'Habilitar certificado de Gómez, Tomás' }));

    await waitFor(() => expect(enabled).toHaveBeenCalledWith('31'));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('requires a reason of at least 10 characters before disabling and sends it', async () => {
    const user = userEvent.setup();
    let sentBody;
    reviewList([tomas]);
    server.use(
      http.post(`${API}/graduation-certificate-reviews/:id/disable`, async ({ request }) => {
        sentBody = await request.json();
        return HttpResponse.json({ ...tomas, reviewStatus: 'DISABLED' });
      }),
    );
    renderPage();
    await openReviewTab(user);

    await user.click(await screen.findByRole('button', { name: 'Deshabilitar certificado de Gómez, Tomás' }));
    const dialog = await screen.findByRole('dialog');

    await user.click(within(dialog).getByRole('button', { name: 'Deshabilitar' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('El motivo es obligatorio para deshabilitar.');
    expect(sentBody).toBeUndefined();

    await user.type(within(dialog).getByRole('textbox'), 'corto');
    await user.click(within(dialog).getByRole('button', { name: 'Deshabilitar' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('al menos 10 caracteres');
    expect(sentBody).toBeUndefined();

    await user.clear(within(dialog).getByRole('textbox'));
    await user.type(within(dialog).getByRole('textbox'), 'Datos de título inconsistentes');
    await user.click(within(dialog).getByRole('button', { name: 'Deshabilitar' }));

    await waitFor(() => expect(sentBody).toEqual({ reason: 'Datos de título inconsistentes' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('keeps the reason dialog open with the domain message when disabling fails', async () => {
    const user = userEvent.setup();
    reviewList([tomas]);
    rejectWith('31/disable', 'GRADUATION_REQUEST_NOT_FOUND', 404);
    renderPage();
    await openReviewTab(user);

    await user.click(await screen.findByRole('button', { name: 'Deshabilitar certificado de Gómez, Tomás' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByRole('textbox'), 'Datos de título inconsistentes');
    await user.click(within(dialog).getByRole('button', { name: 'Deshabilitar' }));

    expect(await within(dialog).findByText('No encontramos la solicitud de egreso.')).toBeInTheDocument();
  });

  it('selects rows, shows the action bar and stops at the batch limit', async () => {
    const user = userEvent.setup();
    const many = Array.from({ length: REVIEW_BATCH_LIMIT + 1 }, (_, index) =>
      graduationCertificateReview({ graduationRequestId: 100 + index, studentFullName: `Alumno ${index + 1}` }),
    );
    reviewList(many);
    renderPage();
    await openReviewTab(user);

    await screen.findByRole('checkbox', { name: 'Seleccionar a Alumno 1' });
    expect(screen.queryByRole('region', { name: 'Acciones sobre la selección' })).not.toBeInTheDocument();
    const boxes = Array.from({ length: REVIEW_BATCH_LIMIT }, (_, index) => screen.getByLabelText(`Seleccionar a Alumno ${index + 1}`));
    act(() => {
      boxes.forEach((box) => fireEvent.click(box));
    });

    const bar = screen.getByRole('region', { name: 'Acciones sobre la selección' });
    expect(within(bar).getByText(`${REVIEW_BATCH_LIMIT} seleccionados`)).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: `Seleccionar a Alumno ${REVIEW_BATCH_LIMIT + 1}` })).toBeDisabled();
  });

  it('enables the selection in one batch after confirming and shows the per-item result', async () => {
    const user = userEvent.setup();
    let sentBody;
    reviewList([tomas, lucia]);
    server.use(
      http.post(`${API}/graduation-certificate-reviews/batch`, async ({ request }) => {
        sentBody = await request.json();
        return HttpResponse.json({
          requested: 2,
          decided: 1,
          failed: 1,
          results: [
            { graduationRequestId: 31, outcome: 'DECIDED', review: { ...tomas, reviewStatus: 'ENABLED' }, errorCode: null },
            { graduationRequestId: 32, outcome: 'FAILED', review: null, errorCode: 'GRADUATION_REQUEST_NOT_FOUND' },
          ],
        });
      }),
    );
    renderPage();
    await openReviewTab(user);

    await user.click(await screen.findByRole('checkbox', { name: 'Seleccionar a Gómez, Tomás' }));
    await user.click(screen.getByRole('checkbox', { name: 'Seleccionar a Ruiz, Lucía' }));
    await user.click(screen.getByRole('button', { name: 'Habilitar seleccionados' }));

    const dialog = await screen.findByRole('alertdialog');
    expect(sentBody).toBeUndefined();
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar y habilitar 2' }));

    expect(await screen.findByText((_, element) => element?.tagName === 'P' && element.textContent === 'Habilitados 1 de 2')).toBeInTheDocument();
    expect(sentBody).toEqual({ graduationRequestIds: [31, 32], decision: 'ENABLE' });
    expect(screen.getByText('Ruiz, Lucía:')).toBeInTheDocument();
    expect(screen.getByText('No encontramos la solicitud de egreso.')).toBeInTheDocument();
  });

  it('disables the selection in one batch with the typed reason', async () => {
    const user = userEvent.setup();
    let sentBody;
    reviewList([tomas, lucia]);
    server.use(
      http.post(`${API}/graduation-certificate-reviews/batch`, async ({ request }) => {
        sentBody = await request.json();
        return HttpResponse.json({
          requested: 2,
          decided: 2,
          failed: 0,
          results: [31, 32].map((graduationRequestId) => ({ graduationRequestId, outcome: 'DECIDED', review: null, errorCode: null })),
        });
      }),
    );
    renderPage();
    await openReviewTab(user);

    await user.click(await screen.findByRole('checkbox', { name: 'Seleccionar a Gómez, Tomás' }));
    await user.click(screen.getByRole('checkbox', { name: 'Seleccionar a Ruiz, Lucía' }));
    await user.click(screen.getByRole('button', { name: 'Deshabilitar seleccionados' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByRole('textbox'), 'Importación con errores de carga');
    await user.click(within(dialog).getByRole('button', { name: 'Deshabilitar 2' }));

    expect(await screen.findByText((_, element) => element?.tagName === 'P' && element.textContent === 'Deshabilitados 2 de 2')).toBeInTheDocument();
    expect(sentBody).toEqual({
      graduationRequestIds: [31, 32],
      decision: 'DISABLE',
      reason: 'Importación con errores de carga',
    });
  });

  it('locks a row whose certificate can no longer be reviewed and explains why', async () => {
    const user = userEvent.setup();
    reviewList([tomas]);
    rejectWith('31/enable', 'INVALID_GRADUATION_CERTIFICATE_REVIEW_DECISION');
    renderPage();
    await openReviewTab(user);

    await user.click(await screen.findByRole('button', { name: 'Habilitar certificado de Gómez, Tomás' }));

    expect(await screen.findByText('Certificado emitido')).toBeInTheDocument();
    expect(screen.getByText(/ya no admite cambios de revisión/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Habilitar certificado de Gómez, Tomás' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Deshabilitar certificado de Gómez, Tomás' })).toBeDisabled();
    expect(screen.getByRole('checkbox', { name: 'Seleccionar a Gómez, Tomás' })).toBeDisabled();
  });

  it('has no accessibility violations with rows loaded', async () => {
    const user = userEvent.setup();
    reviewList([tomas, lucia]);
    const { container } = renderPage();
    await openReviewTab(user);
    await screen.findByText('Gómez, Tomás');

    expect(await axe(container)).toHaveNoViolations();
  });
});
