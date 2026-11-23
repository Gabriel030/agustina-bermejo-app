import { useState } from 'react';
import { Check } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/Skeleton';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { useDebtExceptionStudentSearch } from '@/features/graduation/debt-exceptions/hooks/useDebtExceptions';
import { SEARCH_MIN_LENGTH, studentDisplayName } from '@/features/graduation/debt-exceptions/lib/debtException';

const SEARCH_DEBOUNCE_MS = 300;

function Results({ query, term, onPick }) {
  if (term.trim().length < SEARCH_MIN_LENGTH) return null;
  if (query.isLoading || query.isFetching) return <Skeleton className="h-11 w-full" />;
  if (query.isError) return <p role="alert" className="text-size-13 text-danger">No pudimos buscar alumnos. Probá de nuevo.</p>;
  const items = query.data?.items ?? [];
  if (items.length === 0) return <p role="status" className="text-size-13 text-ink-soft">No encontramos alumnos con ese apellido o DNI.</p>;
  return (
    <ul aria-label="Alumnos encontrados" className="flex flex-col gap-1">
      {items.map((student) => (
        <li key={student.id}>
          <Button
            type="button"
            variant="row-card"
            size="row"
            className="min-h-11"
            onClick={() => onPick(student)}
            aria-label={`Elegir a ${studentDisplayName(student)}, DNI ${student.dni}`}
          >
            <span className="font-semibold text-ink">{studentDisplayName(student)}</span>
            <span className="text-size-13 text-ink-soft">DNI {student.dni}</span>
          </Button>
        </li>
      ))}
    </ul>
  );
}

export function StudentPicker({ student, onChange, disabled }) {
  const [term, setTerm] = useState('');
  const debounced = useDebouncedValue(term, SEARCH_DEBOUNCE_MS);
  const query = useDebtExceptionStudentSearch(debounced);

  if (student) {
    return (
      <div className="flex flex-col gap-2">
        <span className="text-size-12 font-semibold text-ink-soft">Alumno</span>
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-accent-soft px-3 py-2">
          <span className="flex items-center gap-2 text-size-14 text-ink">
            <Check className="size-4 text-brand" aria-hidden="true" />
            <span className="font-semibold">{studentDisplayName(student)}</span>
            <span className="text-ink-soft">DNI {student.dni}</span>
          </span>
          <Button type="button" variant="outline" className="min-h-11" disabled={disabled} onClick={() => onChange(null)}>
            Cambiar alumno
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor="debt-exception-student" className="text-size-12 font-semibold text-ink-soft">
        Alumno
      </Label>
      <Input
        id="debt-exception-student"
        type="search"
        className="min-h-11"
        value={term}
        onChange={(event) => setTerm(event.target.value)}
        placeholder="Buscar por apellido o DNI"
        autoComplete="off"
        disabled={disabled}
      />
      <p className="text-size-12 text-ink-faint">Escribí al menos {SEARCH_MIN_LENGTH} caracteres. Ejemplo: Pérez o 38512774.</p>
      <Results query={query} term={debounced} onPick={(picked) => { onChange(picked); setTerm(''); }} />
    </div>
  );
}
