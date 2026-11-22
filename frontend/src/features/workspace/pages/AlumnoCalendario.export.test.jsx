import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { installFixedClock } from '@/test/fixedClock';
import { calendarHandler } from '@/test/fixtures/studentCalendar';
import { ISSUED_FEED_URL, activeFeed, calendarFeedHandler, inactiveFeed, issuedTokenResponse } from '@/test/fixtures/calendarFeed';
import { AlumnoCalendario } from '@/features/workspace/pages/AlumnoCalendario';

const API = 'http://localhost:8080';
const originalMatchMedia = window.matchMedia;
const originalClipboard = navigator.clipboard;

installFixedClock({ now: '2026-11-21T12:00:00-03:00' });

beforeEach(() => {
  window.matchMedia = () => ({ matches: true, addEventListener() {}, removeEventListener() {} });
  server.use(calendarHandler());
});

afterEach(() => {
  window.matchMedia = originalMatchMedia;
  Object.defineProperty(navigator, 'clipboard', { value: originalClipboard, configurable: true });
  sessionStorage.clear();
  localStorage.clear();
});

function useFeed(feed) {
  server.use(calendarFeedHandler(feed));
}

function useIssuedToken(onRequest = () => {}) {
  server.use(
    http.post(`${API}/me/calendar/feed/token`, () => {
      onRequest();
      return HttpResponse.json(issuedTokenResponse(), { status: 201 });
    }),
  );
}

function useClipboard(writeText) {
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
}

async function openPanel() {
  const user = userEvent.setup();
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AlumnoCalendario />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await screen.findByRole('heading', { name: 'noviembre de 2026' });
  const trigger = screen.getByRole('button', { name: 'Agregar a mi calendario' });
  await user.click(trigger);
  const dialog = await screen.findByRole('dialog');
  return { user, dialog, queryClient, trigger };
}

function cachedUrls(queryClient) {
  const serialized = JSON.stringify([
    queryClient.getQueryCache().getAll().map((query) => query.state.data),
    queryClient.getMutationCache().getAll().map((mutation) => mutation.state.data),
  ]);
  return serialized.includes('secret-token-123');
}

