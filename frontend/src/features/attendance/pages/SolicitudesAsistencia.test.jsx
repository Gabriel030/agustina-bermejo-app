import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { SolicitudesAsistencia } from '@/features/attendance/pages/SolicitudesAsistencia';

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

const API = 'http://localhost:8080';

const CORRECTION = {
  id: 41,
  attendanceId: 7,
  createdAt: '2026-10-14T09:00:00Z',
  classDate: '2026-10-14',
  previousStatus: 'ABSENT',
  requestedStatus: 'PRESENT',
  reason: 'El alumno llegó y quedó sin marcar.',
  requestedByName: 'Lucía Ferreyra',
  sectionName: 'Comisión A',
  studentName: 'Mateo Giménez',
  subjectName: 'Historia del Arte',
  status: 'PENDING',
  rejectionReason: null,
};

const JUSTIFICATION = {
  attendanceId: 42,
  classDate: '2026-10-14',
  reason: 'Certificado médico por gripe.',
  attachmentUrl: 'students/1/justificaciones-asistencia_abc.pdf',
  sectionName: 'Comisión B',
  studentName: 'Valentina Suárez',
  subjectName: 'Taller de Cerámica',
  status: 'PENDING',
  decidedAt: '2026-10-14T10:00:00Z',
  rejectionReason: '',
};

const ATTENDANCE = {
  id: 42,
  enrollmentId: 3,
  classDate: '2026-10-14',
  status: 'JUSTIFIED',
  justificationReason: 'x',
  justificationAttachmentUrl: '',
  justificationRejectionReason: '',
  justifiedAt: '2026-10-14T10:00:00Z',
};

function mockInbox({ corrections: initialCorrections = [CORRECTION], justifications: initialJustifications = [JUSTIFICATION] } = {}) {
  let corrections = initialCorrections;
  let justifications = initialJustifications;
  const calls = { approvedId: null, rejectBody: null, correctionRejected: false, correctionRejectBody: null };
  server.use(
    http.post(`${API}/attendance/change-requests/:id/reject`, async ({ request }) => {
      calls.correctionRejected = true;
      calls.correctionRejectBody = await request.json();
      corrections = [];
      return HttpResponse.json(CORRECTION);
    }),
    http.post(`${API}/attendance/:id/approve-justification`, ({ params }) => {
      calls.approvedId = params.id;
      justifications = [];
      return HttpResponse.json(ATTENDANCE);
    }),
    http.post(`${API}/attendance/:id/reject-justification`, async ({ request }) => {
      calls.rejectBody = await request.json();
      justifications = [];
      return HttpResponse.json(ATTENDANCE);
    }),
    http.get(`${API}/attendance/change-requests`, () => HttpResponse.json(corrections)),
    http.get(`${API}/attendance/justifications`, () => HttpResponse.json({ items: justifications, totalElements: justifications.length })),
  );
  return calls;
}

