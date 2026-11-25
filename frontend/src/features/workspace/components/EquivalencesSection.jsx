import { useMemo, useRef, useState } from 'react';
import { Eye, Info, ListFilter } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { EquivalenceDetailSheet } from '@/features/workspace/components/EquivalenceDetailSheet';
import { EquivalenceStatusPill } from '@/features/workspace/components/EquivalenceStatusPill';
import {
  EQUIVALENCES_SECTION_ID,
  EQUIVALENCE_FILTER_OPTIONS,
  equivalenceTitle,
  filterEquivalences,
} from '@/features/workspace/lib/subjectEquivalences';
import { formatFinalScore } from '@/features/workspace/lib/curriculumPlan';
import { useSubjectEquivalences } from '@/features/enrollment';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { formatDate, formatDateTime } from '@/lib/billing';

const ITEM_CLASS = 'min-h-11 min-w-11 px-3 text-ink-soft data-[state=on]:bg-accent-soft data-[state=on]:text-brand';

function SubjectLabel({ equivalence }) {
  return (
    <div className="flex flex-col items-start gap-1">
      <span className="font-semibold text-ink">{equivalenceTitle(equivalence)}</span>
      {equivalence.inCurrentPlan ? (
        <span className="text-size-12 font-normal text-ink-faint">{equivalence.subjectCode}</span>
      ) : (
        <StatusPill tone="warning">Fuera del plan vigente</StatusPill>
      )}
    </div>
  );
}

function EquivalenceTrail({ equivalence }) {
  const voided = equivalence.status === 'VOIDED';
  const actor = voided ? equivalence.voidedBy : equivalence.recordedBy;
  const moment = voided ? equivalence.voidedAt : equivalence.recordedAt;
  const reason = voided ? equivalence.voidReason : equivalence.reason;
  return (
    <div className="flex flex-col items-start gap-1">
      <EquivalenceStatusPill status={equivalence.status} />
      <span className="text-size-12 text-ink-faint">
        {voided ? 'Anulada' : 'Cargada'} por {actor?.name ?? '—'} el {formatDateTime(moment)}
      </span>
      <span className="line-clamp-2 text-size-12 text-ink-soft">Motivo: {reason}</span>
    </div>
  );
}

function OriginTooltip({ equivalence }) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={`Origen de ${equivalenceTitle(equivalence)}`}
          className="-my-2 h-11 w-11 shrink-0 rounded-full text-ink-faint hover:text-ink"
        >
          <Info className="h-4 w-4" aria-hidden="true" />
        </Button>
      </TooltipTrigger>
      <TooltipContent>
        <ul className="flex flex-col gap-0.5">
          <li>{equivalence.originInstitution}</li>
          <li>Nota {formatFinalScore(equivalence.originScore)}</li>
        </ul>
      </TooltipContent>
    </Tooltip>
  );
}

function DetailButton({ equivalence, onOpen }) {
  return (
    <Button
      type="button"
      variant="outline"
      className="h-11"
      aria-label={`Ver detalle de ${equivalenceTitle(equivalence)}`}
      onClick={(event) => onOpen(equivalence, event.currentTarget)}
    >
      <Eye className="h-4 w-4" aria-hidden="true" />
      Ver detalle
    </Button>
  );
}

