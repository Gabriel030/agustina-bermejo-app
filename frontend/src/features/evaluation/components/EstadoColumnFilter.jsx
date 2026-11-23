import { Select, SelectTrigger, SelectContent, SelectItem, SelectValue } from '@/components/ui/select';
import { TABS_ESTADO } from '@/lib/notas';

// Filtro de estado como parte del header de la columna "Estado" (no una tira
// de botones aparte arriba de la grilla) — feedback explícito del usuario:
// una segunda fila de pills sobre la tabla "daba feo", desconectada de lo que
// filtraba. Reusado por TabMaterias.jsx y TabNotas.jsx.
export function EstadoColumnFilter({ value, onChange }) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger
        size="sm"
        className="h-auto gap-1 border-none bg-transparent p-0 text-xs font-semibold text-ink-faint shadow-none hover:text-ink data-[state=open]:text-ink"
      >
        <SelectValue />
      </SelectTrigger>
      <SelectContent align="end">
        {TABS_ESTADO.map((t) => (
          <SelectItem key={t.id} value={t.id}>
            {t.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