describe('SolicitudesAsistencia', () => {
  it('muestra correcciones y justificaciones juntas y filtra por tipo', async () => {
    mockInbox();
    renderWithProviders(<SolicitudesAsistencia />);

    await screen.findByText('Mateo Giménez');
    expect(screen.getByText('Valentina Suárez')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('tab', { name: /Justificaciones/ }));
    expect(screen.queryByText('Mateo Giménez')).not.toBeInTheDocument();
    expect(screen.getByText('Valentina Suárez')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('tab', { name: /Correcciones/ }));
    expect(screen.getByText('Mateo Giménez')).toBeInTheDocument();
    expect(screen.queryByText('Valentina Suárez')).not.toBeInTheDocument();
  });

  it('shows how many justifications are displayed out of the total and loads more', async () => {
    const sizes = [];
    server.use(
      http.get(`${API}/attendance/change-requests`, () => HttpResponse.json([])),
      http.get(`${API}/attendance/justifications`, ({ request }) => {
        const size = Number(new URL(request.url).searchParams.get('size'));
        sizes.push(size);
        const total = size > 100 ? 3 : 150;
        const items = Array.from({ length: size > 100 ? 3 : 2 }, (_, i) => ({ ...JUSTIFICATION, attendanceId: i + 1 }));
        return HttpResponse.json({ items, totalElements: total });
      }),
    );
    renderWithProviders(<SolicitudesAsistencia />);

    expect(await screen.findByText('Mostrando 2 de 150 justificaciones')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Ver más' }));

    expect(await screen.findByText('Mostrando 3 de 3 justificaciones')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ver más' })).not.toBeInTheDocument();
    expect(sizes).toEqual([100, 200]);
  });

  it('shows a clear error when approving fails', async () => {
    mockInbox({ corrections: [] });
    server.use(
      http.post(`${API}/attendance/:id/approve-justification`, () =>
        HttpResponse.json(errorBody('CONFLICT'), { status: 409 }),
      ),
    );
    renderWithProviders(<SolicitudesAsistencia />);

    await screen.findByText('Valentina Suárez');
    await userEvent.click(screen.getByRole('button', { name: 'Aprobar' }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith(expect.stringContaining('No pudimos aprobar la justificación')));
    expect(screen.getByText('Valentina Suárez')).toBeInTheDocument();
  });

  it('aprobar una justificación llama al endpoint de la inasistencia', async () => {
    const calls = mockInbox({ corrections: [] });
    renderWithProviders(<SolicitudesAsistencia />);

    await screen.findByText('Valentina Suárez');
    await userEvent.click(screen.getByRole('button', { name: 'Aprobar' }));

    await screen.findByText('Sin solicitudes pendientes');
    expect(calls.approvedId).toBe('42');
  });

  it('rechazar una justificación exige un motivo de al menos 10 caracteres y lo envía', async () => {
    const calls = mockInbox({ corrections: [] });
    renderWithProviders(<SolicitudesAsistencia />);

    await screen.findByText('Valentina Suárez');
    await userEvent.click(screen.getByRole('button', { name: 'Rechazar' }));
    const textarea = await screen.findByPlaceholderText('Explicá por qué se rechaza');

    await userEvent.type(textarea, 'corto');
    await userEvent.click(screen.getAllByRole('button', { name: 'Rechazar' }).at(-1));
    expect(await screen.findByText('El motivo debe tener al menos 10 caracteres.')).toBeInTheDocument();
    expect(calls.rejectBody).toBeNull();

    await userEvent.clear(textarea);
    await userEvent.type(textarea, 'El certificado no corresponde al día.');
    await userEvent.click(screen.getAllByRole('button', { name: 'Rechazar' }).at(-1));

    await screen.findByText('Sin solicitudes pendientes');
    expect(calls.rejectBody).toEqual({ reason: 'El certificado no corresponde al día.' });
  });

  it('rejecting a correction requires a reason of at least 10 characters and sends it', async () => {
    const calls = mockInbox({ justifications: [] });
    renderWithProviders(<SolicitudesAsistencia />);

    await screen.findByText('Mateo Giménez');
    await userEvent.click(screen.getByRole('button', { name: 'Rechazar' }));
    const textarea = await screen.findByPlaceholderText('Explicá por qué se rechaza');

    await userEvent.click(screen.getAllByRole('button', { name: 'Rechazar' }).at(-1));
    expect(await screen.findByText('El motivo del rechazo es obligatorio.')).toBeInTheDocument();
    await userEvent.type(textarea, 'corto');
    expect(screen.getByText('5/500')).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('button', { name: 'Rechazar' }).at(-1));
    expect(await screen.findByText('El motivo debe tener al menos 10 caracteres.')).toBeInTheDocument();
    expect(calls.correctionRejected).toBe(false);

    await userEvent.clear(textarea);
    await userEvent.type(textarea, '  La clase ya estaba firmada.  ');
    await userEvent.click(screen.getAllByRole('button', { name: 'Rechazar' }).at(-1));

    await screen.findByText('Sin solicitudes pendientes');
    expect(calls.correctionRejectBody).toEqual({ reason: 'La clase ya estaba firmada.' });
  });

  it('opens the attachment through the authenticated download endpoint', async () => {
    const preview = { location: { href: '' }, close: vi.fn() };
    vi.spyOn(window, 'open').mockReturnValue(preview);
    URL.createObjectURL = vi.fn(() => 'blob:justificacion-42');
    server.use(
      http.get(`${API}/attendance/42/justification-attachment`, () =>
        new HttpResponse('contenido', { headers: { 'Content-Type': 'application/pdf' } }),
      ),
    );
    mockInbox({ corrections: [] });
    renderWithProviders(<SolicitudesAsistencia />);

    await userEvent.click(await screen.findByRole('button', { name: 'Ver adjunto' }));

    await vi.waitFor(() => expect(preview.location.href).toBe('blob:justificacion-42'));
  });

  it('shows no attachment action when the justification has no file', async () => {
    mockInbox({ corrections: [], justifications: [{ ...JUSTIFICATION, attachmentUrl: null }] });
    renderWithProviders(<SolicitudesAsistencia />);

    await screen.findByText('Valentina Suárez');
    expect(screen.queryByRole('button', { name: 'Ver adjunto' })).not.toBeInTheDocument();
    expect(screen.getByText('Sin adjunto')).toBeInTheDocument();
  });

  it('opens the tab named in the query string', async () => {
    mockInbox();
    renderWithProviders(<SolicitudesAsistencia />, { route: '/?tab=justificaciones' });

    await screen.findByText('Valentina Suárez');
    expect(screen.queryByText('Mateo Giménez')).not.toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Justificaciones/ })).toHaveAttribute('data-state', 'active');
  });

  it('muestra el estado vacío cuando no hay nada pendiente', async () => {
    mockInbox({ corrections: [], justifications: [] });
    renderWithProviders(<SolicitudesAsistencia />);

    expect(await screen.findByText('Sin solicitudes pendientes')).toBeInTheDocument();
  });
});
