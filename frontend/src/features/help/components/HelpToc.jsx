import { Button } from '@/components/ui/button';

function TocList({ headings, onSelect }) {
  return (
    <ul className="flex flex-col">
      {headings.map((heading) => (
        <li key={heading.id}>
          <Button
            type="button"
            variant="ghost"
            size="row"
            onClick={() => onSelect(heading.id)}
            className="min-h-11 justify-start px-3 py-1.5 text-size-13 text-ink-soft lg:min-h-0"
          >
            {heading.text}
          </Button>
        </li>
      ))}
    </ul>
  );
}

export function HelpToc({ headings, onSelect }) {
  if (headings.length === 0) return null;
  return (
    <nav aria-label="En esta tarea" className="lg:sticky lg:top-6 lg:self-start">
      <details className="rounded-card border border-border bg-surface lg:hidden">
        <summary className="flex min-h-11 cursor-pointer items-center px-3 text-size-14 font-medium text-ink">
          En esta tarea
        </summary>
        <div className="px-1 pb-2">
          <TocList headings={headings} onSelect={onSelect} />
        </div>
      </details>
      <div className="hidden flex-col gap-1 lg:flex">
        <div className="px-3 pb-1 text-size-11 font-bold uppercase tracking-[0.08em] text-ink-faint">En esta tarea</div>
        <TocList headings={headings} onSelect={onSelect} />
      </div>
    </nav>
  );
}
