import { ChevronRight } from 'lucide-react';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';

const CARD = 'min-h-20 w-full gap-3 rounded-card border border-border bg-surface p-4';

export function HelpSectionCard({ label, taskCount, underRedesign = false, onOpen }) {
  if (underRedesign) {
    return (
      <div className={`${CARD} flex items-center text-ink-faint`}>
        <span className="min-w-0 flex-1 truncate font-heading text-size-15 font-semibold">{label}</span>
        <StatusPill tone="warning">En rediseño</StatusPill>
      </div>
    );
  }
  return (
    <Button type="button" variant="ghost" size="row" onClick={onOpen} className={`${CARD} justify-start`}>
      <span className="min-w-0 flex-1">
        <span className="block truncate font-heading text-size-15 font-semibold text-ink">{label}</span>
        <span className="block text-size-13 font-normal tabular-nums text-ink-faint">
          {taskCount === 1 ? '1 tarea' : `${taskCount} tareas`}
        </span>
      </span>
      <ChevronRight aria-hidden="true" className="h-4 w-4 shrink-0 text-ink-faint" />
    </Button>
  );
}
