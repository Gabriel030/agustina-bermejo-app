import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { X } from 'lucide-react';
import { StatusPill } from '@/components/StatusPill';
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { PlanStatusPill } from '@/features/workspace/components/PlanStatusPill';
import { EquivalenceChip } from '@/features/workspace/components/EquivalenceChip';
import {
  describeCurricularTerm,
  describeMissingPrerequisites,
  getDependentsOf,
  isApprovedByEquivalence,
} from '@/features/workspace/lib/curriculumMap';
import { CURRICULUM_STATE_TO_PLAN_STATUS, formatCredits } from '@/features/workspace/lib/curriculumPlan';
import { EquivalenceRecordButton } from '@/features/workspace/components/EquivalenceRecordButton';
import { useEquivalenceActions } from '@/features/workspace/hooks/EquivalenceActionsContext';
import { canRecordFor } from '@/features/workspace/lib/equivalenceEffect';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { formatDate } from '@/lib/billing';

function DetailSection({ title, children }) {
  return (
    <section className="flex flex-col gap-2">
      <h3 className="text-size-11 font-bold tracking-wide text-ink-faint uppercase">{title}</h3>
      {children}
    </section>
  );
}

function EquivalenceDetails({ equivalence }) {
  const rows = [
    ['Institución', equivalence.originInstitution],
    ['Materia de origen', equivalence.originSubjectName],
    ['Fecha', formatDate(equivalence.recognizedOn)],
    ['Resolución', equivalence.resolutionNumber],
  ];
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-size-13">
      {rows.map(([label, value]) => (
        <div key={label} className="col-span-2 grid grid-cols-subgrid">
          <dt className="text-ink-faint">{label}</dt>
          <dd className="font-semibold text-ink">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function SubjectDetailContent({ subject, relationIndex, audience, onViewEnrollment, onRequestRecord, getReturnFocusTarget }) {
  const actions = useEquivalenceActions();
  const staff = audience === 'staff';
  const status = CURRICULUM_STATE_TO_PLAN_STATUS[subject.state];
  const missing = subject.state === 'BLOCKED' ? describeMissingPrerequisites(subject) : [];
  const dependents = getDependentsOf(relationIndex, subject.programSubjectId);
  const byEquivalence = isApprovedByEquivalence(subject) && subject.equivalence;

  return (
    <div className="flex flex-col gap-5 px-4 pb-6">
      <div className="flex flex-wrap items-center gap-1.5">
        <PlanStatusPill status={status} />
        {isApprovedByEquivalence(subject) && <EquivalenceChip />}
        {staff && subject.prerequisitesWaived && <StatusPill tone="neutral">Eximida de correlativas</StatusPill>}
      </div>
      {byEquivalence && (
        <DetailSection title="Equivalencia">
          <EquivalenceDetails equivalence={subject.equivalence} />
        </DetailSection>
      )}
      {missing.length > 0 && (
        <DetailSection title={staff ? 'Qué le falta' : 'Qué te falta'}>
          <ul className="flex flex-col gap-1.5 text-size-13 text-ink">
            {missing.map((text) => (
              <li key={text}>{text}</li>
            ))}
          </ul>
        </DetailSection>
      )}
      {status === 'disponible' && (
        <DetailSection title="Disponible">
          <p className="text-size-13 text-ink">{staff ? 'Ya cumple las correlativas de esta materia.' : 'Ya cumplís las correlativas de esta materia.'}</p>
          {!staff && <p className="text-size-12 text-ink-faint">Podés inscribirte cuando abra la inscripción</p>}
        </DetailSection>
      )}
      <DetailSection title="Qué desbloquea">
        {dependents.length > 0 ? (
          <ul className="flex flex-col gap-1.5 text-size-13 text-ink">
            {dependents.map((dependent) => (
              <li key={dependent.programSubjectId}>
                {dependent.subjectName} <span className="text-ink-faint">({dependent.subjectCode})</span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-size-13 text-ink-faint">No es correlativa de otras materias.</p>
        )}
      </DetailSection>
      {staff && actions?.visible && canRecordFor(subject) && (
        <EquivalenceRecordButton
          actions={actions}
          variant="outline"
          subjectId={subject.programSubjectId}
          onBeforeOpen={onRequestRecord}
          getTrigger={getReturnFocusTarget}
        />
      )}
      {subject.currentEnrollmentId != null && staff && onViewEnrollment && (
        <Button type="button" variant="outline" className="h-11 self-start" onClick={() => onViewEnrollment(subject.currentEnrollmentId)}>
          Ver cursada en Evaluación
        </Button>
      )}
      {subject.currentEnrollmentId != null && !staff && (
        <Button asChild variant="outline" className="h-11 self-start">
          <Link to={`/alumno/mis-carreras/materias/${subject.currentEnrollmentId}`}>Ver mi cursada</Link>
        </Button>
      )}
    </div>
  );
}

export function SubjectDetailSheet({ subject, relationIndex, creditsDefined = true, audience = 'student', onViewEnrollment, getReturnFocusTarget, open, onOpenChange, onCloseAutoFocus }) {
  const compact = useMediaQuery('(max-width: 767px)');
  if (!subject) return null;
  const term = describeCurricularTerm(subject.curricularTerm);
  const facts = [subject.subjectCode, term, creditsDefined ? `${formatCredits(subject.credits)} cr.` : null].filter(Boolean);

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side={compact ? 'bottom' : 'right'}
        showCloseButton={false}
        onCloseAutoFocus={onCloseAutoFocus}
        className="data-[side=right]:w-96 data-[side=right]:max-w-full data-[side=bottom]:max-h-[85dvh] data-[side=bottom]:overflow-y-auto data-[side=right]:overflow-y-auto"
      >
        <SheetHeader className="pr-14">
          <SheetTitle>{subject.subjectName}</SheetTitle>
          <SheetDescription>{facts.join(' · ')}</SheetDescription>
        </SheetHeader>
        <SheetClose asChild>
          <Button variant="ghost" size="icon" className="absolute top-2 right-2 size-11">
            <X aria-hidden="true" />
            <span className="sr-only">Cerrar</span>
          </Button>
        </SheetClose>
        <SubjectDetailContent subject={subject} relationIndex={relationIndex} audience={audience} onViewEnrollment={onViewEnrollment} onRequestRecord={() => onOpenChange(false)} getReturnFocusTarget={getReturnFocusTarget} />
      </SheetContent>
    </Sheet>
  );
}
