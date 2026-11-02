import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { InscripcionMaterias } from './InscripcionMaterias';

const API = 'http://localhost:8080';

const PROGRAM_ENROLLMENT = {
  id: 7,
  studentId: 3,
  programId: 11,
  programName: 'Tecnicatura en Cerámica',
  status: 'ACTIVE',
  enrollmentDate: '2027-03-01T10:00:00Z',
  completionDate: '2030-12-01T10:00:00Z',
};

const PROGRAM_SUBJECT = {
  id: 501,
  programId: 11,
  subjectId: 81,
  subjectCode: 'CER-101',
  subjectName: 'Modelado I',
  curricularYear: 1,
  curricularTerm: 1,
  credits: 6,
  category: 'REQUIRED',
  status: 'ACTIVE',
  subjectStatus: 'ACTIVE',
  sortOrder: 1,
  weeklyHours: 4,
  weeklyHoursLab: 0,
  electiveGroupId: null,
};

const SECTION = {
  id: 900,
  subjectId: 81,
  subjectName: 'Modelado I',
  name: 'Comisión A',
  termId: 4,
  capacity: 30,
  schedule: 'Lunes 18 a 21',
  status: 'ACTIVE',
};

function useStudentWithOpenSection() {
  server.use(
    http.get(`${API}/me/program-enrollments`, () => HttpResponse.json([PROGRAM_ENROLLMENT])),
    http.get(`${API}/me/section-enrollments`, () => HttpResponse.json([])),
    http.get(`${API}/programs/:id/subjects`, () => HttpResponse.json([PROGRAM_SUBJECT])),
    http.get(`${API}/subjects/:id/sections`, ({ params }) =>
      HttpResponse.json(params.id === '81' ? [SECTION] : []),
    ),
    http.get(`${API}/me/program-enrollments/:id/enrollment-fee-status`, () => HttpResponse.json([])),
  );
}

describe('InscripcionMaterias', () => {
  it('lists the open sections of the chosen plan subject', async () => {
    useStudentWithOpenSection();
    renderWithProviders(<InscripcionMaterias carreraId={7} onCerrar={() => {}} />);

    await userEvent.setup().click(await screen.findByRole('button', { name: /Modelado I/ }));

    expect(await screen.findByRole('button', { name: /Comisión A/ })).toBeInTheDocument();
    expect(screen.queryByText('No hay comisiones abiertas')).not.toBeInTheDocument();
  });
});
