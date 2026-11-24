import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { restoreViewport, useCompactViewport } from '@/test/compactViewport';
import { takeContractDeviations } from '@/test/contract/withContract';
import { errorBody } from '@/test/fixtures/errors';
import { subjectEquivalence } from '@/test/fixtures/subjectEquivalences';
import { EQUIVALENCE_ENROLLMENT_IDS } from '@/test/mocks/subjectEquivalenceHandlers';
import { EquivalenceActionsProvider } from './EquivalenceActionsProvider';
import { EquivalenceMobileBar } from './EquivalenceMobileBar';
import { CurriculumMap } from './CurriculumMap';
import { EquivalencesSection } from './EquivalencesSection';

const auth = vi.hoisted(() => ({ roles: ['ADMINISTRATIVO'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: auth.roles } }),
}));

const API = 'http://localhost:8080';
const ENROLLMENT = EQUIVALENCE_ENROLLMENT_IDS.active;

function planSubject(overrides) {
  return {
    programSubjectId: 1,
    subjectId: 10,
    subjectCode: 'MAT101',
    subjectName: 'Matemática I',
    credits: 3,
    curricularYear: 1,
    curricularTerm: 'FIRST',
    category: 'MANDATORY',
    electiveGroupId: null,
    state: 'APPROVED',
    finalScore: 8,
    attempts: 1,
    blockedBy: [],
    prerequisites: [],
    currentEnrollmentId: null,
    corequisites: [],
    approvalSource: 'COURSEWORK',
    equivalence: null,
    prerequisitesWaived: false,
    ...overrides,
  };
}

const analysisPrerequisite = { subjectId: 20, subjectCode: 'ANA201', subjectName: 'Análisis', requirementType: 'APPROVED', standing: 'NOT_TAKEN', minGrade: null, approvedScore: null };

const progress = {
  programEnrollmentId: ENROLLMENT,
  programName: 'Tecnicatura de prueba',
  summary: {
    completionPercent: 25,
    accumulatedCredits: 3,
    requiredCredits: 12,
    remainingCredits: 9,
    mandatoryTotal: 4,
    mandatoryApproved: 1,
    counts: { approved: 1, inProgress: 1, regularPendingFinal: 0, failed: 0, available: 1, blocked: 1 },
    electiveGroups: [],
    approvedByEquivalence: 0,
    creditsByEquivalence: 0,
    averageScore: 8,
    accumulatedCreditsByCoursework: 3,
  },
  subjects: [
    planSubject(),
    planSubject({ programSubjectId: 2, subjectId: 20, subjectCode: 'ANA201', subjectName: 'Análisis', state: 'AVAILABLE', finalScore: null }),
    planSubject({
      programSubjectId: 3,
      subjectId: 30,
      subjectCode: 'FIS301',
      subjectName: 'Física',
      state: 'BLOCKED',
      finalScore: null,
      blockedBy: [analysisPrerequisite],
      prerequisites: [analysisPrerequisite],
    }),
    planSubject({ programSubjectId: 4, subjectId: 40, subjectCode: 'QUI401', subjectName: 'Química', state: 'IN_PROGRESS', finalScore: null }),
  ],
  warnings: [],
};

const counters = { progress: 0, equivalences: 0 };
let recorded;

const failure = (status, code) => HttpResponse.json(errorBody(code), { status });

beforeEach(() => {
  auth.roles = ['ADMINISTRATIVO'];
  counters.progress = 0;
  counters.equivalences = 0;
  recorded = null;
  server.use(
    http.get(`${API}/program-enrollments/:id/progress/subjects`, () => {
      counters.progress += 1;
      return HttpResponse.json(progress);
    }),
    http.get(`${API}/program-enrollments/:id/equivalences`, () => {
      counters.equivalences += 1;
      return HttpResponse.json([]);
    }),
    http.post(`${API}/program-enrollments/:id/equivalences`, async ({ request }) => {
      recorded = await request.json();
      return HttpResponse.json(subjectEquivalence({ id: 310 }), { status: 201 });
    }),
    http.get(`${API}/students/:id/documents`, () =>
      HttpResponse.json([
        { id: 51, type: 'ANALITICO_SECUNDARIO', uploadedAt: '2026-03-10T09:00:00' },
        { id: 52, type: 'DNI_FRENTE', uploadedAt: '2026-03-11T09:00:00' },
      ]),
    ),
  );
});

