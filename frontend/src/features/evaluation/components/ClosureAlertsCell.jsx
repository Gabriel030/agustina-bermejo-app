import { Button } from '@/components/ui/button';
import { ALERT_LABEL, isWithdrawn } from '../lib/closureSheet';

export function ClosureAlertsCell({ row, onOpenPrerequisites }) {
  if (isWithdrawn(row)) return <span className="text-xs text-ink-faint">Sin cómputo</span>;
  const items = row.alerts.map((alert) => {
    const label = ALERT_LABEL[alert] ?? alert;
    if (alert === 'UNMET_PREREQUISITE') {
      return (
        <Button key={alert} type="button" variant="ghost" size="sm" className="min-h-11 px-1.5 text-warning md:min-h-8" aria-label={`${label}. Ver detalle`} onClick={() => onOpenPrerequisites(row)}>
          ⚠ Correl.
        </Button>
      );
    }
    return (
      <span key={alert} title={label} aria-label={label} role="img" className="px-1.5 text-xs font-semibold text-warning">
        ⚠ TP
      </span>
    );
  });
  if (row.exempted) items.push(<span key="exempted" className="px-1.5 text-xs font-semibold text-ink-soft">★ Exceptuado</span>);
  if (row.teacherObservation) items.push(<span key="obs" title={row.teacherObservation} aria-label={`Observación del docente: ${row.teacherObservation}`} role="img" className="px-1.5 text-xs text-ink-soft">✎</span>);
  return items.length > 0 ? <div className="flex items-center gap-0.5">{items}</div> : <span className="text-xs text-ink-faint">—</span>;
}
