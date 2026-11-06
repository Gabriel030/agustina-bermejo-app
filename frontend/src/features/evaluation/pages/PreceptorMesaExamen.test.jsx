import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { PreceptorMesaExamen } from './PreceptorMesaExamen';
import { installFixedClock } from '@/test/fixedClock';

const toastMock = vi.hoisted(() => ({ error: vi.fn(), success: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

const API = 'http://localhost:8080';

globalThis.ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

function mesa(extra = {}) {
  return {
    id: 6,
    subjectId: 1,
    courseSubjectId: 2,
    courseSubjectName: 'Curso',
    termId: 1,
    tribunalInstructorIds: [],
    canceledAt: null,
    cancelReason: null,
    rescheduledFrom: null,
    subjectName: 'Didáctica General',
    examDate: '2026-09-02T18:00:00',
    status: 'SCHEDULED',
    tribunal: [],
    registrationStart: null,
    registrationEnd: null,
    classroom: 'Aula 8',
    ...extra,
  };
}

function anotado(id, status, finalScore = null) {
  return { id, studentName: `Alumno ${id}`, dni: `${id}`, carrera: 'PEI', attemptNumber: 1, status, finalScore };
}

async function abrirMesa(user, tab) {
  server.use(http.get(`${API}/instructors`, () => HttpResponse.json([])));
  if (tab) await user.click(await screen.findByRole('tab', { name: tab }));
  await user.click(await screen.findByRole('button', { name: /Buscar una mesa por materia/ }));
  await user.click(await screen.findByRole('option', { name: /Didáctica General/ }));
}

installFixedClock();

describe('PreceptorMesaExamen', () => {
  it('una mesa cerrada cuenta a quienes rindieron, traduce el estado y muestra la nota de final', async () => {
    const cerrada = mesa({ status: 'CLOSED' });
    server.use(
      http.get(`${API}/exam-boards`, ({ request }) =>
        HttpResponse.json(new URL(request.url).searchParams.get('status') === 'CLOSED' ? [cerrada] : []),
      ),
      http.get(`${API}/exam-boards/6`, () => HttpResponse.json(cerrada)),
      http.get(`${API}/exam-boards/6/registrations`, () =>
        HttpResponse.json([anotado(1, 'GRADED', 10), anotado(2, 'GRADED', 4), anotado(3, 'WITHDRAWN')]),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(<PreceptorMesaExamen />);

    await abrirMesa(user, 'Cerradas');

    const inscriptos = (await screen.findByText('Inscriptos')).parentElement;
    expect(within(inscriptos).getByText('2')).toBeInTheDocument();
    expect(screen.getAllByText('Rindió')).toHaveLength(2);
    expect(screen.queryByText('GRADED')).not.toBeInTheDocument();
    expect(screen.getByText('10')).toBeInTheDocument();
  });

  it('no deja cerrar el acta antes de la fecha del examen', async () => {
    const futura = mesa({ examDate: '2099-11-20T18:00:00' });
    server.use(
      http.get(`${API}/exam-boards`, () => HttpResponse.json([futura])),
      http.get(`${API}/exam-boards/6`, () => HttpResponse.json(futura)),
      http.get(`${API}/exam-boards/6/registrations`, () => HttpResponse.json([])),
    );
    const user = userEvent.setup();
    renderWithProviders(<PreceptorMesaExamen />);

    await abrirMesa(user);

    expect(await screen.findByRole('button', { name: 'Cerrar acta' })).toBeDisabled();
    expect(screen.getByText(/El acta se cierra después del examen/)).toBeInTheDocument();
  });

  it('avisa cuántos anotados siguen sin nota antes de confirmar el cierre del acta', async () => {
    const pasada = mesa({ examDate: '2020-01-10T18:00:00' });
    server.use(
      http.get(`${API}/exam-boards`, () => HttpResponse.json([pasada])),
      http.get(`${API}/exam-boards/6`, () => HttpResponse.json(pasada)),
      http.get(`${API}/exam-boards/6/registrations`, () =>
        HttpResponse.json([anotado(1, 'REGISTERED'), anotado(2, 'REGISTERED'), anotado(3, 'GRADED', 7)]),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(<PreceptorMesaExamen />);

    await abrirMesa(user);
    await user.click(await screen.findByRole('button', { name: 'Cerrar acta' }));

    expect(await screen.findByText(/Hay 2 alumnos anotados sin nota/)).toBeInTheDocument();
    expect(screen.getByText(/no se puede deshacer/)).toBeInTheDocument();
  });

  it('no muestra la advertencia de notas si todos los anotados tienen nota', async () => {
    const pasada = mesa({ examDate: '2020-01-10T18:00:00' });
    server.use(
      http.get(`${API}/exam-boards`, () => HttpResponse.json([pasada])),
      http.get(`${API}/exam-boards/6`, () => HttpResponse.json(pasada)),
      http.get(`${API}/exam-boards/6/registrations`, () => HttpResponse.json([anotado(3, 'GRADED', 7)])),
    );
    const user = userEvent.setup();
    renderWithProviders(<PreceptorMesaExamen />);

    await abrirMesa(user);
    await user.click(await screen.findByRole('button', { name: 'Cerrar acta' }));

    await screen.findByText('¿Cerrar el acta de esta mesa?');
    expect(screen.queryByText(/sin nota/)).not.toBeInTheDocument();
  });

  it('traduce el error al dar de baja una inscripción', async () => {
    const pasada = mesa({ examDate: '2020-01-10T18:00:00' });
    server.use(
      http.get(`${API}/exam-boards`, () => HttpResponse.json([pasada])),
      http.get(`${API}/exam-boards/6`, () => HttpResponse.json(pasada)),
      http.get(`${API}/exam-boards/6/registrations`, () => HttpResponse.json([anotado(1, 'REGISTERED')])),
      http.post(`${API}/exam-board-registrations/1/withdraw`, () =>
        HttpResponse.json(errorBody('EXAM_BOARD_REGISTRATION_NOT_WITHDRAWABLE'), { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(<PreceptorMesaExamen />);

    await abrirMesa(user);
    await user.click(await screen.findByRole('button', { name: 'Dar de baja' }));

    await vi.waitFor(() => expect(toastMock.error).toHaveBeenCalledWith('Esta inscripción ya no se puede dar de baja.'));
  });
  function mockMesa(m, anotados) {
    server.use(
      http.get(`${API}/exam-boards`, () => HttpResponse.json([m])),
      http.get(`${API}/exam-boards/6`, () => HttpResponse.json(m)),
      http.get(`${API}/exam-boards/6/registrations`, () => HttpResponse.json(anotados)),
    );
  }

  it('bloquea reprogramar y cancelar con un texto visible si algún anotado tiene nota final', async () => {
    mockMesa(mesa(), [anotado(1, 'REGISTERED', 8)]);
    const user = userEvent.setup();
    renderWithProviders(<PreceptorMesaExamen />);

    await abrirMesa(user);

    expect(await screen.findByRole('button', { name: 'Reprogramar' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Cancelar mesa' })).toBeDisabled();
    expect(screen.getByText(/no se puede reprogramar ni cancelar la mesa/)).toBeInTheDocument();
  });

  it('reprograma en dos pasos mostrando la tabla Hoy, Nuevo y Diferencia', async () => {
    mockMesa(mesa(), [anotado(1, 'REGISTERED')]);
    let enviado = null;
    server.use(
      http.post(`${API}/exam-boards/6/reschedule`, async ({ request }) => {
        enviado = await request.json();
        return HttpResponse.json(mesa());
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<PreceptorMesaExamen />);

    await abrirMesa(user);
    await user.click(await screen.findByRole('button', { name: 'Reprogramar' }));
    const dialogo = await screen.findByRole('dialog');
    expect(within(dialogo).getByRole('button', { name: 'Revisar cambios' })).toBeDisabled();

    const fecha = within(dialogo).getByLabelText('Fecha y hora del examen');
    await user.clear(fecha);
    await user.type(fecha, '2026-09-09T18:00');
    await user.click(within(dialogo).getByRole('button', { name: 'Revisar cambios' }));

    expect(within(dialogo).getByText('Hoy')).toBeInTheDocument();
    expect(within(dialogo).getByText('Nuevo')).toBeInTheDocument();
    expect(within(dialogo).getByText('Diferencia')).toBeInTheDocument();
    expect(within(dialogo).getByText('+7 días')).toBeInTheDocument();

    await user.click(within(dialogo).getByRole('button', { name: 'Confirmar reprogramación' }));

    await vi.waitFor(() => expect(enviado).toMatchObject({ examDate: '2026-09-09T18:00:00', classroom: 'Aula 8' }));
  });

  it('traduce el error de fecha en feriado al reprogramar', async () => {
    mockMesa(mesa(), []);
    server.use(
      http.post(`${API}/exam-boards/6/reschedule`, () => HttpResponse.json(errorBody('EXAM_DATE_ON_HOLIDAY'), { status: 422 })),
    );
    const user = userEvent.setup();
    renderWithProviders(<PreceptorMesaExamen />);

    await abrirMesa(user);
    await user.click(await screen.findByRole('button', { name: 'Reprogramar' }));
    const dialogo = await screen.findByRole('dialog');
    const fecha = within(dialogo).getByLabelText('Fecha y hora del examen');
    await user.clear(fecha);
    await user.type(fecha, '2026-09-09T18:00');
    await user.click(within(dialogo).getByRole('button', { name: 'Revisar cambios' }));
    await user.click(within(dialogo).getByRole('button', { name: 'Confirmar reprogramación' }));

    await vi.waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith('La fecha elegida es feriado o no es un día hábil. Elegí otra fecha.'),
    );
  });

  it('exige un motivo para cancelar y lo envía al backend', async () => {
    mockMesa(mesa(), [anotado(1, 'REGISTERED')]);
    let enviado = null;
    server.use(
      http.post(`${API}/exam-boards/6/cancel`, async ({ request }) => {
        enviado = await request.json();
        return HttpResponse.json(mesa({ status: 'CANCELED' }));
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<PreceptorMesaExamen />);

    await abrirMesa(user);
    await user.click(await screen.findByRole('button', { name: 'Cancelar mesa' }));
    const dialogo = await screen.findByRole('dialog');
    const confirmar = within(dialogo).getByRole('button', { name: 'Cancelar mesa' });
    expect(confirmar).toBeDisabled();

    await user.type(within(dialogo).getByLabelText('Motivo de la cancelación'), 'Paro docente');
    await user.click(confirmar);

    await vi.waitFor(() => expect(enviado).toEqual({ reason: 'Paro docente' }));
  });

  it('traduce el error cuando la mesa ya tiene notas al cancelar', async () => {
    mockMesa(mesa(), []);
    server.use(http.post(`${API}/exam-boards/6/cancel`, () => HttpResponse.json(errorBody('EXAM_BOARD_HAS_GRADES'), { status: 409 })));
    const user = userEvent.setup();
    renderWithProviders(<PreceptorMesaExamen />);

    await abrirMesa(user);
    await user.click(await screen.findByRole('button', { name: 'Cancelar mesa' }));
    const dialogo = await screen.findByRole('dialog');
    await user.type(within(dialogo).getByLabelText('Motivo de la cancelación'), 'Paro');
    await user.click(within(dialogo).getByRole('button', { name: 'Cancelar mesa' }));

    await vi.waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith('Hay alumnos con nota final cargada: no se puede reprogramar ni cancelar la mesa.'),
    );
  });

  it('en la pestaña Canceladas muestra la mesa como Cancelada, con su motivo y sin acciones', async () => {
    const cancelada = mesa({ status: 'CANCELED', cancelReason: 'Paro docente', canceledAt: '2026-08-20T10:00:00' });
    server.use(
      http.get(`${API}/exam-boards`, ({ request }) =>
        HttpResponse.json(new URL(request.url).searchParams.get('status') === 'CANCELED' ? [cancelada] : []),
      ),
      http.get(`${API}/exam-boards/6`, () => HttpResponse.json(cancelada)),
      http.get(`${API}/exam-boards/6/registrations`, () => HttpResponse.json([anotado(1, 'WITHDRAWN')])),
    );
    const user = userEvent.setup();
    renderWithProviders(<PreceptorMesaExamen />);

    await abrirMesa(user, 'Canceladas');

    expect(await screen.findByText('Paro docente')).toBeInTheDocument();
    expect(screen.getAllByText('Cancelada').length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: 'Reprogramar' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Cancelar mesa' })).toBeDisabled();
  });
});
