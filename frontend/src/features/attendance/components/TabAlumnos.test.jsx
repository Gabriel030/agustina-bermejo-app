import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { axe } from '@/test/axe';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { TabAlumnos } from './TabAlumnos';

const API = 'http://localhost:8080';

const ROSTER = [
  { enrollmentId: 1, studentId: 11, studentName: 'Acosta, Joaquín', dni: '40111222', absenceCount: 4, attendancePercent: 66.7, atRisk: true, classesHeld: 12, maxAbsenceRate: 25, remainingAbsences: 0 },
  { enrollmentId: 2, studentId: 12, studentName: 'Benítez, Lucía', dni: '41222333', absenceCount: 2, attendancePercent: 83.3, atRisk: false, classesHeld: 12, maxAbsenceRate: 25, remainingAbsences: 1 },
  { enrollmentId: 3, studentId: 13, studentName: 'Cabrera, Julieta', dni: '42555666', absenceCount: 0, attendancePercent: 100, atRisk: false, classesHeld: 12, maxAbsenceRate: 25, remainingAbsences: 3 },
];

function mockRoster(body = ROSTER, status = 200) {
  server.use(
    http.get(`${API}/program-sections/5/roster`, () =>
      status === 200 ? HttpResponse.json(body) : new HttpResponse(null, { status }),
    ),
  );
}

function rowOf(name) {
  return screen.getByText(name).closest('tr');
}

describe('TabAlumnos', () => {
  it('shows the three stat cards and the graded risk per student', async () => {
    mockRoster();
    renderWithProviders(<TabAlumnos type="PROGRAM" sectionId={5} />);

    await screen.findByText('Acosta, Joaquín');
    const stat = (label) => screen.getByText(label).closest('div').parentElement;
    expect(within(stat('Inscriptos')).getByText('3')).toBeInTheDocument();
    expect(within(stat('En riesgo')).getByText('2')).toBeInTheDocument();
    expect(within(stat('Faltas acumuladas')).getByText('6')).toBeInTheDocument();

    expect(within(rowOf('Acosta, Joaquín')).getByText('4 de 3 faltas permitidas')).toBeInTheDocument();
    expect(within(rowOf('Acosta, Joaquín')).getByText('Superó el máximo')).toBeInTheDocument();
    expect(within(rowOf('Benítez, Lucía')).getByText('2 de 3 faltas permitidas')).toBeInTheDocument();
    expect(within(rowOf('Benítez, Lucía')).getByText('Queda 1 falta')).toBeInTheDocument();
    expect(within(rowOf('Cabrera, Julieta')).getByText('0 de 3 faltas permitidas')).toBeInTheDocument();
    expect(within(rowOf('Cabrera, Julieta')).getByText('—')).toBeInTheDocument();
    expect(screen.getByText('Máximo de la comisión: 25% de las clases (3 de 12 dictadas)')).toBeInTheDocument();
  });

  it('shows only the absence count when the backend sends no allowance', async () => {
    mockRoster([{ enrollmentId: 1, studentId: 11, studentName: 'Acosta, Joaquín', dni: '40111222', absenceCount: 1, attendancePercent: 100, atRisk: false }]);
    renderWithProviders(<TabAlumnos type="PROGRAM" sectionId={5} />);

    await screen.findByText('Acosta, Joaquín');
    expect(within(rowOf('Acosta, Joaquín')).getByText('1 falta')).toBeInTheDocument();
    expect(screen.queryByText(/Máximo de la comisión/)).not.toBeInTheDocument();
  });

  it('filters by name or DNI and explains when nothing matches', async () => {
    mockRoster();
    const user = userEvent.setup();
    renderWithProviders(<TabAlumnos type="PROGRAM" sectionId={5} />);

    await screen.findByText('Acosta, Joaquín');
    await user.type(screen.getByPlaceholderText('Buscar por nombre o DNI'), '4155');
    expect(screen.queryByText('Acosta, Joaquín')).not.toBeInTheDocument();
    await user.clear(screen.getByPlaceholderText('Buscar por nombre o DNI'));
    await user.type(screen.getByPlaceholderText('Buscar por nombre o DNI'), 'zzz');
    expect(await screen.findByText('Ningún alumno coincide')).toBeInTheDocument();
  });

  it('shows the empty state when there are no enrolled students', async () => {
    mockRoster([]);
    renderWithProviders(<TabAlumnos type="PROGRAM" sectionId={5} />);

    expect(await screen.findByText('Todavía no hay alumnos inscriptos')).toBeInTheDocument();
  });

  it('shows the error state and retries', async () => {
    mockRoster([], 500);
    const user = userEvent.setup();
    renderWithProviders(<TabAlumnos type="PROGRAM" sectionId={5} />);

    expect(await screen.findByText('No pudimos cargar los alumnos')).toBeInTheDocument();
    mockRoster();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Acosta, Joaquín')).toBeInTheDocument();
  });

  it('has no axe violations with data', async () => {
    mockRoster();
    const { container } = renderWithProviders(<TabAlumnos type="PROGRAM" sectionId={5} />);

    await screen.findByText('Acosta, Joaquín');
    expect(await axe(container)).toHaveNoViolations();
  });
});
