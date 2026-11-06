import { cn } from '@/lib/utils';

export function ChangesTable({ rows }) {
  return (
    <div className="flex flex-col gap-3">
      <div className="hidden grid-cols-[1.2fr_1fr_1fr_0.8fr] gap-3 text-xs font-semibold uppercase tracking-wide text-ink-faint sm:grid" aria-hidden="true">
        <span />
        <span>Hoy</span>
        <span>Nuevo</span>
        <span>Diferencia</span>
      </div>
      {rows.map((row) => (
        <div
          key={`${row.kind ?? 'row'}-${row.key}`}
          role="group"
          aria-label={row.label}
          className={cn(
            'grid gap-1 border-t border-border pt-3 first:border-t-0 first:pt-0 sm:grid-cols-[1.2fr_1fr_1fr_0.8fr] sm:gap-3',
            row.unchanged && 'text-ink-faint',
          )}
        >
          <span className={cn('font-medium', row.unchanged ? 'text-ink-faint' : 'text-ink')}>{row.label}</span>
          <span className="text-sm text-ink-soft">
            <span className="mr-2 text-xs text-ink-faint sm:hidden">Hoy</span>
            {row.today ?? '—'}
          </span>
          <span className={cn('text-sm', row.unchanged ? 'text-ink-faint' : 'text-ink')}>
            <span className="mr-2 text-xs text-ink-faint sm:hidden">Nuevo</span>
            {row.next ?? '—'}
          </span>
          <span className={cn('text-sm font-semibold', row.unchanged ? 'font-normal text-ink-faint' : 'text-ink')}>{row.difference}</span>
        </div>
      ))}
    </div>
  );
}
