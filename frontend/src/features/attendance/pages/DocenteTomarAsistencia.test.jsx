import { describe, expect, it, vi } from 'vitest';
import { errorBody } from '@/test/fixtures/errors';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { TabAsistencia, TabHistorial } from '@/features/attendance/pages/DocenteTomarAsistencia';
import { localIsoDate } from '@/lib/calendario';
import { installFixedClock } from '@/test/fixedClock';

const API = 'http://localhost:8080';

const ROSTER = [
  { enrollmentId: 11, studentId: 1, studentName: 'Ana Gómez', dni: '30111222', absenceCount: 0, attendancePercent: 100, atRisk: false, classesHeld: 12, maxAbsenceRate: 25, remainingAbsences: 3 },
  { enrollmentId: 12, studentId: 2, studentName: 'Bruno Díaz', dni: '30111333', absenceCount: 1, attendancePercent: 100, atRisk: false, classesHeld: 12, maxAbsenceRate: 25, remainingAbsences: 2 },
];

function mockSection({ savedStatusForAna, savedDate = localIsoDate() } = {}) {
  server.use(
    http.get(`${API}/program-sections/5/roster`, () => HttpResponse.json(ROSTER)),
    http.get(`${API}/program-sections/5/attendance/history`, () =>
      HttpResponse.json({
        rows: savedStatusForAna ? [{ studentId: 1, studentName: 'Ana Gómez', cellsByDate: { [savedDate]: { attendanceId: 77, status: savedStatusForAna, justificationDeadline: null, justifiable: false, rejection: null } } }] : [],
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
            rejectionReason: null,
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
    expect(within(rowOf('Ana Gómez')).getByText('0 de 3 faltas permitidas')).toBeInTheDocument();
  });

  it('tentative absence moves a student to the limit and then over the maximum', async () => {
    mockSection();
    const user = userEvent.setup();
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    expect(within(rowOf('Bruno Díaz')).getByText('1 de 3 faltas permitidas')).toBeInTheDocument();
    expect(within(rowOf('Bruno Díaz')).queryByText('Queda 1 falta')).not.toBeInTheDocument();

    await user.click(within(rowOf('Bruno Díaz')).getByRole('button', { name: 'Ausente' }));

    expect(within(rowOf('Bruno Díaz')).getByText('2 de 3 faltas permitidas')).toBeInTheDocument();
    expect(within(rowOf('Bruno Díaz')).getByText('Queda 1 falta')).toBeInTheDocument();
  });

  it('shows only the absence count when the backend sends no allowance', async () => {
    server.use(
      http.get(`${API}/program-sections/5/roster`, () =>
        HttpResponse.json([{ enrollmentId: 11, studentId: 1, studentName: 'Ana Gómez', dni: '30111222', absenceCount: 2, attendancePercent: 100, atRisk: false }]),
      ),
      http.get(`${API}/program-sections/5/attendance/history`, () => HttpResponse.json({ rows: [] })),
      http.get(`${API}/program-sections/5/class-sessions/:date`, () => new HttpResponse(null, { status: 404 })),
    );
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    expect(within(rowOf('Ana Gómez')).getByText('2 faltas')).toBeInTheDocument();
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
        if (calls === 1) return HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 });
        mockSection({ savedStatusForAna: 'ABSENT' });
        return new HttpResponse(null, { status: 204 });
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
    expect(await screen.findByText('✓ Asistencia guardada')).toBeInTheDocument();
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
    await vi.waitFor(() => expect(localStorage.getItem(draftKey())).toBeNull());
  });
});

describe('TabAsistencia load failure', () => {
  function failing(failedPath) {
    server.use(
      http.get(`${API}/program-sections/5/roster`, () =>
        failedPath === 'roster' ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(ROSTER),
      ),
      http.get(`${API}/program-sections/5/attendance/history`, () =>
        failedPath === 'history' ? new HttpResponse(null, { status: 500 }) : HttpResponse.json({ rows: [] }),
      ),
      http.get(`${API}/program-sections/5/class-sessions/:date`, () =>
        failedPath === 'session' ? new HttpResponse(null, { status: 500 }) : new HttpResponse(null, { status: 404 }),
      ),
    );
  }

  it.each([
    ['roster', 'No pudimos cargar los alumnos de la comisión'],
    ['history', 'No pudimos cargar la asistencia ya guardada'],
    ['session', 'No pudimos cargar los datos de la clase'],
  ])('shows a load error and no save controls when %s fails', async (failedPath, message) => {
    let posts = 0;
    failing(failedPath);
    server.use(
      http.post(`${API}/program-sections/5/attendance`, () => {
        posts += 1;
        return HttpResponse.json({});
      }),
    );
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText(message);
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText('No hay alumnos para mostrar')).not.toBeInTheDocument();
    expect(screen.queryByText('Todavía no se tomó asistencia esta fecha')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Guardar/ })).not.toBeInTheDocument();
    expect(posts).toBe(0);
  });
});

