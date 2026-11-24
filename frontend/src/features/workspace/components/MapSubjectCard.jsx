import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { PlanStatusPill } from '@/features/workspace/components/PlanStatusPill';
import { EquivalenceChip } from '@/features/workspace/components/EquivalenceChip';
import { PLAN_STATUS_PILL } from '@/features/workspace/components/planStatusPills';
import { describeCurricularTerm, isApprovedByEquivalence } from '@/features/workspace/lib/curriculumMap';
import { CURRICULUM_STATE_TO_PLAN_STATUS, formatCredits, formatFinalScore } from '@/features/workspace/lib/curriculumPlan';

function describeScore(subject, byEquivalence) {
  if (subject.state !== 'APPROVED') return null;
  if (subject.finalScore != null) return { text: formatFinalScore(subject.finalScore), spoken: `nota ${formatFinalScore(subject.finalScore)}` };
  if (byEquivalence) return { text: 'Eq.', spoken: 'sin nota' };
  return null;
}

export function MapSubjectCard({ subject, creditsDefined = true, selected = false, onSelect }) {
  const status = CURRICULUM_STATE_TO_PLAN_STATUS[subject.state];
  const byEquivalence = isApprovedByEquivalence(subject);
  const score = describeScore(subject, byEquivalence);
  const term = describeCurricularTerm(subject.curricularTerm);
  const statusLabel = byEquivalence ? 'Aprobada por equivalencia' : PLAN_STATUS_PILL[status].label;
  const accessibleName = [subject.subjectName, statusLabel, score?.spoken].filter(Boolean).join(', ');

  return (
    <Button
      type="button"
      variant="row-card"
      size="row"
      aria-pressed={selected}
      aria-haspopup="dialog"
      aria-label={accessibleName}
      onClick={() => onSelect?.(subject.programSubjectId)}
      className={cn(
        'min-h-11 flex-col items-stretch justify-start gap-2 px-3 font-normal',
        selected && 'border-primary bg-accent-soft ring-1 ring-primary hover:bg-accent-soft',
      )}
    >
      <span className="flex items-start justify-between gap-2">
        <span className="min-w-0 font-semibold text-ink">{subject.subjectName}</span>
        {score && <span className="shrink-0 font-semibold tabular-nums text-ink">{score.text}</span>}
      </span>
      <span className="flex flex-wrap items-center gap-x-2 text-size-12 text-ink-faint">
        <span>{subject.subjectCode}</span>
        {term && <span>{term}</span>}
        {creditsDefined && <span className="tabular-nums">{formatCredits(subject.credits)} cr.</span>}
      </span>
      <span className="flex flex-wrap items-center gap-1.5">
        <PlanStatusPill status={status} />
        {byEquivalence && <EquivalenceChip />}
      </span>
      {status === 'disponible' && (
        <span className="text-size-12 text-ink-faint">Podés inscribirte cuando abra la inscripción</span>
      )}
    </Button>
  );
}
