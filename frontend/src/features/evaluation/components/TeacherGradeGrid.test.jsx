import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { TeacherGradeGrid } from './TeacherGradeGrid';

const API = 'http://localhost:8080';

const grid = {
  sectionId: 7,
  evaluationSchemeName: 'Cuatrimestral',
  minRegularGrade: 6,
  minApprovalGrade: 7,
  promotionMinGrade: 8,
  columns: [
    { type: 'EXAM', sequenceNumber: 1, date: '2026-09-15' },
    { type: 'TP', sequenceNumber: 1, date: null },
    { type: 'FINAL', sequenceNumber: 1, date: '2026-12-02' },
  ],
  students: [
    {
      enrollmentId: 11,
      lastname: 'Benítez',
      name: 'Lucía',
      dni: '42007315',
      condition: 'REGULAR',
      finalEnabled: true,
      grades: [{ gradeItemId: 1, type: 'EXAM', sequenceNumber: 1, score: 7, date: '2026-09-16T10:00:00', comments: null }],
    },
    {
      enrollmentId: 12,
      lastname: 'Acosta',
      name: 'Joaquín',
      dni: '40118992',
      condition: 'FREE',
      finalEnabled: false,
      grades: [],
    },
  ],
};

function useGrid(body = grid, kind = 'program') {
  server.use(http.get(`${API}/${kind}-sections/7/grade-grid`, () => HttpResponse.json(body)));
}

describe('TeacherGradeGrid', () => {
  it('builds one column per scheme item, orders students and disables the final for a non regular student', async () => {
    useGrid();
    renderWithProviders(<TeacherGradeGrid sectionId="7" kind="PROGRAM" />);

    expect(await screen.findByLabelText('Parcial 1 de Benítez, Lucía')).toHaveValue('7');
    expect(screen.getByRole('columnheader', { name: /Parcial 1/ })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: /TP 1/ })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: /Final/ })).toBeInTheDocument();
    expect(screen.getByLabelText('Final de Acosta, Joaquín')).toBeDisabled();
    expect(screen.getByLabelText('Final de Benítez, Lucía')).toBeEnabled();
    const rows = screen.getAllByRole('row');
    expect(within(rows[1]).getByText('Acosta, Joaquín')).toBeInTheDocument();
  });

  it('shows the previous to new correction and posts every modified cell once', async () => {
    useGrid();
    const posts = [];
    server.use(
      http.post(`${API}/section-enrollments/:id/grade-items`, async ({ request, params }) => {
        const body = await request.json();
        posts.push({ id: params.id, body });
        return HttpResponse.json({ id: 9, enrollmentId: Number(params.id), type: body.type, sequenceNumber: body.sequenceNumber, score: body.score, date: body.date, examBoardId: 0 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<TeacherGradeGrid sectionId="7" kind="PROGRAM" />);

    const exam = await screen.findByLabelText('Parcial 1 de Benítez, Lucía');
    await user.clear(exam);
    await user.type(exam, '8,5');
    expect(screen.getByText('7 → 8,5')).toBeInTheDocument();
    await user.type(screen.getByLabelText('TP 1 de Acosta, Joaquín'), '5');
    await user.click(screen.getByRole('button', { name: 'Guardar 2 notas' }));

    await waitFor(() => expect(posts).toHaveLength(2));
    const correction = posts.find((post) => post.id === '11');
    expect(correction.body).toEqual({ type: 'EXAM', sequenceNumber: 1, score: 8.5, date: '2026-09-16T10:00:00', comments: null });
    expect(posts.find((post) => post.id === '12').body).toMatchObject({ type: 'TP', sequenceNumber: 1, score: 5, comments: null });
    expect(await screen.findByText('Todo guardado')).toBeInTheDocument();
  });

  it('marks a failed cell with a retry and keeps its value', async () => {
    useGrid();
    server.use(
      http.post(`${API}/section-enrollments/12/grade-items`, () => HttpResponse.json({ code: 'NO_EVALUATION_SCHEME' }, { status: 409 })),
    );
    const user = userEvent.setup();
    renderWithProviders(<TeacherGradeGrid sectionId="7" kind="PROGRAM" />);

    const tp = await screen.findByLabelText('TP 1 de Acosta, Joaquín');
    await user.type(tp, '5');
    await user.click(screen.getByRole('button', { name: 'Guardar 1 nota' }));

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.getByLabelText('TP 1 de Acosta, Joaquín')).toHaveValue('5');
  });

  it('blocks saving while a grade is out of range', async () => {
    useGrid();
    const user = userEvent.setup();
    renderWithProviders(<TeacherGradeGrid sectionId="7" kind="PROGRAM" />);

    await user.type(await screen.findByLabelText('TP 1 de Acosta, Joaquín'), '11');

    expect(screen.getByText(/hay notas fuera de 1 a 10/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Guardar notas' })).toBeDisabled();
  });

  it('posts to the course enrollment endpoint for a course section', async () => {
    useGrid(grid, 'course');
    const posted = [];
    server.use(
      http.post(`${API}/course-section-enrollments/12/grade-items`, async ({ request }) => {
        posted.push(await request.json());
        return HttpResponse.json({ id: 1, enrollmentId: 12, type: 'TP', sequenceNumber: 1, score: 6, date: '2026-11-02T10:00:00', examBoardId: 0 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<TeacherGradeGrid sectionId="7" kind="COURSE" />);

    await user.type(await screen.findByLabelText('TP 1 de Acosta, Joaquín'), '6');
    await user.click(screen.getByRole('button', { name: 'Guardar 1 nota' }));

    await waitFor(() => expect(posted).toHaveLength(1));
  });

  it('explains a section without scheme', async () => {
    useGrid({ ...grid, columns: [], evaluationSchemeName: null });
    renderWithProviders(<TeacherGradeGrid sectionId="7" kind="PROGRAM" />);

    expect(await screen.findByText('Esta comisión todavía no tiene un esquema de evaluación')).toBeInTheDocument();
  });

  it('explains a section without students and offers retry on error', async () => {
    useGrid({ ...grid, students: [] });
    const { unmount } = renderWithProviders(<TeacherGradeGrid sectionId="7" kind="PROGRAM" />);
    expect(await screen.findByText('Todavía no hay alumnos inscriptos')).toBeInTheDocument();
    unmount();

    server.use(http.get(`${API}/program-sections/7/grade-grid`, () => new HttpResponse(null, { status: 500 })));
    renderWithProviders(<TeacherGradeGrid sectionId="7" kind="PROGRAM" />);
    expect(await screen.findByText('No pudimos cargar las notas')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
