import { useMediaQuery } from '@/hooks/useMediaQuery';

function TocLinks({ sections }) {
  return (
    <nav aria-label="Contenido" className="grid gap-0.5 text-size-13">
      {sections.map((section) => (
        <a
          key={section.id}
          href={`#${section.id}`}
          className="rounded-md px-2 py-1.5 text-ink-soft hover:bg-accent-soft hover:text-primary"
        >
          {section.title}
        </a>
      ))}
    </nav>
  );
}

export function DocumentToc({ sections }) {
  const isDesktop = useMediaQuery('(min-width: 1024px)');
  if (isDesktop) {
    return (
      <div className="sticky top-2 self-start">
        <TocLinks sections={sections} />
      </div>
    );
  }
  return (
    <details className="rounded-lg border border-border bg-surface px-3 py-2">
      <summary className="cursor-pointer text-size-13 font-semibold text-ink">Contenido</summary>
      <div className="mt-2">
        <TocLinks sections={sections} />
      </div>
    </details>
  );
}
