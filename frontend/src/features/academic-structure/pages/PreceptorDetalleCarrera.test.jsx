import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route, useLocation } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PreceptorDetalleCarrera } from './PreceptorDetalleCarrera';

const API = 'http://localhost:8080';

function aCycle(extra = {}) {
  return {
    id: 15,
    status: 'DRAFT',
    termId: 7,
    termName: 'Ciclo lectivo 2027',
    programId: 6,
    programName: 'Gestión Ambiental',
    createdByName: 'Preceptor Demo',
    rejectionReason: null,
    ...extra,
  };
}

function mockProgramWithCycles(cycles) {
  server.use(
    http.get(`${API}/programs/6`, () => HttpResponse.json({ id: 6, name: 'Gestión Ambiental', totalYears: 3 })),
    http.get(`${API}/academic-offering-cycles`, () => HttpResponse.json(cycles)),
    http.get(`${API}/academic-offering-cycles/:id/sections`, () => HttpResponse.json([])),
  );
}

function CurrentLocation() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname + location.search}</div>;
}

function renderDetail() {
  return renderWithProviders(
    <>
      <Routes>
        <Route path="/preceptor/oferta-academica/carreras/:programaId" element={<PreceptorDetalleCarrera />} />
        <Route path="*" element={null} />
      </Routes>
      <CurrentLocation />
    </>,
    { route: '/preceptor/oferta-academica/carreras/6' },
  );
}

describe('PreceptorDetalleCarrera', () => {
  it('shows the rejection reason and lets the preceptor fix and resend a rejected cycle', async () => {
    mockProgramWithCycles([
      aCycle({ status: 'REJECTED', rejectionReason: 'Faltan docentes asignados en tres comisiones de primer año' }),
    ]);
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Anteriores' }));
    expect(await screen.findByText('Faltan docentes asignados en tres comisiones de primer año')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Corregir y reenviar' }));

    expect(screen.getByTestId('location')).toHaveTextContent('/preceptor/oferta-academica/nuevo-ciclo?cicloId=15');
  });

  it('lets the preceptor continue building a draft cycle', async () => {
    mockProgramWithCycles([aCycle({ status: 'DRAFT' })]);
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Continuar armado' }));

    expect(screen.getByTestId('location')).toHaveTextContent('/preceptor/oferta-academica/nuevo-ciclo?cicloId=15');
  });

  it('does not offer edition for a cycle already sent for approval', async () => {
    mockProgramWithCycles([aCycle({ status: 'PENDING_APPROVAL' })]);
    renderDetail();

    expect(await screen.findByText('En revisión')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Continuar armado' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Corregir y reenviar' })).not.toBeInTheDocument();
  });

  it('opens the classroom calendar for the period of the cycle', async () => {
    mockProgramWithCycles([aCycle({ status: 'APPROVED' })]);
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Calendario' }));

    expect(screen.getByTestId('location')).toHaveTextContent('/preceptor/oferta-academica/calendario?termId=7');
  });
});
