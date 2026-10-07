import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
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

function mockSection({ savedStatusForAna } = {}) {
  const today = localIsoDate();
  server.use(
    http.get(`${API}/program-sections/5/roster`, () => HttpResponse.json(ROSTER)),
    http.get(`${API}/program-sections/5/attendance/history`, () =>
      HttpResponse.json({
        rows: savedStatusForAna ? [{ studentId: 1, studentName: 'Ana Gómez', cellsByDate: { [today]: { status: savedStatusForAna } } }] : [],
      }),
    ),
    http.get(`${API}/program-sections/5/class-sessions/:date`, () => new HttpResponse(null, { status: 404 })),
  );
}

function rowOf(name) {
  return screen.getByText(name).closest('div.rounded-2xl');
}

describe('TabAsistencia', () => {
  it('abre en la fecha de hoy según el reloj local', async () => {
    mockSection();
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    expect(screen.getByDisplayValue(localIsoDate())).toBeInTheDocument();
  });

  it('bloquea a quien ya tiene la asistencia guardada y deja cargar al resto', async () => {
    mockSection({ savedStatusForAna: 'LATE' });
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    expect(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Ausente' })).toBeDisabled();
    expect(within(rowOf('Bruno Díaz')).getByRole('button', { name: 'Ausente' })).toBeEnabled();
    expect(screen.getByText(/pedí la modificación desde Historial/)).toBeInTheDocument();
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
      http.post(`${API}/program-sections/5/attendance`, () => HttpResponse.json({ code: 'CLASS_ON_HOLIDAY' }, { status: 400 })),
    );
    const user = userEvent.setup();
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    await user.click(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Presente' }));
    await user.click(screen.getByRole('button', { name: 'Guardar asistencia' }));

    expect(await screen.findByText('Esa fecha es feriado: no se puede tomar asistencia.')).toBeInTheDocument();
  });
});
