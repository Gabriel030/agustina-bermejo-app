import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { PreceptorMesaExamen } from './PreceptorMesaExamen';

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
});
