import { describe, expect, it, beforeEach, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { apiFetch, setAccessToken, setSessionExpiredHandler, RateLimitedError, AuthError } from '@/api/client';

const API = 'http://localhost:8080';

describe('apiFetch', () => {
  beforeEach(() => {
    setAccessToken(null);
    setSessionExpiredHandler(null);
    localStorage.clear();
  });

  function refreshHandler(counter) {
    return http.post(`${API}/auth/refresh`, async () => {
      counter.calls += 1;
      await new Promise((resolve) => setTimeout(resolve, 20));
      return HttpResponse.json({ accessToken: 'access-2', refreshToken: 'refresh-2', expiresIn: 900 });
    });
  }

  it('two concurrent requests answered with 401 trigger a single refresh and both are retried', async () => {
    localStorage.setItem('refreshToken', 'refresh-1');
    const refresh = { calls: 0 };
    server.use(
      http.get(`${API}/protegido`, ({ request }) =>
        request.headers.get('authorization') === 'Bearer access-2'
          ? HttpResponse.json({ id: new URL(request.url).searchParams.get('id') })
          : new HttpResponse(null, { status: 401 }),
      ),
      refreshHandler(refresh),
    );

    const results = await Promise.all([apiFetch('/protegido?id=a'), apiFetch('/protegido?id=b')]);

    expect(results).toEqual([{ id: 'a' }, { id: 'b' }]);
    expect(refresh.calls).toBe(1);
  });

  it('a 403 never triggers a refresh and surfaces as a permission error', async () => {
    localStorage.setItem('refreshToken', 'refresh-1');
    const refresh = { calls: 0 };
    let attempts = 0;
    server.use(
      http.get(`${API}/protegido`, () => {
        attempts += 1;
        return new HttpResponse(null, { status: 403 });
      }),
      refreshHandler(refresh),
    );

    await expect(apiFetch('/protegido')).rejects.toMatchObject({ status: 403 });
    expect(refresh.calls).toBe(0);
    expect(attempts).toBe(1);
  });

  it('a failed refresh closes the session exactly once even with concurrent requests', async () => {
    localStorage.setItem('refreshToken', 'refresh-expired');
    const onExpired = vi.fn();
    setSessionExpiredHandler(onExpired);
    server.use(
      http.get(`${API}/protegido`, () => new HttpResponse(null, { status: 401 })),
      http.post(`${API}/auth/refresh`, () => new HttpResponse(null, { status: 401 })),
    );

    const results = await Promise.allSettled([apiFetch('/protegido?id=a'), apiFetch('/protegido?id=b')]);

    expect(results.every((result) => result.reason instanceof AuthError)).toBe(true);
    expect(onExpired).toHaveBeenCalledTimes(1);
  });

  it('a request that still gets 401 after the refresh is retried only once and then closes the session', async () => {
    localStorage.setItem('refreshToken', 'refresh-1');
    const refresh = { calls: 0 };
    const onExpired = vi.fn();
    setSessionExpiredHandler(onExpired);
    let attempts = 0;
    server.use(
      http.get(`${API}/protegido`, () => {
        attempts += 1;
        return new HttpResponse(null, { status: 401 });
      }),
      refreshHandler(refresh),
    );

    await expect(apiFetch('/protegido')).rejects.toBeInstanceOf(AuthError);
    expect(attempts).toBe(2);
    expect(refresh.calls).toBe(1);
    expect(onExpired).toHaveBeenCalledTimes(1);
  });

  it('a 401 from the refresh endpoint itself never triggers another refresh', async () => {
    localStorage.setItem('refreshToken', 'refresh-expired');
    const refresh = { calls: 0 };
    server.use(
      http.get(`${API}/protegido`, () => new HttpResponse(null, { status: 401 })),
      http.post(`${API}/auth/refresh`, () => {
        refresh.calls += 1;
        return new HttpResponse(null, { status: 401 });
      }),
    );

    await expect(apiFetch('/protegido')).rejects.toBeInstanceOf(AuthError);
    expect(refresh.calls).toBe(1);
  });

  it('a request sent with a token that was already replaced is retried without a second refresh', async () => {
    localStorage.setItem('refreshToken', 'refresh-1');
    setAccessToken('access-1');
    const refresh = { calls: 0 };
    server.use(
      http.get(`${API}/protegido`, async ({ request }) => {
        if (new URL(request.url).searchParams.get('speed') === 'slow') {
          await new Promise((resolve) => setTimeout(resolve, 80));
        }
        return request.headers.get('authorization') === 'Bearer access-2'
          ? HttpResponse.json({ ok: true })
          : new HttpResponse(null, { status: 401 });
      }),
      refreshHandler(refresh),
    );

    await Promise.all([apiFetch('/protegido?speed=slow'), apiFetch('/protegido?speed=fast')]);

    expect(refresh.calls).toBe(1);
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

  it('expone el code que manda el backend en el cuerpo del error', async () => {
    server.use(
      http.post(`${API}/me/course-enrollments`, () =>
        HttpResponse.json({ code: 'ALREADY_ENROLLED_IN_COURSE' }, { status: 409 }),
      ),
    );

    await expect(apiFetch('/me/course-enrollments', { method: 'POST' })).rejects.toMatchObject({
      status: 409,
      code: 'ALREADY_ENROLLED_IN_COURSE',
    });
  });

  it('un error sin cuerpo deja el code en null', async () => {
    server.use(http.post(`${API}/me/course-enrollments`, () => new HttpResponse(null, { status: 409 })));

    await expect(apiFetch('/me/course-enrollments', { method: 'POST' })).rejects.toMatchObject({
      status: 409,
      code: null,
    });
  });
});
