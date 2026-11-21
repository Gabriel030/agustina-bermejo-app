import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { TooltipProvider } from '@/components/ui/tooltip';
import { UpcomingEventsCard } from '@/features/workspace/components/UpcomingEventsCard';

const definition = { id: 'upcomingEvents', title: 'Próximos eventos', icon: 'calendar' };

function renderCard(card) {
  return render(
    <MemoryRouter>
      <TooltipProvider>
        <UpcomingEventsCard card={card} />
      </TooltipProvider>
    </MemoryRouter>,
  );
}

describe('UpcomingEventsCard', () => {
  it('shows a loading placeholder without claiming anything is clear', () => {
    renderCard({ ...definition, state: 'loading' });
    const card = screen.getByRole('article', { name: 'Próximos eventos' });
    expect(card.querySelector('[aria-busy="true"]')).not.toBeNull();
    expect(screen.queryByText('Todo al día')).not.toBeInTheDocument();
    expect(screen.queryByText('No hay eventos próximos')).not.toBeInTheDocument();
  });

  it('shows an alert and retries the failed sources when the card errors', async () => {
    const retry = vi.fn();
    renderCard({ ...definition, state: 'error', retry });
    expect(screen.getByRole('alert')).toHaveTextContent('No pudimos cargar los próximos eventos');
    expect(screen.queryByText('No hay eventos próximos')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it('lists each event with its day, weekday and month, type pill and title', () => {
    renderCard({
      ...definition,
      state: 'ready',
      events: [
        { id: 'exam-1', type: 'exam', date: '2026-11-10', title: 'Didáctica', to: '/director/mesas-de-examen' },
        { id: 'holiday-2', type: 'holiday', date: '2026-11-11', title: 'Feriado de prueba', to: null },
      ],
    });
    const card = screen.getByRole('article', { name: 'Próximos eventos' });
    expect(within(card).getByText('10')).toBeInTheDocument();
    expect(within(card).getByText('mar nov')).toBeInTheDocument();
    expect(within(card).getByText('Didáctica')).toBeInTheDocument();
    expect(within(card).getByText('Mesa de examen')).toBeInTheDocument();
    expect(within(card).getByText('Feriado de prueba')).toBeInTheDocument();
    expect(within(card).getByText('Feriado')).toBeInTheDocument();
  });

  it('makes a row a link to its destination only when the event has one', () => {
    renderCard({
      ...definition,
      state: 'ready',
      events: [
        { id: 'exam-1', type: 'exam', date: '2026-11-10', title: 'Didáctica', to: '/director/mesas-de-examen' },
        { id: 'holiday-2', type: 'holiday', date: '2026-11-11', title: 'Feriado de prueba', to: null },
      ],
    });
    expect(screen.getByRole('link', { name: /Didáctica/ })).toHaveAttribute('href', '/director/mesas-de-examen');
    expect(screen.queryByRole('link', { name: /Feriado de prueba/ })).not.toBeInTheDocument();
    expect(screen.getByText('Feriado de prueba')).toBeInTheDocument();
  });

  it('shows the empty text instead of all clear when there are no events', () => {
    renderCard({ ...definition, state: 'ready', events: [] });
    expect(screen.getByText('No hay eventos próximos')).toBeInTheDocument();
    expect(screen.getByText('Cuando se programe algo, lo vas a ver acá.')).toBeInTheDocument();
    expect(screen.queryByText('Todo al día')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
