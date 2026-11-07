import { beforeEach, describe, expect, it, vi } from 'vitest';
import { errorBody } from '@/test/fixtures/errors';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { TeacherGradeGrid } from './TeacherGradeGrid';

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

const API = 'http://localhost:8080';

const grid = {
  sectionId: 7,
  correctionDeadline: '2026-12-18',
  evaluationSchemeName: 'Cuatrimestral',
  minRegularGrade: 6,
  minApprovalGrade: 7,
  promotionMinGrade: 8,
  columns: [
    { type: 'EXAM', sequenceNumber: 1, date: '2026-09-15', published: false, publishedAt: null },
    { type: 'TP', sequenceNumber: 1, date: null, published: false, publishedAt: null },
    { type: 'FINAL', sequenceNumber: 1, date: '2026-12-02', published: false, publishedAt: null },
  ],
  students: [
    {
      enrollmentId: 11,
      lastname: 'Benítez',
      name: 'Lucía',
      dni: '42007315',
      condition: 'REGULAR',
      finalEnabled: true,
      grades: [{ gradeItemId: 1, type: 'EXAM', sequenceNumber: 1, score: 7, date: '2026-09-16T10:00:00', comments: null, published: false, corrected: false }],
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
  beforeEach(() => {
    toastMock.success.mockClear();
    toastMock.error.mockClear();
  });

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
      http.post(`${API}/section-enrollments/12/grade-items`, () => HttpResponse.json(errorBody('NO_EVALUATION_SCHEME'), { status: 409 })),
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

  it('publishes a loaded column after confirming and shows the summary', async () => {
    useGrid();
    const posted = [];
    server.use(
      http.post(`${API}/program-sections/7/grade-items/publish`, async ({ request }) => {
        posted.push(await request.json());
        return HttpResponse.json({ gradeCount: 1, studentCount: 1 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<TeacherGradeGrid sectionId="7" kind="PROGRAM" />);

    expect(await screen.findByText('Cargada 1/2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publicar TP 1' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Publicar Parcial 1' }));
    expect(await screen.findByText(/no se puede deshacer desde acá/)).toBeInTheDocument();
    expect(screen.getByText('1 alumno todavía no tiene nota en Parcial 1 y ya no podrás cargarla.')).toBeInTheDocument();
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Publicar' }));

    await waitFor(() => expect(posted).toEqual([{ gradeType: 'EXAM', sequenceNumber: 1 }]));
    await waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('1 nota publicada para 1 alumno'));
  });

  it('renders a published column read only with an explanation and no publish button', async () => {
    const published = {
      ...grid,
      columns: grid.columns.map((column) => (column.type === 'EXAM' ? { ...column, published: true, publishedAt: '2026-10-01T12:00:00' } : column)),
    };
    useGrid(published);
    renderWithProviders(<TeacherGradeGrid sectionId="7" kind="PROGRAM" />);

    expect(await screen.findByText('Publicada')).toBeInTheDocument();
    const cell = screen.getByRole('textbox', { name: 'Parcial 1 de Benítez, Lucía' });
    expect(cell).toHaveAttribute('readonly');
    expect(cell).toHaveValue('7');
    expect(cell).toHaveAccessibleDescription('Las notas publicadas quedan bloqueadas. Si hay un error, pedíselo a Dirección.');
    expect(screen.queryByRole('button', { name: 'Publicar Parcial 1' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Publicar Final' })).not.toBeInTheDocument();
  });

  it('disables publishing while the column has unsaved grades', async () => {
    useGrid();
    const user = userEvent.setup();
    renderWithProviders(<TeacherGradeGrid sectionId="7" kind="PROGRAM" />);

    await user.type(await screen.findByLabelText('TP 1 de Acosta, Joaquín'), '5');

    expect(screen.getByRole('button', { name: 'Publicar TP 1' })).toBeDisabled();
  });

  it('explains the lock when publishing fails with a conflict', async () => {
    useGrid();
    server.use(
      http.post(`${API}/program-sections/7/grade-items/publish`, () => HttpResponse.json(errorBody('NO_PENDING_GRADES_TO_PUBLISH'), { status: 409 })),
    );
    const user = userEvent.setup();
    renderWithProviders(<TeacherGradeGrid sectionId="7" kind="PROGRAM" />);

    await user.click(await screen.findByRole('button', { name: 'Publicar Parcial 1' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Publicar' }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith('No hay notas cargadas para publicar en esta evaluación.'));
  });

  it('publishes through the course section endpoint', async () => {
    useGrid(grid, 'course');
    const posted = [];
    server.use(
      http.post(`${API}/course-sections/7/grade-items/publish`, async ({ request }) => {
        posted.push(await request.json());
        return HttpResponse.json({ gradeCount: 1, studentCount: 1 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<TeacherGradeGrid sectionId="7" kind="COURSE" />);

    await user.click(await screen.findByRole('button', { name: 'Publicar Parcial 1' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Publicar' }));

    await waitFor(() => expect(posted).toHaveLength(1));
  });
});
