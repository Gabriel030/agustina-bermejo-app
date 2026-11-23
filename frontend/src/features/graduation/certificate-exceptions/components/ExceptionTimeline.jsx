import { timelineEntries } from '@/features/graduation/certificate-exceptions/lib/certificateExceptions';

export function ExceptionTimeline({ exception }) {
  const entries = timelineEntries(exception);
  return (
    <ol className="flex flex-col gap-3" aria-label="Historial de la excepción">
      {entries.map((entry) => (
        <li key={entry.key} className="relative flex gap-3">
          <span className="mt-1.5 size-2.5 shrink-0 rounded-full bg-brand" aria-hidden="true" />
          <div className="flex min-w-0 flex-col">
            <span className="text-size-13-5 font-semibold text-ink">{entry.title}</span>
            {entry.when && <span className="text-size-12 tabular-nums text-ink-faint">{entry.when}</span>}
            {entry.detail && <span className="whitespace-pre-wrap text-size-12-5 text-ink-soft">{entry.detail}</span>}
          </div>
        </li>
      ))}
    </ol>
  );
}
