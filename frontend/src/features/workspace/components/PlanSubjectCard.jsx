import { GlassCard } from '@/components/GlassCard';
import { PLAN_STATUS_PILL } from '@/features/workspace/components/planStatusPills';
import { PlanStatusPill } from '@/features/workspace/components/PlanStatusPill';
import { TIPOLOGIA_LABEL } from '@/lib/notas';

export function PlanSubjectCard({ row, prerequisites }) {
  return (
    <GlassCard
      role="group"
      aria-label={`${row.subjectName}, ${PLAN_STATUS_PILL[row.estado].label}`}
      className={`flex flex-col gap-2 p-3 ${row.estado === 'bloqueada' ? 'opacity-60' : ''}`}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0 font-semibold text-ink">{row.subjectName}</div>
        <PlanStatusPill status={row.estado} />
      </div>
      <div className="flex justify-between gap-2 text-size-13 text-ink-soft">
        <span>Código</span>
        <span className="font-semibold tabular-nums text-ink">{row.subjectCode ?? '—'}</span>
      </div>
      <div className="flex justify-between gap-2 text-size-13 text-ink-soft">
        <span>Créditos</span>
        <span className="font-semibold tabular-nums text-ink">{row.credits ?? '—'}</span>
      </div>
      <div className="flex justify-between gap-2 text-size-13 text-ink-soft">
        <span>Tipología</span>
        <span className="font-semibold text-ink">{TIPOLOGIA_LABEL[row.curricularTerm] ?? '—'}</span>
      </div>
      <div className="text-size-12 text-ink-faint">
        {prerequisites.length === 0
          ? 'Sin correlativas'
          : `Requiere: ${prerequisites.map((p) => `${p.prerequisiteSubjectName} (${p.prerequisiteSubjectCode})`).join(', ')}`}
      </div>
    </GlassCard>
  );
}
