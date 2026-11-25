import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { GraduationProjectionCard } from './GraduationProjectionCard';

const API = 'http://localhost:8080';

function projection(overrides) {
  return {
    programEnrollmentId: 5,
    status: 'PROJECTED',
    estimatedPeriods: 8,
    range: { minPeriods: 6, maxPeriods: 8 },
    estimatedGraduationYear: 2030,
    periodUnit: 'SEMESTER',
    method: 'HISTORICAL_PACE_WITH_PREREQUISITES',
    confidence: 'MEDIUM',
    basis: null,
    assumptions: [{ code: 'PACE_CONSTANT', message: 'Se asume que mantenés el ritmo.' }],
    warnings: [],
    reasons: [],
    ...overrides,
  };
}

function useProjection(body, status = 200) {
  server.use(http.get(`${API}/me/program-enrollments/5/progress/projection`, () => HttpResponse.json(body, { status })));
}

describe('GraduationProjectionCard', () => {
  it('shows estimated terms, range, confidence and approximate year without an exact date', async () => {
    useProjection(projection());
    renderWithProviders(<GraduationProjectionCard carreraId={5} />);

    expect(await screen.findByText('cuatrimestres estimados')).toBeInTheDocument();
    expect(screen.getByText('Entre 6 y 8 cuatrimestres')).toBeInTheDocument();
    expect(screen.getByText('Confianza media')).toBeInTheDocument();
    expect(screen.getByText('2030')).toBeInTheDocument();
    expect(screen.getByText('Estimación, no un compromiso.')).toBeInTheDocument();
  });

  it('hides the year when confidence is low', async () => {
    useProjection(projection({ confidence: 'LOW', estimatedGraduationYear: null }));
    renderWithProviders(<GraduationProjectionCard carreraId={5} />);

    expect(await screen.findByText('Confianza baja')).toBeInTheDocument();
    expect(screen.queryByText(/Año aproximado/)).not.toBeInTheDocument();
    expect(screen.getByText(/no hay suficiente historial/i)).toBeInTheDocument();
  });

  it('never shows the year of a low confidence projection even if the server sends one', async () => {
    useProjection(projection({ confidence: 'LOW', estimatedGraduationYear: 2031 }));
    renderWithProviders(<GraduationProjectionCard carreraId={5} />);

    await screen.findByText('Confianza baja');
    expect(screen.queryByText('2031')).not.toBeInTheDocument();
  });

  it('explains in Spanish why the projection is not available', async () => {
    useProjection(projection({ status: 'NOT_PROJECTABLE', estimatedPeriods: null, range: null, confidence: null, estimatedGraduationYear: null, reasons: ['ENROLLMENT_PAUSED', 'CREDITS_UNREACHABLE'] }));
    renderWithProviders(<GraduationProjectionCard carreraId={5} />);

    expect(await screen.findByText('Tu matrícula está pausada.')).toBeInTheDocument();
    expect(screen.getByText('Con las materias disponibles no se llega a los créditos que pide el plan.')).toBeInTheDocument();
    expect(screen.queryByText('cuatrimestres estimados')).not.toBeInTheDocument();
  });

  it('explains that the projection horizon was exceeded at the current pace', async () => {
    useProjection(projection({ status: 'NOT_PROJECTABLE', estimatedPeriods: null, range: null, confidence: null, estimatedGraduationYear: null, reasons: ['PROJECTION_HORIZON_EXCEEDED'] }));
    renderWithProviders(<GraduationProjectionCard carreraId={5} />);

    expect(await screen.findByText('A tu ritmo actual la estimación supera los 20 años (40 cuatrimestres), así que no la mostramos. Consultá con Administración o con tu plan de estudios.')).toBeInTheDocument();
    expect(screen.queryByText('No pudimos estimar tu egreso con los datos actuales.')).not.toBeInTheDocument();
  });

  it('says the requirements are met when there is nothing left to project', async () => {
    useProjection(projection({ status: 'REQUIREMENTS_MET', estimatedPeriods: null, range: null, confidence: null }));
    renderWithProviders(<GraduationProjectionCard carreraId={5} />);

    expect(await screen.findByText('Ya cumplís los requisitos del plan para egresar.')).toBeInTheDocument();
  });

  it('reveals the assumptions on demand', async () => {
    useProjection(projection());
    const user = userEvent.setup();
    renderWithProviders(<GraduationProjectionCard carreraId={5} />);

    await user.click(await screen.findByRole('button', { name: /Cómo se calcula/ }));

    expect(screen.getByText('Se asume que mantenés el ritmo.')).toBeInTheDocument();
  });

  it.each([403, 404])('hides the block when the endpoint answers %i', async (status) => {
    useProjection({ code: 'FORBIDDEN', requestId: 'req-1', retryable: false, violations: [] }, status);
    const { container } = renderWithProviders(<GraduationProjectionCard carreraId={5} />);

    await waitFor(() => expect(container).toBeEmptyDOMElement());
  });

  it('offers a retry on a server error', async () => {
    useProjection({ code: 'INTERNAL_ERROR', requestId: 'req-1', retryable: false, violations: [] }, 500);
    renderWithProviders(<GraduationProjectionCard carreraId={5} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar la proyección de egreso');
  });

  it('does not render an empty number when a projected response has no estimated periods', async () => {
    useProjection(projection({ estimatedPeriods: null, range: null }));
    renderWithProviders(<GraduationProjectionCard carreraId={5} />);

    expect(await screen.findByText('No pudimos estimar tu egreso con los datos actuales.')).toBeInTheDocument();
    expect(screen.queryByText('cuatrimestres estimados')).not.toBeInTheDocument();
  });

  it('does not call the periods cuatrimestres when the unit is not a semester', async () => {
    useProjection(projection({ periodUnit: 'YEAR' }));
    renderWithProviders(<GraduationProjectionCard carreraId={5} />);

    expect(await screen.findByText('No pudimos estimar tu egreso con los datos actuales.')).toBeInTheDocument();
    expect(screen.queryByText(/cuatrimestres/)).not.toBeInTheDocument();
  });

  describe('call to action', () => {
    const notProjectable = (reasons) =>
      projection({ status: 'NOT_PROJECTABLE', estimatedPeriods: null, range: null, confidence: null, estimatedGraduationYear: null, reasons });

    it.each([['MEDIUM'], ['LOW']])('offers to see available subjects when the projection has %s confidence', async (confidence) => {
      useProjection(projection({ confidence, estimatedGraduationYear: null }));
      const onViewAvailableSubjects = vi.fn();
      const user = userEvent.setup();
      renderWithProviders(<GraduationProjectionCard carreraId={5} onViewAvailableSubjects={onViewAvailableSubjects} />);

      await user.click(await screen.findByRole('button', { name: 'Ver qué puedo cursar' }));

      expect(onViewAvailableSubjects).toHaveBeenCalledTimes(1);
      expect(screen.getByRole('button', { name: 'Ver qué puedo cursar' })).toHaveClass('min-h-11');
    });

    it('offers to see available subjects when only the horizon was exceeded', async () => {
      useProjection(notProjectable(['PROJECTION_HORIZON_EXCEEDED']));
      renderWithProviders(<GraduationProjectionCard carreraId={5} onViewAvailableSubjects={() => {}} />);

      expect(await screen.findByRole('button', { name: 'Ver qué puedo cursar' })).toBeInTheDocument();
    });

    it('has no call to action when there are no available subjects', async () => {
      useProjection(projection());
      renderWithProviders(<GraduationProjectionCard carreraId={5} />);

      await screen.findByText('cuatrimestres estimados');
      expect(screen.queryByRole('button', { name: 'Ver qué puedo cursar' })).not.toBeInTheDocument();
    });

    it.each([['ENROLLMENT_PAUSED'], ['CREDITS_UNREACHABLE'], ['PREREQUISITE_CYCLE']])('has no call to action for the administrative reason %s', async (reason) => {
      useProjection(notProjectable([reason]));
      renderWithProviders(<GraduationProjectionCard carreraId={5} onViewAvailableSubjects={() => {}} />);

      await screen.findByRole('heading', { name: 'Proyección de egreso' });
      await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());
      expect(screen.queryByRole('button', { name: 'Ver qué puedo cursar' })).not.toBeInTheDocument();
      expect(screen.queryByRole('link', { name: 'Ir a Trámites' })).not.toBeInTheDocument();
    });

    it('sends the student to Trámites when the requirements are met, without the estimate disclaimer', async () => {
      useProjection(projection({ status: 'REQUIREMENTS_MET', estimatedPeriods: null, range: null, confidence: null }));
      renderWithProviders(<GraduationProjectionCard carreraId={5} onViewAvailableSubjects={() => {}} />);

      const link = await screen.findByRole('link', { name: 'Ir a Trámites' });
      expect(link).toHaveAttribute('href', '/?tab=tramites');
      expect(link).toHaveClass('min-h-11');
      expect(screen.queryByText('Estimación, no un compromiso.')).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Ver qué puedo cursar' })).not.toBeInTheDocument();
    });

    it('shows no call to action for staff', async () => {
      server.use(http.get(`${API}/program-enrollments/5/progress/projection`, () => HttpResponse.json(projection({ status: 'REQUIREMENTS_MET' }))));
      renderWithProviders(<GraduationProjectionCard audience="staff" carreraId={5} onViewAvailableSubjects={() => {}} />);

      await screen.findByText('Ya cumple los requisitos del plan para egresar.');
      expect(screen.queryByRole('link', { name: 'Ir a Trámites' })).not.toBeInTheDocument();
    });

    it('has no call to action on a server error and keeps the retry', async () => {
      useProjection({ code: 'INTERNAL_ERROR', requestId: 'req-1', retryable: false, violations: [] }, 500);
      renderWithProviders(<GraduationProjectionCard carreraId={5} onViewAvailableSubjects={() => {}} />);

      expect(await screen.findByRole('alert')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Ver qué puedo cursar' })).not.toBeInTheDocument();
    });

    it('has no accessibility violations with the call to action', async () => {
      useProjection(projection());
      const { container } = renderWithProviders(<GraduationProjectionCard carreraId={5} onViewAvailableSubjects={() => {}} />);

      await screen.findByRole('button', { name: 'Ver qué puedo cursar' });
      expect(await axe(container)).toHaveNoViolations();
    });
  });
});