afterEach(() => {
  restoreViewport();
});

function renderSection() {
  return renderWithProviders(
    <EquivalenceActionsProvider studentId={9} programEnrollmentId={ENROLLMENT}>
      <EquivalencesSection programEnrollmentId={ENROLLMENT} />
      <EquivalenceMobileBar programEnrollmentId={ENROLLMENT} />
    </EquivalenceActionsProvider>,
  );
}

async function openRecordDialog(user) {
  await user.click(await screen.findByRole('button', { name: 'Cargar equivalencia' }));
  return screen.findByRole('dialog', { name: 'Cargar equivalencia' });
}

async function fillForm(user, overrides = {}) {
  const values = {
    subject: '2',
    institution: 'Universidad Nacional de Cuyo',
    originSubject: 'Análisis Matemático',
    score: '6',
    date: '2026-09-15',
    resolution: 'RES-2026-200',
    reason: 'Contenidos y carga horaria equivalentes.',
    ...overrides,
  };
  await screen.findByRole('option', { name: 'Análisis (ANA201)' });
  await user.selectOptions(screen.getByLabelText('Materia del plan'), values.subject);
  await user.type(screen.getByLabelText('Institución de origen'), values.institution);
  await user.type(screen.getByLabelText('Materia de origen'), values.originSubject);
  if (values.score) await user.type(screen.getByLabelText(/^Nota de origen/), values.score);
  fireEvent.change(screen.getByLabelText('Fecha de reconocimiento'), { target: { value: values.date } });
  await user.type(screen.getByLabelText('Resolución'), values.resolution);
  await user.type(screen.getByLabelText('Motivo'), values.reason);
}

