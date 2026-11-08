import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route, useLocation } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { advanceView, obligationWith } from '@/test/fixtures/advanceEnrollmentFee';
import { useEstadoMatricula } from '@/features/enrollment';
import { NextYearBlock } from './NextYearBlock';

const API = 'http://localhost:8080';

function Destination() {
  return <div>{useLocation().pathname}</div>;
}

function Loaded() {
  const { data } = useEstadoMatricula(3);
  return data ? <span>estado cargado</span> : null;
}

function Page() {
  return (
    <>
      <Loaded />
      <NextYearBlock enrollmentId={3} />
    </>
  );
}

function mount(obligations) {
  server.use(http.get(`${API}/me/program-enrollments/3/enrollment-fee-status`, () => HttpResponse.json(obligations)));
  return renderWithProviders(
    <Routes>
      <Route path="/alumno/pagos/carrera/3" element={<Page />} />
      <Route path="*" element={<Destination />} />
    </Routes>,
    { route: '/alumno/pagos/carrera/3' },
  );
}

describe('NextYearBlock', () => {
  it('offersTheFeeWithTodaysPriceAndDueDateWhenAvailable', async () => {
    mount(obligationWith(advanceView()));
    expect(await screen.findByRole('region', { name: 'Para el 2027' })).toBeInTheDocument();
    expect(screen.getByText(/45\.000/)).toBeInTheDocument();
    expect(screen.getByText(/Vence el 12\/02\/2027/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pagar la matrícula 2027' })).toBeInTheDocument();
  });

  it('goesToTheConfirmationStepFromTheCallToAction', async () => {
    const user = userEvent.setup();
    mount(obligationWith(advanceView()));
    await user.click(await screen.findByRole('button', { name: 'Pagar la matrícula 2027' }));
    expect(await screen.findByText('/alumno/pagos/carrera/3/matricula-anticipada')).toBeInTheDocument();
  });

  it('showsOverdueReasonWithoutPayingActionWhenStudentHasOverdueDebt', async () => {
    mount(obligationWith(advanceView({ available: false, reasonCode: 'ADVANCE_FEE_OVERDUE_DEBT' })));
    expect(await screen.findByText('Tenés cuotas vencidas: pagalas primero.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Pagar la matrícula/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ver mis cuotas vencidas' })).toBeInTheDocument();
  });

  it('showsOpeningDateWhenWindowHasNotOpened', async () => {
    mount(obligationWith(advanceView({ available: false, reasonCode: 'ADVANCE_FEE_WINDOW_NOT_OPEN' })));
    expect(await screen.findByText('Se puede pagar desde el 1 de septiembre de 2026.')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it.each(['ADVANCE_FEE_NEXT_YEAR_NOT_CONFIGURED', 'ADVANCE_FEE_ALREADY_ISSUED', 'ADVANCE_FEE_ENROLLMENT_NOT_ACTIVE', 'ADVANCE_FEE_WINDOW_CLOSED'])(
    'rendersNothingWhenReasonIs%s',
    async (reasonCode) => {
      mount(obligationWith(advanceView({ available: false, reasonCode, invoiceId: reasonCode === 'ADVANCE_FEE_ALREADY_ISSUED' ? 9 : null })));
      await screen.findByText('estado cargado');
      expect(screen.queryByRole('region')).not.toBeInTheDocument();
    },
  );

  it('rendersNothingWhenNoObligationCarriesAnAdvanceOffer', async () => {
    mount(obligationWith(null));
    await screen.findByText('estado cargado');
    expect(screen.queryByRole('region')).not.toBeInTheDocument();
  });
});
