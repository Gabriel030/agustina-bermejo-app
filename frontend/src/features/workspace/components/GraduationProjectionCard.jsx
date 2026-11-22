import { CalendarClock, ChevronDown } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Notice } from '@/components/Notice';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { useGraduationProjection } from '@/features/enrollment';
import {
  PROJECTION_CONFIDENCE,
  describePeriodRange,
  describePeriodUnit,
  describeProjectionReason,
} from '@/features/workspace/lib/curriculumPlan';

const HIDDEN_STATUSES = [403, 404];

function ProjectionBody({ projection }) {
  if (projection.status === 'REQUIREMENTS_MET') {
    return <p className="text-sm text-ink">Ya cumplís los requisitos del plan para egresar.</p>;
  }

  if (projection.status === 'NOT_PROJECTABLE') {
    return (
      <ul className="flex flex-col gap-1 text-sm text-ink-soft">
        {projection.reasons.map((reason) => (
          <li key={reason}>{describeProjectionReason(reason)}</li>
        ))}
        {projection.reasons.length === 0 && <li>{describeProjectionReason(null)}</li>}
      </ul>
    );
  }

  const periodsLabel = describePeriodUnit(projection.periodUnit, projection.estimatedPeriods);
  if (projection.estimatedPeriods == null || !periodsLabel) {
    return <p className="text-sm text-ink-soft">{describeProjectionReason(null)}</p>;
  }

  const confidence = PROJECTION_CONFIDENCE[projection.confidence];
  const range = describePeriodRange(projection.range, projection.periodUnit);
  const showYear = projection.confidence !== 'LOW' && projection.estimatedGraduationYear != null;

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
        <span className="font-heading text-2xl font-bold tabular-nums text-ink">{projection.estimatedPeriods}</span>
        <span className="text-sm text-ink-soft">{periodsLabel}</span>
        {confidence && <StatusPill tone={confidence.tone}>{confidence.label}</StatusPill>}
      </div>
      {range && <div className="text-size-13 text-ink-soft">{range}</div>}
      {showYear && (
        <div className="text-size-13 text-ink-soft">
          Año aproximado: <span className="font-semibold tabular-nums text-ink">{projection.estimatedGraduationYear}</span>
        </div>
      )}
      {projection.confidence === 'LOW' && (
        <Notice tone="warning">Todavía no hay suficiente historial para dar un año. Es una referencia del plan.</Notice>
      )}
    </div>
  );
}

function ProjectionAssumptions({ assumptions }) {
  if (assumptions.length === 0) return null;
  return (
    <Collapsible>
      <CollapsibleTrigger className="group inline-flex min-h-11 items-center gap-1 text-size-13 font-semibold text-ink-soft hover:text-ink">
        Cómo se calcula
        <ChevronDown className="h-4 w-4 transition-transform group-data-[state=open]:rotate-180 motion-reduce:transition-none" aria-hidden="true" />
      </CollapsibleTrigger>
      <CollapsibleContent>
        <ul className="flex flex-col gap-1 pb-1 text-size-12 text-ink-faint">
          {assumptions.map((assumption) => (
            <li key={assumption.code}>{assumption.message}</li>
          ))}
        </ul>
      </CollapsibleContent>
    </Collapsible>
  );
}

export function GraduationProjectionCard({ carreraId }) {
  const { data: projection, isLoading, isError, error, refetch } = useGraduationProjection(carreraId);

  if (isError && HIDDEN_STATUSES.includes(error?.status)) return null;

  return (
    <GlassCard role="region" className="flex flex-col gap-2 p-4" aria-labelledby="graduation-projection-title">
      <h2 id="graduation-projection-title" className="flex items-center gap-1.5 font-heading text-base font-bold text-ink">
        <CalendarClock className="h-4 w-4 text-ink-faint" aria-hidden="true" />
        Proyección de egreso
      </h2>
      {isLoading ? (
        <Skeleton className="h-16" />
      ) : isError ? (
        <LoadErrorState error={error} onRetry={refetch} resource="la proyección de egreso" bare />
      ) : (
        <>
          <ProjectionBody projection={projection} />
          {projection.warnings.map((warning) => (
            <Notice key={warning.code} tone="warning">
              {warning.message}
            </Notice>
          ))}
          <ProjectionAssumptions assumptions={projection.assumptions} />
          <div className="text-size-12 text-ink-faint">Estimación, no un compromiso.</div>
        </>
      )}
    </GlassCard>
  );
}
