import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { formatearFechaCorta } from '@/lib/avisos';
import { Skeleton } from '@/components/Skeleton';
import { LoadErrorState } from '@/components/LoadErrorState';
import { useMisInscripcionesAMesas } from '@/features/evaluation';
import { examOptionsOf } from '../lib/procedures';

export function ExamPicker({ carreraId, value, onChange, disabled }) {
  const { data, isLoading, isError, error, refetch } = useMisInscripcionesAMesas();
  if (isLoading) return <Skeleton className="h-24" />;
  if (isError) return <LoadErrorState bare error={error} onRetry={refetch} resource="tus exámenes" />;
  const options = examOptionsOf(data, carreraId);
  if (options.length === 0) {
    return <p className="text-sm text-ink-soft">Todavía no rendiste ningún examen.</p>;
  }
  return (
    <div className="flex flex-col gap-2">
      <span id="exam-picker-label" className="text-size-13 font-semibold text-ink-soft">
        Elegí el examen
      </span>
      <RadioGroup
        aria-labelledby="exam-picker-label"
        value={value ? String(value) : ''}
        onValueChange={(next) => onChange(Number(next))}
        disabled={disabled}
        className="gap-2"
      >
        {options.map((registration) => {
          const id = `exam-${registration.id}`;
          return (
            <label
              key={registration.id}
              htmlFor={id}
              className="flex min-h-14 cursor-pointer items-center gap-3 rounded-xl border border-border bg-surface px-3 py-2.5 has-[[data-state=checked]]:border-accent has-[[data-state=checked]]:bg-accent-soft"
            >
              <RadioGroupItem id={id} value={String(registration.id)} />
              <span className="flex flex-col">
                <span className="font-semibold text-ink">{registration.examBoard.subjectName}</span>
                <span className="text-sm text-ink-soft">{formatearFechaCorta(registration.examBoard.examDate)}</span>
              </span>
            </label>
          );
        })}
      </RadioGroup>
    </div>
  );
}
