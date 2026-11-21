import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { installFixedClock } from '@/test/fixedClock';
import { renderWithProviders } from '@/test/renderWithProviders';
import { calendarHandler, calendarResponse, classEvent, holidayEvent } from '@/test/fixtures/studentCalendar';
import { AlumnoCalendario } from '@/features/workspace/pages/AlumnoCalendario';

const originalMatchMedia = window.matchMedia;

installFixedClock({ now: '2026-11-21T12:00:00-03:00' });

beforeEach(() => {
  server.use(calendarHandler());
});

afterEach(() => {
  window.matchMedia = originalMatchMedia;
});

function useViewport(desktop) {
  window.matchMedia = () => ({ matches: desktop, addEventListener() {}, removeEventListener() {} });
}

function respondWith(events) {
  const requests = [];
  server.use(
    calendarHandler(({ request }) => {
      requests.push(new URL(request.url).searchParams.get('from'));
      return HttpResponse.json(calendarResponse({ events }));
    }),
  );
  return requests;
}

async function renderDesktop() {
  useViewport(true);
  renderWithProviders(<AlumnoCalendario />);
  await screen.findByRole('button', { name: 'sábado 21 de noviembre, hoy, sin eventos' });
}

describe('AlumnoCalendario', () => {
  it('shows the heading, the update time and the disabled export button', async () => {
    await renderDesktop();

    expect(screen.getByRole('heading', { level: 1, name: 'Calendario' })).toBeInTheDocument();
    expect(screen.getByText('Actualizado hoy 09:30')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Agregar a mi calendario' })).toBeDisabled();
    expect(screen.getByRole('heading', { name: 'noviembre de 2026' })).toBeInTheDocument();
  });

  describe('month view on desktop', () => {
    it('names each day with its event count and each event with its type', async () => {
      await renderDesktop();

      expect(screen.getByRole('button', { name: 'viernes 20 de noviembre, 1 evento' })).toBeInTheDocument();
      expect(screen.getAllByRole('button', { name: 'Clase: Programación II' })).toHaveLength(2);
      expect(screen.getByRole('button', { name: 'Feriado: Día de la Soberanía Nacional' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Mesa de examen: Mesa de Matemática' })).toBeInTheDocument();
    });

    it('strikes through canceled classes and names them as canceled', async () => {
      await renderDesktop();

      const canceled = screen.getByRole('button', { name: 'Clase: Matemática (cancelada)' });
      expect(within(canceled).getByText('Matemática')).toHaveClass('line-through');
      const regular = screen.getAllByRole('button', { name: 'Clase: Programación II' })[0];
      expect(within(regular).getByText('Programación II')).not.toHaveClass('line-through');
    });

    it('limits a day to three events and links the rest as "+N más"', async () => {
      const crowded = [1, 2, 3, 4, 5].map((n) => classEvent({ id: `c-${n}`, title: `Materia ${n}`, subjectName: `Materia ${n}` }));
      respondWith(crowded);
      useViewport(true);
      renderWithProviders(<AlumnoCalendario />);

      expect(await screen.findByRole('button', { name: '+2 más' })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Clase: Materia 4' })).not.toBeInTheDocument();
    });

    it('selects the day when an event is clicked and lists it in the selected day block', async () => {
      const user = userEvent.setup();
      await renderDesktop();

      await user.click(screen.getAllByRole('button', { name: 'Clase: Programación II' })[0]);

      const block = screen.getByRole('region', { name: /viernes 20 de noviembre/i });
      expect(within(block).getByText('Programada')).toBeInTheDocument();
      expect(within(block).getByText('Aula 12', { exact: false })).toBeInTheDocument();
    });

    it('shows an empty message for a day without events', async () => {
      await renderDesktop();

      const block = screen.getByRole('region', { name: /sábado 21 de noviembre/i });
      expect(within(block).getByText('Sin eventos este día')).toBeInTheDocument();
      expect(within(block).getByText('Elegí otro día o revisá los filtros.')).toBeInTheDocument();
    });
  });

  describe('navigation', () => {
    it('requests the visible grid of the next month and comes back with "Hoy"', async () => {
      const user = userEvent.setup();
      const requestedFrom = respondWith([classEvent()]);
      useViewport(true);
      renderWithProviders(<AlumnoCalendario />);
      await screen.findByRole('heading', { name: 'noviembre de 2026' });

      await user.click(screen.getByRole('button', { name: 'Mes siguiente' }));
      expect(await screen.findByRole('heading', { name: 'diciembre de 2026' })).toBeInTheDocument();
      expect(await screen.findByRole('button', { name: /^martes 1 de diciembre/ })).toBeInTheDocument();

      await user.click(screen.getByRole('button', { name: 'Hoy' }));
      expect(await screen.findByRole('heading', { name: 'noviembre de 2026' })).toBeInTheDocument();
      expect(requestedFrom).toContain('2026-10-26');
      expect(requestedFrom).toContain('2026-11-30');
    });
  });

  describe('week and agenda views', () => {
    it('shows seven columns with "Sin eventos" on empty days in the week view', async () => {
      const user = userEvent.setup();
      await renderDesktop();

      await user.click(screen.getByRole('button', { name: 'Semana' }));

      expect(screen.getByRole('heading', { name: '16 al 22 de noviembre de 2026' })).toBeInTheDocument();
      expect(screen.getAllByText('Sin eventos')).toHaveLength(6);
      expect(screen.getByRole('button', { name: 'Clase: Programación II' })).toBeInTheDocument();
    });

    it('moves a week at a time and crosses into the next month', async () => {
      const user = userEvent.setup();
      await renderDesktop();
      await user.click(screen.getByRole('button', { name: 'Semana' }));

      await user.click(screen.getByRole('button', { name: 'Semana siguiente' }));
      await user.click(screen.getByRole('button', { name: 'Semana siguiente' }));

      expect(await screen.findByRole('heading', { name: '30 de noviembre al 6 de diciembre de 2026' })).toBeInTheDocument();
      expect(await screen.findByRole('button', { name: 'Mesa de examen: Mesa de Didáctica' })).toBeInTheDocument();
    });

    it('groups the month by day in the agenda view with pills and no selected day block', async () => {
      const user = userEvent.setup();
      await renderDesktop();

      await user.click(screen.getByRole('button', { name: 'Agenda' }));

      expect(screen.getByRole('heading', { name: 'viernes 20 de noviembre' })).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: 'lunes 23 de noviembre' })).toBeInTheDocument();
      expect(screen.getByText('Clase cancelada')).toBeInTheDocument();
      expect(screen.getByText('Inscripto a la mesa')).toBeInTheDocument();
      expect(screen.getByText('Inscripción abierta')).toBeInTheDocument();
      expect(screen.getAllByText('Todo el día').length).toBeGreaterThan(0);
      expect(screen.queryByRole('region', { name: /sábado 21 de noviembre/i })).not.toBeInTheDocument();
    });

    it('explains an empty month in the agenda view', async () => {
      const user = userEvent.setup();
      respondWith([classEvent({ id: 'dec', date: '2026-12-15' })]);
      useViewport(true);
      renderWithProviders(<AlumnoCalendario />);
      await screen.findByRole('heading', { name: 'noviembre de 2026' });

      await user.click(screen.getByRole('button', { name: 'Agenda' }));

      expect(await screen.findByText('Sin eventos este mes')).toBeInTheDocument();
      expect(screen.getByText('Probá con otro mes o revisá los filtros.')).toBeInTheDocument();
    });
  });

  describe('mobile', () => {
    it('opens on the week strip with the agenda of the selected day below', async () => {
      const user = userEvent.setup();
      useViewport(false);
      renderWithProviders(<AlumnoCalendario />);

      expect(await screen.findByRole('heading', { name: '16 al 22 de noviembre de 2026' })).toBeInTheDocument();
      expect(await screen.findByText('Sin eventos este día')).toBeInTheDocument();

      await user.click(screen.getByRole('button', { name: 'viernes 20 de noviembre, 1 evento' }));

      expect(screen.getByRole('button', { name: 'Clase: Programación II' })).toBeInTheDocument();
      expect(screen.getByText('Programada')).toBeInTheDocument();
    });

    it('reveals the filters behind a "Filtros" button and flags an active filter', async () => {
      const user = userEvent.setup();
      useViewport(false);
      renderWithProviders(<AlumnoCalendario />);
      await screen.findByRole('heading', { name: '16 al 22 de noviembre de 2026' });
      expect(screen.queryByRole('button', { name: /^Clases/ })).not.toBeInTheDocument();

      await user.click(screen.getByRole('button', { name: 'Filtros' }));
      await user.click(screen.getByRole('button', { name: /^Feriados/ }));

      expect(screen.getByRole('button', { name: /^Feriados/ })).toHaveAttribute('aria-pressed', 'false');
      expect(screen.getByRole('img', { name: 'con filtros activos' })).toBeInTheDocument();
    });

    it('uses the compact month grid with the day agenda below', async () => {
      const user = userEvent.setup();
      useViewport(false);
      renderWithProviders(<AlumnoCalendario />);
      await screen.findByRole('heading', { name: '16 al 22 de noviembre de 2026' });

      await user.click(screen.getByRole('button', { name: 'Mes' }));

      expect(screen.getByRole('heading', { name: 'noviembre de 2026' })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Clase: Programación II' })).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'viernes 20 de noviembre, 1 evento' })).toBeInTheDocument();
    });
  });

  describe('filters', () => {
    it('shows per-type counters for the visible month and the disabled "Vencimientos" chip', async () => {
      await renderDesktop();

      expect(screen.getByRole('button', { name: 'Clases 3' })).toHaveAttribute('aria-pressed', 'true');
      expect(screen.getByRole('button', { name: 'Feriados 1' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Mesas 2' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Vencimientos/ })).toBeDisabled();
      expect(screen.getByText('Después')).toBeInTheDocument();
    });

    it('hides a type when its chip is switched off', async () => {
      const user = userEvent.setup();
      await renderDesktop();

      await user.click(screen.getByRole('button', { name: 'Clases 3' }));

      expect(screen.queryByRole('button', { name: 'Clase: Programación II' })).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Feriado: Día de la Soberanía Nacional' })).toBeInTheDocument();
    });

    it('filters by subject but keeps holidays visible', async () => {
      const user = userEvent.setup();
      await renderDesktop();

      await user.selectOptions(screen.getByLabelText('Materia'), 'Programación II');

      expect(screen.queryByRole('button', { name: 'Clase: Matemática (cancelada)' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Mesa de examen: Mesa de Matemática' })).not.toBeInTheDocument();
      expect(screen.getAllByRole('button', { name: 'Clase: Programación II' })).toHaveLength(2);
      expect(screen.getByRole('button', { name: 'Feriado: Día de la Soberanía Nacional' })).toBeInTheDocument();
      expect(screen.getByText('Los feriados se muestran siempre, aunque filtres por materia.')).toBeInTheDocument();
    });
  });

  describe('states', () => {
    it('shows a busy skeleton while loading', async () => {
      server.use(calendarHandler(() => new Promise(() => {})));
      useViewport(true);
      renderWithProviders(<AlumnoCalendario />);

      const skeleton = await screen.findByLabelText('Cargando calendario');
      expect(skeleton).toHaveAttribute('aria-busy', 'true');
    });

    it('shows an alert with retry when the request fails and recovers on retry', async () => {
      const user = userEvent.setup();
      let attempts = 0;
      server.use(
        calendarHandler(() => {
          attempts += 1;
          return attempts === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(calendarResponse());
        }),
      );
      useViewport(true);
      renderWithProviders(<AlumnoCalendario />);

      const alert = await screen.findByRole('alert');
      expect(within(alert).getByText('No pudimos cargar tu calendario')).toBeInTheDocument();
      expect(within(alert).getByText('Revisá tu conexión e intentá de nuevo. Si sigue fallando, avisale a Secretaría.')).toBeInTheDocument();

      await user.click(within(alert).getByRole('button', { name: 'Reintentar' }));

      expect(await screen.findByRole('button', { name: 'sábado 21 de noviembre, hoy, sin eventos' })).toBeInTheDocument();
    });

    it('shows the no-enrollments banner when only holidays come back', async () => {
      respondWith([holidayEvent()]);
      useViewport(true);
      renderWithProviders(<AlumnoCalendario />);

      const banner = await screen.findByRole('status');
      expect(within(banner).getByText('Todavía no estás inscripto a ninguna materia')).toBeInTheDocument();
      expect(within(banner).getByRole('link', { name: 'Ver oferta académica' })).toHaveAttribute('href', '/catalogo');
      expect(screen.getByRole('button', { name: 'Feriado: Día de la Soberanía Nacional' })).toBeInTheDocument();
    });

    it('does not show the banner when the student has classes', async () => {
      await renderDesktop();

      expect(screen.queryByText('Todavía no estás inscripto a ninguna materia')).not.toBeInTheDocument();
    });
  });
});
