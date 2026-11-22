import { Check, Clock, Users, X } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { ProgressBar } from '@/components/ProgressBar';
import { cn } from '@/lib/utils';

function SummaryStat({ icon: Icon, label, value, className }) {
  return (
    <div className={cn('flex min-w-0 flex-col gap-1 rounded-control bg-neutral-bg px-3 py-2.5', className)}>
      <span className="flex items-center gap-1.5 text-size-12 font-bold uppercase tracking-wide">
        <Icon className="size-3.5 shrink-0" aria-hidden="true" />
        {label}
      </span>
      <span className="font-heading text-2xl font-bold tabular-nums">{value}</span>
    </div>
  );
}

export function AttendanceSummaryCard({ enrolled, present, late, absent, marked }) {
  const percent = enrolled === 0 ? 0 : Math.round((marked / enrolled) * 100);
  return (
    <GlassCard role="region" aria-label="Resumen de la clase" className="p-4">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <SummaryStat icon={Users} label="Inscriptos" value={enrolled} className="text-ink" />
        <SummaryStat icon={Check} label="Presentes" value={present} className="bg-success-bg text-success-ink" />
        <SummaryStat icon={Clock} label="Tarde" value={late} className="bg-warning-bg text-warning-ink" />
        <SummaryStat icon={X} label="Ausentes" value={absent} className="bg-danger-bg text-danger-ink" />
      </div>
      <div className="mt-3 border-t border-border pt-3">
        <ProgressBar value={percent} label={`${marked} de ${enrolled} marcados`} />
      </div>
    </GlassCard>
  );
}
