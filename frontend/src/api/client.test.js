import { describe, expect, it, beforeEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { apiFetch, setAccessToken, RateLimitedError, AuthError } from '@/api/client';

const API = 'http://localhost:8080';

describe('apiFetch', () => {
  beforeEach(() => {
    setAccessToken(null);
    localStorage.clear();
  });

  it('reintenta una vez con refresh cuando la request original da 401, y reintenta con el token nuevo', async () => {
    localStorage.setItem('refreshToken', 'refresh-viejo');
    let intentos = 0;
    server.use(
      http.get(`${API}/protegido`, ({ request }) => {
        intentos += 1;
        const auth = request.headers.get('authorization');
        if (auth === 'Bearer access-2') return HttpResponse.json({ ok: true });
        return new HttpResponse(null, { status: 401 });
      }),
    );

    const resultado = await apiFetch('/protegido');

    expect(resultado).toEqual({ ok: true });
    expect(intentos).toBe(2);
  });

  it('si el refresh también falla, limpia la sesión y tira AuthError sin reintentar de nuevo', async () => {
    localStorage.setItem('refreshToken', 'refresh-vencido');
    server.use(
      http.get(`${API}/protegido`, () => new HttpResponse(null, { status: 401 })),
      http.post(`${API}/auth/refresh`, () => new HttpResponse(null, { status: 401 })),
    );

    await expect(apiFetch('/protegido')).rejects.toBeInstanceOf(AuthError);
  });

  it('un 429 tira RateLimitedError en vez de un Error genérico', async () => {
    server.use(http.post(`${API}/auth/login`, () => new HttpResponse(null, { status: 429 })));

    await expect(
      apiFetch('/auth/login', { method: 'POST', body: JSON.stringify({ username: 'x', password: 'y' }) }),
    ).rejects.toBeInstanceOf(RateLimitedError);
  });

  it('un 401 en /auth/login no dispara el flujo de refresh — se propaga como credenciales inválidas', async () => {
    let refreshLlamado = false;
    server.use(
      http.post(`${API}/auth/login`, () => new HttpResponse(null, { status: 401 })),
      http.post(`${API}/auth/refresh`, () => {
        refreshLlamado = true;
        return HttpResponse.json({ accessToken: 'x', refreshToken: 'y', expiresIn: 1 });
      }),
    );

    await expect(
      apiFetch('/auth/login', { method: 'POST', body: JSON.stringify({ username: 'x', password: 'y' }) }),
    ).rejects.toThrow();
    expect(refreshLlamado).toBe(false);
  });
});
