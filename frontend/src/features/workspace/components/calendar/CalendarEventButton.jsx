import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { eventAriaLabel, eventMeta, eventTimeLabel, isCanceled } from '@/features/workspace/components/calendar/calendarEventMeta';

export function CalendarEventButton({ event, onSelectEvent, showTime = true }) {
  const meta = eventMeta(event);
  return (
    <Button variant="ghost" size="inline"
      aria-label={eventAriaLabel(event)}
      onClick={() => onSelectEvent(event)}
      className={cn(
        'flex w-full min-w-0 items-center justify-start gap-1 rounded-control border-l-[3px] bg-neutral-bg/60 px-1.5 py-0.5 text-left text-size-12 text-ink hover:bg-neutral-bg focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand',
        meta.bar,
      )}
    >
      <span aria-hidden="true" className={cn('shrink-0 text-size-11', meta.tone)}>
        {meta.glyph}
      </span>
      {showTime && event.startTime && <span className="shrink-0 tabular-nums text-ink-soft">{eventTimeLabel(event)}</span>}
      <span className={cn('truncate', isCanceled(event) && 'line-through')}>{event.title}</span>
    </Button>
  );
}
