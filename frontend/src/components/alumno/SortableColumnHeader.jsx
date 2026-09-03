import { ChevronUp, ChevronDown, ChevronsUpDown } from 'lucide-react';

// Header de columna clickeable para ordenar — reutilizable en cualquier tabla
// (Materias/Notas/Asistencia/Plan de estudio y las que sigan). `sortState` es
// el resultado de useOrdenamiento().estadoDe(sortId): null si la columna no
// está en la cadena de orden, o { direccion, prioridad } si sí — `prioridad`
// se muestra como un numerito superíndice cuando hay 2 criterios activos, para
// que quede claro cuál desempata a cuál. `extra` deja combinar el toggle de
// orden con el filtro que ya tenía esa columna (AnioColumnFilter/EstadoColumnFilter),
// en el mismo header, sin agregar una fila de controles aparte.
export function SortableColumnHeader({ label, sortId, sortState, onToggle, extra }) {
  return (
    <div className="flex items-center gap-1.5">
      <button
        type="button"
        onClick={() => onToggle(sortId)}
        className="flex items-center gap-1 text-xs font-semibold text-ink-faint hover:text-ink"
      >
        {label}
        {sortState ? (
          <span className="flex items-center gap-0.5 text-primary">
            {sortState.direccion === 'asc' ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
            {sortState.prioridad > 1 && <span className="text-[10px] font-bold">{sortState.prioridad}</span>}
          </span>
        ) : (
          <ChevronsUpDown className="h-3.5 w-3.5 opacity-30" />
        )}
      </button>
      {extra}
    </div>
  );
}
