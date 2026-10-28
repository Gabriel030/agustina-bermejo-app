import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ReminderSettingsPage } from './ReminderSettingsPage';

const API = 'http://localhost:8080';
const SCHEDULES = `${API}/billing-configuration/collection-reminder-schedules`;
const SETTINGS = `${API}/billing-configuration/COLLECTION_REMINDERS/topics/COLLECTION_REMINDERS`;

const auth = vi.hoisted(() => ({ roles: ['ADMINISTRATIVO'] }));
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ usuario: { roles: auth.roles } }) }));
vi.mock('@/features/billing/hooks/useOfferNames', () => ({
  useOfferNames: () => ({
    isLoading: false,
    programs: [
      { id: 5, name: 'Tecnicatura en Gestión' },
      { id: 6, name: 'Tecnicatura en Datos' },
    ],
    courses: [],
    programName: (id) => `Carrera ${id}`,
    courseName: (id) => `Curso ${id}`,
  }),
}));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: { success: vi.fn(), error: vi.fn() } }));

vi.setConfig({ testTimeout: 20000 });

const overrides = {
  suspensionNoticeEnabled: null,
  postWithdrawalSummaryMonths: null,
  emailWindowStart: null,
  emailWindowEnd: null,
  dailyEmailCap: null,
  contactListDaysOverdue: null,
  contactListOverdueInstallments: null,
};

const beforeDue = { noticeKey: 'before-due', messageType: 'BEFORE_DUE_REMINDER', milestone: 'DUE_DATE', offsetDays: -3, channels: ['IN_APP', 'EMAIL'], repetition: null };
const settings = {
  suspensionNoticeEnabled: true,
  postWithdrawalSummaryMonths: 6,
  emailWindowStart: '09:00',
  emailWindowEnd: '20:00',
  dailyEmailCap: 200,
  contactListDaysOverdue: 30,
  contactListOverdueInstallments: 2,
  dailyDigestEnabled: true,
};

function version(schedule, extra = {}) {
  return { id: 1, status: 'EFFECTIVE', effectiveFrom: '2026-01-01', changeReason: 'Alta', proposedById: 1, proposedByName: 'Pablo Ríos', decidedById: 2, decidedByName: null, schedule, ...extra };
}

let impactResponse;
let changeResponse;
let submitted;
let impactRequests;

beforeEach(() => {
  auth.roles = ['ADMINISTRATIVO'];
  impactResponse = { studentsWithChangedSchedule: 40 };
  changeResponse = () => HttpResponse.json({ authorizationRequestId: 1287 }, { status: 202 });
  submitted = [];
  impactRequests = [];
  server.use(
    http.get(SCHEDULES, () =>
      HttpResponse.json({
        groups: [
          { group: 'PROGRAMS', versionId: 1, effectiveFrom: '2026-01-01', noticeCount: 1, hasOpenProposal: false },
          { group: 'MONTHLY_COURSES', versionId: 2, effectiveFrom: '2026-01-01', noticeCount: 1, hasOpenProposal: true },
        ],
        exceptions: [],
      }),
    ),
    http.get(`${SCHEDULES}/:group/versions`, () => HttpResponse.json([version({ followsGroupSchedule: false, notices: [beforeDue], sendingOverrides: overrides })])),
    http.get(`${SETTINGS}/versions`, () => HttpResponse.json([{ ...version(null), schedule: undefined, values: settings }])),
    http.post(`${SCHEDULES}/:group/validation`, () => HttpResponse.json({ violations: [] })),
    http.post(`${SETTINGS}/validation`, () => HttpResponse.json({ violations: [] })),
    http.post(`${API}/collection-reminders/impact`, async ({ request }) => {
      impactRequests.push(await request.json());
      return HttpResponse.json(impactResponse);
    }),
    http.post(`${SCHEDULES}/:group/changes`, async ({ request }) => {
      submitted.push(await request.json());
      return changeResponse();
    }),
    http.post(`${SETTINGS}/changes`, async ({ request }) => {
      submitted.push(await request.json());
      return changeResponse();
    }),
  );
});

function renderPage(route = '/administrativo/recordatorios-de-pago') {
  return renderWithProviders(
    <Routes>
      <Route path="/:role/recordatorios-de-pago" element={<ReminderSettingsPage />} />
    </Routes>,
    { route },
  );
}

async function addAfterDueNotice(user) {
  await user.click(await screen.findByRole('button', { name: 'Agregar aviso' }));
  const form = screen.getByRole('form', { name: 'Agregar aviso' });
  await user.click(within(form).getByLabelText('Tipo de aviso'));
  await user.click(await screen.findByRole('option', { name: 'Aviso de cuota vencida' }));
  await user.clear(within(form).getByLabelText('Días'));
  await user.type(within(form).getByLabelText('Días'), '5');
  await user.click(within(form).getByRole('button', { name: 'Aplicar al borrador' }));
}