function EquivalencesTable({ equivalences, onOpen }) {
  return (
    <TooltipProvider>
      <Table className="table-fixed min-w-[720px]">
        <colgroup>
          <col className="w-[20%]" />
          <col className="w-[22%]" />
          <col className="w-[14%]" />
          <col className="w-[26%]" />
          <col className="w-[18%]" />
        </colgroup>
        <TableCaption className="sr-only">Equivalencias del alumno</TableCaption>
        <TableHeader>
          <TableRow>
            <TableHead scope="col">Materia</TableHead>
            <TableHead scope="col">Origen</TableHead>
            <TableHead scope="col">Resolución</TableHead>
            <TableHead scope="col">Estado</TableHead>
            <TableHead scope="col">
              <span className="sr-only">Detalle</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {equivalences.map((equivalence) => (
            <TableRow key={equivalence.id}>
              <TableCell className="whitespace-normal align-top">
                <SubjectLabel equivalence={equivalence} />
              </TableCell>
              <TableCell className="whitespace-normal align-top">
                <div className="flex items-start gap-1">
                  <span className="min-w-0 pt-0.5 text-ink">{equivalence.originSubjectName}</span>
                  <OriginTooltip equivalence={equivalence} />
                </div>
              </TableCell>
              <TableCell className="whitespace-normal align-top tabular-nums">
                <div className="text-ink">{equivalence.resolutionNumber}</div>
                <div className="text-size-12 text-ink-faint">{formatDate(equivalence.recognizedOn)}</div>
              </TableCell>
              <TableCell className="whitespace-normal align-top">
                <EquivalenceTrail equivalence={equivalence} />
              </TableCell>
              <TableCell className="align-top">
                <DetailButton equivalence={equivalence} onOpen={onOpen} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TooltipProvider>
  );
}

function EquivalenceCards({ equivalences, onOpen }) {
  return (
    <ul className="flex flex-col gap-2.5">
      {equivalences.map((equivalence) => (
        <li key={equivalence.id} className="flex flex-col gap-2 rounded-xl bg-neutral-bg/60 p-3 text-size-13">
          <SubjectLabel equivalence={equivalence} />
          <div className="text-ink-soft">
            {equivalence.originSubjectName} · <span className="tabular-nums">{equivalence.resolutionNumber}</span>
          </div>
          <EquivalenceTrail equivalence={equivalence} />
          <DetailButton equivalence={equivalence} onOpen={onOpen} />
        </li>
      ))}
    </ul>
  );
}

export function EquivalencesSection({ programEnrollmentId, readOnlyNotice = false }) {
  const compact = useMediaQuery('(max-width: 767px)');
  const [filterId, setFilterId] = useState('vigentes');
  const [selectedId, setSelectedId] = useState(null);
  const triggerRef = useRef(null);
  const { data, isLoading, isError, error, refetch } = useSubjectEquivalences(programEnrollmentId, 'ALL');
  const visible = useMemo(() => filterEquivalences(data ?? [], filterId), [data, filterId]);
  const selected = data?.find((equivalence) => equivalence.id === selectedId) ?? null;

  function openDetail(equivalence, trigger) {
    triggerRef.current = trigger;
    setSelectedId(equivalence.id);
  }

  function returnFocusToTrigger(event) {
    event.preventDefault();
    triggerRef.current?.focus();
  }

  return (
    <GlassCard
      role="region"
      id={EQUIVALENCES_SECTION_ID}
      tabIndex={-1}
      aria-labelledby="equivalences-title"
      className="flex flex-col gap-3 p-4 focus-visible:outline-2 focus-visible:outline-solid focus-visible:outline-(--focus-ring)"
    >
      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2">
        <h2 id="equivalences-title" className="font-heading text-base font-bold text-ink">
          Equivalencias
        </h2>
        {readOnlyNotice && <StatusPill tone="neutral" icon={Eye}>Solo lectura</StatusPill>}
      </div>
      {isLoading ? (
        <div className="flex flex-col gap-2" aria-busy="true" aria-label="Cargando equivalencias">
          <Skeleton className="h-12" />
          <Skeleton className="h-12" />
        </div>
      ) : isError ? (
        <LoadErrorState error={error} onRetry={refetch} resource="las equivalencias" bare distinguishForbidden />
      ) : data.length === 0 ? (
        <EmptyState title="Sin equivalencias" description="Cuando se reconozca una van a aparecer acá." />
      ) : (
        <>
          <ToggleGroup
            type="single"
            variant="outline"
            spacing={0}
            value={filterId}
            onValueChange={(next) => next && setFilterId(next)}
            aria-label="Filtrar equivalencias"
            className="self-start"
          >
            {EQUIVALENCE_FILTER_OPTIONS.map((option) => (
              <ToggleGroupItem key={option.id} value={option.id} className={ITEM_CLASS}>
                {option.label}
              </ToggleGroupItem>
            ))}
          </ToggleGroup>
          {visible.length === 0 ? (
            <EmptyState
              title="Ninguna equivalencia coincide"
              description="Probá con otro filtro."
              action={
                <Button variant="outline" className="h-11" onClick={() => setFilterId('todas')}>
                  <ListFilter className="h-4 w-4" aria-hidden="true" />
                  Ver todas
                </Button>
              }
            />
          ) : compact ? (
            <EquivalenceCards equivalences={visible} onOpen={openDetail} />
          ) : (
            <EquivalencesTable equivalences={visible} onOpen={openDetail} />
          )}
        </>
      )}
      <EquivalenceDetailSheet
        equivalence={selected}
        open={selected != null}
        onOpenChange={(next) => !next && setSelectedId(null)}
        onCloseAutoFocus={returnFocusToTrigger}
      />
    </GlassCard>
  );
}
