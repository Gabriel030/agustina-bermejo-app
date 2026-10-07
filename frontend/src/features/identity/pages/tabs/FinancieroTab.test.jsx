import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { FinancieroTab } from './FinancieroTab';

const API = 'http://localhost:8080';

function mockStudent({ obligations, exemptions = [] }) {
  server.use(
    http.get(`${API}/students/9/program-enrollments`, () =>
      HttpResponse.json([{ id: 4, programId: 2, programName: 'Tecnicatura', status: 'ENROLLED' }])),
    http.get(`${API}/program-enrollments/4/enrollment-fee-status`, () => HttpResponse.json(obligations)),
    http.get(`${API}/program-enrollments/4/enrollment-fee-exemptions`, () => HttpResponse.json(exemptions)),
  );
}

describe('FinancieroTab', () => {
  it('stays as coming soon for roles that cannot grant exemptions', () => {
    renderWithProviders(<FinancieroTab studentId={9} />);

    expect(screen.getByText('Disponible próximamente')).toBeInTheDocument();
  });

  it('requires a reason of at least 10 characters and posts the exemption with the fee identity', async () => {
    mockStudent({ obligations: [{ templateId: 5, slot: 1, status: 'PENDING', satisfied: false, dueDate: '2027-03-10' }] });
    let body;
    server.use(
      http.post(`${API}/program-enrollments/4/enrollment-fee-exemptions`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ id: 1 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<FinancieroTab studentId={9} puedeHabilitar />);

    await user.click(await screen.findByRole('button', { name: 'Permitir inscripción sin matrícula 1' }));
    const submit = screen.getByRole('button', { name: 'Registrar habilitación' });
    expect(submit).toBeDisabled();
    await user.type(screen.getByLabelText('Motivo'), 'corto');
    expect(submit).toBeDisabled();
    await user.type(screen.getByLabelText('Motivo'), ' y ahora sí alcanza');
    expect(submit).toBeEnabled();
    await user.click(submit);

    await waitFor(() => expect(body).toEqual({ templateId: 5, slot: 1, reason: 'corto y ahora sí alcanza' }));
  });

  it('lists who granted each exemption and allows revoking the active ones', async () => {
    mockStudent({
      obligations: [{ templateId: 5, slot: 1, status: 'EXEMPTED', satisfied: true }],
      exemptions: [
        { id: 8, slot: 1, reason: 'Plan de pago acordado', grantedByName: 'Ana Dir', grantedAt: '2027-02-01T10:00:00', active: true },
        { id: 9, slot: 1, reason: 'Anterior', grantedByName: 'Ana Dir', grantedAt: '2026-02-01T10:00:00', revokedByName: 'Ana Dir', revokedAt: '2026-03-01T10:00:00', active: false },
      ],
    });
    let revoked;
    server.use(http.post(`${API}/enrollment-fee-exemptions/8/revoke`, () => {
      revoked = true;
      return new HttpResponse(null, { status: 204 });
    }));
    const user = userEvent.setup();
    renderWithProviders(<FinancieroTab studentId={9} puedeHabilitar />);

    expect(await screen.findByText('Habilitado sin pagar')).toBeInTheDocument();
    expect(screen.getByText(/Otorgada por Ana Dir el 01\/02\/2027/)).toBeInTheDocument();
    expect(screen.getByText('Revocada')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Revocar habilitación de matrícula 1' }));

    await waitFor(() => expect(revoked).toBe(true));
  });

  it('shows an empty state when the career has no enrollment fee configured', async () => {
    mockStudent({ obligations: [] });
    renderWithProviders(<FinancieroTab studentId={9} puedeHabilitar />);

    expect(await screen.findByText('Sin matrícula configurada')).toBeInTheDocument();
  });
});
