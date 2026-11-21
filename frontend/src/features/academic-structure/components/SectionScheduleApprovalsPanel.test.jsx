import { beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { SectionScheduleApprovalsPanel } from '@/features/academic-structure/components/SectionScheduleApprovalsPanel';

const API = 'http://localhost:8080';
const SECTION_TITLE = 'Matemática I · Comisión A';

const pendingRequest = {
  sectionId: 7,
  sectionName: 'Comisión A',
  subjectName: 'Matemática I',
  offeringName: 'Primer año 2026',
  cycleId: 3,
  requestedByName: 'Lucía Gómez',
  requestedAt: new Date().toISOString(),
  replacesSchedule: true,
  currentSlots: [{ classroomId: 1, classroomName: 'Aula 2', dayOfWeek: 'MONDAY', startTime: '08:00:00', endTime: '10:00:00' }],
  proposedSlots: [{ classroomId: 2, classroomName: 'Aula 5', dayOfWeek: 'WEDNESDAY', startTime: '14:00:00', endTime: '16:00:00' }],
};

const LONG_REASON = 'El aula propuesta ya está reservada';

function pendingWith(list) {
  server.use(http.get(`${API}/section-modification-requests/pending`, () => HttpResponse.json(list)));
}

describe('SectionScheduleApprovalsPanel', () => {
  beforeEach(() => {
    server.use(
      http.post(`${API}/subject-sections/:id/modification-request/preview`, () => HttpResponse.json({ conflicts: [] })),
    );
  });

  it('shows the current and the proposed schedule of each pending request', async () => {
    pendingWith([pendingRequest]);
    renderWithProviders(<SectionScheduleApprovalsPanel />);

    expect(await screen.findByText(SECTION_TITLE)).toBeInTheDocument();
    expect(screen.getByText(/08:00–10:00/)).toBeInTheDocument();
    expect(screen.getByText(/14:00–16:00/)).toBeInTheDocument();
    expect(screen.getByText('Reemplaza el horario')).toBeInTheDocument();
    expect(screen.getByText('1 pendiente')).toBeInTheDocument();
  });

  it('says there is nothing pending when the list is empty', async () => {
    pendingWith([]);
    renderWithProviders(<SectionScheduleApprovalsPanel />);

    expect(await screen.findByText('Ningún cambio de horario esperando tu decisión')).toBeInTheDocument();
  });

  it('shows an error instead of the empty state when the list fails to load', async () => {
    server.use(http.get(`${API}/section-modification-requests/pending`, () => HttpResponse.json(errorBody('INTERNAL'), { status: 500 })));
    renderWithProviders(<SectionScheduleApprovalsPanel />);

    expect(await screen.findByText('No pudimos cargar los cambios de horario')).toBeInTheDocument();
    expect(screen.queryByText('Ningún cambio de horario esperando tu decisión')).not.toBeInTheDocument();
  });

  it('shows the conflicts of the proposed schedule before approving', async () => {
    pendingWith([pendingRequest]);
    server.use(
      http.post(`${API}/subject-sections/:id/modification-request/preview`, () =>
        HttpResponse.json({
          conflicts: [{ type: 'INSTRUCTOR_BUSY', conflictingSectionLabel: 'Álgebra · Comisión B', slotIndex: 0 }],
        }),
      ),
    );
    renderWithProviders(<SectionScheduleApprovalsPanel />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: `Aprobar el horario de ${SECTION_TITLE}` }));

    expect(await screen.findByText('El docente tiene otra clase en ese horario · Álgebra · Comisión B · franja 1')).toBeInTheDocument();
  });

  it('approves only after confirming and removes the request from the list', async () => {
    let list = [pendingRequest];
    const approvedSectionIds = [];
    server.use(
      http.get(`${API}/section-modification-requests/pending`, () => HttpResponse.json(list)),
      http.post(`${API}/subject-sections/:id/modification-request/approve`, ({ params }) => {
        approvedSectionIds.push(Number(params.id));
        list = [];
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(<SectionScheduleApprovalsPanel />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: `Aprobar el horario de ${SECTION_TITLE}` }));
    expect(approvedSectionIds).toHaveLength(0);
    await user.click(await screen.findByRole('button', { name: 'Confirmar aprobación' }));

    await waitFor(() => expect(approvedSectionIds).toEqual([7]));
    expect(await screen.findByText('Ningún cambio de horario esperando tu decisión')).toBeInTheDocument();
  });

  it('does not reject without a reason of at least the minimum length', async () => {
    pendingWith([pendingRequest]);
    renderWithProviders(<SectionScheduleApprovalsPanel />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: `Rechazar el horario de ${SECTION_TITLE}` }));
    const confirm = await screen.findByRole('button', { name: 'Rechazar horario' });

    expect(confirm).toBeDisabled();
    await user.type(screen.getByPlaceholderText('Motivo del rechazo'), 'corto');
    expect(confirm).toBeDisabled();
  });

  it('sends the rejection reason to the reject endpoint', async () => {
    let list = [pendingRequest];
    const rejections = [];
    server.use(
      http.get(`${API}/section-modification-requests/pending`, () => HttpResponse.json(list)),
      http.post(`${API}/subject-sections/:id/modification-request/reject`, async ({ params, request }) => {
        rejections.push({ id: Number(params.id), body: await request.json() });
        list = [];
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(<SectionScheduleApprovalsPanel />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: `Rechazar el horario de ${SECTION_TITLE}` }));
    await user.type(screen.getByPlaceholderText('Motivo del rechazo'), LONG_REASON);
    await user.click(screen.getByRole('button', { name: 'Rechazar horario' }));

    await waitFor(() => expect(rejections).toEqual([{ id: 7, body: { reason: LONG_REASON } }]));
  });

  it('shows the domain message when the cycle was already closed', async () => {
    pendingWith([pendingRequest]);
    server.use(
      http.post(`${API}/subject-sections/:id/modification-request/approve`, () =>
        HttpResponse.json(errorBody('SECTION_MODIFICATION_CYCLE_CLOSED'), { status: 409 }),
      ),
    );
    renderWithProviders(<SectionScheduleApprovalsPanel />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: `Aprobar el horario de ${SECTION_TITLE}` }));
    await user.click(await screen.findByRole('button', { name: 'Confirmar aprobación' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('El ciclo ya cerró y no se puede modificar el horario de sus comisiones.');
  });
});