describe('TabAsistencia justification proof', () => {
  installFixedClock();

  const OPEN_DATE = '2026-11-03';
  const EXPIRED_DATE = '2026-10-20';

  function mockAbsences() {
    server.use(
      http.get(`${API}/program-sections/5/roster`, () => HttpResponse.json(ROSTER)),
      http.get(`${API}/program-sections/5/attendance/history`, () =>
        HttpResponse.json({
          rows: [
            {
              studentId: 1,
              studentName: 'Ana Gómez',
              cellsByDate: { [OPEN_DATE]: { attendanceId: 77, status: 'ABSENT', justificationDeadline: '2026-11-10', justifiable: true, rejection: null } },
            },
            {
              studentId: 2,
              studentName: 'Bruno Díaz',
              cellsByDate: { [EXPIRED_DATE]: { attendanceId: 78, status: 'ABSENT', justificationDeadline: '2026-10-27', justifiable: false, rejection: null } },
            },
          ],
        }),
      ),
      http.get(`${API}/program-sections/5/class-sessions/:date`, () => new HttpResponse(null, { status: 404 })),
    );
  }

  const PROOF_RESPONSE = {
    id: 77,
    classDate: OPEN_DATE,
    enrollmentId: 11,
    status: 'JUSTIFICATION_PENDING',
    justificationAttachmentUrl: 'https://files.test/proof.pdf',
    justificationReason: '',
    justificationRejectionReason: '',
    justifiedAt: '2026-11-05T12:00:00',
  };

  async function openRow(user, date, name) {
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);
    await screen.findByText(name);
    fireEvent.change(screen.getByDisplayValue('2026-11-05'), { target: { value: date } });
    return within(await waitFor(() => rowOf(name)));
  }

  it('offers to attach a proof on a justifiable absence and uploads it as multipart', async () => {
    mockAbsences();
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    server.use(http.post(`${API}/attendance/77/justification-proof`, () => HttpResponse.json(PROOF_RESPONSE)));
    const user = userEvent.setup();
    const row = await openRow(user, OPEN_DATE, 'Ana Gómez');
    await user.click(await row.findByRole('button', { name: 'Adjuntar comprobante' }));
    await user.upload(screen.getByLabelText('Comprobante de justificación'), new File(['pdf'], 'certificado.pdf', { type: 'application/pdf' }));
    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Enviar comprobante' })).not.toBeInTheDocument());
    const [, options] = fetchSpy.mock.calls.find(([url, opts]) => String(url).endsWith('/attendance/77/justification-proof') && opts?.method === 'POST');
    expect(options.body.get('proof').name).toBe('certificado.pdf');
  });

  it('does not offer the action after the deadline and says the preceptor resolves it', async () => {
    mockAbsences();
    const user = userEvent.setup();
    const row = await openRow(user, EXPIRED_DATE, 'Bruno Díaz');

    expect(await row.findByText('Venció el plazo para justificar: lo resuelve el preceptor.')).toBeInTheDocument();
    expect(row.queryByRole('button', { name: 'Adjuntar comprobante' })).not.toBeInTheDocument();
  });

  it('rejects a file that is not a PDF or JPG and keeps the send button disabled', async () => {
    mockAbsences();
    const user = userEvent.setup({ applyAccept: false });
    const row = await openRow(user, OPEN_DATE, 'Ana Gómez');
    await user.click(await row.findByRole('button', { name: 'Adjuntar comprobante' }));
    await user.upload(screen.getByLabelText('Comprobante de justificación'), new File(['x'], 'nota.txt', { type: 'text/plain' }));

    expect(await screen.findByText('El comprobante tiene que ser un PDF o un JPG.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Enviar comprobante' })).toBeDisabled();
  });

  it('shows the deadline message when the backend answers 409', async () => {
    mockAbsences();
    server.use(
      http.post(`${API}/attendance/77/justification-proof`, () =>
        HttpResponse.json(errorBody('JUSTIFICATION_DEADLINE_EXPIRED'), { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    const row = await openRow(user, OPEN_DATE, 'Ana Gómez');
    await user.click(await row.findByRole('button', { name: 'Adjuntar comprobante' }));
    await user.upload(screen.getByLabelText('Comprobante de justificación'), new File(['pdf'], 'certificado.pdf', { type: 'application/pdf' }));
    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));

    expect(await screen.findByText(/Venció el plazo para justificar esta falta/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Enviar comprobante' })).toBeEnabled();
  });
});

describe('TabHistorial rejected justification', () => {
  installFixedClock();

  function mockHistory() {
    const absent = { attendanceId: 77, status: 'ABSENT', justificationDeadline: '2026-11-10', justifiable: true };
    server.use(
      http.get(`${API}/program-sections/5/attendance/history`, () =>
        HttpResponse.json({
          classDates: ['2026-11-03'],
          rows: [
            { studentId: 1, studentName: 'Ana Gómez', attendancePercent: 80, cellsByDate: { '2026-11-03': { ...absent, rejection: { reason: 'Certificado ilegible', rejectedAt: '2026-11-04T10:30:00' } } } },
            { studentId: 2, studentName: 'Bruno Díaz', attendancePercent: 90, cellsByDate: { '2026-11-03': { ...absent, attendanceId: 78, rejection: null } } },
          ],
        }),
      ),
    );
  }

  it('marks the rejected cell with the reason and date, reachable by keyboard', async () => {
    mockHistory();
    renderWithProviders(<TabHistorial type="PROGRAM" sectionId={5} />);

    const cell = await screen.findByLabelText('Ausente. Justificación rechazada: Certificado ilegible (04/11/2026)');
    expect(cell).toHaveAttribute('tabindex', '0');
    expect(cell).toHaveAttribute('title', 'Justificación rechazada: Certificado ilegible (04/11/2026)');
  });

  it('shows the rejection in the cell detail and not for cells without rejection', async () => {
    mockHistory();
    const user = userEvent.setup();
    renderWithProviders(<TabHistorial type="PROGRAM" sectionId={5} />);

    await user.click(await screen.findByLabelText(/Justificación rechazada: Certificado ilegible/));
    expect(await screen.findByRole('dialog')).toHaveTextContent('Justificación rechazada: Certificado ilegible (04/11/2026)');
    await user.click(screen.getByRole('button', { name: 'Cerrar' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    await user.click(screen.getAllByTitle('Ausente')[0]);
    expect(await screen.findByRole('dialog')).not.toHaveTextContent('Justificación rechazada');
  });
});
