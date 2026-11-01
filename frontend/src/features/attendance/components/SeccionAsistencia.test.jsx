import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { SeccionAsistencia } from '@/features/attendance/components/SeccionAsistencia';

const API = 'http://localhost:8080';

const REJECTED_ABSENCE = {
  id: 10,
  enrollmentId: 5,
  justifiedAt: '2026-04-02T10:00:00Z',
  classDate: '2026-04-01',
  status: 'ABSENT',
  justificationReason: 'Fiebre',
  justificationAttachmentUrl: 'students/1/x.pdf',
  justificationRejectionReason: 'El certificado es ilegible',
};

function mockAttendance(records) {
  server.use(
    http.get(`${API}/me/section-enrollments/5/attendance`, () => HttpResponse.json(records)),
    http.get(`${API}/me/section-enrollments/5/attendance/risk`, () =>
      HttpResponse.json({ calculable: false, libre: false, absencePercent: { fraction: 0.5 }, maxAbsencePercent: { fraction: 0.25 } }),
    ),
  );
}

describe('SeccionAsistencia', () => {
  it('shows the rejection reason when the student opens a rejected absence and lets them justify again', async () => {
    mockAttendance([REJECTED_ABSENCE]);
    renderWithProviders(<SeccionAsistencia sectionEnrollmentId={5} />);

    await userEvent.click(await screen.findByRole('button', { name: '1' }));

    expect(await screen.findByText('Volver a justificar')).toBeInTheDocument();
    expect(screen.getAllByText('El certificado es ilegible').length).toBeGreaterThan(0);
    expect(screen.getByText('Subir comprobante')).toBeInTheDocument();
  });

  it('lists the rejected absence with its reason', async () => {
    mockAttendance([REJECTED_ABSENCE]);
    renderWithProviders(<SeccionAsistencia sectionEnrollmentId={5} />);

    expect(await screen.findByText('Rechazada')).toBeInTheDocument();
    expect(screen.getByText('Motivo del rechazo: El certificado es ilegible')).toBeInTheDocument();
  });

  it('asks for a plain justification when the absence was never rejected', async () => {
    mockAttendance([{ ...REJECTED_ABSENCE, justificationRejectionReason: '' }]);
    renderWithProviders(<SeccionAsistencia sectionEnrollmentId={5} />);

    await userEvent.click(await screen.findByRole('button', { name: '1' }));

    expect(await screen.findByText('Justificar inasistencia')).toBeInTheDocument();
    expect(screen.queryByText('Volver a justificar')).not.toBeInTheDocument();
  });
});
