import { Lock } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { ProgressBar } from '@/components/ProgressBar';
import { PLAN_STATUS_PILL } from '@/features/workspace/components/planStatusPills';
import { PlanStatusPill } from '@/features/workspace/components/PlanStatusPill';
import {
  describeBlockingPrerequisite,
  describeCorequisite,
  formatEarnedCredits,
  formatFinalScore,
} from '@/features/workspace/lib/curriculumPlan';

export function PlanSubjectCard({ row }) {
  return (
    <GlassCard
      role="group"
      aria-label={`${row.subjectName}, ${PLAN_STATUS_PILL[row.estado].label}`}
      className="flex flex-col gap-2 p-3"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0 font-semibold text-ink">{row.subjectName}</div>
        <PlanStatusPill status={row.estado} />
      </div>
      <div className="flex justify-between gap-2 text-size-13 text-ink-soft">
        <span>Créditos</span>
        <span className="font-semibold tabular-nums text-ink">
          {formatEarnedCredits(row)}
        </span>
      </div>
      <div className="flex justify-between gap-2 text-size-13 text-ink-soft">
        <span>Nota</span>
        <span className="font-semibold tabular-nums text-ink">{formatFinalScore(row.finalScore)}</span>
      </div>
      {row.courseworkDone && <ProgressBar value={100} label="Cursada completa" tone="success" showValue={false} />}
      {row.blockedBy.map((prerequisite) => (
        <div key={prerequisite.subjectId} className="flex items-start gap-1.5 text-size-12 text-ink-faint">
          <Lock className="mt-0.5 h-3 w-3 shrink-0" aria-hidden="true" />
          <span>{describeBlockingPrerequisite(prerequisite)}</span>
        </div>
      ))}
      {row.corequisites.map((corequisite) => (
        <div key={corequisite.subjectId} className="text-size-12 text-ink-faint">
          {describeCorequisite(corequisite)}
        </div>
      ))}
    </GlassCard>
  );
}
