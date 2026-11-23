import { Select, SelectTrigger, SelectContent, SelectItem, SelectValue } from '@/components/ui/select';

// Filtro de año curricular como parte del header de la columna "Año" — mismo
// criterio que EstadoColumnFilter.jsx (filtro embebido en la columna, no una
// tira de botones aparte). `anios` son los años presentes en los datos
// actuales (ver lib/notas.js, aniosDisponibles) — nunca una lista fija, la
// grilla no ofrece filtrar por un año que no tiene ninguna materia.
export function AnioColumnFilter({ anios, value, onChange }) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger
        size="sm"
        className="h-auto gap-1 border-none bg-transparent p-0 text-xs font-semibold text-ink-faint shadow-none hover:text-ink data-[state=open]:text-ink"
      >
        <SelectValue />
      </SelectTrigger>
      <SelectContent align="end">
        <SelectItem value="todos">Todos</SelectItem>
        {anios.map((anio) => (
          <SelectItem key={anio} value={String(anio)}>
            {anio}° año
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
