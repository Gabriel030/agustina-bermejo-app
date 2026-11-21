import { StatusPill } from '@/components/StatusPill';

function changeCountLabel(count) {
  if (count === 0) return 'Sin cambios en los campos';
  if (count === 1) return '1 campo cambió';
  return `${count} campos cambiaron`;
}

export function AuditChangeSummary({ changes }) {
  return (
    <section aria-label="Campos que cambiaron" className="flex flex-col gap-2">
      <StatusPill tone={changes.count ? 'accent' : 'neutral'}>{changeCountLabel(changes.count)}</StatusPill>
      {changes.count > 0 && (
        <ul className="flex flex-col gap-1.5 text-size-13">
          {changes.changes.map((change) => (
            <li key={change.field} className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-x-2 gap-y-0.5 rounded-control border border-border p-2">
              <span className="col-span-2 break-words font-semibold text-ink-soft">{change.field}</span>
              <span className="min-w-0 break-words rounded-control bg-neutral-bg px-1.5 py-0.5 tabular-nums">{change.before}</span>
              <span className="min-w-0 break-words rounded-control bg-warning-bg px-1.5 py-0.5 font-semibold text-warning-ink tabular-nums">{change.after}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
