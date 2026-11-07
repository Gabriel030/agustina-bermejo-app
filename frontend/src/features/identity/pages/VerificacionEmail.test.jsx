import { StrictMode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { createDeferred } from '@/test/deferred';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { VerificacionEmail } from '@/features/identity/pages/VerificacionEmail';

const API = 'http://localhost:8080';

function renderInStrictMode(route) {
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={[route]}>
        <VerificacionEmail />
      </MemoryRouter>
    </StrictMode>,
  );
}

describe('VerificacionEmail', () => {
  it('consumes the single-use token only once even when effects run twice', async () => {
    let calls = 0;
    const verification = createDeferred();
    server.use(
      http.get(`${API}/verify-email`, async () => {
        calls += 1;
        if (calls > 1) return new HttpResponse(null, { status: 400 });
        await verification.promise;
        return new HttpResponse(null, { status: 200 });
      }),
    );
    renderInStrictMode('/verificar-email?token=abc');

    await vi.waitFor(() => expect(calls).toBeGreaterThan(0));
    verification.resolve();

    expect(await screen.findByText('Email confirmado')).toBeInTheDocument();
    expect(calls).toBe(1);
  });

  it('offers a link to sign in after verifying', async () => {
    server.use(http.get(`${API}/verify-email`, () => new HttpResponse(null, { status: 200 })));
    renderInStrictMode('/verificar-email?token=abc');

    expect(await screen.findByRole('link', { name: 'Ir a ingresar' })).toHaveAttribute('href', '/login');
  });

  it('explains that the link expired or was already used with both ways out', async () => {
    server.use(
      http.get(`${API}/verify-email`, () =>
        HttpResponse.json(errorBody('INVALID_EMAIL_VERIFICATION_TOKEN'), { status: 400 }),
      ),
    );
    renderInStrictMode('/verificar-email?token=abc');

    expect(await screen.findByText('El enlace venció o ya se usó')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a Mi perfil' })).toHaveAttribute('href', '/perfil');
    expect(screen.getByRole('link', { name: 'Ir a ingresar' })).toHaveAttribute('href', '/login');
  });

  it('shows the confirming state while the request is in flight', async () => {
    const verification = createDeferred();
    server.use(
      http.get(`${API}/verify-email`, async () => {
        await verification.promise;
        return new HttpResponse(null, { status: 200 });
      }),
    );
    renderInStrictMode('/verificar-email?token=abc');

    expect(screen.getByText('Confirmando tu email…')).toBeInTheDocument();
    verification.resolve();
    expect(await screen.findByText('Email confirmado')).toBeInTheDocument();
  });
});
