import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Toaster } from 'sonner';
import { axe } from '@/test/axe';
import {
  approvedProcedure,
  approvedSubjectsCertificate,
  ineligibleProcedure,
  ownDocument,
  procedureEligibility,
} from '@/test/fixtures/constancias';
import { errorBody } from '@/test/fixtures/errors';
import { contractList } from '@/test/fixtures/fromContract';
import { sectionEnrollment } from '@/test/fixtures/studentHome';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { StudentProceduresSection } from './StudentProceduresSection';

const API = 'http://localhost:8080';

const pdf = () => new HttpResponse(new Uint8Array([37, 80, 68, 70]), { headers: { 'Content-Type': 'application/pdf' } });

const examBoard = (overrides) => ({
  id: 9,
  status: 'OPEN',
  subjectName: 'Anatomía II',
  examDate: '2020-11-18T09:00:00',
  cancelReason: null,
  canceledAt: null,
  classroom: null,
  courseSubjectId: 1,
  courseSubjectName: 'Anatomía II',
  registrationEnd: null,
  registrationStart: null,
  rescheduledFrom: null,
  subjectId: 1,
  termId: 1,
  tribunal: [],
  tribunalInstructorIds: [],
  ...overrides,
});

const registrations = [
  {
    id: 55,
    enrollmentId: 5,
    status: 'REGISTERED',
    score: 7,
    examBoard: examBoard({ id: 9 }),
  },
  {
    id: 56,
    enrollmentId: 5,
    status: 'WITHDRAWN',
    score: 0,
    examBoard: examBoard({ id: 10, subjectName: 'Química', examDate: '2020-11-10T09:00:00' }),
  },
];

function useEligibility(byType) {
  server.use(
    http.get(`${API}/me/procedures/:type/eligibility`, ({ params }) =>
      HttpResponse.json(byType[params.type] ?? procedureEligibility({ type: params.type })),
    ),
  );
}

const sectionEnrollmentRows = [
  sectionEnrollment({ id: 100, programEnrollmentId: 5, subjectName: 'Bases de Datos', sectionName: 'B', status: 'IN_PROGRESS' }),
  sectionEnrollment({ id: 101, programEnrollmentId: 6, subjectName: 'Anatomía II', sectionName: 'A', status: 'IN_PROGRESS' }),
  sectionEnrollment({ id: 102, programEnrollmentId: 5, subjectName: 'Química', sectionName: 'A', status: 'APPROVED' }),
];

const sectionCandidates = contractList('/me/section-enrollments/{id}/change-section-candidates', [
  { sectionId: 1, name: 'B', availableSeats: 3, slots: [], selectable: false, blockReason: 'CURRENT_SECTION', blockDetail: null },
  {
    sectionId: 2,
    name: 'C',
    availableSeats: 4,
    slots: [{ dayOfWeek: 'SATURDAY', startTime: '09:00:00', endTime: '13:00:00', classroomName: 'Aula 2' }],
    selectable: true,
    blockReason: null,
    blockDetail: null,
  },
]);

function useSectionEnrollments() {
  server.use(
    http.get(`${API}/me/section-enrollments`, () => HttpResponse.json(sectionEnrollmentRows)),
    http.get(`${API}/me/section-enrollments/:id/change-section-candidates`, () => HttpResponse.json(sectionCandidates)),
  );
}

function renderSection() {
  return renderWithProviders(
    <>
      <StudentProceduresSection carreraId={5} onSeeStudyPlan={() => {}} />
      <Toaster />
    </>,
  );
}

async function generateButtonOf(title) {
  const row = (await screen.findByText(title)).closest('li');
  return within(row).findByRole('button', { name: 'Generar' });
}

beforeEach(() => {
  URL.createObjectURL = vi.fn(() => 'blob:procedure');
  URL.revokeObjectURL = vi.fn();
  server.use(
    http.get(`${API}/me/exam-board-registrations`, () => HttpResponse.json(registrations)),
    http.get(`${API}/me/procedures/approved/:documentType/:id/pdf`, () => pdf()),
  );
});

