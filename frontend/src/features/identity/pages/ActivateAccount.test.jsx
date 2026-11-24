import { StrictMode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { axe } from '@/test/axe';
import { createDeferred } from '@/test/deferred';
import { errorBody } from '@/test/fixtures/errors';
import { takeContractDeviations } from '@/test/contract/withContract';
import { server } from '@/test/mocks/server';
import { activationTokenStatus } from '@/test/fixtures/invitationWaves';
import { ActivateAccount } from '@/features/identity/pages/ActivateAccount';

const API = 'http://localhost:8080';
const TOKEN = 'tok-secret-123';
const INSTITUTION = activationTokenStatus('VALID').institution;
const VALID_PASSWORD = 'Clave2026x';

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>;
}

function renderPage(route = `/activar?token=${TOKEN}`) {
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={[route]}>
        <ActivateAccount />
        <LocationProbe />
      </MemoryRouter>
    </StrictMode>,
  );
}

function failNetworkFor(pathFragment) {
  const realFetch = globalThis.fetch;
  const control = { failing: true };
  vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) =>
    control.failing && String(input).includes(pathFragment)
      ? Promise.reject(new TypeError('Failed to fetch'))
      : realFetch(input, init),
  );
  return control;
}

function statusReturns(body) {
  const seen = [];
  server.use(
    http.post(`${API}/auth/activation-token/status`, async ({ request }) => {
      seen.push({ body: await request.json(), request });
      return HttpResponse.json(body);
    }),
  );
  return seen;
}

const validStatus = activationTokenStatus('VALID', { username: 'lucia.acosta' });
const importedStatus = activationTokenStatus('VALID', { username: 'lucia.acosta.muy.larga@ejemplo.edu.ar' });

async function fillAndSubmit(user, { password = VALID_PASSWORD, confirmation = password } = {}) {
  await user.type(await screen.findByLabelText('Nueva contraseña'), password);
  await user.type(screen.getByLabelText('Repetir contraseña'), confirmation);
  await user.click(screen.getByRole('button', { name: 'Activar cuenta' }));
}

async function expectFocusedHeading(name) {
  const heading = await screen.findByRole('heading', { name });
  await waitFor(() => expect(heading).toHaveFocus());
}

afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
  window.localStorage.clear();
  window.sessionStorage.clear();
});

