import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { SolicitudesAsistencia } from '@/features/attendance/pages/SolicitudesAsistencia';

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
};

const JUSTIFICATION = {
  attendanceId: 42,
  classDate: '2026-10-14',
  reason: 'Certificado médico por gripe.',
  attachmentUrl: 'https://files.example.com/certificado.pdf',
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
  const calls = { approvedId: null, rejectBody: null, correctionRejected: false };
  server.use(
    http.post(`${API}/attendance/change-requests/:id/reject`, () => {
      calls.correctionRejected = true;
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

  it('rechazar una corrección no pide motivo', async () => {
    const calls = mockInbox({ justifications: [] });
    renderWithProviders(<SolicitudesAsistencia />);

    await screen.findByText('Mateo Giménez');
    await userEvent.click(screen.getByRole('button', { name: 'Rechazar' }));
    await userEvent.click(screen.getAllByRole('button', { name: 'Rechazar' }).at(-1));

    await screen.findByText('Sin solicitudes pendientes');
    expect(calls.correctionRejected).toBe(true);
  });

  it('solo ofrece el adjunto cuando es una URL http', async () => {
    mockInbox({ corrections: [], justifications: [{ ...JUSTIFICATION, attachmentUrl: 'javascript:alert(1)' }] });
    renderWithProviders(<SolicitudesAsistencia />);

    await screen.findByText('Valentina Suárez');
    expect(screen.queryByRole('link', { name: 'Ver adjunto' })).not.toBeInTheDocument();
    expect(screen.getByText('Sin adjunto')).toBeInTheDocument();
  });

  it('muestra el estado vacío cuando no hay nada pendiente', async () => {
    mockInbox({ corrections: [], justifications: [] });
    renderWithProviders(<SolicitudesAsistencia />);

    expect(await screen.findByText('Sin solicitudes pendientes')).toBeInTheDocument();
  });
});
