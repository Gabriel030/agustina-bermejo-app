import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { MisSolicitudes } from '@/pages/MisSolicitudes';

const API = 'http://localhost:8080';

function DetalleFake() {
  const { id } = useParams();
  return <div>Detalle de la solicitud {id}</div>;
}

function renderMisSolicitudes() {
  return render(
    <MemoryRouter initialEntries={['/mis-solicitudes']}>
      <Routes>
        <Route path="/mis-solicitudes" element={<MisSolicitudes />} />
        <Route path="/mis-solicitudes/:id" element={<DetalleFake />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('MisSolicitudes', () => {
  it('muestra el estado vacío cuando no hay solicitudes', async () => {
    server.use(http.get(`${API}/me/program-enrollment-applications`, () => HttpResponse.json([])));
    renderMisSolicitudes();

    expect(await screen.findByText(/todavía no pediste el alta/i)).toBeInTheDocument();
  });

  it('lista las solicitudes con su carrera y estado', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([
          { id: 1, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' },
          { id: 2, programName: 'Programación Web', status: 'APPROVED', requestedAt: '2026-02-18T10:00:00' },
        ]),
      ),
    );
    renderMisSolicitudes();

    expect(await screen.findByText('Diseño Gráfico')).toBeInTheDocument();
    expect(screen.getByText('Pendiente')).toBeInTheDocument();
    expect(screen.getByText('Programación Web')).toBeInTheDocument();
    expect(screen.getByText('Aprobada')).toBeInTheDocument();
  });

  it('navega al detalle al clickear una fila', async () => {
    server.use(
      http.get(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json([{ id: 7, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' }]),
      ),
    );
    const user = userEvent.setup();
    renderMisSolicitudes();

    await user.click(await screen.findByText('Diseño Gráfico'));

    expect(await screen.findByText('Detalle de la solicitud 7')).toBeInTheDocument();
  });
});
