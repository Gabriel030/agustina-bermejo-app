import { Button } from '@/components/ui/button';

export function HelpToc({ headings, onSelect }) {
  if (headings.length === 0) return null;
  return (
    <nav aria-label="En esta tarea" className="flex flex-col gap-1 lg:sticky lg:top-6 lg:self-start">
      <div className="px-3 pb-1 text-size-11 font-bold uppercase tracking-[0.08em] text-ink-faint">En esta tarea</div>
      <ul className="flex flex-col">
        {headings.map((heading) => (
          <li key={heading.id}>
            <Button
              type="button"
              variant="ghost"
              size="row"
              onClick={() => onSelect(heading.id)}
              className="justify-start px-3 py-1.5 text-size-13 text-ink-soft"
            >
              {heading.text}
            </Button>
          </li>
        ))}
      </ul>
    </nav>
  );
}
