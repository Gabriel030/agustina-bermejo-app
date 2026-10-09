import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { usePaymentReturn } from '@/hooks/usePaymentReturn';

function PaymentScreen() {
  const { originLabel, goToOrigin, goTo } = usePaymentReturn();
  return (
    <>
      <button type="button" onClick={goToOrigin}>{originLabel}</button>
      <button type="button" onClick={() => goTo('/siguiente')}>Siguiente</button>
    </>
  );
}

function NextScreen() {
  const { state } = useLocation();
  return <p>Llegó con {state?.volverA ?? 'nada'}</p>;
}

function renderFrom(state) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/pago', state }]}>
      <Routes>
        <Route path="/pago" element={<PaymentScreen />} />
        <Route path="/siguiente" element={<NextScreen />} />
        <Route path="/mis-solicitudes/5" element={<p>Detalle de la solicitud</p>} />
        <Route path="/alumno/pagos" element={<p>Mis pagos</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('usePaymentReturn', () => {
  it('vuelve a la pantalla de origen indicada con su etiqueta', async () => {
    renderFrom({ volverA: '/mis-solicitudes/5', volverALabel: 'Volver a tu solicitud' });

    await userEvent.setup().click(screen.getByRole('button', { name: 'Volver a tu solicitud' }));

    expect(screen.getByText('Detalle de la solicitud')).toBeInTheDocument();
  });

  it('sin origen vuelve a Mis pagos', async () => {
    renderFrom(undefined);

    await userEvent.setup().click(screen.getByRole('button', { name: 'Volver a Mis pagos' }));

    expect(screen.getByText('Mis pagos')).toBeInTheDocument();
  });

  it('conserva el origen al avanzar dentro del flujo de pago', async () => {
    renderFrom({ volverA: '/mis-solicitudes/5', volverALabel: 'Volver a tu solicitud' });

    await userEvent.setup().click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(screen.getByText('Llegó con /mis-solicitudes/5')).toBeInTheDocument();
  });
});
