import { Link } from 'react-router-dom';
import { CalendarClock, ChevronDown } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Notice } from '@/components/Notice';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Button } from '@/components/ui/button';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { useGraduationProjectionFor } from '@/features/enrollment';
import {
  PROJECTION_CONFIDENCE,
  describePeriodRange,
  describePeriodUnit,
  describeProjectionReason,
} from '@/features/workspace/lib/curriculumPlan';

const HIDDEN_STATUSES = [403, 404];

function ProjectionCallToAction({ projection, audience, onViewAvailableSubjects }) {
  if (audience !== 'student') return null;
  if (projection.status === 'REQUIREMENTS_MET') {
    return (
      <Button asChild variant="primary" className="min-h-11 w-full @min-[640px]:w-fit">
        <Link to="?tab=tramites">Ir a Trámites</Link>
      </Button>
    );
  }
  const projectable = projection.status !== 'NOT_PROJECTABLE';
  const horizonOnly =
    projection.status === 'NOT_PROJECTABLE' &&
    projection.reasons.includes('PROJECTION_HORIZON_EXCEEDED') &&
    !projection.reasons.some((reason) => reason !== 'PROJECTION_HORIZON_EXCEEDED');
  if (!(projectable || horizonOnly) || !onViewAvailableSubjects) return null;
  return (
    <Button type="button" variant="outline" className="min-h-11 w-full @min-[640px]:w-fit" onClick={onViewAvailableSubjects}>
      Ver qué puedo cursar
    </Button>
  );
}

function ProjectionBody({ projection, audience }) {
  if (projection.status === 'REQUIREMENTS_MET') {
    return (
      <p className="text-sm text-ink">
        {audience === 'staff' ? 'Ya cumple los requisitos del plan para egresar.' : 'Ya cumplís los requisitos del plan para egresar.'}
      </p>
    );
  }

  if (projection.status === 'NOT_PROJECTABLE') {
    return (
      <ul className="flex flex-col gap-1 text-sm text-ink-soft">
        {projection.reasons.map((reason) => (
          <li key={reason}>{describeProjectionReason(reason, audience)}</li>
        ))}
        {projection.reasons.length === 0 && <li>{describeProjectionReason(null, audience)}</li>}
      </ul>
    );
  }

  const periodsLabel = describePeriodUnit(projection.periodUnit, projection.estimatedPeriods);
  if (projection.estimatedPeriods == null || !periodsLabel) {
    return <p className="text-sm text-ink-soft">{describeProjectionReason(null, audience)}</p>;
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

function ProjectionContent({ projection, audience, isLoading, isError, error, refetch, onViewAvailableSubjects }) {
  if (isLoading) {
    return (
      <>
        <Skeleton className="h-16" />
        {audience === 'student' && <Skeleton className="h-11" />}
      </>
    );
  }
  if (isError) return <LoadErrorState error={error} onRetry={refetch} resource="la proyección de egreso" bare />;
  return (
    <>
      <ProjectionBody projection={projection} audience={audience} />
      {projection.warnings.map((warning) => (
        <Notice key={warning.code} tone="warning">
          {warning.message}
        </Notice>
      ))}
      <ProjectionCallToAction projection={projection} audience={audience} onViewAvailableSubjects={onViewAvailableSubjects} />
      <ProjectionAssumptions assumptions={projection.assumptions} />
      {projection.status !== 'REQUIREMENTS_MET' && <div className="text-size-12 text-ink-faint">Estimación, no un compromiso.</div>}
    </>
  );
}

export function GraduationProjectionCard({ carreraId, collapsible = false, audience = 'student', onViewAvailableSubjects }) {
  const { data: projection, isLoading, isError, error, refetch } = useGraduationProjectionFor(audience, carreraId);

  if (isError && HIDDEN_STATUSES.includes(error?.status)) return null;

  const content = <ProjectionContent projection={projection} audience={audience} isLoading={isLoading} isError={isError} error={error} refetch={refetch} onViewAvailableSubjects={onViewAvailableSubjects} />;

  if (collapsible) {
    return (
      <GlassCard role="region" className="@container p-4" aria-labelledby="graduation-projection-title">
        <Collapsible>
          <h2 id="graduation-projection-title" className="font-heading text-base font-bold text-ink">
            <CollapsibleTrigger className="group flex min-h-11 w-full items-center justify-between gap-2 text-left">
              <span className="flex items-center gap-1.5">
                <CalendarClock className="h-4 w-4 text-ink-faint" aria-hidden="true" />
                Proyección de egreso
              </span>
              <ChevronDown className="h-4 w-4 shrink-0 transition-transform group-data-[state=open]:rotate-180 motion-reduce:transition-none" aria-hidden="true" />
            </CollapsibleTrigger>
          </h2>
          <CollapsibleContent className="flex flex-col gap-2 pt-2">{content}</CollapsibleContent>
        </Collapsible>
      </GlassCard>
    );
  }

  return (
    <GlassCard role="region" className="@container flex flex-col gap-2 p-4" aria-labelledby="graduation-projection-title">
      <h2 id="graduation-projection-title" className="flex items-center gap-1.5 font-heading text-base font-bold text-ink">
        <CalendarClock className="h-4 w-4 text-ink-faint" aria-hidden="true" />
        Proyección de egreso
      </h2>
      {content}
    </GlassCard>
  );
}
