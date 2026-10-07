import { StrictMode } from 'react';
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
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
    server.use(
      http.get(`${API}/verify-email`, async () => {
        calls += 1;
        if (calls > 1) return new HttpResponse(null, { status: 400 });
        await new Promise((resolve) => setTimeout(resolve, 20));
        return new HttpResponse(null, { status: 200 });
      }),
    );
    renderInStrictMode('/verificar-email?token=abc');

    expect(await screen.findByText('Email verificado')).toBeInTheDocument();
    expect(calls).toBe(1);
  });

  it('offers a link to sign in after verifying', async () => {
    server.use(http.get(`${API}/verify-email`, () => new HttpResponse(null, { status: 200 })));
    renderInStrictMode('/verificar-email?token=abc');

    expect(await screen.findByRole('link', { name: 'Ir a ingresar' })).toHaveAttribute('href', '/login');
  });
});
