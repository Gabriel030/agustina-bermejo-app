import { X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { StatusPill } from '@/components/StatusPill';
import { EquivalenceChip } from '@/features/workspace/components/EquivalenceChip';
import { EquivalenceStatusPill } from '@/features/workspace/components/EquivalenceStatusPill';
import { equivalenceTitle } from '@/features/workspace/lib/subjectEquivalences';
import { formatFinalScore } from '@/features/workspace/lib/curriculumPlan';
import { useEquivalenceActions } from '@/features/workspace/hooks/EquivalenceActionsContext';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { formatDate, formatDateTime } from '@/lib/billing';

function DetailSection({ title, rows }) {
  return (
    <section className="flex flex-col gap-2">
      <h3 className="text-size-11 font-bold tracking-wide text-ink-faint uppercase">{title}</h3>
      <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-size-13">
        {rows.map(([label, value]) => (
          <div key={label} className="col-span-2 grid grid-cols-subgrid">
            <dt className="text-ink-faint">{label}</dt>
            <dd className="font-semibold text-ink">{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

function EquivalenceDetailContent({ equivalence, onRequestVoid }) {
  const actions = useEquivalenceActions();
  const voided = equivalence.status === 'VOIDED';
  const originRows = [
    ['Institución', equivalence.originInstitution],
    ['Materia de origen', equivalence.originSubjectName],
    ['Nota', formatFinalScore(equivalence.originScore)],
    ['Resolución', equivalence.resolutionNumber],
    ['Fecha', formatDate(equivalence.recognizedOn)],
  ];
  const recordedRows = [
    ['Cargada por', equivalence.recordedBy?.name ?? '—'],
    ['Cuándo', formatDateTime(equivalence.recordedAt)],
    ['Motivo', equivalence.reason],
  ];
  const voidedRows = [
    ['Anulada por', equivalence.voidedBy?.name ?? '—'],
    ['Cuándo', formatDateTime(equivalence.voidedAt)],
    ['Motivo', equivalence.voidReason],
  ];

  return (
    <div className="flex flex-col gap-5 px-4 pb-6">
      <div className="flex flex-wrap items-center gap-1.5">
        <EquivalenceStatusPill status={equivalence.status} />
        <EquivalenceChip />
        {!equivalence.inCurrentPlan && <StatusPill tone="warning">Fuera del plan vigente</StatusPill>}
      </div>
      <DetailSection title="Origen" rows={originRows} />
      <DetailSection title="Carga" rows={recordedRows} />
      {voided && <DetailSection title="Anulación" rows={voidedRows} />}
      {!voided && actions?.visible && (
        <div className="flex flex-col items-start gap-1">
          <Button
            type="button"
            variant="destructive"
            className="min-h-11"
            disabled={!actions.canVoid}
            aria-describedby={actions.canVoid ? undefined : 'equivalence-void-denied'}
            onClick={() => onRequestVoid(equivalence)}
          >
            Anular equivalencia
          </Button>
          {!actions.canVoid && (
            <span id="equivalence-void-denied" className="text-size-12 text-ink-soft">
              {actions.voidDeniedReason}
            </span>
          )}
        </div>
      )}
    </div>
  );
}

export function EquivalenceDetailSheet({ equivalence, open, onOpenChange, onCloseAutoFocus, onRequestVoid }) {
  const compact = useMediaQuery('(max-width: 767px)');
  if (!equivalence) return null;
  const facts = [equivalence.subjectCode, equivalence.credits != null ? `${equivalence.credits} cr.` : null].filter(Boolean);

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side={compact ? 'bottom' : 'right'}
        showCloseButton={false}
        onCloseAutoFocus={onCloseAutoFocus}
        className="data-[side=right]:w-96 data-[side=right]:max-w-full data-[side=bottom]:max-h-[85dvh] data-[side=bottom]:overflow-y-auto data-[side=right]:overflow-y-auto"
      >
        <SheetHeader className="pr-14">
          <SheetTitle>{equivalenceTitle(equivalence)}</SheetTitle>
          <SheetDescription>{facts.length > 0 ? facts.join(' · ') : 'Equivalencia'}</SheetDescription>
        </SheetHeader>
        <SheetClose asChild>
          <Button variant="ghost" size="icon" className="absolute top-2 right-2 size-11">
            <X aria-hidden="true" />
            <span className="sr-only">Cerrar</span>
          </Button>
        </SheetClose>
        <EquivalenceDetailContent equivalence={equivalence} onRequestVoid={onRequestVoid} />
      </SheetContent>
    </Sheet>
  );
}