describe('ActivateAccount status', () => {
  it('showsASkeletonWhileTheLinkIsChecked', async () => {
    const gate = createDeferred();
    server.use(
      http.post(`${API}/auth/activation-token/status`, async () => {
        await gate.promise;
        return HttpResponse.json(validStatus);
      }),
    );
    renderPage();

    expect(screen.getByRole('status', { name: 'Verificando el enlace' })).toHaveAttribute('aria-busy', 'true');
    gate.resolve();
    expect(await screen.findByRole('heading', { name: 'Activá tu cuenta' })).toBeInTheDocument();
  });

  it('showsThePasswordFormWithTheRealLoginUsernameWhenTheLinkIsValid', async () => {
    const seen = statusReturns(validStatus);
    renderPage();

    await expectFocusedHeading('Activá tu cuenta');
    expect(screen.getByText('lucia.acosta')).toBeInTheDocument();
    expect(screen.getByText(/tu usuario para ingresar es/i)).toBeInTheDocument();
    expect(seen).toHaveLength(1);
    expect(seen[0].body).toEqual({ token: TOKEN });
  });

  it('showsTheEmailAsLoginUsernameForImportedStudentsWithWrapping', async () => {
    statusReturns(importedStatus);
    renderPage();

    const username = await screen.findByText('lucia.acosta.muy.larga@ejemplo.edu.ar');
    expect(username).toHaveClass('[overflow-wrap:anywhere]');
    expect(username).not.toHaveClass('break-all');
    expect(screen.getByText(/tu usuario para ingresar es/i)).toBeInTheDocument();
  });

  it('sendsTheStatusRequestWithoutCachingOrReferrerAndWithoutTheTokenInTheUrl', async () => {
    const seen = statusReturns(validStatus);
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    renderPage();

    await screen.findByRole('heading', { name: 'Activá tu cuenta' });
    const [url, init] = fetchSpy.mock.calls[0];
    expect(init).toMatchObject({ method: 'POST', cache: 'no-store', referrerPolicy: 'no-referrer' });
    expect(String(url)).not.toContain(TOKEN);
    expect(new URL(seen[0].request.url).search).toBe('');
  });

  it('showsTheInstitutionContactAndHowToGetANewInvitationWhenTheLinkExpired', async () => {
    statusReturns(activationTokenStatus('EXPIRED'));
    renderPage();

    await expectFocusedHeading('El enlace venció');
    expect(screen.getByText(/pedí que te manden una invitación nueva/i)).toBeInTheDocument();
    expect(screen.getByText('Instituto Agustina Bermejo', { selector: 'b' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: INSTITUTION.email })).toHaveAttribute('href', `mailto:${INSTITUTION.email}`);
    expect(screen.getByRole('link', { name: INSTITUTION.phoneNumber })).toHaveAttribute('href', 'tel:01155550000');
    expect(screen.queryByLabelText('Nueva contraseña')).not.toBeInTheDocument();
  });

  it('showsAGenericInvalidMessageWithoutHintsWhenTheLinkIsInvalid', async () => {
    statusReturns(activationTokenStatus('INVALID'));
    renderPage();

    await expectFocusedHeading('Enlace no válido');
    expect(screen.getByText('El enlace de activación no es válido o ya fue usado.')).toBeInTheDocument();
    expect(screen.queryByText(/tu usuario para ingresar es/i)).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a iniciar sesión' })).toHaveAttribute('href', '/login');
  });

  it.each([
    ['anUnknownStatus', { status: 'ACTIVE', username: 'ana.perez', institution: INSTITUTION }],
    ['aMissingStatus', { username: 'ana.perez' }],
    ['aLowercaseStatus', { status: 'valid', username: 'ana.perez' }],
    ['anEmptyObject', {}],
    ['aNullBody', null],
  ])('failsClosedToTheInvalidScreenWithoutPasswordFieldsFor_%s', async (_label, body) => {
    statusReturns(body);
    renderPage();

    await expectFocusedHeading('Enlace no válido');
    takeContractDeviations();
    expect(screen.queryByLabelText('Nueva contraseña')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Repetir contraseña')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Activar cuenta' })).not.toBeInTheDocument();
    expect(screen.queryByText(/tu usuario para ingresar es/i)).not.toBeInTheDocument();
  });

  it('failsClosedWhenTheStatusEndpointReturnsAnEmptyBody', async () => {
    server.use(http.post(`${API}/auth/activation-token/status`, () => new HttpResponse(null, { status: 200 })));
    renderPage();

    await expectFocusedHeading('Enlace no válido');
    expect(screen.queryByLabelText('Nueva contraseña')).not.toBeInTheDocument();
  });

  it.each([
    ['aQueryInjection', 'a@b.com?cc=x&body=y'],
    ['aNewline', 'a@b.com\nbcc=x@y.com'],
    ['aJavascriptUrl', 'javascript:alert(1)'],
    ['aComma', 'a@b.com,c@d.com'],
    ['aSpace', 'a b@c.com'],
  ])('rendersAHostileInstitutionEmailAsPlainTextWithoutLinkFor_%s', async (_label, email) => {
    statusReturns(activationTokenStatus('EXPIRED', { institution: { ...INSTITUTION, email } }));
    const { container } = renderPage();

    await expectFocusedHeading('El enlace venció');
    expect(container.querySelector('a[href^="mailto:"]')).toBeNull();
    expect(container.querySelector('a[href^="javascript:"]')).toBeNull();
    expect(container.textContent).toContain(email.replace(/\s+/g, ' ').trim().split(' ')[0]);
  });

  it('wrapsAVeryLongInstitutionEmailAndPhoneInsteadOfOverflowing', async () => {
    const email = `${'secretaria.administrativa.de.alumnos'.repeat(2)}@instituto-agustina-bermejo.edu.ar`;
    statusReturns(activationTokenStatus('EXPIRED', { institution: { ...INSTITUTION, email } }));
    renderPage();

    await expectFocusedHeading('El enlace venció');
    const link = screen.getByRole('link', { name: email });
    expect(link).toHaveClass('min-w-0', '[overflow-wrap:anywhere]');
    expect(screen.getByRole('link', { name: INSTITUTION.phoneNumber })).toHaveClass('min-w-0', '[overflow-wrap:anywhere]');
  });

  it('keepsTheMailtoLinkForAWellFormedInstitutionEmail', async () => {
    statusReturns(activationTokenStatus('EXPIRED', { institution: { ...INSTITUTION, email: 'secretaria+info@instituto.edu.ar' } }));
    renderPage();

    await expectFocusedHeading('El enlace venció');
    expect(screen.getByRole('link', { name: 'secretaria+info@instituto.edu.ar' })).toHaveAttribute(
      'href',
      'mailto:secretaria%2Binfo@instituto.edu.ar',
    );
  });

  it('showsTheSameInvalidScreenWhenThereIsNoTokenAndDoesNotCallTheBackend', async () => {
    let calls = 0;
    server.use(
      http.post(`${API}/auth/activation-token/status`, () => {
        calls += 1;
        return HttpResponse.json(validStatus);
      }),
    );
    renderPage('/activar');

    expect(await screen.findByRole('heading', { name: 'Enlace no válido' })).toBeInTheDocument();
    expect(calls).toBe(0);
  });

  it('showsTheSameInvalidScreenForAMalformedAndForAnUnknownToken', async () => {
    statusReturns(activationTokenStatus('INVALID'));
    const unknown = renderPage('/activar?token=unknown');
    await screen.findByRole('heading', { name: 'Enlace no válido' });
    const unknownText = unknown.container.textContent;
    unknown.unmount();

    const malformed = renderPage(`/activar?token=${'x'.repeat(500)}`);
    await screen.findByRole('heading', { name: 'Enlace no válido' });

    expect(malformed.container.textContent).toBe(unknownText);
  });

  it('offersRetryWhenTheStatusRequestFailsOnTheNetworkAndRecovers', async () => {
    const network = failNetworkFor('/auth/activation-token/status');
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole('heading', { name: 'No pudimos verificar el enlace' })).toBeInTheDocument();
    network.failing = false;
    statusReturns(validStatus);
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('heading', { name: 'Activá tu cuenta' })).toBeInTheDocument();
  });

  it('showsACountdownWhenTheStatusRequestIsRateLimited', async () => {
    server.use(
      http.post(
        `${API}/auth/activation-token/status`,
        () => new HttpResponse(null, { status: 429, headers: { 'Retry-After': '2' } }),
      ),
    );
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Demasiados intentos' })).toBeInTheDocument();
    const button = screen.getByRole('button', { name: 'Reintentar en 2 s' });
    expect(button).toHaveAttribute('aria-disabled', 'true');
    expect(await screen.findByRole('button', { name: 'Reintentar' }, { timeout: 3500 })).toBeEnabled();
  });

  it('removesTheTokenFromTheUrlAfterReadingItAndStillValidatesIt', async () => {
    const seen = statusReturns(validStatus);
    renderPage();

    await screen.findByRole('heading', { name: 'Activá tu cuenta' });
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/activar$/));
    expect(screen.getByTestId('location').textContent).not.toContain(TOKEN);
    expect(seen[0].body.token).toBe(TOKEN);
  });

  it('neverPersistsTheTokenInBrowserStorage', async () => {
    statusReturns(validStatus);
    const user = userEvent.setup();
    server.use(http.post(`${API}/auth/activate`, () => new HttpResponse(null, { status: 204 })));
    renderPage();

    await fillAndSubmit(user);
    await screen.findByRole('heading', { name: 'Cuenta activada' });

    const stored = JSON.stringify({ ...window.localStorage }) + JSON.stringify({ ...window.sessionStorage });
    expect(stored).not.toContain(TOKEN);
    expect(stored).not.toContain(VALID_PASSWORD);
  });
});

