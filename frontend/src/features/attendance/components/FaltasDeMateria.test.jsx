import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { createDeferred } from '@/test/deferred';
import { errorBody } from '@/test/fixtures/errors';
import { renderWithProviders } from '@/test/renderWithProviders';
import { FaltasDeMateria } from './FaltasDeMateria';

const API = 'http://localhost:8080';
const toastMock = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn() }));
vi.mock('sonner', () => ({ toast: toastMock }));

function entry(overrides) {
  return {
    id: 1,
    enrollmentId: 11,
    classDate: '2026-11-05',
    status: 'ABSENT',
    justificationAttachmentUrl: '',
    justificationReason: '',
    justificationRejectionReason: '',
    justifiedAt: '2026-10-01T08:00:00',
    justificationDeadline: '2026-11-12',
    justifiable: true,
    ...overrides,
  };
}

const ENTRIES = [
  entry({ id: 41 }),
  entry({ id: 40, classDate: '2026-10-29', justificationDeadline: '2026-11-05', justifiable: false }),
  entry({ id: 39, classDate: '2026-10-22', status: 'JUSTIFICATION_PENDING', justificationDeadline: '2026-10-29', justifiable: false }),
  entry({ id: 38, classDate: '2026-10-15', status: 'JUSTIFIED', justifiedAt: '2026-10-16T10:00:00', justificationDeadline: null, justifiable: null }),
  entry({ id: 37, classDate: '2026-10-08', justificationRejectionReason: 'El certificado no tiene fecha ni sello.', justificationDeadline: '2026-10-15', justifiable: false }),
  entry({ id: 36, classDate: '2026-10-01', status: 'PRESENT', justificationDeadline: null, justifiable: null }),
];

function mockAttendance(entries = ENTRIES) {
  server.use(http.get(`${API}/section-enrollments/11/attendance`, () => HttpResponse.json(entries)));
}

function renderFaltas(props = {}) {
  return renderWithProviders(
    <FaltasDeMateria
      sectionEnrollmentId={11}
      studentName="Valentina Suárez"
      subjectLabel="Historia del Arte · Com. A"
      canJustify
      authorName="Ana Ruiz"
      roleBase="preceptor"
      {...props}
    />,
  );
}