describe('AlumnoCalendario export panel', () => {
  it('opens with its title, overline and the footer privacy note', async () => {
    useFeed(inactiveFeed());
    const { dialog } = await openPanel();

    expect(within(dialog).getByText('Exportar')).toBeInTheDocument();
    expect(within(dialog).getByRole('heading', { name: 'Agregar a mi calendario' })).toBeInTheDocument();
    expect(
      within(dialog).getByText('El enlace es personal y va firmado. Incluye solo tus clases, feriados y mesas. No incluye notas. No lo compartas.'),
    ).toBeInTheDocument();
  });

  it('invites to generate the link when there is none', async () => {
    useFeed(inactiveFeed());
    const { dialog } = await openPanel();

    expect(await within(dialog).findByText('Todavía no generaste tu enlace. Al generarlo, tu calendario se mantiene al día solo.')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Generar mi enlace' })).toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Regenerar enlace' })).not.toBeInTheDocument();
  });

  it('shows the new link once generated with the setup steps', async () => {
    useFeed(inactiveFeed());
    useIssuedToken();
    const { user, dialog } = await openPanel();

    await user.click(await within(dialog).findByRole('button', { name: 'Generar mi enlace' }));

    expect(await within(dialog).findByRole('textbox', { name: 'Enlace de suscripción' })).toHaveValue(ISSUED_FEED_URL);
    expect(within(dialog).getByText('Pegalo una vez en Google Calendar, Apple o Outlook y se actualiza solo.')).toBeInTheDocument();
    expect(within(dialog).getByText('En Google Calendar, abrí Otros calendarios y elegí Desde URL.')).toBeInTheDocument();
    expect(within(dialog).getByText('Pegá el enlace y tocá Agregar calendario.')).toBeInTheDocument();
    expect(within(dialog).getByText('Google puede tardar unas horas en mostrar los cambios.')).toBeInTheDocument();
    expect(within(dialog).queryByText(/Enlace nuevo listo/)).not.toBeInTheDocument();
  });

  it('copies the link to the clipboard and confirms it', async () => {
    useFeed(inactiveFeed());
    useIssuedToken();
    const { user, dialog } = await openPanel();
    await user.click(await within(dialog).findByRole('button', { name: 'Generar mi enlace' }));
    await within(dialog).findByRole('textbox', { name: 'Enlace de suscripción' });
    const writeText = vi.fn().mockResolvedValue(undefined);
    useClipboard(writeText);

    await user.click(within(dialog).getByRole('button', { name: 'Copiar enlace' }));

    expect(writeText).toHaveBeenCalledWith(ISSUED_FEED_URL);
    expect(await within(dialog).findByRole('button', { name: 'Copiado ✓' })).toBeInTheDocument();
  });

  it('falls back to selecting the text when the clipboard API is not available', async () => {
    useFeed(inactiveFeed());
    useIssuedToken();
    const { user, dialog } = await openPanel();
    await user.click(await within(dialog).findByRole('button', { name: 'Generar mi enlace' }));
    await within(dialog).findByRole('textbox', { name: 'Enlace de suscripción' });
    useClipboard(vi.fn().mockRejectedValue(new Error('denied')));
    document.execCommand = vi.fn().mockReturnValue(true);

    await user.click(within(dialog).getByRole('button', { name: 'Copiar enlace' }));

    expect(document.execCommand).toHaveBeenCalledWith('copy');
    expect(await within(dialog).findByRole('button', { name: 'Copiado ✓' })).toBeInTheDocument();
  });

  it('explains that an active link cannot be shown again', async () => {
    useFeed(activeFeed({ lastUsedAt: null }));
    const { dialog } = await openPanel();

    expect(await within(dialog).findByText('Tenés un enlace activo')).toBeInTheDocument();
    expect(within(dialog).getByText(/Creado el 18\/11\/2026/)).toBeInTheDocument();
    expect(within(dialog).getByText(/todavía no lo leyó ningún calendario/)).toBeInTheDocument();
    expect(within(dialog).getByText('Por seguridad no podemos volver a mostrarlo. Si lo perdiste, regeneralo.')).toBeInTheDocument();
    expect(within(dialog).queryByRole('textbox', { name: 'Enlace de suscripción' })).not.toBeInTheDocument();
    expect(within(dialog).getByText('Los calendarios que ya usaban el enlace pueden seguir mostrando datos viejos hasta una hora.')).toBeInTheDocument();
  });

  it('shows how long ago the active link was last read', async () => {
    useFeed(activeFeed({ lastUsedAt: '2026-11-21T14:30:00Z' }));
    const { dialog } = await openPanel();

    expect(await within(dialog).findByText(/Última lectura: hace 30 min/)).toBeInTheDocument();
  });

  it('asks for confirmation before regenerating and keeps the link when canceled', async () => {
    useFeed(activeFeed());
    let issued = 0;
    useIssuedToken(() => {
      issued += 1;
    });
    const { user, dialog } = await openPanel();

    await user.click(await within(dialog).findByRole('button', { name: 'Regenerar enlace' }));
    expect(
      within(dialog).getByText('¿Regenerar el enlace? El enlace actual deja de funcionar y los calendarios que lo usan dejan de actualizarse hasta que pegues el nuevo.'),
    ).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    expect(within(dialog).getByRole('button', { name: 'Regenerar enlace' })).toBeInTheDocument();
    expect(issued).toBe(0);
  });

  it('regenerates after confirming and warns that the previous link stopped working', async () => {
    useFeed(activeFeed());
    useIssuedToken();
    const { user, dialog } = await openPanel();

    await user.click(await within(dialog).findByRole('button', { name: 'Regenerar enlace' }));
    await user.click(within(dialog).getByRole('button', { name: 'Sí, regenerar' }));

    expect(await within(dialog).findByRole('textbox', { name: 'Enlace de suscripción' })).toHaveValue(ISSUED_FEED_URL);
    expect(within(dialog).getByText('Enlace nuevo listo. El anterior ya no funciona. Pegá este en tu calendario.')).toBeInTheDocument();
  });

  it('revokes the link after confirming and goes back to the empty state', async () => {
    let active = true;
    useFeed(() => (active ? activeFeed() : inactiveFeed()));
    let revoked = 0;
    server.use(
      http.delete(`${API}/me/calendar/feed/token`, () => {
        revoked += 1;
        active = false;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user, dialog } = await openPanel();

    await user.click(await within(dialog).findByRole('button', { name: 'Revocar enlace' }));
    expect(within(dialog).getByText('¿Revocar el enlace? Los calendarios que lo usan dejan de actualizarse.')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Sí, revocar' }));

    expect(await within(dialog).findByRole('button', { name: 'Generar mi enlace' })).toBeInTheDocument();
    expect(within(dialog).getByText(/Enlace revocado/)).toBeInTheDocument();
    expect(revoked).toBe(1);
  });

  it('shows an alert with a retry when the link could not be generated', async () => {
    useFeed(inactiveFeed());
    let attempts = 0;
    server.use(
      http.post(`${API}/me/calendar/feed/token`, () => {
        attempts += 1;
        if (attempts === 1) {
          return HttpResponse.json({ code: 'EXTERNAL_SERVICE_UNAVAILABLE', retryable: false }, { status: 503 });
        }
        return HttpResponse.json(issuedTokenResponse(), { status: 201 });
      }),
    );
    const { user, dialog } = await openPanel();

    await user.click(await within(dialog).findByRole('button', { name: 'Generar mi enlace' }));

    const alert = await within(dialog).findByRole('alert');
    expect(alert).toHaveTextContent('No pudimos generar tu enlace. Intentá de nuevo en unos minutos. Tu archivo .ics se puede descargar igual.');
    await user.click(within(alert).getByRole('button', { name: 'Reintentar' }));

    expect(await within(dialog).findByRole('textbox', { name: 'Enlace de suscripción' })).toHaveValue(ISSUED_FEED_URL);
  });

  it('never keeps the link in storage, in the query cache or after closing the panel', async () => {
    useFeed(inactiveFeed());
    useIssuedToken();
    const { user, dialog, queryClient, trigger } = await openPanel();
    await user.click(await within(dialog).findByRole('button', { name: 'Generar mi enlace' }));
    await within(dialog).findByRole('textbox', { name: 'Enlace de suscripción' });

    expect(cachedUrls(queryClient)).toBe(false);
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain('secret-token-123');
    expect(window.location.href).not.toContain('secret-token-123');

    server.use(calendarFeedHandler(activeFeed()));
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue(ISSUED_FEED_URL)).not.toBeInTheDocument();

    await user.click(trigger);
    const reopened = await screen.findByRole('dialog');
    expect(await within(reopened).findByText('Tenés un enlace activo')).toBeInTheDocument();
    expect(screen.queryByDisplayValue(ISSUED_FEED_URL)).not.toBeInTheDocument();
    expect(document.body).not.toHaveTextContent('secret-token-123');
  });

  it('returns the focus to the export button when the panel closes', async () => {
    useFeed(inactiveFeed());
    const { user, trigger } = await openPanel();

    await user.keyboard('{Escape}');

    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it('downloads the .ics file with the authenticated client and shows progress', async () => {
    useFeed(inactiveFeed());
    const createObjectURL = vi.fn(() => 'blob:calendar');
    const revokeObjectURL = vi.fn();
    URL.createObjectURL = createObjectURL;
    URL.revokeObjectURL = revokeObjectURL;
    let release;
    const gate = new Promise((resolve) => {
      release = resolve;
    });
    let requested = 0;
    server.use(
      http.get(`${API}/me/calendar/export.ics`, async () => {
        requested += 1;
        await gate;
        return new HttpResponse('BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n', {
          headers: { 'Content-Type': 'text/calendar', 'Content-Disposition': 'attachment; filename="calendario.ics"' },
        });
      }),
    );
    const { user, dialog } = await openPanel();

    await user.click(within(dialog).getByRole('button', { name: 'Descargar archivo .ics' }));

    expect(await within(dialog).findByRole('button', { name: 'Descargando…' })).toBeDisabled();
    release();
    await waitFor(() => expect(createObjectURL).toHaveBeenCalled());
    expect(requested).toBe(1);
    expect(await within(dialog).findByRole('button', { name: 'Descargar archivo .ics' })).toBeEnabled();
  });

  it('describes the failure when the .ics download is unavailable', async () => {
    useFeed(inactiveFeed());
    server.use(
      http.get(`${API}/me/calendar/export.ics`, () =>
        HttpResponse.json({ code: 'EXTERNAL_SERVICE_UNAVAILABLE', retryable: false }, { status: 503 }),
      ),
    );
    const { user, dialog } = await openPanel();

    await user.click(within(dialog).getByRole('button', { name: 'Descargar archivo .ics' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Un servicio externo no está disponible. Probá en unos minutos.');
  });

  it('lets the keyboard reach every control inside the panel', async () => {
    useFeed(inactiveFeed());
    const { user, dialog } = await openPanel();
    await within(dialog).findByRole('button', { name: 'Generar mi enlace' });

    const reached = [];
    for (let step = 0; step < 6; step += 1) {
      await user.tab();
      reached.push(document.activeElement.textContent || document.activeElement.getAttribute('aria-label'));
    }

    expect(dialog).toContainElement(document.activeElement);
    expect(reached).toEqual(expect.arrayContaining(['Generar mi enlace', 'Descargar archivo .ics']));
  });
});
