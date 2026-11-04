import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { useClassroomOccupancy } from '@/features/academic-structure/hooks/useClassrooms';
import { WEEKDAYS, describeOccupant, describeTerm } from '@/features/academic-structure/lib/classroomAvailability';
import { toMinutes } from '@/features/academic-structure/lib/classroomModules';

function overlapsProposal(slot, proposal) {
  if (!proposal || proposal.dayOfWeek !== slot.dayOfWeek) return false;
  return toMinutes(slot.startTime) < toMinutes(proposal.endTime) && toMinutes(slot.endTime) > toMinutes(proposal.startTime);
}

function SlotCard({ slot, proposal }) {
  const clashes = slot.overlapsInDates && overlapsProposal(slot, proposal);
  return (
    <li className="flex flex-col gap-1 rounded-lg border border-surface-border p-2 text-[12px]" data-clash={clashes}>
      <span className="font-semibold text-ink">
        {slot.startTime}–{slot.endTime}
      </span>
      <span className="text-ink-soft">{describeOccupant(slot)}</span>
      {!slot.overlapsInDates && (
        <>
          <StatusPill tone="neutral">Otro período</StatusPill>
          <span className="text-ink-faint">{describeTerm(slot)}</span>
        </>
      )}
      {clashes && <StatusPill tone="warning">Choca con tu franja</StatusPill>}
    </li>
  );
}

export function ClassroomWeekOccupancy({ classroomId, classroomName, termId, proposal, onBack }) {
  const { data, isLoading, isError } = useClassroomOccupancy({ classroomId, termId });
  const slots = data?.slots ?? [];
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-heading text-base font-bold text-ink">Semana de {classroomName}</h2>
        <Button type="button" variant="outline" size="sm" onClick={onBack}>
          <ArrowLeft className="h-3.5 w-3.5" />
          Volver a la lista
        </Button>
      </div>
      {isLoading && <Skeleton className="h-[160px] w-full rounded-xl" />}
      {isError && <EmptyState title="No pudimos cargar la semana del aula." className="py-6" />}
      {data && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {WEEKDAYS.map((day) => {
            const daySlots = slots
              .filter((slot) => slot.dayOfWeek === day.id)
              .sort((a, b) => toMinutes(a.startTime) - toMinutes(b.startTime));
            return (
              <section key={day.id} aria-label={day.label} className="flex flex-col gap-2">
                <h3 className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">{day.label}</h3>
                {daySlots.length === 0 ? (
                  <p className="text-[12px] text-ink-faint">Sin clases.</p>
                ) : (
                  <ul className="flex flex-col gap-2">
                    {daySlots.map((slot) => (
                      <SlotCard key={`${slot.section.id}-${slot.startTime}`} slot={slot} proposal={proposal} />
                    ))}
                  </ul>
                )}
              </section>
            );
          })}
        </div>
      )}
    </div>
  );
}