describe('ReminderSettingsPage', () => {
  it('shows the effective notices of the group grouped by milestone', async () => {
    renderPage();

    expect(await screen.findByText('3 días antes del vencimiento · App y Mail')).toBeInTheDocument();
    expect(screen.queryByText(/cambio sin publicar/)).not.toBeInTheDocument();
  });

  it('walks the two steps and sends a proposal when the user is an administrative', async () => {
    const user = userEvent.setup();
    renderPage();
    await addAfterDueNotice(user);

    expect(screen.getByText('1 cambio sin publicar')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }));

    const row = await screen.findByRole('group', { name: 'Aviso de cuota vencida' });
    expect(within(row).getByText('Aviso nuevo')).toBeInTheDocument();
    expect(within(row).getByText(/5 días después del vencimiento/)).toBeInTheDocument();
    expect(await screen.findByText('40')).toBeInTheDocument();
    expect(screen.getByText('Alumnos afectados')).toBeInTheDocument();

    const send = screen.getByRole('button', { name: 'Enviar propuesta' });
    expect(send).toBeDisabled();
    await user.type(screen.getByLabelText('Motivo (obligatorio)'), 'Más recordatorios');
    await waitFor(() => expect(send).toBeEnabled());
    await user.click(send);

    expect(await screen.findByText('Propuesta enviada al director')).toBeInTheDocument();
    expect(screen.getByText(/n\.° 1287/)).toBeInTheDocument();
    expect(submitted).toHaveLength(1);
    expect(submitted[0].changeReason).toBe('Más recordatorios');
    expect(submitted[0].schedule.notices).toHaveLength(2);
    expect(submitted[0].schedule.followsGroupSchedule).toBe(false);
    expect(impactRequests[0]).toMatchObject({ group: 'PROGRAMS' });
  });

  it('publishes directly when the user is the director', async () => {
    auth.roles = ['DIRECTOR'];
    changeResponse = () => HttpResponse.json({ id: 9, status: 'SCHEDULED' }, { status: 201 });
    const user = userEvent.setup();
    renderPage('/director/recordatorios-de-pago');
    await addAfterDueNotice(user);
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }));
    await user.type(await screen.findByLabelText('Motivo (obligatorio)'), 'Ajuste');

    await user.click(await screen.findByRole('button', { name: 'Publicar cambio' }));

    expect(await screen.findByText('Cambio publicado')).toBeInTheDocument();
  });

  it('discarding the draft goes back to the effective notices', async () => {
    const user = userEvent.setup();
    renderPage();
    await addAfterDueNotice(user);

    await user.click(screen.getByRole('button', { name: 'Descartar' }));

    expect(screen.queryByText(/cambio sin publicar/)).not.toBeInTheDocument();
    expect(screen.queryByText(/5 días después del vencimiento/)).not.toBeInTheDocument();
  });

  it('shows the server violations without leaving the confirmation step', async () => {
    changeResponse = () =>
      HttpResponse.json({ code: 'REMINDER_POLICY_VIOLATION', violations: [{ code: 'DUPLICATE_SLOT', message: 'Dos avisos caen el mismo día.' }] }, { status: 400 });
    const user = userEvent.setup();
    renderPage();
    await addAfterDueNotice(user);
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }));
    await user.type(await screen.findByLabelText('Motivo (obligatorio)'), 'Prueba');
    await user.click(await screen.findByRole('button', { name: 'Enviar propuesta' }));

    expect(await screen.findByText('Dos avisos caen el mismo día.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Volver a los cambios' })).toBeInTheDocument();
  });

  it('keeps a policy with an open proposal read-only', async () => {
    renderPage('/administrativo/recordatorios-de-pago?tab=MONTHLY_COURSES');

    expect(await screen.findByText(/Hay una propuesta pendiente de aprobación/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Agregar aviso' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Editar/ })).not.toBeInTheDocument();
  });

  it('explains a zero impact for a general settings change without scaring or lying', async () => {
    impactResponse = { studentsWithChangedSchedule: 0 };
    const user = userEvent.setup();
    renderPage('/administrativo/recordatorios-de-pago?tab=GENERAL');
    const cap = await screen.findByLabelText('Tope diario de mails');
    await user.clear(cap);
    await user.type(cap, '100');

    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }));

    const row = await screen.findByRole('group', { name: 'Tope diario de mails' });
    expect(within(row).getByText('200')).toBeInTheDocument();
    expect(within(row).getByText('100')).toBeInTheDocument();
    expect(await screen.findByText(/no mueve a ningún alumno de calendario/)).toBeInTheDocument();
    expect(screen.queryByText('Alumnos afectados')).not.toBeInTheDocument();
    expect(impactRequests[0]).toMatchObject({ globalSettings: { dailyEmailCap: 100 } });
    expect(impactRequests[0].group).toBeUndefined();
  });

  it('lets the user pick an offer item to create an exception that starts from the group notices', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Agregar excepción' }));
    await user.click(screen.getByRole('combobox', { name: 'Elegí una carrera' }));
    await user.click(await screen.findByRole('option', { name: 'Tecnicatura en Datos' }));
    await user.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByText('Carrera 6')).toBeInTheDocument();
    expect(screen.getByText('3 días antes del vencimiento · App y Mail')).toBeInTheDocument();
    expect(screen.getByText('1 cambio sin publicar')).toBeInTheDocument();
  });
});