describe('StudentProceduresSection', () => {
  it('lists the four automatic procedures with Generar when eligible', async () => {
    renderSection();

    for (const title of [
      'Constancia de alumno regular',
      'Constancia de inscripción a examen',
      'Constancia de examen',
      'Constancia de materias aprobadas',
    ]) {
      expect(await screen.findByText(title)).toBeInTheDocument();
    }
    expect(await screen.findAllByRole('button', { name: 'Generar' })).toHaveLength(4);
    expect(screen.getByText('Cambio de comisión')).toBeInTheDocument();
    expect(screen.getByText('Solicitud de egreso')).toBeInTheDocument();
  });

  it('shows the reasons with their action link instead of a disabled button when not eligible', async () => {
    useEligibility({
      REGULAR_STUDENT_CERTIFICATE: ineligibleProcedure({ type: 'REGULAR_STUDENT_CERTIFICATE' }),
    });
    renderSection();

    expect(await screen.findByText('Tenés pagos pendientes')).toBeInTheDocument();
    expect(screen.getByText('Todavía no sos alumno regular')).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByRole('button', { name: 'Generar' })).toHaveLength(3));
  });

  it('links to the exam tables when the student is not registered for an exam', async () => {
    useEligibility({
      EXAM_REGISTRATION_CERTIFICATE: procedureEligibility({
        type: 'EXAM_REGISTRATION_CERTIFICATE',
        eligible: false,
        reasons: [
          {
            code: 'NOT_REGISTERED_FOR_EXAM',
            message: 'No estás inscripto a ninguna mesa con fecha.',
            nextStep: { kind: 'REGISTER_FOR_EXAM', label: 'Ver mesas', path: null },
          },
        ],
      }),
    });
    renderSection();

    const link = await screen.findByRole('link', { name: 'Ver mesas de examen' });
    expect(link).toHaveAttribute('href', '/alumno/mis-carreras?carrera=5&tab=mesas-de-examen');
  });

  it('points to Trámites aprobados when the daily limit was reached', async () => {
    useEligibility({
      REGULAR_STUDENT_CERTIFICATE: procedureEligibility({
        eligible: false,
        reasons: [
          {
            code: 'ISSUANCE_LIMIT_REACHED',
            message: 'Ya generaste hoy esta constancia.',
            nextStep: { kind: 'TRY_AGAIN_LATER', label: 'Probá mañana', path: null },
          },
        ],
      }),
    });
    const user = userEvent.setup();
    renderSection();

    await user.click(await screen.findByRole('button', { name: 'Ver Trámites aprobados' }));

    expect(await screen.findByText('Constancia de examen')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Trámites aprobados/, selected: true })).toBeInTheDocument();
  });

  it('generates the regular certificate with the recipient and downloads it', async () => {
    const posted = [];
    server.use(
      http.post(`${API}/me/documents/:type`, async ({ params, request }) => {
        posted.push({ type: params.type, body: await request.json() });
        return HttpResponse.json(ownDocument({ documentType: params.type, id: 70 }), { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(await generateButtonOf('Constancia de alumno regular'));
    expect(screen.getByText('La constancia va a incluir')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Obra social' }));
    await user.type(screen.getByLabelText(/Nombre de la obra social/), 'OSDE');
    expect(screen.getByText('4/80')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Generar y descargar' }));

    expect(await screen.findByText('Tu constancia está lista.')).toBeInTheDocument();
    expect(posted).toEqual([
      {
        type: 'REGULAR_STUDENT_CERTIFICATE',
        body: { enrollmentId: 5, recipientType: 'HEALTH_INSURANCE', recipientText: 'OSDE', registrationId: null },
      },
    ]);
    expect(URL.createObjectURL).toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Volver a Trámites' }));
    expect(await screen.findByText('Cambio de comisión')).toBeInTheDocument();
  });

  it('does not offer a recipient on the approved subjects certificate and posts the enrollment', async () => {
    const posted = [];
    server.use(
      http.post(`${API}/me/approved-subjects-certificates`, async ({ request }) => {
        posted.push(await request.json());
        return HttpResponse.json(approvedSubjectsCertificate(), { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(await generateButtonOf('Constancia de materias aprobadas'));
    expect(screen.queryByText(/¿Para quién es\?/)).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Generar y descargar' }));

    expect(await screen.findByText('Tu constancia está lista.')).toBeInTheDocument();
    expect(posted).toEqual([{ enrollmentId: 5 }]);
  });

  it('requires choosing an exam and sends its registration id', async () => {
    const posted = [];
    server.use(
      http.post(`${API}/me/documents/:type`, async ({ request }) => {
        posted.push(await request.json());
        return HttpResponse.json(ownDocument({ documentType: 'EXAM_CERTIFICATE', id: 71 }), { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(await generateButtonOf('Constancia de examen'));
    expect(await screen.findByText('Anatomía II')).toBeInTheDocument();
    expect(screen.queryByText('Química')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Generar y descargar' })).toBeDisabled();
    await user.click(screen.getByRole('radio'));
    await user.click(screen.getByRole('button', { name: 'Generar y descargar' }));

    expect(await screen.findByText('Tu constancia está lista.')).toBeInTheDocument();
    expect(posted).toEqual([{ enrollmentId: 5, recipientType: null, recipientText: null, registrationId: 55 }]);
  });

  it('limits the recipient text to 80 characters', async () => {
    const user = userEvent.setup();
    renderSection();

    await user.click(await generateButtonOf('Constancia de alumno regular'));
    await user.click(screen.getByRole('button', { name: 'Otro' }));
    await user.click(screen.getByLabelText(/¿Para quién es\?/, { selector: 'input' }));
    await user.paste('a'.repeat(95));

    expect(screen.getByText('80/80')).toBeInTheDocument();
  });

  it('shows the daily limit message on 429', async () => {
    server.use(
      http.post(`${API}/me/documents/:type`, () => HttpResponse.json(errorBody('ISSUANCE_LIMIT_REACHED'), { status: 429 })),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(await generateButtonOf('Constancia de alumno regular'));
    await user.click(screen.getByRole('button', { name: 'Generar y descargar' }));

    expect(await screen.findByText(/Ya generaste hoy esta constancia/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Generar y descargar' })).toBeEnabled();
  });

  it('tells the student to contact Administration on debt without amounts', async () => {
    server.use(
      http.post(`${API}/me/documents/:type`, () =>
        HttpResponse.json(errorBody('DOCUMENT_REQUIRES_PAYMENTS_UP_TO_DATE'), { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(await generateButtonOf('Constancia de alumno regular'));
    await user.click(screen.getByRole('button', { name: 'Generar y descargar' }));

    expect(await screen.findByText(/Comunicate con Administración/)).toBeInTheDocument();
    expect(screen.queryByText(/\$/)).not.toBeInTheDocument();
  });

  it('shows a generic error when generation fails', async () => {
    server.use(http.post(`${API}/me/documents/:type`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })));
    const user = userEvent.setup();
    renderSection();

    await user.click(await generateButtonOf('Constancia de alumno regular'));
    await user.click(screen.getByRole('button', { name: 'Generar y descargar' }));

    expect(await screen.findByText(/Tuvimos un problema de nuestro lado/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Generar y descargar' })).toBeEnabled();
    expect(screen.queryByText('Tu constancia está lista.')).not.toBeInTheDocument();
  });

  it('downloads valid approved procedures and regenerates expired ones', async () => {
    const user = userEvent.setup();
    renderSection();

    await user.click(await screen.findByRole('tab', { name: /Trámites aprobados/ }));
    const valid = (await screen.findByText('Constancia de alumno regular')).closest('li');
    await user.click(within(valid).getByRole('button', { name: 'Descargar PDF' }));
    await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalled());

    const expired = screen.getByText('Constancia de materias aprobadas').closest('li');
    expect(within(expired).getByText('Vencida')).toBeInTheDocument();
    await user.click(within(expired).getByRole('button', { name: 'Generar de nuevo' }));
    expect(await screen.findByRole('button', { name: 'Generar y descargar' })).toBeInTheDocument();
  });

  it.each(['ISSUED_BY_STAFF', 'ISSUED_BY_TEACHER'])('never shows an exception state for %s procedures', async (origin) => {
    server.use(http.get(`${API}/me/procedures/approved`, () => HttpResponse.json([approvedProcedure({ origin, detail: null })])));
    const user = userEvent.setup();
    renderSection();

    await user.click(await screen.findByRole('tab', { name: /Trámites aprobados/ }));

    expect(await screen.findByRole('button', { name: 'Descargar PDF' })).toBeInTheDocument();
    expect(screen.queryByText(/excepción/i)).not.toBeInTheDocument();
  });

  it('shows a retry on the row when the eligibility check fails and recovers', async () => {
    let failing = true;
    server.use(
      http.get(`${API}/me/procedures/REGULAR_STUDENT_CERTIFICATE/eligibility`, () =>
        failing
          ? HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })
          : HttpResponse.json(procedureEligibility()),
      ),
    );
    const user = userEvent.setup();
    renderSection();

    const retry = await screen.findByRole('button', { name: 'Reintentar' }, { timeout: 8000 });
    failing = false;
    await user.click(retry);

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument());
    await waitFor(() => expect(screen.getAllByRole('button', { name: 'Generar' })).toHaveLength(4));
  });

  it('keeps the form and shows an error when the service is unavailable', async () => {
    server.use(
      http.post(`${API}/me/documents/:type`, () => HttpResponse.json(errorBody('SERVICE_UNAVAILABLE'), { status: 503 })),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(await generateButtonOf('Constancia de alumno regular'));
    await user.click(screen.getByRole('button', { name: 'Generar y descargar' }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Generar y descargar' })).toBeEnabled());
    expect(screen.queryByText('Tu constancia está lista.')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeEnabled();
  });

  it('opens the change of section sheet from the Cambio de comisión row', async () => {
    useSectionEnrollments();
    const user = userEvent.setup();
    renderSection();

    const row = (await screen.findByText('Cambio de comisión')).closest('li');
    expect(within(row).queryByRole('link', { name: 'Abrir' })).not.toBeInTheDocument();
    await user.click(within(row).getByRole('button', { name: 'Abrir' }));

    const sheet = await screen.findByRole('dialog');
    expect(within(sheet).getByText('Cambiar de comisión')).toBeInTheDocument();
    expect(await within(sheet).findByText('Bases de Datos')).toBeInTheDocument();
    expect(within(sheet).queryByText('Anatomía II')).not.toBeInTheDocument();
  });

  it('changes the section without asking for a reason and refreshes enrollments and procedures', async () => {
    useSectionEnrollments();
    const posted = [];
    let enrollmentRequests = 0;
    let approvedRequests = 0;
    server.use(
      http.get(`${API}/me/section-enrollments`, () => {
        enrollmentRequests += 1;
        return HttpResponse.json(sectionEnrollmentRows);
      }),
      http.get(`${API}/me/procedures/approved`, () => {
        approvedRequests += 1;
        return HttpResponse.json([]);
      }),
      http.post(`${API}/me/section-enrollments/:id/change-section`, async ({ params, request }) => {
        posted.push({ id: params.id, body: await request.json() });
        return HttpResponse.json(sectionEnrollment({ id: 100, sectionId: 2 }));
      }),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(within((await screen.findByText('Cambio de comisión')).closest('li')).getByRole('button', { name: 'Abrir' }));
    await user.click(await screen.findByRole('button', { name: /Bases de Datos/ }));
    await user.click(await screen.findByRole('radio', { name: /Com\. C/ }));
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    expect(screen.queryByLabelText('Motivo')).not.toBeInTheDocument();
    const before = { enrollmentRequests, approvedRequests };
    await user.click(screen.getByRole('button', { name: 'Confirmar el cambio' }));

    await waitFor(() => expect(posted).toEqual([{ id: '100', body: { newSectionId: 2 } }]));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await waitFor(() => expect(enrollmentRequests).toBeGreaterThan(before.enrollmentRequests));
    await waitFor(() => expect(approvedRequests).toBeGreaterThan(before.approvedRequests));
    expect(await screen.findByText('Comisión cambiada')).toBeInTheDocument();
  });

  it('keeps the sheet open and shows the SECTION_HAS_RECORDS message on confirm', async () => {
    useSectionEnrollments();
    server.use(
      http.post(`${API}/me/section-enrollments/:id/change-section`, () =>
        HttpResponse.json(errorBody('SECTION_HAS_RECORDS'), { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(within((await screen.findByText('Cambio de comisión')).closest('li')).getByRole('button', { name: 'Abrir' }));
    await user.click(await screen.findByRole('button', { name: /Bases de Datos/ }));
    await user.click(await screen.findByRole('radio', { name: /Com\. C/ }));
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await user.click(screen.getByRole('button', { name: 'Confirmar el cambio' }));

    expect(await screen.findByText('Para cambiar de comisión hablá con Administración.')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Elegir otra comisión' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirmar el cambio' })).toBeDisabled();
  });

  it('clears the selection, refetches the candidates and warns when the section filled up while confirming', async () => {
    let candidateRequests = 0;
    server.use(
      http.get(`${API}/me/section-enrollments`, () => HttpResponse.json(sectionEnrollmentRows)),
      http.get(`${API}/me/section-enrollments/:id/change-section-candidates`, () => {
        candidateRequests += 1;
        if (candidateRequests === 1) return HttpResponse.json(sectionCandidates);
        return HttpResponse.json(
          sectionCandidates.map((candidate) =>
            candidate.sectionId === 2 ? { ...candidate, availableSeats: 0, selectable: false, blockReason: 'SECTION_FULL' } : candidate,
          ),
        );
      }),
      http.post(`${API}/me/section-enrollments/:id/change-section`, () => HttpResponse.json(errorBody('SECTION_FULL'), { status: 409 })),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(within((await screen.findByText('Cambio de comisión')).closest('li')).getByRole('button', { name: 'Abrir' }));
    await user.click(await screen.findByRole('button', { name: /Bases de Datos/ }));
    await user.click(await screen.findByRole('radio', { name: /Com\. C/ }));
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await user.click(screen.getByRole('button', { name: 'Confirmar el cambio' }));

    expect(await screen.findByText('La comisión se llenó mientras confirmabas.')).toBeInTheDocument();
    await waitFor(() => expect(candidateRequests).toBe(2));
    await waitFor(() => expect(screen.queryByRole('radio', { name: /Com\. C/ })).not.toBeInTheDocument());
    expect(screen.getByRole('button', { name: 'Continuar' })).toBeDisabled();
  });

  it('closes the sheet with a toast on an unexpected error', async () => {
    useSectionEnrollments();
    server.use(
      http.post(`${API}/me/section-enrollments/:id/change-section`, () => HttpResponse.json(errorBody('FORBIDDEN'), { status: 403 })),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(within((await screen.findByText('Cambio de comisión')).closest('li')).getByRole('button', { name: 'Abrir' }));
    await user.click(await screen.findByRole('button', { name: /Bases de Datos/ }));
    await user.click(await screen.findByRole('radio', { name: /Com\. C/ }));
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await user.click(screen.getByRole('button', { name: 'Confirmar el cambio' }));

    expect(await screen.findByText('No tenés permiso para hacer esto.')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('shows the closed window message on confirm when the POST answers 400 and blocks a new attempt', async () => {
    useSectionEnrollments();
    server.use(
      http.post(`${API}/me/section-enrollments/:id/change-section`, () =>
        HttpResponse.json(errorBody('ENROLLMENT_WINDOW_CLOSED'), { status: 400 }),
      ),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(within((await screen.findByText('Cambio de comisión')).closest('li')).getByRole('button', { name: 'Abrir' }));
    await user.click(await screen.findByRole('button', { name: /Bases de Datos/ }));
    await user.click(await screen.findByRole('radio', { name: /Com\. C/ }));
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await user.click(screen.getByRole('button', { name: 'Confirmar el cambio' }));

    expect(await screen.findByText('El período de inscripción está cerrado.')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirmar el cambio' })).toBeDisabled();
  });

  it('keeps the dialog open with the overlap detail', async () => {
    useSectionEnrollments();
    server.use(
      http.post(`${API}/me/section-enrollments/:id/change-section`, () =>
        HttpResponse.json(
          { ...errorBody('SECTION_SCHEDULE_OVERLAP'), details: { conflictingSubject: 'Química', day: 'sábado', from: '09:00', to: '13:00' } },
          { status: 409 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(within((await screen.findByText('Cambio de comisión')).closest('li')).getByRole('button', { name: 'Abrir' }));
    await user.click(await screen.findByRole('button', { name: /Bases de Datos/ }));
    await user.click(await screen.findByRole('radio', { name: /Com\. C/ }));
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await user.click(screen.getByRole('button', { name: 'Confirmar el cambio' }));

    expect(await screen.findByText('Se superpone con Química (sábado de 09:00 a 13:00).')).toBeInTheDocument();
  });

  it('shows a truthful confirmation table for the student', async () => {
    useSectionEnrollments();
    const user = userEvent.setup();
    renderSection();

    await user.click(within((await screen.findByText('Cambio de comisión')).closest('li')).getByRole('button', { name: 'Abrir' }));
    await user.click(await screen.findByRole('button', { name: /Bases de Datos/ }));
    await user.click(await screen.findByRole('radio', { name: /Com\. C/ }));
    await user.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(screen.getByRole('group', { name: 'Días y horarios' })).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Lugares' })).getByText('Quedan 4')).toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Asistencia y notas' })).not.toBeInTheDocument();
    expect(screen.queryByText('Cupo de la nueva comisión')).not.toBeInTheDocument();
  });

  it('shows an empty state when no subject can be changed', async () => {
    server.use(http.get(`${API}/me/section-enrollments`, () => HttpResponse.json([sectionEnrollmentRows[1], sectionEnrollmentRows[2]])));
    const user = userEvent.setup();
    renderSection();

    await user.click(within((await screen.findByText('Cambio de comisión')).closest('li')).getByRole('button', { name: 'Abrir' }));

    expect(await screen.findByText('No hay materias para cambiar')).toBeInTheDocument();
    expect(screen.queryByText('Anatomía II')).not.toBeInTheDocument();
    expect(screen.queryByText('Química')).not.toBeInTheDocument();
  });

  it('shows the loading state and then an error with retry when the enrollments fail', async () => {
    let fail = true;
    server.use(
      http.get(`${API}/me/section-enrollments`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 50));
        return fail ? HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 }) : HttpResponse.json(sectionEnrollmentRows);
      }),
      http.get(`${API}/me/section-enrollments/:id/change-section-candidates`, () => HttpResponse.json(sectionCandidates)),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(within((await screen.findByText('Cambio de comisión')).closest('li')).getByRole('button', { name: 'Abrir' }));
    expect(await screen.findByLabelText('Cargando materias')).toBeInTheDocument();
    expect(await screen.findByText('No pudimos cargar tus materias', {}, { timeout: 5000 })).toBeInTheDocument();
    fail = false;
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByLabelText('Cargando materias')).toBeInTheDocument();
    expect(screen.queryByText('No pudimos cargar tus materias')).not.toBeInTheDocument();
    expect(await screen.findByRole('button', { name: /Bases de Datos/ })).toBeInTheDocument();
  });

  it('sends the change only once on a double click', async () => {
    useSectionEnrollments();
    let posts = 0;
    server.use(
      http.post(`${API}/me/section-enrollments/:id/change-section`, async () => {
        posts += 1;
        await new Promise((resolve) => setTimeout(resolve, 100));
        return HttpResponse.json(sectionEnrollment({ id: 100, sectionId: 2 }));
      }),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(within((await screen.findByText('Cambio de comisión')).closest('li')).getByRole('button', { name: 'Abrir' }));
    await user.click(await screen.findByRole('button', { name: /Bases de Datos/ }));
    await user.click(await screen.findByRole('radio', { name: /Com\. C/ }));
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    const confirm = screen.getByRole('button', { name: 'Confirmar el cambio' });
    await user.dblClick(confirm);

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(posts).toBe(1);
  });

  it('has no accessibility violations in the sheet', async () => {
    useSectionEnrollments();
    const user = userEvent.setup();
    renderSection();

    await user.click(within((await screen.findByText('Cambio de comisión')).closest('li')).getByRole('button', { name: 'Abrir' }));
    await screen.findByRole('button', { name: /Bases de Datos/ });

    expect(await axe(screen.getByRole('dialog'))).toHaveNoViolations();
  });

  it('shows the empty state when there are no approved procedures', async () => {
    server.use(http.get(`${API}/me/procedures/approved`, () => HttpResponse.json([])));
    const user = userEvent.setup();
    renderSection();

    await user.click(await screen.findByRole('tab', { name: 'Trámites aprobados' }));

    expect(await screen.findByText('Todavía no tenés trámites aprobados')).toBeInTheDocument();
  });

  it('shows the loading state', () => {
    renderSection();

    expect(screen.getByLabelText('Cargando trámites')).toBeInTheDocument();
  });

  it('shows an error with retry when the list fails', async () => {
    server.use(http.get(`${API}/me/procedures/approved`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })));
    renderSection();

    expect(await screen.findByText('No pudimos cargar tus trámites')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('shows the no permission state on 403', async () => {
    server.use(http.get(`${API}/me/procedures/approved`, () => HttpResponse.json(errorBody('ACCESS_DENIED'), { status: 403 })));
    renderSection();

    expect(await screen.findByText('No tenés permiso para ver los trámites')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = renderSection();

    await screen.findAllByRole('button', { name: 'Generar' });
    expect(await axe(container)).toHaveNoViolations();
  });
});
