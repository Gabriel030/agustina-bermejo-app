import { AlertTriangle, CheckCircle2, OctagonX } from 'lucide-react';
import { StatCard } from '@/components/StatCard';

const COUNTERS = [
  { key: 'issued', label: 'Emitidas', icon: CheckCircle2, tone: 'text-success' },
  { key: 'failed', label: 'Fallidas', icon: AlertTriangle, tone: 'text-warning' },
  { key: 'retriesExhausted', label: 'Reintentos agotados', icon: OctagonX, tone: 'text-danger' },
];

export function RunCounterCards({ counters }) {
  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
      {COUNTERS.map(({ key, label, icon: Icon, tone }) => (
        <StatCard key={key} label={label}>
          <div className="flex items-center gap-2">
            <Icon className={`size-5 shrink-0 ${tone}`} aria-hidden="true" />
            <span className="font-heading text-3xl font-bold text-ink" data-testid={`counter-${key}`}>
              {counters?.[key] ?? 0}
            </span>
          </div>
          {key === 'issued' && <div className="mt-1 text-xs text-ink-faint">Ya emitidas antes: {counters?.alreadyIssued ?? 0}</div>}
        </StatCard>
      ))}
    </div>
  );
}
