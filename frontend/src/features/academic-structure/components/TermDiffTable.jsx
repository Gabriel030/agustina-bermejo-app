export function TermDiffTable({ rows }) {
  return (
    <div className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-3 text-sm sm:grid-cols-[1.2fr_1fr_1fr_1fr]">
      <span className="hidden sm:block" aria-hidden="true" />
      <span className="hidden text-right text-xs font-semibold uppercase tracking-wide text-ink-faint sm:block">Hoy</span>
      <span className="hidden text-right text-xs font-semibold uppercase tracking-wide text-ink-faint sm:block">Nuevo</span>
      <span className="hidden text-right text-xs font-semibold uppercase tracking-wide text-ink-faint sm:block">Diferencia</span>
      {rows.map((row) => (
        <div
          key={row.key}
          role="group"
          aria-label={row.label}
          className="col-span-full grid grid-cols-subgrid items-baseline gap-y-1 border-t border-border pt-3 first:border-t-0 first:pt-0"
        >
          <span className="col-span-full font-medium text-ink sm:col-span-1">{row.label}</span>
          <DiffCell label="Hoy">{row.today}</DiffCell>
          <DiffCell label="Nuevo">{row.next}</DiffCell>
          <DiffCell label="Diferencia" strong>
            {row.difference}
          </DiffCell>
        </div>
      ))}
    </div>
  );
}

function DiffCell({ label, strong = false, children }) {
  return (
    <span className="col-span-full flex items-baseline justify-between gap-3 sm:col-span-1 sm:justify-end">
      <span className="text-xs text-ink-faint sm:hidden">{label}</span>
      <span className={strong ? 'font-semibold tabular-nums text-ink' : 'tabular-nums text-ink-soft'}>{children}</span>
    </span>
  );
}
