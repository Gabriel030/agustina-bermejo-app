import { Button } from '@/components/ui/button';
import { ChoiceChip } from '@/components/ChoiceChip';
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select';

const TYPE_CHIPS = [
  { type: 'CLASS', label: 'Clases' },
  { type: 'HOLIDAY', label: 'Feriados' },
  { type: 'EXAM_BOARD', label: 'Mesas' },
];

export function CalendarFilters({ activeTypes, counts, subject, subjects, onToggleType, onChangeSubject }) {
  return (
    <div className="flex flex-col gap-3">
      <h2 className="font-heading text-size-14 font-semibold text-ink">Filtros</h2>
      <div className="flex flex-wrap gap-2">
        {TYPE_CHIPS.map(({ type, label }) => (
          <ChoiceChip
            key={type}
            selected={activeTypes.has(type)}
            onClick={() => onToggleType(type)}
            className="min-h-11 sm:min-h-0"
          >
            {label} <span className="tabular-nums">{counts[type]}</span>
          </ChoiceChip>
        ))}
        <Button variant="ghost" size="inline"
          disabled
          className="inline-flex min-h-11 items-center gap-1.5 rounded-full bg-neutral-bg/60 px-3 py-1 text-size-13 text-ink-faint opacity-70 sm:min-h-0"
        >
          Vencimientos
          <span className="rounded-full bg-neutral-bg px-1.5 text-size-11 font-semibold">Después</span>
        </Button>
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="calendar-subject-filter" className="text-size-12 font-semibold text-ink-soft">
          Materia
        </label>
        <NativeSelect id="calendar-subject-filter" value={subject} onChange={(event) => onChangeSubject(event.target.value)}>
          <NativeSelectOption value="">Todas las materias</NativeSelectOption>
          {subjects.map((name) => (
            <NativeSelectOption key={name} value={name}>
              {name}
            </NativeSelectOption>
          ))}
        </NativeSelect>
        <p className="text-size-12 text-ink-faint">Los feriados se muestran siempre, aunque filtres por materia.</p>
      </div>
    </div>
  );
}