describe('ActivateAccount form', () => {
  it('validatesThePasswordPolicyInlineAndDoesNotSubmit', async () => {
    let activations = 0;
    statusReturns(validStatus);
    server.use(
      http.post(`${API}/auth/activate`, () => {
        activations += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText('Nueva contraseña'), 'hola123');
    await user.type(screen.getByLabelText('Repetir contraseña'), 'Hola1234');
    await user.click(screen.getByRole('button', { name: 'Activar cuenta' }));

    expect(await screen.findByText('La contraseña debe tener al menos 8 caracteres.')).toBeInTheDocument();
    expect(screen.getByText('Las contraseñas no coinciden.')).toBeInTheDocument();
    expect(screen.getByLabelText('Nueva contraseña')).toHaveAttribute('aria-invalid', 'true');
    await waitFor(() => expect(screen.getByLabelText('Nueva contraseña')).toHaveFocus());
    expect(activations).toBe(0);
  });

  it('asksForAnUppercaseLetterAndADigit', async () => {
    statusReturns(validStatus);
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText('Nueva contraseña'), 'todominuscula');
    await user.tab();

    expect(await screen.findByText('La contraseña debe tener al menos una mayúscula y un número.')).toBeInTheDocument();
  });

  it('clearsTheInlineErrorOnceThePasswordIsFixed', async () => {
    statusReturns(validStatus);
    const user = userEvent.setup();
    renderPage();

    const field = await screen.findByLabelText('Nueva contraseña');
    await user.type(field, 'corta');
    await user.tab();
    expect(await screen.findByText(/al menos 8 caracteres/i, { selector: 'p' })).toBeInTheDocument();
    await user.type(field, 'LargaYBuena1');

    expect(screen.queryByText(/al menos 8 caracteres/i, { selector: 'p' })).not.toBeInTheDocument();
  });

  it('togglesPasswordVisibilityForBothFields', async () => {
    statusReturns(validStatus);
    const user = userEvent.setup();
    renderPage();

    const field = await screen.findByLabelText('Nueva contraseña');
    expect(field).toHaveAttribute('type', 'password');
    await user.click(screen.getAllByRole('button', { name: 'Mostrar contraseña' })[0]);

    expect(field).toHaveAttribute('type', 'text');
    expect(screen.getByLabelText('Repetir contraseña')).toHaveAttribute('type', 'text');
  });

  it('sendsTheTokenAndNewPasswordAndShowsTheSuccessScreenWithTheUsername', async () => {
    statusReturns(validStatus);
    let sent = null;
    server.use(
      http.post(`${API}/auth/activate`, async ({ request }) => {
        sent = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await fillAndSubmit(user);

    await expectFocusedHeading('Cuenta activada');
    expect(sent).toEqual({ token: TOKEN, newPassword: VALID_PASSWORD });
    expect(screen.getByText('lucia.acosta')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a iniciar sesión' })).toHaveAttribute('href', '/login');
    expect(screen.queryByLabelText('Nueva contraseña')).not.toBeInTheDocument();
  });

  it('disablesTheFormWhileSending', async () => {
    statusReturns(validStatus);
    const gate = createDeferred();
    server.use(
      http.post(`${API}/auth/activate`, async () => {
        await gate.promise;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await fillAndSubmit(user);

    const sending = await screen.findByRole('button', { name: 'Activando…' });
    expect(sending).toBeDisabled();
    expect(screen.getByLabelText('Nueva contraseña')).toBeDisabled();
    gate.resolve();
    await screen.findByRole('heading', { name: 'Cuenta activada' });
  });

  it('switchesToTheExpiredScreenWithContactWhenTheTokenExpiredBetweenSteps', async () => {
    statusReturns(validStatus);
    server.use(
      http.post(`${API}/auth/activate`, () => HttpResponse.json(errorBody('ACTIVATION_TOKEN_EXPIRED'), { status: 400 })),
    );
    const user = userEvent.setup();
    renderPage();

    await fillAndSubmit(user);

    await expectFocusedHeading('El enlace venció');
    expect(screen.getByRole('link', { name: INSTITUTION.email })).toBeInTheDocument();
  });

  it('switchesToTheInvalidScreenWhenTheTokenWasAlreadyUsedBetweenSteps', async () => {
    statusReturns(validStatus);
    server.use(
      http.post(`${API}/auth/activate`, () => HttpResponse.json(errorBody('ACTIVATION_TOKEN_INVALID'), { status: 400 })),
    );
    const user = userEvent.setup();
    renderPage();

    await fillAndSubmit(user);

    expect(await screen.findByRole('heading', { name: 'Enlace no válido' })).toBeInTheDocument();
  });

  it('showsThePasswordPolicyRejectionUnderThePasswordFieldAndKeepsTheForm', async () => {
    statusReturns(validStatus);
    server.use(
      http.post(`${API}/auth/activate`, () => HttpResponse.json(errorBody('INVALID_PASSWORD_POLICY'), { status: 400 })),
    );
    const user = userEvent.setup();
    renderPage();

    await fillAndSubmit(user);

    expect(await screen.findByText('La contraseña no cumple los requisitos de seguridad.')).toBeInTheDocument();
    expect(screen.getByLabelText('Nueva contraseña')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('button', { name: 'Activar cuenta' })).toBeEnabled();
  });

  it('showsACountdownOnRateLimitAndEnablesRetryWhenItEnds', async () => {
    statusReturns(validStatus);
    let attempts = 0;
    server.use(
      http.post(`${API}/auth/activate`, () => {
        attempts += 1;
        if (attempts === 1) return new HttpResponse(null, { status: 429, headers: { 'Retry-After': '2' } });
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await fillAndSubmit(user);

    expect(await screen.findByText('Demasiados intentos. Esperá para volver a probar.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar en 2 s' })).toHaveAttribute('aria-disabled', 'true');
    const retry = await screen.findByRole('button', { name: 'Reintentar' }, { timeout: 3500 });
    await user.click(retry);

    expect(await screen.findByRole('heading', { name: 'Cuenta activada' })).toBeInTheDocument();
    expect(attempts).toBe(2);
  });

  it('usesADefaultCountdownWhenRetryAfterIsMissing', async () => {
    statusReturns(validStatus);
    server.use(http.post(`${API}/auth/activate`, () => new HttpResponse(null, { status: 429 })));
    const user = userEvent.setup();
    renderPage();

    await fillAndSubmit(user);

    expect(await screen.findByRole('button', { name: /^Reintentar en \d+ s$/ })).toHaveAttribute('aria-disabled', 'true');
  });

  it('keepsTheCountdownButtonLegibleAndDoesNotResendWhenClicked', async () => {
    statusReturns(validStatus);
    let attempts = 0;
    server.use(
      http.post(`${API}/auth/activate`, () => {
        attempts += 1;
        return new HttpResponse(null, { status: 429, headers: { 'Retry-After': '30' } });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await fillAndSubmit(user);
    const button = await screen.findByRole('button', { name: /^Reintentar en \d+ s$/ });
    await user.click(button);
    await user.click(button);

    expect(attempts).toBe(1);
    expect(button).toHaveAttribute('aria-disabled', 'true');
    expect(button).not.toBeDisabled();
    expect(button.className).not.toMatch(/(^|\s)opacity-/);
    expect(button.className).toContain('aria-disabled:text-ink-soft');
  });

  it('returnsFocusToThePasswordFieldWhenThePolicyIsRejected', async () => {
    statusReturns(validStatus);
    server.use(
      http.post(`${API}/auth/activate`, () => HttpResponse.json(errorBody('INVALID_PASSWORD_POLICY'), { status: 400 })),
    );
    const user = userEvent.setup();
    renderPage();

    await fillAndSubmit(user);

    await screen.findByText('La contraseña no cumple los requisitos de seguridad.');
    await waitFor(() => expect(screen.getByLabelText('Nueva contraseña')).toHaveFocus());
    expect(document.body).not.toHaveFocus();
  });

  it('returnsFocusToTheSubmitButtonAfterARateLimit', async () => {
    statusReturns(validStatus);
    server.use(http.post(`${API}/auth/activate`, () => new HttpResponse(null, { status: 429, headers: { 'Retry-After': '30' } })));
    const user = userEvent.setup();
    renderPage();

    await fillAndSubmit(user);

    const button = await screen.findByRole('button', { name: /^Reintentar en \d+ s$/ });
    await waitFor(() => expect(button).toHaveFocus());
  });

  it('returnsFocusToTheRetryButtonAfterANetworkFailure', async () => {
    statusReturns(validStatus);
    const user = userEvent.setup();
    renderPage();

    await screen.findByLabelText('Nueva contraseña');
    failNetworkFor('/auth/activate');
    await fillAndSubmit(user);

    const button = await screen.findByRole('button', { name: 'Reintentar' });
    await waitFor(() => expect(button).toHaveFocus());
  });

  it('prefixesInlineErrorsWithAnAriaHiddenIcon', async () => {
    statusReturns(validStatus);
    const user = userEvent.setup();
    const { container } = renderPage();

    await user.click(await screen.findByRole('button', { name: 'Activar cuenta' }));

    const alert = await screen.findByText('La contraseña debe tener al menos 8 caracteres.');
    expect(alert.closest('p').querySelector('svg[aria-hidden="true"]')).not.toBeNull();
    expect(container.querySelectorAll('p[role="alert"] svg[aria-hidden="true"]').length).toBeGreaterThan(0);
  });

  it('showsANetworkErrorWithRetryAndKeepsWhatWasTyped', async () => {
    statusReturns(validStatus);
    server.use(http.post(`${API}/auth/activate`, () => new HttpResponse(null, { status: 204 })));
    const user = userEvent.setup();
    renderPage();

    await screen.findByLabelText('Nueva contraseña');
    const network = failNetworkFor('/auth/activate');
    await fillAndSubmit(user);

    expect(await screen.findByText('No pudimos conectar. Revisá tu conexión.')).toBeInTheDocument();
    expect(screen.getByLabelText('Nueva contraseña')).toHaveValue(VALID_PASSWORD);
    network.failing = false;
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('heading', { name: 'Cuenta activada' })).toBeInTheDocument();
  });

  it('doesNotSendTheActivationTwiceOnDoubleClick', async () => {
    statusReturns(validStatus);
    const gate = createDeferred();
    let attempts = 0;
    server.use(
      http.post(`${API}/auth/activate`, async () => {
        attempts += 1;
        await gate.promise;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await fillAndSubmit(user);
    await screen.findByRole('button', { name: 'Activando…' });
    await act(async () => {
      screen.getByRole('button', { name: 'Activando…' }).form.requestSubmit();
    });
    gate.resolve();
    await screen.findByRole('heading', { name: 'Cuenta activada' });

    expect(attempts).toBe(1);
  });
});

describe('ActivateAccount accessibility', () => {
  it.each([
    ['form', validStatus, 'Activá tu cuenta'],
    ['expired', activationTokenStatus('EXPIRED'), 'El enlace venció'],
    ['invalid', activationTokenStatus('INVALID'), 'Enlace no válido'],
  ])('hasNoAxeViolationsInTheStateOf%s', async (_name, body, heading) => {
    statusReturns(body);
    const { container } = renderPage();

    await screen.findByRole('heading', { name: heading });

    expect(await axe(container)).toHaveNoViolations();
  });

  it('hasNoAxeViolationsWithValidationErrors', async () => {
    statusReturns(validStatus);
    const user = userEvent.setup();
    const { container } = renderPage();

    await user.type(await screen.findByLabelText('Nueva contraseña'), 'corta');
    await user.click(screen.getByRole('button', { name: 'Activar cuenta' }));
    await screen.findAllByRole('alert');

    expect(await axe(container)).toHaveNoViolations();
  });
});