function rowOf(date) {
  return screen.getByText(date).closest('tr');
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('FaltasDeMateria', () => {
  it('shows one row per absence with its status and deadline, and skips attendances that are not absences', async () => {
    mockAttendance();
    renderFaltas();

    await screen.findByText('05/11/2026');
    const open = rowOf('05/11/2026');
    expect(within(open).getByText('Ausente')).toBeInTheDocument();
    expect(within(open).getByText('Dentro de plazo')).toBeInTheDocument();
    expect(within(open).getByText('hasta 12/11/2026')).toBeInTheDocument();
    const expired = rowOf('29/10/2026');
    expect(within(expired).getByText('Plazo vencido')).toBeInTheDocument();
    expect(within(expired).getByText('el 05/11/2026')).toBeInTheDocument();
    expect(within(rowOf('22/10/2026')).getByText('Justificación pendiente')).toBeInTheDocument();
    const justified = rowOf('15/10/2026');
    expect(within(justified).getByText('Justificada')).toBeInTheDocument();
    expect(within(justified).getByText('Resuelta el 16/10/2026')).toBeInTheDocument();
    const rejected = rowOf('08/10/2026');
    expect(within(rejected).getByText('Justificación rechazada')).toBeInTheDocument();
    expect(within(rejected).getByText('Motivo del rechazo: El certificado no tiene fecha ni sello.')).toBeInTheDocument();
    expect(screen.queryByText('01/10/2026')).not.toBeInTheDocument();
  });

  it('offers the justify action only on absences and links pending proofs to the requests inbox', async () => {
    mockAttendance();
    renderFaltas();

    await screen.findByText('05/11/2026');
    expect(within(rowOf('05/11/2026')).getByRole('button', { name: 'Justificar la falta del 05/11/2026 de Valentina Suárez' })).toBeInTheDocument();
    expect(within(rowOf('29/10/2026')).getByRole('button', { name: /Justificar la falta del 29\/10\/2026/ })).toBeInTheDocument();
    const link = within(rowOf('22/10/2026')).getByRole('link', { name: 'Revisar solicitud' });
    expect(link).toHaveAttribute('href', '/preceptor/solicitudes-de-asistencia?tab=justificaciones');
    expect(within(rowOf('15/10/2026')).queryByRole('button')).not.toBeInTheDocument();
    expect(within(rowOf('15/10/2026')).getByText('Sin acción')).toBeInTheDocument();
  });

  it('is read only when the viewer cannot justify', async () => {
    mockAttendance();
    renderFaltas({ canJustify: false });

    await screen.findByText('05/11/2026');
    expect(screen.queryByRole('button', { name: /Justificar la falta/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Revisar solicitud' })).not.toBeInTheDocument();
    expect(screen.getAllByText('Solo lectura')).toHaveLength(5);
  });

  it('shows an empty message when the student has no absences', async () => {
    mockAttendance([entry({ id: 1, status: 'PRESENT', justifiable: null, justificationDeadline: null })]);
    renderFaltas();

    expect(await screen.findByText('Sin faltas registradas.')).toBeInTheDocument();
  });

  it('shows a loading state and then a retryable error', async () => {
    const pending = createDeferred();
    let calls = 0;
    server.use(
      http.get(`${API}/section-enrollments/11/attendance`, async () => {
        calls += 1;
        if (calls === 1) {
          await pending.promise;
          return HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 });
        }
        return HttpResponse.json(ENTRIES);
      }),
    );
    const user = userEvent.setup();
    renderFaltas();

    expect(screen.getByLabelText('Cargando faltas')).toBeInTheDocument();
    pending.resolve();
    await user.click(await screen.findByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('05/11/2026')).toBeInTheDocument();
  });
});

describe('JustificarFaltaDialog', () => {
  async function openDialog(user, date) {
    await screen.findByText(date);
    await user.click(within(rowOf(date)).getByRole('button', { name: /Justificar la falta/ }));
    return screen.findByRole('dialog');
  }

  it('justifies an absence inside the deadline with only the reason and rereads the list', async () => {
    let attendanceCalls = 0;
    let body;
    server.use(
      http.get(`${API}/section-enrollments/11/attendance`, () => {
        attendanceCalls += 1;
        return HttpResponse.json(attendanceCalls === 1 ? ENTRIES : ENTRIES.map((e) => (e.id === 41 ? { ...e, status: 'JUSTIFIED', justifiedAt: '2026-11-06T09:00:00', justifiable: null, justificationDeadline: null } : e)));
      }),
      http.post(`${API}/attendance/41/justify`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...ENTRIES[0], status: 'JUSTIFIED', justificationDeadline: null, justifiable: null });
      }),
    );
    const user = userEvent.setup();
    renderFaltas();
    const dialog = await openDialog(user, '05/11/2026');

    expect(within(dialog).queryByLabelText('Motivo de la excepción')).not.toBeInTheDocument();
    expect(within(dialog).getByText('Dentro de plazo')).toBeInTheDocument();
    expect(within(dialog).getByText('Queda registrado a nombre de Ana Ruiz.')).toBeInTheDocument();
    expect(within(dialog).getByText('No se puede deshacer desde esta pantalla.')).toBeInTheDocument();
    const faltas = within(dialog).getByRole('group', { name: 'Faltas computadas (materia)' });
    expect(within(faltas).getByText('4')).toBeInTheDocument();
    expect(within(faltas).getByText('3')).toBeInTheDocument();
    expect(within(faltas).getByText('−1')).toBeInTheDocument();
    const submit = within(dialog).getByRole('button', { name: 'Justificar falta' });
    expect(submit).toBeDisabled();

    await user.type(within(dialog).getByLabelText('Motivo'), 'Certificado médico');
    await user.click(submit);

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(body).toEqual({ reason: 'Certificado médico' });
    expect(toastMock.success).toHaveBeenCalledWith('Falta del 05/11/2026 justificada');
    await waitFor(() => expect(within(rowOf('05/11/2026')).getByText('Justificada')).toBeInTheDocument());
  });

  it('requires the exception reason when the deadline expired and sends it', async () => {
    let body;
    mockAttendance();
    server.use(
      http.post(`${API}/attendance/40/justify`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...ENTRIES[1], status: 'JUSTIFIED', justificationDeadline: null, justifiable: null });
      }),
    );
    const user = userEvent.setup();
    renderFaltas();
    const dialog = await openDialog(user, '29/10/2026');

    expect(within(dialog).getByText(/Plazo vencido el 05\/11\/2026/)).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Motivo'), 'Internación');
    const submit = within(dialog).getByRole('button', { name: 'Justificar falta' });
    expect(submit).toBeDisabled();
    await user.type(within(dialog).getByLabelText('Motivo de la excepción'), 'Entregó el certificado tarde');
    expect(submit).toBeEnabled();
    await user.click(submit);

    await waitFor(() => expect(body).toEqual({ reason: 'Internación', exceptionReason: 'Entregó el certificado tarde' }));
  });

  it('shares one 500 character budget between both reasons and blocks the submit past it', async () => {
    mockAttendance();
    const user = userEvent.setup();
    renderFaltas();
    const dialog = await openDialog(user, '29/10/2026');
    const reason = within(dialog).getByLabelText('Motivo');
    const exception = within(dialog).getByLabelText('Motivo de la excepción');

    await user.click(reason);
    await user.paste('a'.repeat(300));
    await user.click(exception);
    await user.paste('b'.repeat(200));
    expect(within(dialog).getByText('500 de 500 caracteres')).toBeInTheDocument();
    expect(within(dialog).getByText('Quedan 0')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Justificar falta' })).toBeEnabled();

    await user.type(exception, 'c');
    expect(within(dialog).getByText('Te pasaste 1')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Justificar falta' })).toBeDisabled();
  });

  it('disables the button while justifying and keeps the dialog open with a Spanish message on a 400', async () => {
    const pending = createDeferred();
    mockAttendance();
    server.use(
      http.post(`${API}/attendance/41/justify`, async () => {
        await pending.promise;
        return HttpResponse.json(errorBody('INVALID_JUSTIFICATION_INPUT'), { status: 400 });
      }),
    );
    const user = userEvent.setup();
    renderFaltas();
    const dialog = await openDialog(user, '05/11/2026');
    await user.type(within(dialog).getByLabelText('Motivo'), 'Gripe');
    await user.click(within(dialog).getByRole('button', { name: 'Justificar falta' }));

    expect(await within(dialog).findByRole('button', { name: 'Justificando…' })).toBeDisabled();
    pending.resolve();

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('El motivo de la justificación supera los 500 caracteres. Acortalo e intentá de nuevo.');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Justificar falta' })).toBeEnabled();
  });

  it('closes the dialog, warns and rereads the list on a 409 because the absence changed', async () => {
    let attendanceCalls = 0;
    server.use(
      http.get(`${API}/section-enrollments/11/attendance`, () => {
        attendanceCalls += 1;
        return HttpResponse.json(attendanceCalls === 1 ? ENTRIES : ENTRIES.map((e) => (e.id === 41 ? { ...e, status: 'JUSTIFICATION_PENDING', justifiable: false } : e)));
      }),
      http.post(`${API}/attendance/41/justify`, () => HttpResponse.json(errorBody('CANNOT_JUSTIFY'), { status: 409 })),
    );
    const user = userEvent.setup();
    renderFaltas();
    const dialog = await openDialog(user, '05/11/2026');
    await user.type(within(dialog).getByLabelText('Motivo'), 'Gripe');
    await user.click(within(dialog).getByRole('button', { name: 'Justificar falta' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(toastMock.warning).toHaveBeenCalledWith('Esta falta ya cambió de estado. Actualizamos la lista.');
    await waitFor(() => expect(within(rowOf('05/11/2026')).getByRole('link', { name: 'Revisar solicitud' })).toBeInTheDocument());
  });

  it('shows a server failure inside the dialog without closing it', async () => {
    mockAttendance();
    server.use(http.post(`${API}/attendance/41/justify`, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })));
    const user = userEvent.setup();
    renderFaltas();
    const dialog = await openDialog(user, '05/11/2026');
    await user.type(within(dialog).getByLabelText('Motivo'), 'Gripe');
    await user.click(within(dialog).getByRole('button', { name: 'Justificar falta' }));

    expect(await within(dialog).findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('cancels without calling the backend', async () => {
    mockAttendance();
    const user = userEvent.setup();
    renderFaltas();
    const dialog = await openDialog(user, '05/11/2026');

    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
