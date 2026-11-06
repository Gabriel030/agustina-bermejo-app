import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route, useLocation } from 'react-router-dom';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { StaffSectionRoster } from './StaffSectionRoster';

const API = 'http://localhost:8080';

const PROGRAM_CYCLE = {
  id: 15,
  status: 'APPROVED',
  termId: 7,
  termName: '2.º cuatr. 2026',
  programId: 6,
  programName: 'Tecnicatura en Gestión Cultural',
  courseId: null,
  courseName: null,
  createdByName: 'Paula Roldán',
  createdAt: '2026-08-01T10:00:00',
  decidedAt: null,
  decidedByName: null,
  rejectionReason: null,
};

const SECTIONS = [
  {
    id: 31,
    name: 'Com. B',
    subjectName: 'Historia del Arte',
    capacity: 40,
    status: 'ACTIVE',
    pendingApproval: false,
    lastRejectionReason: null,
    instructors: [{ name: 'Peña', role: 'LEAD' }],
    slots: [{ dayOfWeek: 'TUESDAY', startTime: '18:30:00', endTime: '20:30:00', classroomId: 4, classroomName: 'Aula 2' }],
  },
];

const ENTRIES = [
  { enrollmentId: 101, studentId: 11, studentName: 'Acosta, Joaquín', dni: '40.123.456', absenceCount: 7, atRisk: true, attendancePercent: 70.8 },
  { enrollmentId: 102, studentId: 12, studentName: 'Benítez, Lucía', dni: '41.222.333', absenceCount: 1, atRisk: false, attendancePercent: 95.8 },
  { enrollmentId: 103, studentId: 13, studentName: 'Cabrera, Julieta', dni: '42.555.666', absenceCount: 0, atRisk: false, attendancePercent: 100 },
];

let requestedUrl;

function mockBackend({ entries = ENTRIES, reportStatus = 200 } = {}) {
  requestedUrl = undefined;
  server.use(
    http.get(`${API}/academic-offering-cycles/15`, () => HttpResponse.json(PROGRAM_CYCLE)),
    http.get(`${API}/academic-offering-cycles/15/sections`, () => HttpResponse.json(SECTIONS)),
    http.get(`${API}/program-sections/31/attendance/absence-report`, ({ request }) => {
      requestedUrl = new URL(request.url);
      return reportStatus === 200 ? HttpResponse.json(entries) : HttpResponse.json(errorBody('error'), { status: reportStatus });
    }),
  );
}

function CurrentLocation() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname}</div>;
}

function renderRoster(role = 'preceptor') {
  return renderWithProviders(
    <>
      <Routes>
        <Route path={`/${role}/oferta-academica/ciclos/:cycleId/comisiones/:sectionId/inscriptos`} element={<StaffSectionRoster />} />
        <Route path="*" element={null} />
      </Routes>
      <CurrentLocation />
    </>,
    { route: `/${role}/oferta-academica/ciclos/15/comisiones/31/inscriptos` },
  );
}

describe('StaffSectionRoster', () => {
  it('lists every enrolled student without asking the backend to filter by absences', async () => {
    mockBackend();
    renderRoster();

    expect(await screen.findByText('Acosta, Joaquín')).toBeInTheDocument();
    expect(screen.getByText('Benítez, Lucía')).toBeInTheDocument();
    expect(screen.getByText('Cabrera, Julieta')).toBeInTheDocument();
    expect(requestedUrl.searchParams.has('minAbsences')).toBe(false);
    expect(screen.getByText('Historia del Arte · Com. B · Mar 18:30 · Prof. Peña')).toBeInTheDocument();
  });

  it('summarises enrolled, at risk and average attendance', async () => {
    mockBackend();
    renderRoster();

    await screen.findByText('Acosta, Joaquín');
    expect(screen.getByText('Inscriptos').nextElementSibling).toHaveTextContent('3');
    expect(screen.getByText('En riesgo', { selector: 'div' }).nextElementSibling).toHaveTextContent('1');
    expect(screen.getByText('Asist. media').nextElementSibling).toHaveTextContent('88,9%');
  });

  it('marks only students at risk with the risk pill', async () => {
    mockBackend();
    renderRoster();

    const row = (await screen.findByText('Acosta, Joaquín')).closest('tr');
    expect(within(row).getByText('En riesgo')).toBeInTheDocument();
    expect(within(screen.getByText('Benítez, Lucía').closest('tr')).queryByText('En riesgo')).not.toBeInTheDocument();
  });

  it('filters by risk from the student column header', async () => {
    mockBackend();
    const user = userEvent.setup();
    renderRoster();

    await screen.findByText('Acosta, Joaquín');
    await user.click(screen.getByRole('button', { name: 'Filtrar por riesgo' }));
    await user.click(await screen.findByRole('checkbox', { name: /Sin riesgo/ }));

    expect(screen.getByText('Acosta, Joaquín')).toBeInTheDocument();
    expect(screen.queryByText('Benítez, Lucía')).not.toBeInTheDocument();
  });

  it('searches by name or document number', async () => {
    mockBackend();
    const user = userEvent.setup();
    renderRoster();

    await screen.findByText('Acosta, Joaquín');
    await user.type(screen.getByPlaceholderText('Buscar por nombre o DNI'), '42.555');

    expect(screen.getByText('Cabrera, Julieta')).toBeInTheDocument();
    expect(screen.queryByText('Acosta, Joaquín')).not.toBeInTheDocument();
  });

  it('opens the student record under the role of the current user', async () => {
    mockBackend();
    const user = userEvent.setup();
    renderRoster('administrativo');

    await user.click(await screen.findByText('Benítez, Lucía'));

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/administrativo/alumnos/12'));
  });

  it('shows the empty state when the section has no enrolled students', async () => {
    mockBackend({ entries: [] });
    renderRoster();

    expect(await screen.findByText('Esta comisión todavía no tiene inscriptos')).toBeInTheDocument();
  });

  it('offers a retry when the report cannot be loaded', async () => {
    mockBackend({ reportStatus: 500 });
    renderRoster();

    expect(await screen.findByText('No se pudieron cargar los inscriptos')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('links the breadcrumb back to the offering under the role of the current user', async () => {
    mockBackend();
    renderRoster('administrativo');

    const link = await screen.findByRole('link', { name: 'Tecnicatura en Gestión Cultural' });
    expect(link).toHaveAttribute('href', '/administrativo/oferta-academica/carreras/6');
    expect(screen.getByRole('link', { name: 'Oferta académica' })).toHaveAttribute('href', '/administrativo/oferta-academica');
  });

  it('opens the announcements and read receipts of the section from the header button', async () => {
    mockBackend();
    renderRoster('director');

    await userEvent.click(await screen.findByRole('button', { name: 'Avisos y lectura' }));

    expect(screen.getByTestId('location')).toHaveTextContent('/director/oferta-academica/ciclos/15/comisiones/31/avisos');
  });
});
