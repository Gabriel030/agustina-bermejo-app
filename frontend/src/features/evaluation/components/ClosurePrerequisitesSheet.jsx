import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { PREREQUISITE_STATUS, REQUIREMENT_TYPE_LABEL } from '../lib/closureSheet';
import { useClosurePrerequisites } from '../hooks/useClosureSheet';
import { ClosureRequirementMark } from './ClosureRequirementMark';

export function ClosurePrerequisitesSheet({ closureId, student, subjectName, onClose }) {
  const { data, isLoading, isError, refetch } = useClosurePrerequisites(closureId, student?.enrollmentId);
  const allMet = (data ?? []).every((item) => item.met);
  return (
    <Sheet open={Boolean(student)} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-md">
        <SheetHeader>
          <SheetTitle>{student ? `Correlativas de ${student.lastname}, ${student.name}` : 'Correlativas'}</SheetTitle>
          <SheetDescription>
            {subjectName} · {isLoading || isError ? 'Revisando correlativas.' : allMet ? 'Cumple todas las correlativas.' : 'Falta cumplir una correlativa.'}
          </SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-2 px-4 pb-4">
          {isLoading && <Skeleton className="h-16" />}
          {isError && (
            <div className="flex flex-col items-start gap-2 text-sm text-ink-soft">
              No pudimos cargar las correlativas.
              <Button type="button" variant="outline" onClick={() => refetch()}>
                Reintentar
              </Button>
            </div>
          )}
          {(data ?? []).map((item) => {
            const status = PREREQUISITE_STATUS[item.studentStatus] ?? { label: item.studentStatus, tone: 'neutral' };
            return (
              <div key={item.subjectCode} className="flex flex-col gap-1 rounded-lg border border-border p-3">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm text-ink">
                    <span className="rounded bg-neutral-bg px-1.5 py-0.5 font-mono text-xs font-bold text-pill-neutral">{item.subjectCode}</span>{' '}
                    {item.subjectName}
                  </span>
                  <ClosureRequirementMark label={item.subjectName} value={item.met} />
                </div>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-xs text-ink-faint">Se pide: {REQUIREMENT_TYPE_LABEL[item.requirementType] ?? item.requirementType}</span>
                  <StatusPill tone={status.tone}>{status.label}</StatusPill>
                </div>
              </div>
            );
          })}
          {!isLoading && !isError && data?.length === 0 && <p className="text-sm text-ink-soft">Esta materia no tiene correlativas.</p>}
          <p className="text-xs text-ink-faint">
            Los códigos son los de la materia en el plan de estudios. &quot;Exceptuada&quot; significa que Dirección autorizó la correlativa.
          </p>
        </div>
      </SheetContent>
    </Sheet>
  );
}
