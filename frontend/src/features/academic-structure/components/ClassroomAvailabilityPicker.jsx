import { CalendarDays } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import {
  CLASSROOM_STATES,
  classifyClassroom,
  describeOccupant,
  describeTerm,
  sortClassrooms,
} from '@/features/academic-structure/lib/classroomAvailability';

function CapacityBar({ capacity, seats }) {
  if (!seats) return null;
  const percentage = Math.min(100, Math.round((seats / capacity) * 100));
  const exceeded = seats > capacity;
  return (
    <div
      role="img"
      aria-label={`Cupo ${seats} de ${capacity} lugares`}
      className="h-1.5 w-full overflow-hidden rounded-full bg-neutral-bg"
    >
      <div className={exceeded ? 'h-full bg-danger' : 'h-full bg-accent'} style={{ width: `${percentage}%` }} />
    </div>
  );
}

function ClassroomOption({ classroom, seats, chosen, onChoose, onViewWeek }) {
  const stateKey = classifyClassroom(classroom);
  const state = CLASSROOM_STATES[stateKey];
  const occupied = stateKey === 'OCCUPIED' || stateKey === 'FREE_IN_PERIOD';
  return (
    <li className="flex flex-col gap-2 rounded-xl border border-surface-border p-3 data-[chosen=true]:border-accent" data-chosen={chosen}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <label className="flex items-center gap-2 text-[13.5px] font-medium text-ink">
          <input
            type="radio"
            name="classroom"
            checked={chosen}
            onChange={() => onChoose(classroom)}
            aria-label={`${classroom.name}, ${classroom.capacity} lugares, ${state.label}`}
          />
          {classroom.name}
          <span className="text-[12px] font-normal text-ink-faint">{classroom.capacity} lugares</span>
        </label>
        <StatusPill tone={state.tone}>{state.label}</StatusPill>
      </div>
      <CapacityBar capacity={classroom.capacity} seats={seats} />
      {stateKey === 'SHORT_CAPACITY' && (
        <p className="text-[12px] text-danger">Faltan {classroom.capacityShortfall} lugares para el cupo.</p>
      )}
      {occupied && classroom.occupant && (
        <p className="text-[12px] text-ink-soft">
          {describeOccupant(classroom.occupant)} · {describeTerm(classroom.occupant)}
        </p>
      )}
      <div>
        <Button type="button" variant="outline" size="sm" onClick={() => onViewWeek(classroom)}>
          <CalendarDays className="h-3.5 w-3.5" />
          Ver la semana
        </Button>
      </div>
    </li>
  );
}

export function ClassroomAvailabilityPicker({ classrooms, isLoading, isError, seats, chosenId, onChoose, onViewWeek }) {
  if (isLoading) return <Skeleton className="h-[120px] w-full rounded-xl" />;
  if (isError) {
    return <EmptyState title="No pudimos revisar la disponibilidad. Probá de nuevo." className="py-6" />;
  }
  if (!classrooms?.length) return <EmptyState title="No hay aulas activas." className="py-6" />;
  return (
    <ul aria-label="Aulas" className="flex flex-col gap-2">
      {sortClassrooms(classrooms).map((classroom) => (
        <ClassroomOption
          key={classroom.id}
          classroom={classroom}
          seats={seats}
          chosen={chosenId === classroom.id}
          onChoose={onChoose}
          onViewWeek={onViewWeek}
        />
      ))}
    </ul>
  );
}
