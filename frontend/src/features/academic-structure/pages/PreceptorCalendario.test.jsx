import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PreceptorCalendario } from './PreceptorCalendario';

const API = 'http://localhost:8080';

function mockTermsAndAvailability() {
  const requests = [];
  server.use(
    http.get(`${API}/terms`, () =>
      HttpResponse.json([
        { id: 6, description: 'Ciclo lectivo 2026' },
        { id: 7, description: 'Ciclo lectivo 2027' },
      ]),
    ),
    http.get(`${API}/terms/:termId/classrooms/availability`, ({ params, request }) => {
      requests.push({ termId: params.termId, query: new URL(request.url).searchParams });
      return HttpResponse.json([
        { id: 1, name: 'Laboratorio 2', capacity: 20, available: false },
        { id: 2, name: 'Aula 1', capacity: 30, available: true },
        { id: 3, name: 'Aula 3', capacity: 10, available: true, capacityShortfall: 5 },
        { id: 4, name: 'Aula 4', capacity: 25, available: true },
      ]);
    }),
    http.get(`${API}/classrooms/:id/occupancy`, ({ params }) =>
      HttpResponse.json({
        classroomId: Number(params.id),
        classroomName: 'Laboratorio 2',
        capacity: 20,
        termId: 7,
        from: '2027-03-01',
        to: '2027-12-01',
        slots: [],
      }),
    ),
  );
  return requests;
}

function renderCalendar(route) {
  return renderWithProviders(
    <Routes>
      <Route path="/preceptor/oferta-academica/calendario" element={<PreceptorCalendario />} />
    </Routes>,
    { route },
  );
}

async function pickModules(user, first, last = first) {
  for (let number = first; number <= last; number += 1) {
    await user.click(screen.getByRole('button', { name: new RegExp(`Módulo ${number}\\b`) }));
  }
}

describe('PreceptorCalendario', () => {
  it('asks for modules before searching and sends the range, seats and period', async () => {
    const requests = mockTermsAndAvailability();
    const user = userEvent.setup();
    renderCalendar('/preceptor/oferta-academica/calendario?termId=7');

    expect(screen.getByText('Elegí los módulos para ver qué aulas están libres.')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Cupo'), '15');
    await pickModules(user, 1, 3);

    expect(await screen.findByText('Laboratorio 2')).toBeInTheDocument();
    const last = requests[requests.length - 1];
    expect(last.termId).toBe('7');
    expect(Object.fromEntries(last.query)).toEqual({
      dayOfWeek: 'MONDAY',
      startTime: '08:00',
      endTime: '10:00',
      seats: '15',
    });
  });

  it('shows each classroom with its state and who occupies it', async () => {
    mockTermsAndAvailability();
    const user = userEvent.setup();
    renderCalendar('/preceptor/oferta-academica/calendario?termId=7');
    await pickModules(user, 1, 3);

    await screen.findByText('Laboratorio 2');
    expect(screen.getByText('Ocupada')).toBeInTheDocument();
    expect(screen.getAllByText('Libre')).toHaveLength(2);
    expect(screen.getByText('Capacidad menor')).toBeInTheDocument();
    expect(screen.getByText('Faltan 5 lugares para el cupo.')).toBeInTheDocument();
  });

  it('deselects a module that is touched again', async () => {
    mockTermsAndAvailability();
    const user = userEvent.setup();
    renderCalendar('/preceptor/oferta-academica/calendario?termId=7');

    await pickModules(user, 1);
    const module = screen.getByRole('button', { name: /Módulo 1\b/ });
    expect(module).toHaveAttribute('aria-pressed', 'true');
    await user.click(module);
    expect(module).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByText('Elegí los módulos para ver qué aulas están libres.')).toBeInTheDocument();
  });

  it('shows the week of a classroom and goes back to the list', async () => {
    mockTermsAndAvailability();
    const user = userEvent.setup();
    renderCalendar('/preceptor/oferta-academica/calendario?termId=7');
    await pickModules(user, 1, 3);
    await screen.findByText('Laboratorio 2');

    const labRow = screen.getByText('Laboratorio 2').closest('li');
    await user.click(within(labRow).getByRole('button', { name: /Ver la semana/ }));

    expect(await screen.findByText('Semana de Laboratorio 2')).toBeInTheDocument();
    expect(await screen.findAllByText('Sin clases.')).toHaveLength(6);
    await user.click(screen.getByRole('button', { name: /Volver a la lista/ }));
    expect(screen.getByText('Aula 1')).toBeInTheDocument();
  });

  it('shows an error when the availability check fails', async () => {
    mockTermsAndAvailability();
    server.use(
      http.get(`${API}/terms/:termId/classrooms/availability`, () =>
        HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 }),
      ),
    );
    const user = userEvent.setup();
    renderCalendar('/preceptor/oferta-academica/calendario?termId=7');
    await pickModules(user, 1, 3);

    expect(await screen.findByText('No pudimos revisar la disponibilidad. Probá de nuevo.')).toBeInTheDocument();
  });

  it('lets the user choose a period when none is given', async () => {
    mockTermsAndAvailability();
    const user = userEvent.setup();
    renderCalendar('/preceptor/oferta-academica/calendario');

    expect(screen.getByText('Seleccioná un período para ver disponibilidad.')).toBeInTheDocument();
    const periodSelect = await screen.findByLabelText('Período');
    await screen.findByRole('option', { name: 'Ciclo lectivo 2026' });
    await user.selectOptions(periodSelect, '6');

    expect(await screen.findByRole('button', { name: /Módulo 1\b/ })).toBeInTheDocument();
  });
});
