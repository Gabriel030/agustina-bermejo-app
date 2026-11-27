import { http, HttpResponse } from 'msw';
import { errorBody } from '@/test/fixtures/errors';

const API = 'http://localhost:8080';

export const INFORMATIVE_SESSION_IDS = {
  open: 1,
  almostFull: 2,
  full: 3,
  cancelled: 4,
  started: 5,
  alreadyRegistered: 6,
  notFound: 404,
};

const failure = (status, code) => HttpResponse.json(errorBody(code), { status });

export const informativeSession = (overrides = {}) => ({
  id: INFORMATIVE_SESSION_IDS.open,
  title: 'Charla sobre la Tecnicatura en Programación',
  scheduledAt: '2045-03-10T18:00:00',
  modality: 'ONLINE',
  meetingUrl: 'https://meet.example.com/charla',
  location: null,
  capacity: 30,
  relatedProgramId: null,
  status: 'SCHEDULED',
  registrationCount: 12,
  registeredByMe: false,
  ...overrides,
});

export const registerInformativeSessionError = (sessionId) => {
  if (sessionId === INFORMATIVE_SESSION_IDS.notFound) return failure(404, 'INFORMATIVE_SESSION_NOT_FOUND');
  if (sessionId === INFORMATIVE_SESSION_IDS.full) return failure(409, 'SESSION_FULL');
  if (sessionId === INFORMATIVE_SESSION_IDS.alreadyRegistered) {
    return failure(409, 'ALREADY_REGISTERED_FOR_INFORMATIVE_SESSION');
  }
  if (sessionId === INFORMATIVE_SESSION_IDS.cancelled) return failure(422, 'INFORMATIVE_SESSION_NOT_SCHEDULED');
  if (sessionId === INFORMATIVE_SESSION_IDS.started) return failure(422, 'INFORMATIVE_SESSION_ALREADY_STARTED');
  return null;
};

export const informativeSessionHandlers = [
  http.get(`${API}/informative-sessions`, () =>
    HttpResponse.json([
      informativeSession(),
      informativeSession({
        id: INFORMATIVE_SESSION_IDS.almostFull,
        title: 'Charla sobre Diseño Gráfico',
        modality: 'IN_PERSON',
        meetingUrl: null,
        location: 'Sede central, aula 3',
        capacity: 20,
        registrationCount: 19,
      }),
    ]),
  ),
  http.post(`${API}/informative-sessions`, async ({ request }) => {
    const body = await request.json().catch(() => ({}));
    return HttpResponse.json(informativeSession({ ...body, id: 100, registrationCount: 0, status: 'SCHEDULED' }), {
      status: 201,
    });
  }),
  http.put(`${API}/informative-sessions/:id`, async ({ params, request }) => {
    const body = await request.json().catch(() => ({}));
    return HttpResponse.json(informativeSession({ ...body, id: Number(params.id) }));
  }),
  http.post(`${API}/informative-sessions/:id/cancel`, ({ params }) =>
    HttpResponse.json(informativeSession({ id: Number(params.id), status: 'CANCELLED' })),
  ),
  http.post(`${API}/informative-sessions/:id/register`, ({ params }) => {
    const sessionId = Number(params.id);
    return (
      registerInformativeSessionError(sessionId) ??
      HttpResponse.json(informativeSession({ id: sessionId, registrationCount: 13, registeredByMe: true }))
    );
  }),
];