describe('equivalence recording access by role', () => {
  it.each([['PRECEPTOR'], ['SUBDIRECTOR']])('hides the record button from %s', async (role) => {
    auth.roles = [role];
    renderSection();

    expect(await screen.findByText('Sin equivalencias')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cargar equivalencia' })).not.toBeInTheDocument();
  });

  it.each([['ADMINISTRATIVO'], ['DIRECTOR']])('shows the record button to %s', async (role) => {
    auth.roles = [role];
    renderSection();

    expect(await screen.findByRole('button', { name: 'Cargar equivalencia' })).toBeEnabled();
  });
});

describe('equivalence record form', () => {
  it('lists every client validation error in an alert summary with links to the fields', async () => {
    const user = userEvent.setup();
    renderSection();
    const dialog = await openRecordDialog(user);

    await user.click(within(dialog).getByRole('button', { name: 'Revisar' }));

    const summary = await within(dialog).findByRole('alert');
    expect(within(summary).getByText('Revisá estos datos para continuar')).toBeInTheDocument();
    expect(screen.getByLabelText('Institución de origen')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Resolución')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Motivo')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Motivo')).toHaveAccessibleDescription(/al menos 10 caracteres/);
    await user.click(within(summary).getByRole('link', { name: 'Resolución' }));
    expect(screen.getByLabelText('Resolución')).toHaveFocus();
  });

  it('rejects a score out of range, a future date and a short reason', async () => {
    const user = userEvent.setup();
    renderSection();
    const dialog = await openRecordDialog(user);
    await fillForm(user, { score: '11', date: '2999-01-01', reason: 'corto' });

    await user.click(within(dialog).getByRole('button', { name: 'Revisar' }));

    expect(await screen.findByText('La nota debe estar entre 1 y 10, con hasta 2 decimales.', { selector: 'p' })).toBeInTheDocument();
    expect(screen.getByText('La fecha de reconocimiento no puede ser futura.', { selector: 'p' })).toBeInTheDocument();
    expect(screen.getByLabelText(/^Nota de origen/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Fecha de reconocimiento')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Motivo')).toHaveAttribute('aria-invalid', 'true');
  });

  it('shows the Hoy, Nuevo and Diferencia comparison and the unlocked subjects in step 2', async () => {
    const user = userEvent.setup();
    renderSection();
    const dialog = await openRecordDialog(user);
    expect(within(dialog).getByText('Paso 1 de 2')).toBeInTheDocument();
    await fillForm(user);

    await user.click(within(dialog).getByRole('button', { name: 'Revisar' }));

    expect(await within(dialog).findByText('Paso 2 de 2')).toBeInTheDocument();
    const table = within(dialog).getByRole('table');
    ['Hoy', 'Nuevo', 'Diferencia'].forEach((name) => expect(within(table).getByRole('columnheader', { name })).toBeInTheDocument());
    const cells = (name) => within(within(table).getByRole('row', { name })).getAllByRole('cell').map((cell) => cell.textContent);
    expect(cells(/Materias aprobadas/)).toEqual(['1', '2', '+1']);
    expect(cells(/Créditos/)).toEqual(['3', '6', '+3']);
    expect(cells(/Avance del plan/)).toEqual(['25 %', '50 %', '+25 %']);
    expect(cells(/Promedio/)).toEqual(['8,00', '7,00', '−1,00']);
    expect(cells(/Estado de la materia/)).toEqual(['Disponible', 'Aprobada', 'Aprobada por equivalencia']);
    expect(within(dialog).getByRole('heading', { name: 'Materias que se desbloquean' })).toBeInTheDocument();
    expect(within(dialog).getByText('Física')).toBeInTheDocument();
  });

  it('records the equivalence, refreshes progress and equivalences and returns focus to the trigger', async () => {
    const user = userEvent.setup();
    renderSection();
    const dialog = await openRecordDialog(user);
    await fillForm(user);
    await user.selectOptions(screen.getByLabelText(/^Documento del legajo/), '51');
    await user.click(within(dialog).getByRole('button', { name: 'Revisar' }));
    await screen.findByText('Paso 2 de 2');
    const progressBefore = counters.progress;
    const equivalencesBefore = counters.equivalences;

    await user.click(within(dialog).getByRole('button', { name: 'Cargar equivalencia' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(recorded).toEqual({
      subjectId: 20,
      originInstitution: 'Universidad Nacional de Cuyo',
      originSubjectName: 'Análisis Matemático',
      originScore: 6,
      recognizedOn: '2026-09-15',
      resolutionNumber: 'RES-2026-200',
      studentDocumentId: 51,
      reason: 'Contenidos y carga horaria equivalentes.',
    });
    await waitFor(() => expect(counters.progress).toBeGreaterThan(progressBefore));
    await waitFor(() => expect(counters.equivalences).toBeGreaterThan(equivalencesBefore));
    expect(screen.getByRole('button', { name: 'Cargar equivalencia' })).toHaveFocus();
  });

  it('keeps the dialog locked while saving: no cancel, no Escape, no close button, announced as Guardando', async () => {
    const user = userEvent.setup();
    let release;
    const gate = new Promise((resolve) => {
      release = resolve;
    });
    server.use(
      http.post(`${API}/program-enrollments/:id/equivalences`, async () => {
        await gate;
        return HttpResponse.json(subjectEquivalence({ id: 310 }), { status: 201 });
      }),
    );
    renderSection();
    const dialog = await openRecordDialog(user);
    await fillForm(user);
    await user.click(within(dialog).getByRole('button', { name: 'Revisar' }));
    await screen.findByText('Paso 2 de 2');

    await user.click(within(dialog).getByRole('button', { name: 'Cargar equivalencia' }));

    expect(await within(dialog).findByRole('button', { name: 'Guardando…' })).toBeDisabled();
    expect(within(dialog).getAllByText('Guardando…').length).toBeGreaterThan(0);
    expect(within(dialog).getByRole('button', { name: 'Cancelar' })).toBeDisabled();
    expect(within(dialog).getByRole('button', { name: 'Cerrar ventana' })).toBeDisabled();
    await user.keyboard('{Escape}');
    expect(screen.getByRole('dialog', { name: 'Cargar equivalencia' })).toBeInTheDocument();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    release();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('asks to discard what was typed before closing with Escape or the close button', async () => {
    const user = userEvent.setup();
    renderSection();
    const dialog = await openRecordDialog(user);
    await user.type(await screen.findByLabelText('Institución de origen'), 'Universidad');

    await user.keyboard('{Escape}');
    const confirm = await screen.findByRole('alertdialog', { name: '¿Descartar lo cargado?' });
    await user.click(within(confirm).getByRole('button', { name: 'Seguir editando' }));
    expect(screen.getByLabelText('Institución de origen')).toHaveValue('Universidad');

    await user.click(within(dialog).getByRole('button', { name: 'Cerrar ventana' }));
    await user.click(await screen.findByRole('button', { name: 'Descartar' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('closes an untouched form without asking', async () => {
    const user = userEvent.setup();
    renderSection();
    await openRecordDialog(user);

    await user.keyboard('{Escape}');

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it.each([
    ['EQUIVALENCE_ALREADY_ACTIVE', 'La materia ya tiene una equivalencia vigente.'],
    ['SUBJECT_ALREADY_APPROVED', 'La materia ya está aprobada y no se puede volver a cursar.'],
    ['SUBJECT_IN_PROGRESS', 'La materia se está cursando: primero hay que dar de baja la cursada.'],
    ['ENROLLMENT_NOT_ACTIVE', 'La inscripción a la carrera no está activa.'],
  ])('shows %s as a conflict, refreshes the data and offers Cerrar', async (code, message) => {
    const user = userEvent.setup();
    server.use(http.post(`${API}/program-enrollments/:id/equivalences`, () => failure(409, code)));
    renderSection();
    const dialog = await openRecordDialog(user);
    await fillForm(user);
    await user.click(within(dialog).getByRole('button', { name: 'Revisar' }));
    await screen.findByText('Paso 2 de 2');
    const progressBefore = counters.progress;

    await user.click(within(dialog).getByRole('button', { name: 'Cargar equivalencia' }));

    const alert = await within(dialog).findByRole('alert');
    expect(alert).toHaveTextContent(message);
    expect(alert).not.toHaveTextContent(code);
    expect(alert).not.toHaveTextContent('(');
    expect(within(dialog).getByRole('button', { name: 'Cerrar' })).toBeEnabled();
    expect(within(dialog).queryByRole('button', { name: 'Cargar equivalencia' })).not.toBeInTheDocument();
    await waitFor(() => expect(counters.progress).toBeGreaterThan(progressBefore));
  });

  it.each([
    ['INVALID_SCORE', /^Nota de origen/],
    ['EQUIVALENCE_REASON_INVALID', 'Motivo'],
    ['EQUIVALENCE_DOCUMENT_INVALID', /^Documento del legajo/],
    ['SUBJECT_NOT_IN_PLAN', 'Materia del plan'],
  ])('maps the 422 %s to its field and keeps it invalid back in step 1', async (code, label) => {
    const user = userEvent.setup();
    server.use(http.post(`${API}/program-enrollments/:id/equivalences`, () => failure(422, code)));
    renderSection();
    const dialog = await openRecordDialog(user);
    await fillForm(user);
    await user.selectOptions(screen.getByLabelText(/^Documento del legajo/), '51');
    await user.click(within(dialog).getByRole('button', { name: 'Revisar' }));
    await screen.findByText('Paso 2 de 2');

    await user.click(within(dialog).getByRole('button', { name: 'Cargar equivalencia' }));

    await screen.findByText('Paso 1 de 2');
    expect(screen.getByLabelText(label)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText(label)).toHaveFocus();
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Revisá este dato para continuar');
  });

  it('shows a network failure inside the dialog and lets the user retry', async () => {
    const user = userEvent.setup();
    server.use(http.post(`${API}/program-enrollments/:id/equivalences`, () => HttpResponse.error()));
    renderSection();
    const dialog = await openRecordDialog(user);
    await fillForm(user);
    await user.click(within(dialog).getByRole('button', { name: 'Revisar' }));
    await screen.findByText('Paso 2 de 2');

    await user.click(within(dialog).getByRole('button', { name: 'Cargar equivalencia' }));

    expect(await within(dialog).findByRole('alert')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Cargar equivalencia' })).toBeEnabled();
    expect(within(dialog).getByRole('button', { name: 'Cancelar' })).toBeEnabled();
    expect(takeContractDeviations()).toEqual([{ key: 'POST /program-enrollments/{id}/equivalences', violations: [expect.stringMatching(/^status 0 /)] }]);
  });

  it('turns the buttons off with a visible reason after a 403', async () => {
    const user = userEvent.setup();
    server.use(http.post(`${API}/program-enrollments/:id/equivalences`, () => failure(403, 'FORBIDDEN')));
    renderSection();
    const dialog = await openRecordDialog(user);
    await fillForm(user);
    await user.click(within(dialog).getByRole('button', { name: 'Revisar' }));
    await screen.findByText('Paso 2 de 2');

    await user.click(within(dialog).getByRole('button', { name: 'Cargar equivalencia' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('No tenés permiso para cargar equivalencias.');
    expect(within(dialog).getByRole('button', { name: 'Cargar equivalencia' })).toBeDisabled();
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    await user.click(await screen.findByRole('button', { name: 'Descartar' }));
    const trigger = await screen.findByRole('button', { name: 'Cargar equivalencia' });
    expect(trigger).toBeDisabled();
    expect(trigger).toHaveAccessibleDescription('No tenés permiso para cargar equivalencias');
  });
});

describe('equivalence document picker', () => {
  async function openPicker(user) {
    renderSection();
    await openRecordDialog(user);
    return screen.findByLabelText(/^Documento del legajo/);
  }

  it('lists the student documents with a translated type and upload date', async () => {
    const user = userEvent.setup();
    const picker = await openPicker(user);

    expect(await within(picker).findByRole('option', { name: 'Analítico secundario · subido el 10/03/2026' })).toBeInTheDocument();
    expect(within(picker).getByRole('option', { name: 'DNI frente · subido el 11/03/2026' })).toBeInTheDocument();
    expect(picker).toBeEnabled();
  });

  it('shows a loading state while the documents arrive', async () => {
    const user = userEvent.setup();
    server.use(http.get(`${API}/students/:id/documents`, () => new Promise(() => {})));
    const picker = await openPicker(user);

    expect(picker).toBeDisabled();
    expect(picker).toHaveAttribute('aria-busy', 'true');
    expect(within(picker).getByRole('option', { name: 'Cargando documentos…' })).toBeInTheDocument();
  });

  it('isolates a load error: the form keeps working and the picker can retry', async () => {
    const user = userEvent.setup();
    server.use(http.get(`${API}/students/:id/documents`, () => failure(500, 'INTERNAL_ERROR')));
    const picker = await openPicker(user);

    expect(await screen.findByText(/No se pudieron cargar los documentos/)).toBeInTheDocument();
    expect(picker).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeEnabled();
    expect(screen.getByLabelText('Institución de origen')).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Revisar' })).toBeEnabled();
  });

  it('explains an empty legajo', async () => {
    const user = userEvent.setup();
    server.use(http.get(`${API}/students/:id/documents`, () => HttpResponse.json([])));
    await openPicker(user);

    expect(await screen.findByText('El legajo todavía no tiene documentos subidos.')).toBeInTheDocument();
  });

  it('explains a 403 on the legajo and still lets the user continue without a document', async () => {
    const user = userEvent.setup();
    server.use(http.get(`${API}/students/:id/documents`, () => failure(403, 'FORBIDDEN')));
    await openPicker(user);

    expect(await screen.findByText(/No tenés permiso para ver los documentos del legajo/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Revisar' })).toBeEnabled();
  });
});

describe('equivalence record on a compact viewport', () => {
  it('shows a single record action in the sticky bar with the active count and opens a bottom sheet', async () => {
    useCompactViewport();
    const user = userEvent.setup();
    server.use(http.get(`${API}/program-enrollments/:id/equivalences`, () => HttpResponse.json([subjectEquivalence()])));
    renderSection();

    const bar = await screen.findByRole('region', { name: 'Acciones de equivalencias' });
    expect(await within(bar).findByText('1 equivalencia vigente')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Cargar equivalencia' })).toHaveLength(1);

    await user.click(within(bar).getByRole('button', { name: 'Cargar equivalencia' }));

    const sheet = await screen.findByRole('dialog', { name: 'Cargar equivalencia' });
    expect(sheet).toHaveAttribute('data-side', 'bottom');
    expect(within(sheet).getByText('Paso 1 de 2')).toBeInTheDocument();
  });
});

describe('equivalence void', () => {
  const activeEquivalence = {
    id: 301,
    subjectId: 11,
    subjectCode: 'MAT-101',
    subjectName: 'Matemática I',
    credits: 6,
    inCurrentPlan: true,
    originInstitution: 'Universidad Nacional de Cuyo',
    originSubjectName: 'Análisis Matemático I',
    originScore: 8,
    resolutionNumber: 'RES-2026-114',
    recognizedOn: '2026-09-15',
    reason: 'Equivalencia por contenidos compatibles.',
    document: null,
    status: 'ACTIVE',
    recordedBy: { personId: 21, name: 'Marta Gómez' },
    recordedAt: '2026-09-15T10:30:00',
    voidedBy: null,
    voidedAt: null,
    voidReason: null,
  };
  let voidBody;

  beforeEach(() => {
    voidBody = null;
    server.use(
      http.get(`${API}/program-enrollments/:id/equivalences`, () => HttpResponse.json([activeEquivalence])),
      http.post(`${API}/program-enrollments/:id/equivalences/:equivalenceId/void`, async ({ request }) => {
        voidBody = await request.json();
        return HttpResponse.json({ ...activeEquivalence, status: 'VOIDED' });
      }),
    );
  });

  async function openVoidDialog(user) {
    renderSection();
    await user.click(await screen.findByRole('button', { name: /^Ver detalle de/ }));
    await user.click(await screen.findByRole('button', { name: 'Anular equivalencia' }));
    return screen.findByRole('dialog', { name: 'Anular equivalencia' });
  }

  it('does not offer to void to roles without the buttons', async () => {
    auth.roles = ['PRECEPTOR'];
    const user = userEvent.setup();
    renderSection();

    await user.click(await screen.findByRole('button', { name: /^Ver detalle de/ }));

    expect(await screen.findByText('Carga')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Anular equivalencia' })).not.toBeInTheDocument();
  });

  it('requires a reason of at least 10 characters and warns about dependent subjects', async () => {
    const user = userEvent.setup();
    const dialog = await openVoidDialog(user);
    expect(within(dialog).getByText(/Las materias dependientes en curso o aprobadas no se modifican/)).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText('Motivo de la anulación'), 'corto');
    await user.click(within(dialog).getByRole('button', { name: 'Anular equivalencia' }));

    const reason = await within(dialog).findByLabelText('Motivo de la anulación');
    expect(reason).toHaveAttribute('aria-invalid', 'true');
    expect(reason).toHaveAccessibleDescription(/al menos 10 caracteres/);
    expect(voidBody).toBeNull();
  });

  it('voids with the reason, closes and returns focus to the section heading when the row leaves the active list', async () => {
    const user = userEvent.setup();
    const dialog = await openVoidDialog(user);

    await user.type(within(dialog).getByLabelText('Motivo de la anulación'), 'Cargada contra la materia equivocada.');
    await user.click(within(dialog).getByRole('button', { name: 'Anular equivalencia' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(voidBody).toEqual({ reason: 'Cargada contra la materia equivocada.' });
  });

  it('closes without asking for discard even with a reason typed', async () => {
    const user = userEvent.setup();
    const dialog = await openVoidDialog(user);
    await user.type(within(dialog).getByLabelText('Motivo de la anulación'), 'Cargada contra la materia equivocada.');

    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('shows the own message for an already voided equivalence and offers Cerrar', async () => {
    const user = userEvent.setup();
    server.use(http.post(`${API}/program-enrollments/:id/equivalences/:equivalenceId/void`, () => failure(409, 'EQUIVALENCE_ALREADY_VOIDED')));
    const dialog = await openVoidDialog(user);
    await user.type(within(dialog).getByLabelText('Motivo de la anulación'), 'Cargada contra la materia equivocada.');

    await user.click(within(dialog).getByRole('button', { name: 'Anular equivalencia' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('La equivalencia ya estaba anulada.');
    expect(within(dialog).getByRole('button', { name: 'Cerrar' })).toBeEnabled();
  });

  it('maps a server reason error to the field', async () => {
    const user = userEvent.setup();
    server.use(http.post(`${API}/program-enrollments/:id/equivalences/:equivalenceId/void`, () => failure(422, 'EQUIVALENCE_REASON_INVALID')));
    const dialog = await openVoidDialog(user);
    await user.type(within(dialog).getByLabelText('Motivo de la anulación'), 'Cargada contra la materia equivocada.');

    await user.click(within(dialog).getByRole('button', { name: 'Anular equivalencia' }));

    await waitFor(() => expect(within(dialog).getByLabelText('Motivo de la anulación')).toHaveAttribute('aria-invalid', 'true'));
    expect(within(dialog).getByText('El motivo debe tener entre 10 y 500 caracteres.')).toBeInTheDocument();
  });

  it('turns the void action off with its own reason after a 403', async () => {
    const user = userEvent.setup();
    server.use(http.post(`${API}/program-enrollments/:id/equivalences/:equivalenceId/void`, () => failure(403, 'FORBIDDEN')));
    const dialog = await openVoidDialog(user);
    await user.type(within(dialog).getByLabelText('Motivo de la anulación'), 'Cargada contra la materia equivocada.');

    await user.click(within(dialog).getByRole('button', { name: 'Anular equivalencia' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('No tenés permiso para anular equivalencias.');
    expect(within(dialog).getByRole('button', { name: 'Anular equivalencia' })).toBeDisabled();
  });
});

describe('equivalence record from the subject detail', () => {
  function renderMap() {
    return renderWithProviders(
      <EquivalenceActionsProvider studentId={9} programEnrollmentId={ENROLLMENT}>
        <CurriculumMap subjects={progress.subjects} statusFilter="todas" onStatusFilterChange={() => {}} creditsDefined audience="staff" />
        <EquivalencesSection programEnrollmentId={ENROLLMENT} />
      </EquivalenceActionsProvider>,
    );
  }

  it('opens the form with the subject already chosen and returns focus to its card on cancel', async () => {
    const user = userEvent.setup();
    renderMap();
    const card = await screen.findByRole('button', { name: /Análisis/ });

    await user.click(card);
    await user.click(await screen.findByRole('button', { name: 'Cargar equivalencia', description: '' }));

    const dialog = await screen.findByRole('dialog', { name: 'Cargar equivalencia' });
    await waitFor(() => expect(within(dialog).getByLabelText('Materia del plan')).toHaveValue('2'));
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(card).toHaveFocus();
  });

  it('does not offer to record on a subject that is being taken', async () => {
    const user = userEvent.setup();
    renderMap();

    await user.click(await screen.findByRole('button', { name: /Química/ }));

    await screen.findByRole('dialog', { name: 'Química' });
    expect(within(screen.getByRole('dialog', { name: 'Química' })).queryByRole('button', { name: 'Cargar equivalencia' })).not.toBeInTheDocument();
  });
});
