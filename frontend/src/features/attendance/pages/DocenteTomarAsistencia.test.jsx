import { describe, expect, it, vi } from 'vitest';
import { errorBody } from '@/test/fixtures/errors';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { TabAsistencia } from '@/features/attendance/pages/DocenteTomarAsistencia';
import { localIsoDate } from '@/lib/calendario';

const API = 'http://localhost:8080';

const ROSTER = [
  { enrollmentId: 11, studentId: 1, studentName: 'Ana Gómez', dni: '30111222', absenceCount: 0, atRisk: false },
  { enrollmentId: 12, studentId: 2, studentName: 'Bruno Díaz', dni: '30111333', absenceCount: 1, atRisk: false },
];

function mockSection({ savedStatusForAna, savedDate = localIsoDate() } = {}) {
  server.use(
    http.get(`${API}/program-sections/5/roster`, () => HttpResponse.json(ROSTER)),
    http.get(`${API}/program-sections/5/attendance/history`, () =>
      HttpResponse.json({
        rows: savedStatusForAna ? [{ studentId: 1, studentName: 'Ana Gómez', cellsByDate: { [savedDate]: { attendanceId: 77, status: savedStatusForAna } } }] : [],
      }),
    ),
    http.get(`${API}/program-sections/5/class-sessions/:date`, () => new HttpResponse(null, { status: 404 })),
  );
}

function rowOf(name) {
  return screen.getByText(name).closest('div.rounded-2xl');
}

const draftKey = () => `attendanceDraft:PROGRAM:5:${localIsoDate()}`;


