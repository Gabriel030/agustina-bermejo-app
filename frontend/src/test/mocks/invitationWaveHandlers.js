import { http, HttpResponse } from 'msw';
import { errorBody } from '@/test/fixtures/errors';
import {
  activationTokenStatus,
  emptyInvitationRecipientPage,
  emptyInvitationWavePage,
  invitationImportBatches,
  invitationRecipientsWithPersonalData,
  invitationRecipientsWithoutPersonalData,
  invitationWave,
  invitationWavePage,
  invitationWaveReportCsv,
  invitationWaveSettings,
  pausedInvitationWave,
  runningInvitationWave,
} from '@/test/fixtures/invitationWaves';

const API = 'http://localhost:8080';

const csvResponse = () =>
  new HttpResponse(invitationWaveReportCsv(), {
    headers: {
      'Content-Type': 'text/csv;charset=UTF-8',
      'Content-Disposition': 'attachment; filename="invitaciones-oleada-2-20261123.csv"',
    },
  });

const errorResponse = (status, code) => HttpResponse.json(errorBody(code), { status });

export const invitationWaveHandlers = [
  http.post(`${API}/invitation-waves`, () => HttpResponse.json(invitationWave(), { status: 201 })),
  http.get(`${API}/invitation-waves`, () =>
    HttpResponse.json(invitationWavePage([runningInvitationWave(), pausedInvitationWave(), invitationWave()])),
  ),
  http.get(`${API}/invitation-waves/settings`, () => HttpResponse.json(invitationWaveSettings())),
  http.get(`${API}/invitation-waves/import-batches`, () => HttpResponse.json(invitationImportBatches())),
  http.get(`${API}/invitation-waves/:id`, ({ params }) =>
    HttpResponse.json(runningInvitationWave({ id: Number(params.id) })),
  ),
  http.get(`${API}/invitation-waves/:id/recipients`, () =>
    HttpResponse.json(invitationRecipientsWithPersonalData()),
  ),
  http.get(`${API}/invitation-waves/:id/report.csv`, () => csvResponse()),
  http.post(`${API}/invitation-waves/:id/start`, ({ params }) =>
    HttpResponse.json(runningInvitationWave({ id: Number(params.id) })),
  ),
  http.post(`${API}/invitation-waves/:id/pause`, ({ params }) =>
    HttpResponse.json(pausedInvitationWave({ id: Number(params.id) })),
  ),
  http.post(`${API}/invitation-waves/:id/resume`, ({ params }) =>
    HttpResponse.json(runningInvitationWave({ id: Number(params.id) })),
  ),
  http.post(`${API}/invitation-waves/:id/cancel`, ({ params }) =>
    HttpResponse.json(
      invitationWave({
        id: Number(params.id),
        status: 'CANCELLED',
        cancelledAt: '2026-11-23T15:00:00Z',
        cancelledByPersonId: 1,
        invitableCount: 0,
        excludedByReason: { NO_EMAIL: 6, ALREADY_ACTIVE: 0, NOT_ELIGIBLE: 1, IN_OTHER_WAVE: 2, WAVE_CANCELLED: 118, SHARED_EMAIL: 0 },
      }),
    ),
  ),
  http.post(`${API}/invitation-waves/:id/retry-failed`, () => HttpResponse.json({ requeued: 3 })),
  http.post(`${API}/auth/activation-token/status`, () => HttpResponse.json(activationTokenStatus('VALID'))),
];

export const emptyInvitationWavesHandlers = [
  http.get(`${API}/invitation-waves`, () => HttpResponse.json(emptyInvitationWavePage())),
  http.get(`${API}/invitation-waves/import-batches`, () => HttpResponse.json([])),
  http.get(`${API}/invitation-waves/:id/recipients`, () => HttpResponse.json(emptyInvitationRecipientPage())),
];

export const invitationRecipientsWithoutPersonalDataHandlers = [
  http.get(`${API}/invitation-waves/:id/recipients`, () =>
    HttpResponse.json(invitationRecipientsWithoutPersonalData()),
  ),
];

export const invitationWaveErrorHandlers = [
  http.post(`${API}/invitation-waves`, () => errorResponse(409, 'INVITATION_WAVE_WITHOUT_RECIPIENTS')),
  http.get(`${API}/invitation-waves/:id`, () => errorResponse(404, 'INVITATION_WAVE_NOT_FOUND')),
  http.post(`${API}/invitation-waves/:id/start`, () => errorResponse(409, 'INVITATION_WAVE_INVALID_TRANSITION')),
  http.post(`${API}/invitation-waves/:id/pause`, () => errorResponse(409, 'INVITATION_WAVE_INVALID_TRANSITION')),
  http.post(`${API}/invitation-waves/:id/resume`, () => errorResponse(409, 'INVITATION_WAVE_INVALID_TRANSITION')),
  http.post(`${API}/invitation-waves/:id/cancel`, () => errorResponse(409, 'INVITATION_WAVE_INVALID_TRANSITION')),
  http.post(`${API}/invitation-waves/:id/retry-failed`, () => errorResponse(409, 'INVITATION_WAVE_NOT_RUNNING')),
  http.get(`${API}/invitation-waves`, () => errorResponse(403, 'FORBIDDEN')),
];

export const invitationWaveNotFoundRetryHandlers = [
  http.post(`${API}/invitation-waves/:id/retry-failed`, () => errorResponse(404, 'INVITATION_WAVE_NOT_FOUND')),
];

export const activationTokenStatusHandlers = {
  valid: http.post(`${API}/auth/activation-token/status`, () => HttpResponse.json(activationTokenStatus('VALID'))),
  expired: http.post(`${API}/auth/activation-token/status`, () => HttpResponse.json(activationTokenStatus('EXPIRED'))),
  invalid: http.post(`${API}/auth/activation-token/status`, () => HttpResponse.json(activationTokenStatus('INVALID'))),
};
