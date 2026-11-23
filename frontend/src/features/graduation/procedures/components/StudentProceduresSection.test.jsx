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

  it('links Cambio de comisión to the subjects tab of the career', async () => {
    renderSection();

    const row = (await screen.findByText('Cambio de comisión')).closest('li');
    expect(within(row).getByRole('link', { name: 'Abrir' })).toHaveAttribute('href', '/alumno/mis-carreras?carrera=5&tab=materias');
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
