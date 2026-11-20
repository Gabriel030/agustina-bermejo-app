import { useState } from 'react';
import { useRoleBasePath } from '@/features/academic-structure/hooks/useRoleBasePath';
import { Link, useSearchParams } from 'react-router-dom';
import { ChevronLeft } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Input } from '@/components/ui/input';
import { EmptyState } from '@/components/EmptyState';
import { ChoiceChip } from '@/components/ChoiceChip';
import { useClassroomsAvailability } from '@/features/academic-structure/hooks/useClassrooms';
import { useTerminos } from '@/features/academic-structure/hooks/useTerminos';
import { ClassroomAvailabilityPicker } from '@/features/academic-structure/components/ClassroomAvailabilityPicker';
import { ClassroomModuleSelector } from '@/features/academic-structure/components/ClassroomModuleSelector';
import { ClassroomWeekOccupancy } from '@/features/academic-structure/components/ClassroomWeekOccupancy';
import { WEEKDAYS } from '@/features/academic-structure/lib/classroomAvailability';
import { SHIFTS, buildModules, selectionRange } from '@/features/academic-structure/lib/classroomModules';
import { NativeSelect } from '@/components/ui/native-select';

function SegmentedButton({ active, onClick, children }) {
  return (
    <ChoiceChip shape="pill" size="md" tone="solid" strong selected={active} onClick={onClick} className="shrink-0">
      {children}
    </ChoiceChip>
  );
}

function FieldTitle({ children }) {
  return <h2 className="text-size-12 font-medium uppercase tracking-wide text-ink-faint">{children}</h2>;
}

function PeriodSelect({ termId, onChange }) {
  const { data: terms } = useTerminos();
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor="calendar-period" className="text-size-12 font-medium uppercase tracking-wide text-ink-faint">
        Período
      </label>
      <NativeSelect
        id="calendar-period"
        value={termId ?? ''}
        onChange={(e) => onChange(e.target.value)}
      >
        <option value="">Seleccioná un período…</option>
        {(terms ?? []).map((t) => <option key={t.id} value={t.id}>{t.description}</option>)}
      </NativeSelect>
    </div>
  );
}

export function PreceptorCalendario() {
  const basePath = useRoleBasePath();
  const [searchParams, setSearchParams] = useSearchParams();
  const termId = searchParams.get('termId');
  const [dayOfWeek, setDayOfWeek] = useState('MONDAY');
  const [shift, setShift] = useState(SHIFTS[0]);
  const [selection, setSelection] = useState([]);
  const [seats, setSeats] = useState('');
  const [chosen, setChosen] = useState(null);
  const [weekClassroom, setWeekClassroom] = useState(null);

  const range = selectionRange(buildModules(shift), selection);
  const seatsNumber = Number(seats) > 0 ? Number(seats) : undefined;
  const availability = useClassroomsAvailability({
    termId,
    dayOfWeek,
    startTime: range?.startTime,
    endTime: range?.endTime,
    seats: seatsNumber,
  });

  function changeShift(next) {
    setShift(next);
    setSelection([]);
  }

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <div>
          <Link
            to={`${basePath}/oferta-academica`}
            className="mb-1 flex items-center gap-1 text-size-12 text-ink-faint hover:text-ink"
          >
            <ChevronLeft className="h-3.5 w-3.5" />
            Oferta académica
          </Link>
          <h1 className="font-heading text-xl font-bold text-ink">Disponibilidad de aula y horario</h1>
        </div>

        <GlassCard className="flex flex-col gap-4 p-4">
          <PeriodSelect
            termId={termId}
            onChange={(value) => {
              setSearchParams(value ? { termId: value } : {}, { replace: true });
              setChosen(null);
              setWeekClassroom(null);
            }}
          />

          {!termId && <EmptyState title="Seleccioná un período para ver disponibilidad." className="py-6" />}

          {termId && weekClassroom && (
            <ClassroomWeekOccupancy
              classroomId={weekClassroom.id}
              classroomName={weekClassroom.name}
              termId={termId}
              proposal={range ? { dayOfWeek, ...range } : null}
              onBack={() => setWeekClassroom(null)}
            />
          )}

          {termId && !weekClassroom && (
            <>
              <FieldTitle>Día</FieldTitle>
              <div className="flex gap-2 overflow-x-auto pb-1">
                {WEEKDAYS.map((day) => (
                  <SegmentedButton key={day.id} active={dayOfWeek === day.id} onClick={() => setDayOfWeek(day.id)}>
                    {day.label}
                  </SegmentedButton>
                ))}
              </div>

              <FieldTitle>Turno</FieldTitle>
              <div className="flex flex-wrap gap-2">
                {SHIFTS.map((item) => (
                  <SegmentedButton key={item.id} active={shift.id === item.id} onClick={() => changeShift(item)}>
                    {item.label} ({item.start}–{item.end})
                  </SegmentedButton>
                ))}
              </div>

              <FieldTitle>Módulos</FieldTitle>
              <ClassroomModuleSelector shift={shift} selection={selection} onChange={setSelection} />

              <div className="flex flex-col gap-1.5">
                <label htmlFor="classroom-seats" className="text-size-12 font-medium uppercase tracking-wide text-ink-faint">
                  Cupo
                </label>
                <Input
                  id="classroom-seats"
                  type="number"
                  min="1"
                  inputMode="numeric"
                  value={seats}
                  onChange={(e) => setSeats(e.target.value)}
                  className="max-w-[160px]"
                />
              </div>

              <FieldTitle>Aulas</FieldTitle>
              {range ? (
                <ClassroomAvailabilityPicker
                  classrooms={availability.data}
                  isLoading={availability.isLoading}
                  isError={availability.isError}
                  seats={seatsNumber}
                  chosenId={chosen?.id}
                  onChoose={setChosen}
                  onViewWeek={setWeekClassroom}
                />
              ) : (
                <EmptyState title="Elegí los módulos para ver qué aulas están libres." className="py-6" />
              )}
            </>
          )}
        </GlassCard>
      </div>
    </PageContainer>
  );
}
