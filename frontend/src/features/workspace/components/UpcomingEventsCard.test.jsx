import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TooltipProvider } from '@/components/ui/tooltip';
import { UpcomingEventsCard } from '@/features/workspace/components/UpcomingEventsCard';

const definition = { id: 'upcomingEvents', title: 'Próximos 30 días', icon: 'calendar' };

function renderCard(card) {
  return render(
    <TooltipProvider>
      <UpcomingEventsCard card={card} />
    </TooltipProvider>,
  );
}

describe('UpcomingEventsCard', () => {
  it('shows a loading placeholder without claiming anything is clear', () => {
    renderCard({ ...definition, state: 'loading' });
    const card = screen.getByRole('article', { name: 'Próximos 30 días' });
    expect(card.querySelector('[aria-busy="true"]')).not.toBeNull();
    expect(screen.queryByText('Todo al día')).not.toBeInTheDocument();
    expect(screen.queryByText(/Sin eventos/)).not.toBeInTheDocument();
  });

  it('shows an alert and retries the failed sources when the card errors', async () => {
    const retry = vi.fn();
    renderCard({ ...definition, state: 'error', retry });
    expect(screen.getByRole('alert')).toHaveTextContent('No pudimos cargar los próximos eventos');
    expect(screen.queryByText(/Sin eventos/)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it('lists events with their short date and title', () => {
    renderCard({
      ...definition,
      state: 'ready',
      count: 2,
      events: [
        { id: 'exam-1', kind: 'exam', date: '2026-11-10', title: 'Mesa de Didáctica' },
        { id: 'holiday-2', kind: 'holiday', date: '2026-11-11', title: 'Feriado de prueba' },
      ],
    });
    const card = screen.getByRole('article', { name: 'Próximos 30 días' });
    expect(within(card).getByText('2')).toBeInTheDocument();
    expect(within(card).getByText('Mesa de Didáctica')).toBeInTheDocument();
    expect(within(card).getByText('10 nov')).toBeInTheDocument();
    expect(within(card).getByText('Feriado de prueba')).toBeInTheDocument();
  });

  it('shows the specific empty text instead of all clear when there are no events', () => {
    renderCard({ ...definition, state: 'ready', count: 0, events: [] });
    expect(screen.getByText('Sin eventos en los próximos 30 días.')).toBeInTheDocument();
    expect(screen.queryByText('Todo al día')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
