import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { informativeSession } from '@/test/fixtures/informativeSessions';
import { ReunionesInformativas } from './ReunionesInformativas';

const API = 'http://localhost:8080';

function listSessions(sessions) {
  server.use(http.get(`${API}/informative-sessions`, () => HttpResponse.json(sessions)));
}

function cardOf(title) {
  return screen.getByRole('article', { name: title });
}

describe('ReunionesInformativas', () => {
  it('showsTheCardWithDateModalityProgramAndRemainingSeats', async () => {
    server.use(http.get(`${API}/programs`, () => HttpResponse.json([{ id: 7, name: 'Licenciatura en Administración' }])));
    listSessions([informativeSession()]);
    renderWithProviders(<ReunionesInformativas />);

    const card = within(await screen.findByRole('article', { name: 'Conocé la Licenciatura en Administración' }));
    expect(card.getByRole('img', { name: '18 de diciembre' })).toBeInTheDocument();
    expect(card.getByText('Online · 19:00 h')).toBeInTheDocument();
    expect(await card.findByText('Licenciatura en Administración')).toBeInTheDocument();
    expect(card.getByText('Quedan 14 lugares')).toBeInTheDocument();
    expect(card.getByRole('button', { name: 'Reservar lugar' })).toBeEnabled();
  });

  it('showsLocationForInPersonSessionsAndAllProgramsWhenUnrelated', async () => {
    listSessions([informativeSession({ modality: 'IN_PERSON', location: 'Sede Centro', relatedProgramId: null })]);
    renderWithProviders(<ReunionesInformativas />);

    expect(await screen.findByText('Presencial · Sede Centro · 19:00 h')).toBeInTheDocument();
    expect(screen.getByText('Todas las carreras')).toBeInTheDocument();
  });

  it('registeringUpdatesTheCounterAndReplacesTheButtonWithTheReservedPill', async () => {
    const user = userEvent.setup();
    listSessions([informativeSession()]);
    server.use(
      http.post(`${API}/informative-sessions/1/register`, () =>
        HttpResponse.json(informativeSession({ registrationCount: 7, registeredByMe: true })),
      ),
    );
    renderWithProviders(<ReunionesInformativas />);

    await user.click(await screen.findByRole('button', { name: 'Reservar lugar' }));

    const card = within(await screen.findByRole('article', { name: 'Conocé la Licenciatura en Administración' }));
    expect(await card.findByText('Ya reservaste')).toBeInTheDocument();
    expect(card.getByText('Quedan 13 lugares')).toBeInTheDocument();
    expect(card.queryByRole('button', { name: 'Reservar lugar' })).not.toBeInTheDocument();
  });

  it('showsOnlyTheFullLabelWithoutAButtonWhenNoSeatsAreLeft', async () => {
    listSessions([informativeSession({ capacity: 10, registrationCount: 10 })]);
    renderWithProviders(<ReunionesInformativas />);

    await screen.findByText('Completa');
    expect(screen.queryByRole('button', { name: /Reservar lugar|Reintentar/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/Quedan/)).not.toBeInTheDocument();
  });

  it('showsAlreadyReservedForSessionsTheStudentBooked', async () => {
    listSessions([informativeSession({ registeredByMe: true })]);
    renderWithProviders(<ReunionesInformativas />);

    expect(await screen.findByText('Ya reservaste')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reservar lugar' })).not.toBeInTheDocument();
  });

  it('showsTheSpanishFullMessageAndTurnsTheCardIntoFullOnSessionFull', async () => {
    const user = userEvent.setup();
    let calls = 0;
    server.use(
      http.get(`${API}/informative-sessions`, () => {
        calls += 1;
        return HttpResponse.json([informativeSession(calls > 1 ? { capacity: 10, registrationCount: 10 } : {})]);
      }),
      http.post(`${API}/informative-sessions/1/register`, () => HttpResponse.json(errorBody('SESSION_FULL'), { status: 409 })),
    );
    renderWithProviders(<ReunionesInformativas />);

    await user.click(await screen.findByRole('button', { name: 'Reservar lugar' }));

    expect(await screen.findByText('Completa')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Reservar lugar|Reintentar/ })).not.toBeInTheDocument();
  });

  it('showsSessionFullMessageInTheCardWhileTheListStillSaysThereIsRoom', async () => {
    const user = userEvent.setup();
    listSessions([informativeSession()]);
    server.use(http.post(`${API}/informative-sessions/1/register`, () => HttpResponse.json(errorBody('SESSION_FULL'), { status: 409 })));
    renderWithProviders(<ReunionesInformativas />);

    await user.click(await screen.findByRole('button', { name: 'Reservar lugar' }));

    expect(await screen.findByText('La charla no tiene lugares disponibles.')).toBeInTheDocument();
  });

  it('showsRetryInsideTheCardWhenRegisteringFailsAndRecoversOnRetry', async () => {
    const user = userEvent.setup();
    let attempts = 0;
    listSessions([informativeSession(), informativeSession({ id: 2, title: 'Otra reunión' })]);
    server.use(
      http.post(`${API}/informative-sessions/1/register`, () => {
        attempts += 1;
        return attempts === 1
          ? HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })
          : HttpResponse.json(informativeSession({ registrationCount: 7, registeredByMe: true }));
      }),
    );
    renderWithProviders(<ReunionesInformativas />);

    await user.click(await within(await screen.findByRole('article', { name: 'Conocé la Licenciatura en Administración' })).findByRole('button', { name: 'Reservar lugar' }));

    const failed = within(cardOf('Conocé la Licenciatura en Administración'));
    expect(await failed.findByRole('alert')).toHaveTextContent('Tuvimos un problema de nuestro lado');
    expect(within(cardOf('Otra reunión')).queryByRole('alert')).not.toBeInTheDocument();

    await user.click(failed.getByRole('button', { name: 'Reintentar' }));

    expect(await failed.findByText('Ya reservaste')).toBeInTheDocument();
    expect(failed.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('showsTheEmptyStateWhenThereAreNoSessions', async () => {
    listSessions([]);
    renderWithProviders(<ReunionesInformativas />);

    expect(await screen.findByText('Todavía no hay reuniones programadas')).toBeInTheDocument();
  });

  it('showsSkeletonsWhileLoading', () => {
    server.use(http.get(`${API}/informative-sessions`, () => new Promise(() => {})));
    const { container } = renderWithProviders(<ReunionesInformativas />);

    expect(container.querySelector('[aria-busy="true"]')).toBeInTheDocument();
  });

  it('showsRetryWhenTheListFailsToLoadAndLoadsOnRetry', async () => {
    const user = userEvent.setup();
    let calls = 0;
    server.use(
      http.get(`${API}/informative-sessions`, () => {
        calls += 1;
        return calls === 1 ? HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 }) : HttpResponse.json([informativeSession()]);
      }),
    );
    renderWithProviders(<ReunionesInformativas />);

    expect(await screen.findByText('No pudimos cargar las reuniones')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('article', { name: 'Conocé la Licenciatura en Administración' })).toBeInTheDocument();
  });

  it('showsNoAccessWithoutRetryWhenForbidden', async () => {
    server.use(http.get(`${API}/informative-sessions`, () => HttpResponse.json(errorBody('FORBIDDEN'), { status: 403 })));
    renderWithProviders(<ReunionesInformativas />);

    expect(await screen.findByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
  });
});
