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
  http.get(`${API}/me`, () =>
    HttpResponse.json({
      id: 1,
      nombre: 'Prueba',
      apellido: 'Login',
      email: 'prueba@example.com',
      roles: ['PRECEPTOR'],
    }),
  ),
  http.post(`${API}/auth/register`, () =>
    HttpResponse.json({ accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 900 }),
  ),
  http.get(`${API}/institutions`, () => HttpResponse.json([{ id: 1, name: 'Instituto de prueba' }])),
  http.get(`${API}/programs`, () => HttpResponse.json([])),
  http.get(`${API}/courses`, () => HttpResponse.json([])),
  http.get(`${API}/me/program-enrollment-applications`, () => HttpResponse.json([])),
  http.post(`${API}/me/program-enrollment-applications/:id/documents`, () => new HttpResponse(null, { status: 201 })),
];
