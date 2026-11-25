import { ChevronDown } from 'lucide-react';
import { Button } from '@/components/ui/button';

export function CurriculumYearAccordion({ sectionKey, title, compact, open, onToggle, children }) {
  const headingId = `curriculum-map-${sectionKey}`;
  const panelId = `curriculum-map-panel-${sectionKey}`;
  const headingClass = 'text-size-11 font-bold tracking-wide text-ink-faint uppercase';

  if (!compact) {
    return (
      <section aria-labelledby={headingId} className="flex min-w-0 flex-col gap-2.5">
        <h3 id={headingId} className={headingClass}>
          {title}
        </h3>
        {children}
      </section>
    );
  }

  return (
    <section aria-labelledby={headingId} className="flex min-w-0 flex-col gap-2.5">
      <h3 id={headingId} className={headingClass}>
        <Button
          type="button"
          variant="ghost"
          aria-expanded={open}
          aria-controls={panelId}
          onClick={onToggle}
          className="h-auto min-h-11 w-full justify-between gap-2 rounded-lg px-1 text-left text-size-11 font-bold tracking-wide text-ink-faint uppercase"
        >
          {title}
          <ChevronDown
            className={`h-4 w-4 shrink-0 transition-transform motion-reduce:transition-none ${open ? 'rotate-180' : ''}`}
            aria-hidden="true"
          />
        </Button>
      </h3>
      <div id={panelId} hidden={!open}>
        {children}
      </div>
    </section>
  );
}
