import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { LegalNoticeBanner } from '@/features/identity/legal/LegalNoticeBanner';
import { GuardianGate } from '@/features/identity/legal/GuardianGate';
import { LegalAcceptancesTab } from '@/features/identity/legal/LegalAcceptancesTab';
import { PrivacyPage, TermsPage } from '@/features/identity/legal/LegalDocumentPage';
import { ConfirmarResponsable } from '@/features/identity/pages/ConfirmarResponsable';
import { isMinorOn } from '@/features/identity/lib/legalDocuments';

const API = 'http://localhost:8080';

const DOCUMENTS = [
  { type: 'TERMS', version: '2026-11-01', path: '/legal/terminos' },
  { type: 'PRIVACY', version: '2026-11-01', path: '/legal/privacidad' },
];

function pending(overrides = {}) {
  return {
    documents: DOCUMENTS,
    suggestedDeadline: '2026-11-30',
    gracePeriodExpired: false,
    guardianConfirmationRequired: false,
    ...overrides,
  };
}

function usePending(body) {
  server.use(http.get(`${API}/me/legal-acceptances/pending`, () => HttpResponse.json(body)));
}

function renderWithSession(ui, route = '/') {
  localStorage.setItem('refreshToken', 'refresh-1');
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <AuthProvider>{ui}</AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function renderPublic(ui, route) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <AuthProvider>{ui}</AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('isMinorOn', () => {
  it('countsTheBirthdayBeforeConsideringAnAdult', () => {
    const today = new Date(2026, 10, 13);
    expect(isMinorOn('2008-11-14', today)).toBe(true);
    expect(isMinorOn('2008-11-13', today)).toBe(false);
    expect(isMinorOn('', today)).toBe(false);
  });
});

describe('LegalNoticeBanner', () => {
  it('showsTheDeadlineAndAcceptsWithoutBlocking', async () => {
    usePending(pending());
    let accepted = false;
    server.use(
      http.post(`${API}/me/legal-acceptances`, () => {
        accepted = true;
        usePending(pending({ documents: [] }));
        return HttpResponse.json(pending({ documents: [] }));
      }),
    );
    const user = userEvent.setup();
    renderWithSession(<LegalNoticeBanner />);

    expect(await screen.findByText('Aceptá los términos actualizados')).toBeInTheDocument();
    expect(screen.getByText(/Tenés tiempo hasta el 30 de noviembre de 2026/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Términos y condiciones' })).toHaveAttribute('href', '/legal/terminos');

    await user.click(screen.getByRole('button', { name: 'Aceptar' }));

    await waitFor(() => expect(accepted).toBe(true));
    await waitFor(() => expect(screen.queryByText('Aceptá los términos actualizados')).not.toBeInTheDocument());
  });

  it('staysHiddenWhenThereIsNothingPending', async () => {
    usePending(pending({ documents: [] }));
    renderWithSession(<LegalNoticeBanner />);

    await waitFor(() => expect(screen.queryByText('Aceptá los términos actualizados')).not.toBeInTheDocument());
  });

  it('staysNonBlockingAndSaysSoWhenTheGracePeriodExpired', async () => {
    usePending(pending({ gracePeriodExpired: true }));
    renderWithSession(<LegalNoticeBanner />);

    expect(await screen.findByText(/Podés seguir usando el sistema/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aceptar' })).toBeEnabled();
  });

  it('canBeDismissedForTheSession', async () => {
    usePending(pending());
    const user = userEvent.setup();
    renderWithSession(<LegalNoticeBanner />);

    await screen.findByText('Aceptá los términos actualizados');
    await user.click(screen.getByRole('button', { name: 'Más tarde' }));

    expect(screen.queryByText('Aceptá los términos actualizados')).not.toBeInTheDocument();
  });

  it('showsTheBackendErrorWhenAcceptingFails', async () => {
    usePending(pending());
    server.use(
      http.post(`${API}/me/legal-acceptances`, () => HttpResponse.json(errorBody('GUARDIAN_ACCEPTANCE_REQUIRED'), { status: 409 })),
    );
    const user = userEvent.setup();
    renderWithSession(<LegalNoticeBanner />);

    await user.click(await screen.findByRole('button', { name: 'Aceptar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('La aceptación de una persona menor de edad');
  });

  it('isHiddenForMinorsWaitingForTheGuardian', async () => {
    usePending(pending({ guardianConfirmationRequired: true }));
    renderWithSession(<LegalNoticeBanner />);

    await waitFor(() => expect(screen.queryByText('Aceptá los términos actualizados')).not.toBeInTheDocument());
  });
});

describe('GuardianGate', () => {
  function renderGate(route = '/catalogo') {
    return renderWithSession(
      <GuardianGate>
        <Routes>
          <Route path="*" element={<p>Contenido de la app</p>} />
        </Routes>
      </GuardianGate>,
      route,
    );
  }

  it('showsTheAppWhenNoGuardianConfirmationIsNeeded', async () => {
    renderGate();

    expect(await screen.findByText('Contenido de la app')).toBeInTheDocument();
  });

  it('replacesTheAppWithTheWaitingScreenForAMinorAndResendsTheEmail', async () => {
    usePending(pending({ guardianConfirmationRequired: true }));
    let resent = 0;
    server.use(
      http.post(`${API}/me/legal-acceptances/guardian-confirmation/resend`, () => {
        resent += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderGate();

    expect(await screen.findByRole('heading', { name: 'Tu responsable tiene que confirmar' })).toBeInTheDocument();
    expect(screen.queryByText('Contenido de la app')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Reenviar correo al responsable' }));

    expect(await screen.findByText('Listo, le reenviamos el correo a tu responsable.')).toBeInTheDocument();
    expect(resent).toBe(1);
    expect(screen.getByRole('button', { name: /Reenviar correo en/ })).toBeDisabled();
  });

  it('startsACooldownWhenTheResendIsRateLimited', async () => {
    usePending(pending({ guardianConfirmationRequired: true }));
    server.use(
      http.post(
        `${API}/me/legal-acceptances/guardian-confirmation/resend`,
        () =>
          new HttpResponse(JSON.stringify(errorBody('GUARDIAN_CONFIRMATION_RESEND_LIMIT')), {
            status: 429,
            headers: { 'Content-Type': 'application/json', 'Retry-After': '540' },
          }),
      ),
    );
    const user = userEvent.setup();
    renderGate();

    await user.click(await screen.findByRole('button', { name: 'Reenviar correo al responsable' }));

    expect(await screen.findByText(/Ya enviamos el correo hace poco/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reenviar correo en 9:00' })).toBeDisabled();
  });

  it('keepsPublicLegalPagesReachableForAMinorWaiting', async () => {
    usePending(pending({ guardianConfirmationRequired: true }));
    renderGate('/legal/terminos');

    expect(await screen.findByText('Contenido de la app')).toBeInTheDocument();
  });
});

describe('ConfirmarResponsable', () => {
  function renderConfirmation(route) {
    return renderPublic(
      <Routes>
        <Route path="/confirmar-responsable" element={<ConfirmarResponsable />} />
        <Route path="/login" element={<p>Pantalla de ingreso</p>} />
      </Routes>,
      route,
    );
  }

  it('sendsTheTokenAndShowsSuccess', async () => {
    let received;
    server.use(
      http.post(`${API}/auth/legal/guardian-confirmation`, async ({ request }) => {
        received = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderConfirmation('/confirmar-responsable?token=abc123');

    expect(await screen.findByRole('heading', { name: 'Confirmación registrada' })).toBeInTheDocument();
    expect(received).toEqual({ token: 'abc123' });
  });

  it('explainsAnInvalidOrExpiredToken', async () => {
    server.use(
      http.post(`${API}/auth/legal/guardian-confirmation`, () =>
        HttpResponse.json(errorBody('INVALID_GUARDIAN_CONFIRMATION_TOKEN'), { status: 400 }),
      ),
    );
    renderConfirmation('/confirmar-responsable?token=vencido');

    expect(await screen.findByRole('heading', { name: 'No pudimos confirmar' })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('El enlace de confirmación no es válido o ya venció.');
    expect(screen.getByRole('link', { name: 'Ir a ingresar' })).toHaveAttribute('href', '/login');
  });

  it('showsAnErrorWithoutCallingTheBackendWhenThereIsNoToken', async () => {
    server.use(
      http.post(`${API}/auth/legal/guardian-confirmation`, () => {
        throw new Error('no debería llamarse sin token');
      }),
    );
    renderConfirmation('/confirmar-responsable');

    expect(await screen.findByRole('heading', { name: 'No pudimos confirmar' })).toBeInTheDocument();
  });
});

describe('LegalAcceptancesTab', () => {
  it('listsTheDocumentsWithTheirVersionAndAcceptanceStatus', async () => {
    usePending(pending({ documents: [DOCUMENTS[0]] }));
    renderWithSession(<LegalAcceptancesTab />);

    expect(await screen.findByRole('link', { name: 'Términos y condiciones' })).toHaveAttribute('href', '/legal/terminos');
    expect(screen.getByText('Pendiente de aceptar')).toBeInTheDocument();
    expect(screen.getByText('Aceptado')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aceptar versiones vigentes' })).toBeInTheDocument();
  });

  it('confirmsWhenEverythingIsAccepted', async () => {
    usePending(pending({ documents: [] }));
    renderWithSession(<LegalAcceptancesTab />);

    expect(await screen.findByText('Aceptaste las versiones vigentes de los dos documentos.')).toBeInTheDocument();
  });

  it('updatesTheNewsletterPreference', async () => {
    usePending(pending({ documents: [] }));
    let sent;
    server.use(
      http.put(`${API}/me/newsletter-preference`, async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ optedIn: sent.optedIn, updatedAt: '2026-11-13T12:00:00Z' });
      }),
    );
    const user = userEvent.setup();
    renderWithSession(<LegalAcceptancesTab />);

    const checkbox = await screen.findByRole('checkbox', { name: /novedades del instituto/i });
    expect(checkbox).not.toBeChecked();
    await user.click(checkbox);

    await waitFor(() => expect(checkbox).toBeChecked());
    expect(sent).toEqual({ optedIn: true });
  });
});

describe('legal documents pages', () => {
  it('rendersTheTermsDraftWithNoticeVersionAndToc', async () => {
    renderPublic(<TermsPage />, '/legal/terminos');

    expect(screen.getByRole('heading', { level: 1, name: 'Términos y condiciones' })).toBeInTheDocument();
    expect(screen.getByRole('note')).toHaveTextContent('BORRADOR: debe revisarlo un abogado antes de publicarse.');
    expect(await screen.findByText('Versión 2026-11-01')).toBeInTheDocument();
    expect(screen.getByText('Contenido')).toBeInTheDocument();
    expect(screen.getAllByText('[Razón social]').length).toBeGreaterThan(0);
  });

  it('rendersThePrivacyThirdPartiesTable', async () => {
    renderPublic(<PrivacyPage />, '/legal/privacidad');

    expect(screen.getByRole('heading', { level: 1, name: 'Política de privacidad' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Tercero' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Mercado Pago' })).toBeInTheDocument();
    expect(await screen.findByText('Ley 25.326')).toBeInTheDocument();
  });
});