describe('TabAsistencia', () => {
  it('abre en la fecha de hoy según el reloj local', async () => {
    mockSection();
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    expect(screen.getByDisplayValue(localIsoDate())).toBeInTheDocument();
  });

  it('en un dia anterior bloquea a quien ya tiene la asistencia guardada y deja cargar al resto', async () => {
    const pastDate = '2020-01-06';
    mockSection({ savedStatusForAna: 'LATE', savedDate: pastDate });
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    fireEvent.change(screen.getByDisplayValue(localIsoDate()), { target: { value: pastDate } });
    await screen.findByText(/pedí la modificación desde Historial/);
    expect(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Ausente' })).toBeDisabled();
    expect(within(rowOf('Bruno Díaz')).getByRole('button', { name: 'Ausente' })).toBeEnabled();
  });

  it('el mismo dia permite corregir lo guardado y avisa que se puede corregir', async () => {
    mockSection({ savedStatusForAna: 'LATE' });
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    expect(screen.getByText('Podés corregir la asistencia de hoy.')).toBeInTheDocument();
    expect(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Ausente' })).toBeEnabled();
  });

  it('el mismo dia corregir manda el nuevo estado y no ofrece pedir modificacion', async () => {
    mockSection({ savedStatusForAna: 'LATE' });
    let body = null;
    server.use(
      http.post(`${API}/program-sections/5/attendance`, async ({ request }) => {
        body = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    expect(within(rowOf('Ana Gómez')).queryByRole('button', { name: 'Pedir modificación' })).not.toBeInTheDocument();
    await user.click(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Ausente' }));
    await user.click(screen.getByRole('button', { name: 'Guardar asistencia' }));

    await waitFor(() => expect(body).not.toBeNull());
    expect(body.entries).toEqual(expect.arrayContaining([{ enrollmentId: 11, status: 'ABSENT' }]));
  });

  it('en un dia anterior ofrece pedir modificacion y envia la solicitud', async () => {
    const pastDate = '2020-01-06';
    mockSection({ savedStatusForAna: 'LATE', savedDate: pastDate });
    let body = null;
    server.use(
      http.post(`${API}/attendance/77/change-requests`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(
          {
            id: 1,
            attendanceId: 77,
            classDate: pastDate,
            createdAt: '2020-01-07T10:00:00',
            previousStatus: 'LATE',
            reason: 'Se equivocó el docente',
            requestedByName: 'Docente',
            requestedStatus: 'PRESENT',
            sectionName: 'Comisión',
            status: 'PENDING',
            studentName: 'Ana Gómez',
            subjectName: 'Materia',
          },
          { status: 201 },
        );
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    fireEvent.change(screen.getByDisplayValue(localIsoDate()), { target: { value: pastDate } });
    await user.click(await within(rowOf('Ana Gómez')).findByRole('button', { name: 'Pedir modificación' }));
    await user.type(screen.getByPlaceholderText('Motivo de la modificación...'), 'Se equivocó el docente');
    await user.click(screen.getByRole('button', { name: 'Enviar solicitud' }));

    await waitFor(() => expect(body).toEqual({ requestedStatus: 'PRESENT', reason: 'Se equivocó el docente' }));
    await waitFor(() => expect(screen.queryByText('Solicitar modificación de asistencia')).not.toBeInTheDocument());
  });

  it('muestra el error cuando el backend rechaza la solicitud de modificacion', async () => {
    const pastDate = '2020-01-06';
    mockSection({ savedStatusForAna: 'LATE', savedDate: pastDate });
    server.use(
      http.post(`${API}/attendance/77/change-requests`, () => HttpResponse.json(errorBody('FORBIDDEN'), { status: 403 })),
    );
    const user = userEvent.setup();
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    fireEvent.change(screen.getByDisplayValue(localIsoDate()), { target: { value: pastDate } });
    await user.click(await within(rowOf('Ana Gómez')).findByRole('button', { name: 'Pedir modificación' }));
    await user.type(screen.getByPlaceholderText('Motivo de la modificación...'), 'Motivo');
    await user.click(screen.getByRole('button', { name: 'Enviar solicitud' }));

    expect(await screen.findByText('No tenés permiso para pedir la modificación en esta comisión.')).toBeInTheDocument();
  });

  it('si el backend rechaza la correccion por codigo muestra el mensaje y conserva el cambio', async () => {
    mockSection({ savedStatusForAna: 'LATE' });
    server.use(
      http.post(`${API}/program-sections/5/attendance`, () => HttpResponse.json(errorBody('ATTENDANCE_ALREADY_RECORDED'), { status: 409 })),
    );
    const user = userEvent.setup();
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    await user.click(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Ausente' }));
    await user.click(screen.getByRole('button', { name: 'Guardar asistencia' }));

    expect(await screen.findByText(/Si la registró otro docente o ya pasó el día/)).toBeInTheDocument();
    expect(screen.getByText('● Cambios sin guardar')).toBeInTheDocument();
  });

  it('conserva el borrador local de una correccion de hoy al recargar', async () => {
    mockSection({ savedStatusForAna: 'LATE' });
    const user = userEvent.setup();
    const first = renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    await user.click(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Ausente' }));
    expect(localStorage.getItem(draftKey())).not.toBeNull();
    first.unmount();

    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);
    await screen.findByText('Ana Gómez');
    expect(await screen.findByText('Recuperamos tu asistencia sin enviar.')).toBeInTheDocument();
  });

  it('el mismo dia una asistencia justificada sigue bloqueada', async () => {
    mockSection({ savedStatusForAna: 'JUSTIFIED' });
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    expect(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Ausente' })).toBeDisabled();
  });

  it('el docente no puede marcar Justificada: eso lo resuelve el circuito de justificación', async () => {
    mockSection();
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    expect(within(rowOf('Ana Gómez')).queryByRole('button', { name: 'Justificada' })).not.toBeInTheDocument();
    expect(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Ausente' })).toBeInTheDocument();
  });

  it('una inasistencia justificada no suma al contador de inasistencias', async () => {
    mockSection({ savedStatusForAna: 'JUSTIFIED' });
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    expect(within(rowOf('Ana Gómez')).getByText('0 inasist.')).toBeInTheDocument();
  });

  it('si la fecha es feriado lo dice en vez de un error genérico', async () => {
    mockSection();
    server.use(
      http.post(`${API}/program-sections/5/attendance`, () => HttpResponse.json(errorBody('CLASS_ON_HOLIDAY'), { status: 400 })),
    );
    const user = userEvent.setup();
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    await user.click(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Presente' }));
    await user.click(screen.getByRole('button', { name: 'Guardar asistencia' }));

    expect(await screen.findByText('Esa fecha es feriado: no se puede tomar asistencia.')).toBeInTheDocument();
  });
  it('keeps what was loaded when the submit fails, tells the cause and allows retrying', async () => {
    mockSection();
    let calls = 0;
    server.use(
      http.post(`${API}/program-sections/5/attendance`, () => {
        calls += 1;
        return calls === 1 ? HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 }) : new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    await user.click(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Ausente' }));
    await user.click(screen.getByRole('button', { name: 'Guardar asistencia' }));

    expect(await screen.findByText(/El servicio no está disponible por ahora/)).toBeInTheDocument();
    expect(screen.getByText('1 / 2 procesados')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Guardar asistencia' })).toBeEnabled();

    await user.click(screen.getByRole('button', { name: 'Guardar asistencia' }));
    await screen.findByText('✓ Asistencia guardada', {}, { timeout: 2000 }).catch(() => null);
    expect(calls).toBe(2);
    expect(localStorage.getItem(draftKey())).toBeNull();
  });

  it('stores a local draft and recovers it after reloading', async () => {
    mockSection();
    const user = userEvent.setup();
    const first = renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    await user.click(within(rowOf('Bruno Díaz')).getByRole('button', { name: 'Tarde' }));
    expect(localStorage.getItem(draftKey())).not.toBeNull();
    first.unmount();

    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);
    await screen.findByText('Ana Gómez');
    expect(await screen.findByText('Recuperamos tu asistencia sin enviar.')).toBeInTheDocument();
    expect(screen.getByText('1 / 2 procesados')).toBeInTheDocument();
    expect(screen.getByText('● Cambios sin guardar')).toBeInTheDocument();
  });

  it('does not recover an expired draft', async () => {
    mockSection();
    localStorage.setItem(
      draftKey(),
      JSON.stringify({ estados: { 12: 'LATE' }, topic: '', observations: '', savedAt: Date.now() - 13 * 60 * 60 * 1000 }),
    );
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    expect(screen.queryByText('Recuperamos tu asistencia sin enviar.')).not.toBeInTheDocument();
    expect(screen.getByText('0 / 2 procesados')).toBeInTheDocument();
  });

  it('clears the draft after a successful submit', async () => {
    mockSection();
    server.use(http.post(`${API}/program-sections/5/attendance`, () => new HttpResponse(null, { status: 204 })));
    const user = userEvent.setup();
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    await user.click(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Presente' }));
    expect(localStorage.getItem(draftKey())).not.toBeNull();
    await user.click(screen.getByRole('button', { name: 'Guardar asistencia' }));
    await screen.findByText('Todavía no se tomó asistencia esta fecha').catch(() => null);
    await vi.waitFor(() => expect(localStorage.getItem(draftKey())).toBeNull());
  });
});
