import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { Routes, Route } from 'react-router-dom';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoPagoEstado } from '@/pages/AlumnoPagoEstado';

function renderStatus(route) {
  return renderWithProviders(
    <Routes>
      <Route path="/alumno/pagos/estado" element={<AlumnoPagoEstado />} />
    </Routes>,
    { route },
  );
}

describe('AlumnoPagoEstado', () => {
  it('shows a neutral state when there is no status', () => {
    renderStatus('/alumno/pagos/estado');

    expect(screen.getByText('No encontramos un pago en curso')).toBeInTheDocument();
    expect(screen.getByText('Revisá el estado de tus cuotas en Mis pagos.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Volver a Mis pagos/ })).toBeInTheDocument();
    expect(screen.queryByText('Pago pendiente')).not.toBeInTheDocument();
  });

  it('shows the neutral state for an unknown status', () => {
    renderStatus('/alumno/pagos/estado?status=whatever');

    expect(screen.getByText('No encontramos un pago en curso')).toBeInTheDocument();
  });

  it('keeps the success state', () => {
    renderStatus('/alumno/pagos/estado?status=success');

    expect(screen.getByText('Pago realizado')).toBeInTheDocument();
  });
});
