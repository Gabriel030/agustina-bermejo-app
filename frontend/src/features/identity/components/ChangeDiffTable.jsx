export function ChangeDiffTable({ rows }) {
  return (
    <div className="grid grid-cols-1 gap-x-4 gap-y-3 text-sm sm:grid-cols-[1fr_1.5fr_1.5fr]">
      <span className="hidden text-xs font-semibold uppercase tracking-wide text-ink-faint sm:block">Dato</span>
      <span className="hidden text-xs font-semibold uppercase tracking-wide text-ink-faint sm:block">Hoy</span>
      <span className="hidden text-xs font-semibold uppercase tracking-wide text-ink-faint sm:block">Nuevo</span>
      {rows.map((row) => (
        <div
          key={row.key}
          role="group"
          aria-label={row.label}
          className="col-span-full grid grid-cols-subgrid items-baseline gap-y-1 border-t border-border pt-3 first:border-t-0 first:pt-0"
        >
          <span className="col-span-full font-medium text-ink sm:col-span-1">{row.label}</span>
          <span className="col-span-full flex justify-between gap-3 break-all text-ink-soft sm:col-span-1">
            <span className="text-xs text-ink-faint sm:hidden">Hoy</span>
            {row.today}
          </span>
          <span className="col-span-full flex justify-between gap-3 break-all font-semibold text-brand sm:col-span-1">
            <span className="text-xs font-normal text-ink-faint sm:hidden">Nuevo</span>
            {row.next}
          </span>
        </div>
      ))}
    </div>
  );
}
