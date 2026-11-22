import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { installFixedClock } from '@/test/fixedClock';
import { renderWithProviders } from '@/test/renderWithProviders';
import {
  calendarHandler,
  calendarResponse,
  canceledClassEvent,
  classEvent,
  defaultCalendarEvents,
  examBoardEvent,
  holidayEvent,
} from '@/test/fixtures/studentCalendar';
import { AlumnoCalendario } from '@/features/workspace/pages/AlumnoCalendario';

const originalMatchMedia = window.matchMedia;

installFixedClock({ now: '2026-11-21T12:00:00-03:00' });

beforeEach(() => {
  server.use(calendarHandler());
});

afterEach(() => {
  window.matchMedia = originalMatchMedia;
});

function setViewport(desktop) {
  window.matchMedia = () => ({ matches: desktop, addEventListener() {}, removeEventListener() {} });
}

async function renderCalendar(events = defaultCalendarEvents(), { desktop = true } = {}) {
  server.use(calendarHandler(() => HttpResponse.json(calendarResponse({ events }))));
  setViewport(desktop);
  renderWithProviders(<AlumnoCalendario />);
  await screen.findByRole('heading', { name: desktop ? 'noviembre de 2026' : '16 al 22 de noviembre de 2026' });
}

async function openDetail(user, name) {
  await user.click((await screen.findAllByRole('button', { name }))[0]);
  return screen.findByRole('dialog');
}

describe('AlumnoCalendario event detail', () => {
  it('shows a scheduled class with its date, hours, room and status', async () => {
    const user = userEvent.setup();
    await renderCalendar([classEvent({ endTime: '21:30', startTime: '18:30' })]);

    const dialog = await openDetail(user, 'Clase: Programación II, 18:30, Programada');

    expect(within(dialog).getByText('Clase')).toBeInTheDocument();
    expect(within(dialog).getByRole('heading', { level: 3, name: 'Programación II' })).toBeInTheDocument();
    expect(within(dialog).getByText('viernes 20 de noviembre')).toBeInTheDocument();
    expect(within(dialog).getByText('18:30 a 21:30 h')).toBeInTheDocument();
    expect(within(dialog).getByText('Aula 12')).toBeInTheDocument();
    expect(within(dialog).getByText('Programada')).toBeInTheDocument();
  });

  it('prefixes a canceled class and shows its reason as a danger callout', async () => {
    const user = userEvent.setup();
    await renderCalendar([canceledClassEvent({ reason: 'Paro docente' })]);

    const dialog = await openDetail(user, 'Clase: Matemática, 10:00, Clase cancelada');

    expect(within(dialog).getByRole('heading', { level: 3, name: 'Clase cancelada: Matemática' })).toBeInTheDocument();
    expect(within(dialog).getByText('Clase cancelada', { selector: 'span' })).toBeInTheDocument();
    expect(within(dialog).getByText('Motivo: Paro docente')).toBeInTheDocument();
  });

  it('shows a notice callout for a non-canceled event with a reason', async () => {
    const user = userEvent.setup();
    await renderCalendar([classEvent({ reason: 'Cambio de aula' })]);

    const dialog = await openDetail(user, 'Clase: Programación II, 18:00, Programada');

    expect(within(dialog).getByText('Aviso: Cambio de aula')).toBeInTheDocument();
  });

  it('explains that there are no classes on a holiday and hides hours and room', async () => {
    const user = userEvent.setup();
    await renderCalendar([holidayEvent()]);

    const dialog = await openDetail(user, 'Feriado: Día de la Soberanía Nacional, Todo el día');

    expect(within(dialog).getByText('No hay clases este día. Las clases habituales no se dictan.')).toBeInTheDocument();
    expect(within(dialog).queryByText('Horario')).not.toBeInTheDocument();
    expect(within(dialog).queryByText('Aula')).not.toBeInTheDocument();
    expect(within(dialog).queryByRole('link')).not.toBeInTheDocument();
  });

  it('offers to sign up for an open exam board through its link', async () => {
    const user = userEvent.setup();
    await renderCalendar([examBoardEvent({ status: 'OPEN_FOR_REGISTRATION', title: 'Mesa de Didáctica' })]);

    const dialog = await openDetail(user, 'Mesa de examen: Mesa de Didáctica, 09:00, Inscripción abierta');

    expect(within(dialog).getByText('Inscripción abierta')).toBeInTheDocument();
    expect(within(dialog).getByRole('link', { name: 'Ir a Mesas de examen para anotarme' })).toHaveAttribute('href', '/alumno/mis-carreras');
  });

  it('offers to see the registration of a registered exam board', async () => {
    const user = userEvent.setup();
    await renderCalendar([examBoardEvent()]);

    const dialog = await openDetail(user, 'Mesa de examen: Mesa de Matemática, 09:00, Inscripto a la mesa');

    expect(within(dialog).getByRole('link', { name: 'Ver mi inscripción en Mesas de examen' })).toHaveAttribute('href', '/alumno/mis-carreras');
  });

  it('offers to see the subject of a class that carries a link', async () => {
    const user = userEvent.setup();
    await renderCalendar([classEvent({ link: '/alumno/mis-carreras/materias/4' })]);

    const dialog = await openDetail(user, 'Clase: Programación II, 18:00, Programada');

    expect(within(dialog).getByRole('link', { name: 'Ver la materia' })).toHaveAttribute('href', '/alumno/mis-carreras/materias/4');
  });

  it('shows no action when the event has no link', async () => {
    const user = userEvent.setup();
    await renderCalendar([classEvent(), examBoardEvent({ id: 'exam-9', link: null, title: 'Mesa sin enlace' })]);

    const dialog = await openDetail(user, 'Clase: Programación II, 18:00, Programada');

    expect(within(dialog).queryByRole('link')).not.toBeInTheDocument();
  });

  it('puts the initial focus on the close button, closes with Escape and returns the focus to the event', async () => {
    const user = userEvent.setup();
    await renderCalendar([classEvent()]);
    const [eventButton] = await screen.findAllByRole('button', { name: 'Clase: Programación II, 18:00, Programada' });

    await user.click(eventButton);

    expect(await screen.findByRole('button', { name: 'Cerrar detalle' })).toHaveFocus();
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await waitFor(() => expect(eventButton).toHaveFocus());
  });

  it('closes with the close button and keeps the focus inside while open', async () => {
    const user = userEvent.setup();
    await renderCalendar([classEvent({ link: '/alumno/mis-carreras/materias/4' })]);

    const dialog = await openDetail(user, 'Clase: Programación II, 18:00, Programada');
    await user.tab();
    expect(within(dialog).getByRole('link', { name: 'Ver la materia' })).toHaveFocus();
    await user.tab();
    expect(dialog).toContainElement(document.activeElement);

    await user.click(within(dialog).getByRole('button', { name: 'Cerrar detalle' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('opens the same detail from the compact mobile layout', async () => {
    const user = userEvent.setup();
    await renderCalendar([classEvent()], { desktop: false });
    await user.click(await screen.findByRole('button', { name: 'viernes 20 de noviembre, 1 evento' }));

    const dialog = await openDetail(user, 'Clase: Programación II, 18:00, Programada');

    expect(dialog).toHaveAttribute('data-side', 'bottom');
  });

  it('opens the detail on the right side on desktop', async () => {
    const user = userEvent.setup();
    await renderCalendar([classEvent()]);

    const dialog = await openDetail(user, 'Clase: Programación II, 18:00, Programada');

    expect(dialog).toHaveAttribute('data-side', 'right');
  });
});
