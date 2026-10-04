import { http, HttpResponse } from 'msw';

const API = 'http://localhost:8080';

/*
 * Handlers "felices" por default — cada test sobreescribe con server.use(...)
 * el caso puntual que quiere probar (401, 429, etc.), ver
 * docs/context/testing.md. Las respuestas de error del backend real no llevan
 * body (ver docs/context/backend.md) — acá se replica ese contrato a propósito.
 */
export const handlers = [
  http.post(`${API}/auth/login`, () =>
    HttpResponse.json({ accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 900 }),
  ),
  http.post(`${API}/auth/refresh`, () =>
    HttpResponse.json({ accessToken: 'access-2', refreshToken: 'refresh-2', expiresIn: 900 }),
  ),
  http.post(`${API}/auth/logout`, () => new HttpResponse(null, { status: 204 })),
  http.post(`${API}/auth/forgot-password`, () => new HttpResponse(null, { status: 202 })),
  http.post(`${API}/auth/verify-reset-code`, () => HttpResponse.json({ resetToken: 'reset-token-1' })),
  http.post(`${API}/auth/reset-password`, () => new HttpResponse(null, { status: 204 })),
  http.get(`${API}/me`, () =>
    HttpResponse.json({
      id: 1,
      nombre: 'Prueba',
      apellido: 'Login',
      email: 'prueba@example.com',
      roles: ['PRECEPTOR'],
    }),
  ),
  http.put(`${API}/me`, () =>
    HttpResponse.json({
      id: 1,
      nombre: 'Prueba',
      apellido: 'Login',
      email: 'prueba@example.com',
      dni: '30111222',
      roles: ['PRECEPTOR'],
    }),
  ),
  http.post(`${API}/auth/register`, () =>
    HttpResponse.json({ accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 900 }),
  ),
  http.get(`${API}/institutions`, () => HttpResponse.json([{ id: 1, name: 'Instituto de prueba' }])),
  http.get(`${API}/programs`, () => HttpResponse.json([])),
  http.get(`${API}/programs/:id`, ({ params }) =>
    HttpResponse.json({ id: Number(params.id), name: 'Carrera de prueba', totalYears: 3, totalCreditsRequired: 0 }),
  ),
  http.get(`${API}/programs/:id/pricing`, ({ params }) =>
    HttpResponse.json({ programId: Number(params.id), matriculaCohorte1: { amount: '50000.00', currency: 'ARS' }, matriculaCohorte2: { amount: '25000.00', currency: 'ARS' }, cuotaMensual: { amount: '15000.00', currency: 'ARS' } }),
  ),
  http.get(`${API}/programs/:id/cohorts`, ({ params }) =>
    HttpResponse.json([
      { id: 1, programId: Number(params.id), label: 'Marzo 2026', ordenEnAnio: 1, cursadaStart: '2026-03-01', admissionOpen: true },
      { id: 2, programId: Number(params.id), label: 'Agosto 2026', ordenEnAnio: 2, cursadaStart: '2026-08-01', admissionOpen: true },
    ]),
  ),
  http.get(`${API}/programs/:id/subjects`, () => HttpResponse.json([])),
  http.get(`${API}/courses`, () => HttpResponse.json([])),
  http.get(`${API}/courses/:id`, ({ params }) =>
    HttpResponse.json({ id: Number(params.id), name: 'Curso de prueba', durationMonths: 3, registrationOpen: true, full: false }),
  ),
  http.get(`${API}/courses/:id/pricing`, ({ params }) => HttpResponse.json({ courseId: Number(params.id), cuotaMensual: { amount: '8000.00', currency: 'ARS' } })),
  http.get(`${API}/courses/:id/subjects`, () => HttpResponse.json([])),
  http.get(`${API}/me/program-enrollment-applications`, () => HttpResponse.json([])),
  http.post(`${API}/me/program-enrollment-applications`, () =>
    HttpResponse.json({ id: 1, status: 'PENDING' }, { status: 201 }),
  ),
  http.post(`${API}/me/program-enrollment-applications/:id/documents`, () => new HttpResponse(null, { status: 201 })),
  http.get(`${API}/me/course-enrollment-applications`, () => HttpResponse.json([])),
  http.post(`${API}/me/course-enrollments`, () =>
    HttpResponse.json({ id: 1, paymentStatus: 'PENDING_REVIEW' }, { status: 201 }),
  ),
  http.get(`${API}/me/notifications`, () => HttpResponse.json([])),
  http.get(`${API}/me/documents`, () => HttpResponse.json([])),
  http.get(`${API}/me/payment-receipts`, () => HttpResponse.json([])),
  http.get(`${API}/payment-config`, () =>
    HttpResponse.json({ bankAlias: null, bankCbu: null, accountHolder: null, qrImageUrl: null, mpEnabled: false }),
  ),
  http.post(`${API}/me/notifications/:id/read`, () => new HttpResponse(null, { status: 200 })),
];
